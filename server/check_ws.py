import asyncio
import websockets

async def check():
    try:
        async with websockets.connect("ws://127.0.0.1:9812/ws") as ws:
            print("Connected to /ws")
    except Exception as e:
        print(f"Error /ws: {e}")
        
    try:
        async with websockets.connect("ws://127.0.0.1:9812/") as ws:
            print("Connected to /")
    except Exception as e:
        print(f"Error /: {e}")

asyncio.run(check())
