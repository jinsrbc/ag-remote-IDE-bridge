#!/usr/bin/env python3
import sys
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding='utf-8')
"""
Antigravity Server - Remote IDE Control System
Local network access for Antigravity IDE (mobile within same network).
"""

import argparse
import asyncio
import json
import os
import signal
import socket
import subprocess
import sys
import pyautogui
from fastapi.responses import FileResponse
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path

AGENT_RESPONSE_FILE = Path("C:/Users/jinsc/.gemini/antigravity/server/agent_response.json")
last_mtime = AGENT_RESPONSE_FILE.stat().st_mtime if AGENT_RESPONSE_FILE.exists() else 0
from typing import Optional

import qrcode
import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware


load_dotenv()


class QRCodeGenerator:
    @staticmethod
    def generate_ascii(data: str) -> str:
        qr = qrcode.QRCode(version=1, error_correction=qrcode.constants.ERROR_CORRECT_L, box_size=1, border=0)
        qr.add_data(data)
        qr.make(fit=True)
        img = qr.make_image(fill_color="black", back_color="white")
        img = img.resize((img.width * 4, img.height * 4))
        
        ascii_art = []
        for y in range(img.height):
            line = []
            for x in range(img.width):
                pixel = img.getpixel((x, y))
                is_dark = sum(pixel[:3]) < 384 if isinstance(pixel, tuple) else pixel < 128
                line.append("██" if is_dark else "  ")
            ascii_art.append("".join(line))
        return "\n".join(ascii_art)

    @staticmethod
    def generate_png(data: str, filepath: str) -> str:
        qr = qrcode.QRCode(version=None, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=20, border=8)
        qr.add_data(data)
        qr.make(fit=True)
        img = qr.make_image(fill_color="black", back_color="white")
        img = img.resize((800, 800))
        img.save(filepath, "PNG", quality=95)
        return filepath


class NetworkManager:
    def __init__(self):
        self.port: int = 5005
        self.local_ip: Optional[str] = None
        self.broadcast_ip: Optional[str] = None

    def get_local_ip(self) -> Optional[str]:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            self.local_ip = ip
            return ip
        except Exception:
            return None

    def get_broadcast_ip(self) -> Optional[str]:
        local = self.get_local_ip()
        if local:
            parts = local.rsplit(".", 1)
            if len(parts) == 2:
                self.broadcast_ip = f"{parts[0]}.255"
                return self.broadcast_ip
        return None

    def scan_network_for_mobile(self) -> list:
        import threading
        found = []
        local = self.get_local_ip()
        if not local:
            return found

        base_ip = local.rsplit(".", 1)[0]
        ips_to_check = [f"{base_ip}.{i}" for i in range(1, 255)]

        def check_ip(ip: str):
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                sock.settimeout(0.5)
                result = sock.connect_ex((ip, self.port))
                sock.close()
                if result == 0:
                    found.append(ip)
            except Exception:
                pass

        threads = []
        for ip in ips_to_check:
            t = threading.Thread(target=check_ip, args=(ip,))
            t.start()
            threads.append(t)

        for t in threads:
            t.join()

        return found


