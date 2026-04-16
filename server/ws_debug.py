"""Debug: connect to Antigravity IDE WebSocket and print all messages."""
import asyncio
import websockets
import json

async def listen():
    uri = "ws://127.0.0.1:9812"
    print(f"Connecting to {uri}...")
    try:
        async with websockets.connect(uri) as ws:
            print("Connected! Waiting for messages (press Ctrl+C to stop)...\n")
            async for message in ws:
                print(f"=== RAW MESSAGE ===")
                print(f"Type: {type(message).__name__}")
                print(f"Length: {len(message)}")
                print(f"Content: {message[:2000]}")
                try:
                    parsed = json.loads(message)
                    print(f"JSON keys: {list(parsed.keys())}")
                    for k, v in parsed.items():
                        print(f"  {k}: {str(v)[:200]}")
                except:
                    print("(Not valid JSON)")
                print("=" * 40 + "\n")
    except Exception as e:
        print(f"Error: {e}")

asyncio.run(listen())
