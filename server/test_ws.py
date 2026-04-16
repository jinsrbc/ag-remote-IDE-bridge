import asyncio
import websockets
import json

async def test_ws_command():
    uri = "ws://127.0.0.1:9812"
    try:
        async with websockets.connect(uri) as ws:
            print("Connected! Sending command...")
            # Let's try sending a simple json payload
            payload = json.dumps({"text": "hello from python ws"})
            await ws.send(payload)
            print("Sent! Waiting for response...")
            response = await ws.recv()
            print(f"Response: {response}")
    except Exception as e:
        print(f"Error: {e}")

asyncio.run(test_ws_command())
