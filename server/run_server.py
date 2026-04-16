"""Minimal Antigravity Server - starts instantly, no QR code blocking."""
import sys
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding='utf-8')

import asyncio
import json
import socket
import uvicorn
import os
from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="Antigravity Server")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# State
active_connections: list[WebSocket] = []
stats = {"auto_run": False, "auto_allow": False, "remote_commands_used": 0, "auto_clicks_used": 0}

AGENT_RESPONSE_FILE = os.path.join(os.path.dirname(__file__), "agent_response.json")
last_mtime = os.path.getmtime(AGENT_RESPONSE_FILE) if os.path.exists(AGENT_RESPONSE_FILE) else 0

async def watch_agent_response():
    global last_mtime
    while True:
        await asyncio.sleep(1)
        if os.path.exists(AGENT_RESPONSE_FILE):
            mtime = os.path.getmtime(AGENT_RESPONSE_FILE)
            if mtime > last_mtime:
                last_mtime = mtime
                try:
                    with open(AGENT_RESPONSE_FILE, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    for conn in list(active_connections):
                        try:
                            await conn.send_json({
                                "title": "Antigravity",
                                "content": data.get("text", ""),
                                "imageUrl": data.get("imageUrl", "")
                            })
                        except Exception:
                            pass
                except Exception:
                    pass

@app.on_event("startup")
async def startup_event():
    asyncio.create_task(watch_agent_response())

@app.get("/")
async def root():
    return {"name": "Antigravity Server", "version": "1.0.0", "status": "running"}


@app.post("/send_command")
async def send_command(request: Request):
    try:
        body = await request.json()
        text = body.get("text", "")
        stats["remote_commands_used"] += 1
        response = await process_command(text)
        # Broadcast to all WS clients
        for conn in list(active_connections):
            try:
                await conn.send_json({"title": "Response", "content": response})
            except Exception:
                pass
        return {"status": "success", "title": "Response", "content": response, "usage": None}
    except Exception as e:
        return {"status": "error", "title": "Error", "content": str(e), "usage": None}


@app.post("/toggle_auto_run")
async def toggle_auto_run():
    stats["auto_run"] = not stats["auto_run"]
    return {"auto_run": stats["auto_run"]}


@app.post("/toggle_auto_allow")
async def toggle_auto_allow():
    stats["auto_allow"] = not stats["auto_allow"]
    return {"auto_allow": stats["auto_allow"]}


@app.get("/stats")
async def get_stats():
    return stats


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    active_connections.append(websocket)
    print(f"[WS] Client connected. Total: {len(active_connections)}")
    try:
        while True:
            data = await websocket.receive_text()
            try:
                message = json.loads(data)
                for conn in list(active_connections):
                    try:
                        await conn.send_json(message)
                    except Exception:
                        pass
            except json.JSONDecodeError:
                await websocket.send_text(data)
    except WebSocketDisconnect:
        active_connections.remove(websocket)
        print(f"[WS] Client disconnected. Total: {len(active_connections)}")
    except Exception:
        if websocket in active_connections:
            active_connections.remove(websocket)


async def process_command(text: str) -> str:
    text_lower = text.lower().strip()
    if any(kw in text_lower for kw in ["hello", "hi", "hey"]):
        return "Hello! I'm your remote assistant. What can I help with?"
    elif any(kw in text_lower for kw in ["help", "what can you do"]):
        return "I can run commands, search, and automate tasks on your PC."
    elif text_lower.startswith("run:"):
        command = text[4:].strip()
        try:
            process = await asyncio.create_subprocess_shell(
                command, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
            )
            stdout, stderr = await process.communicate()
            return stdout.decode() if stdout else (stderr.decode() if stderr else "Done (no output)")
        except Exception as e:
            return f"Error: {e}"
    else:
        return f"Received: {text}\n\nUse 'run:<command>' to execute shell commands."


def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "localhost"


if __name__ == "__main__":
    ip = get_local_ip()
    print(f"\n{'='*50}")
    print(f"  ANTIGRAVITY SERVER - LAN Mode")
    print(f"{'='*50}")
    print(f"  HTTP:  http://{ip}:5000")
    print(f"  WS:    ws://{ip}:5000/ws")
    print(f"{'='*50}")
    print(f"  Connect your phone to: http://{ip}:5000")
    print(f"{'='*50}\n")

    uvicorn.run(app, host="0.0.0.0", port=5000, log_level="info")