class ConnectionManager:
    def __init__(self):
        self.active_connections: list[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def broadcast(self, message: dict):
        disconnected = []
        for connection in self.active_connections:
            try:
                await connection.send_json(message)
            except Exception:
                disconnected.append(connection)
        for conn in disconnected:
            self.disconnect(conn)


class Stats:
    def __init__(self):
        self.auto_run = False
        self.auto_allow = False
        self.remote_commands_used = 0
        self.auto_clicks_used = 0
        self.lock = asyncio.Lock()

    async def toggle_auto_run(self) -> bool:
        async with self.lock:
            self.auto_run = not self.auto_run
            return self.auto_run

    async def toggle_auto_allow(self) -> bool:
        async with self.lock:
            self.auto_allow = not self.auto_allow
            return self.auto_allow

    async def increment_commands(self):
        async with self.lock:
            self.remote_commands_used += 1

    async def get_stats(self) -> dict:
        async with self.lock:
            return {
                "auto_run": self.auto_run,
                "auto_allow": self.auto_allow,
                "remote_commands_used": self.remote_commands_used,
                "auto_clicks_used": self.auto_clicks_used
            }


async def watch_agent_response(app: FastAPI):
    global last_mtime
    connections = app.state.connections
    while True:
        await asyncio.sleep(1)
        if AGENT_RESPONSE_FILE.exists():
            mtime = AGENT_RESPONSE_FILE.stat().st_mtime
            if mtime > last_mtime:
                try:
                    with open(AGENT_RESPONSE_FILE, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    
                    image_url = data.get("imageUrl", "")
                    if image_url and not image_url.startswith("http"):
                        # Convert Windows paths or just prefix /artifact/
                        image_url = image_url.replace("\\", "/")
                        if not image_url.startswith("/artifact/"):
                            image_url = f"/artifact/{image_url}"
                        image_url = f"{app.state.base_url}{image_url}"

                    await connections.broadcast({
                        "title": "Antigravity", 
                        "content": data.get("text", ""),
                        "imageUrl": image_url
                    })
                    last_mtime = mtime
                except Exception as e:
                    pass # Keep old last_mtime so we try again next second

@asynccontextmanager
async def lifespan(app: FastAPI):
    network_manager = app.state.network_manager
    watcher_task = asyncio.create_task(watch_agent_response(app))
    
    print("\n" + "=" * 60)
    print("  ANTIGRAVITY REMOTE IDE SERVER")
    print("=" * 60)
    
    local_ip = network_manager.get_local_ip()
    app.state.base_url = f"http://{local_ip}:{app.state.port}" if local_ip else f"http://localhost:{app.state.port}"
    app.state.tunnel_type = "local"
    
    print("\n" + "=" * 60)
    print("  CONNECTION INFO")
    print("=" * 60 + "\n")
    
    print("  [LOCAL] Same network access:")
    print(f"    http://{local_ip}:{app.state.port}" if local_ip else "    http://localhost:5000")
    print("\n  [MOBILE] Connect your mobile device to the same WiFi network")
    print("    Server will auto-detect mobile devices on the network")
    
    print("\n" + "-" * 60)
    print("  QR Code (Local Network URL):")
    print("-" * 60 + "\n")
    
    display_url = app.state.base_url
    ascii_qr = QRCodeGenerator.generate_ascii(display_url)
    print(f"\x1b[36m{ascii_qr}\x1b[0m")
    
    print("\n" + "-" * 60)
    print(f"  Server running on port {app.state.port}")
    print("  Press Ctrl+C to stop")
    print("-" * 60 + "\n")
    
    qr_png_path = Path(__file__).parent / "server_qr.png"
    QRCodeGenerator.generate_png(display_url, str(qr_png_path))
    print(f"[IMG] QR code saved to: {qr_png_path}\n")
    
    yield
    
    watcher_task.cancel()
    print("\n[!] Shutting down...")
    print("[OK] Done")


def create_app(port: int = 5000) -> FastAPI:
    app = FastAPI(title="Antigravity Remote IDE", version="4.0.0", lifespan=lifespan)
    
    app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])
    
    network_manager = NetworkManager()
    stats = Stats()
    connections = ConnectionManager()
    
    app.state.port = port
    app.state.network_manager = network_manager
    app.state.stats = stats
    app.state.connections = connections
    app.state.base_url = ""
    app.state.tunnel_type = "local"
    app.state.mobile_devices = []

    @app.get("/artifact/{file_path:path}")
    async def get_artifact(file_path: str):
        if os.path.exists(file_path):
            return FileResponse(file_path)
        return {"error": "File not found"}

    @app.get("/")
    async def root():
        return {
            "name": "Antigravity Remote IDE",
            "version": "4.0.0",
            "status": "running",
            "local_ip": app.state.network_manager.get_local_ip(),
            "server_url": app.state.base_url,
            "tunnel_type": app.state.tunnel_type
        }

    @app.get("/scan_mobile")
    async def scan_mobile():
        devices = app.state.network_manager.scan_network_for_mobile()
        app.state.mobile_devices = devices
        return {"devices": devices, "count": len(devices)}

    @app.get("/mobile_status")
    async def mobile_status():
        local_ip = app.state.network_manager.get_local_ip()
        return {
            "server_ip": local_ip,
            "server_port": app.state.port,
            "server_url": app.state.base_url,
            "connected_devices": app.state.mobile_devices,
            "network_ready": local_ip is not None
        }

    @app.post("/send_command")
    async def send_command(request: Request):
        try:
            body = await request.json()
            text = body.get("text", "")
            await stats.increment_commands()
            response = await process_command(text)
            return {"status": "queued", "title": "Response", "content": response}
        except Exception as e:
            return {"status": "error", "title": "Error", "content": str(e)}

    @app.post("/upload_file")
    async def upload_file_endpoint(file: UploadFile = File(...)):
        try:
            save_path = Path(__file__).parent / "uploads"
            save_path.mkdir(exist_ok=True)
            
            file_location = save_path / file.filename
            with open(file_location, "wb+") as f:
                f.write(await file.read())
                
            return {"message": f"Successfully uploaded {file.filename} to {file_location}"}
        except Exception as e:
            return {"message": f"Upload failed: {str(e)}"}

    @app.post("/toggle_auto_run")
    async def toggle_auto_run():
        new_state = await stats.toggle_auto_run()
        return {"auto_run": new_state}

    @app.post("/toggle_auto_allow")
    async def toggle_auto_allow():
        new_state = await stats.toggle_auto_allow()
        return {"auto_allow": new_state}

    @app.get("/stats")
    async def get_stats():
        return await stats.get_stats()

    @app.websocket("/ws")
    async def websocket_endpoint(websocket: WebSocket):
        await connections.connect(websocket)
        if AGENT_RESPONSE_FILE.exists():
            try:
                with open(AGENT_RESPONSE_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
                image_url = data.get("imageUrl", "")
                if image_url and not image_url.startswith("http"):
                    image_url = image_url.replace("\\", "/")
                    if not image_url.startswith("/artifact/"):
                        image_url = f"/artifact/{image_url}"
                    image_url = f"{app.state.base_url}{image_url}"
                await websocket.send_json({
                    "title": "Antigravity", 
                    "content": data.get("text", ""),
                    "imageUrl": image_url
                })
            except Exception:
                pass
        try:
            while True:
                data = await websocket.receive_text()
                try:
                    message = json.loads(data)
                    await connections.broadcast({"title": message.get("title", ""), "content": message.get("content", "")})
                except json.JSONDecodeError:
                    await websocket.send_text(data)
        except WebSocketDisconnect:
            connections.disconnect(websocket)

    return app


async def process_command(text: str) -> str:
    text_lower = text.lower().strip()
    
    if text_lower.startswith("run:") or text_lower.startswith("run "):
        cmd = text_lower[4:].strip()
        try:
            proc = await asyncio.create_subprocess_shell(cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
            stdout, stderr = await proc.communicate()
            output = stdout.decode().strip() or stderr.decode().strip() or "Command executed successfully (no output)."
            return output[:2000]
        except Exception as e:
            return f"Error executing command: {e}"
    
    if text_lower.startswith("open:"):
        app = text_lower[5:].strip()
        return f"Opening: {app}"
    
    # NEW BEHAVIOR: Instead of echoing, use PyAutoGUI to type the user's message 
    # directly into whatever text box is currently active (like the IDE chat box!)
    try:
        # Type the message
        pyautogui.write(text, interval=0.01)
        # Press Enter to send it in the chat
        pyautogui.press("enter")
        return f"Typed injected into IDE chat: {text}"
    except Exception as e:
        return f"Failed to type automatically: {e}"


def get_local_ip() -> Optional[str]:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return None


def main():
    print(f"""
╔══════════════════════════════════════════════════════════════╗
║           ANTIGRAVITY REMOTE IDE SERVER v4.0               ║
╠══════════════════════════════════════════════════════════════╣
║  Mode: Local Network (Mobile in same WiFi)
║  Connect mobile to same network for IDE access
╚══════════════════════════════════════════════════════════════╝
    """)
    
    app = create_app(port=5005)
    
    config = uvicorn.Config(app=app, host="0.0.0.0", port=5005, log_level="info")
    server = uvicorn.Server(config)
    
    signal.signal(signal.SIGINT, lambda s, f: sys.exit(0))
    signal.signal(signal.SIGTERM, lambda s, f: sys.exit(0))
    
    server.run()


if __name__ == "__main__":
    main()
