# Remote IDE Control System

A Claude Remote-style system for controlling Antigravity IDE from your Android device.

## System Overview

```
Android App (Controller)
          ⇅ HTTP + WebSocket (Local Network)
  Antigravity IDE + Automation Extension
          ↓
Built-in AI (Gemini, Claude, etc.)
```

## Prerequisites

1. **Antigravity IDE** installed on your PC
2. **Android Studio** to build the Android app
3. **Android device** on the same network as your PC
4. **Python 3.8+** for the server

## Installation

### Step 1: Install Python Dependencies

```bash
cd server
pip install -r requirements.txt
```

### Step 2: Install Antigravity Automation Extension

1. Open Antigravity IDE
2. Press `Ctrl+Shift+X` to open Extensions
3. Search for "Antigravity Automation"
4. Click Install

Or install from [Open VSX Registry](https://open-vsx.org/extension/joecodecreations/antigravity-automation)

### Step 3: Start the Server

```bash
cd server
python antigravity_server.py
```

The server runs on port 5005 by default.

### Step 4: Build Android App

Open the project in Android Studio and build the APK:

```bash
cd app
./gradlew assembleDebug
```

Or use Android Studio's Build → Build APK option.

Install the APK on your Android device.

## Usage

1. Start Antigravity IDE
2. Start the Antigravity Bridge (Ctrl+Shift+P)
3. Run the server on your PC:
   ```bash
   python server/antigravity_server.py
   ```
4. The mobile app will automatically discover and connect to the server
5. Type commands and tap send to get AI responses

## Features

- **Auto-Discovery**: Mobile automatically finds the server on the local network
- **Send Commands**: Type prompts and receive AI responses
- **Live Streaming**: Real-time AI output via WebSocket
- **Auto-Run Toggle**: Tap the play button to enable auto-running AI responses
- **QR Code Scanner**: Scan server QR code for manual connection
- **Configurable Ports**: Tap settings icon to change HTTP/WebSocket ports
- **Auto-Reconnect**: Automatically reconnects on connection loss

### Default Ports

| Service | Port |
|---------|------|
| HTTP API | 5005 |
| WebSocket | 5005 |

## Mobile Connection

The mobile app will automatically scan the local network for the server:

1. Ensure mobile and server are on the same WiFi network
2. Start the server on your PC
3. Open the mobile app - it will auto-discover and connect
4. If auto-discovery fails, use the QR code or manually enter the server IP

### Manual Connection

If auto-discovery doesn't work:

1. Note the server IP from the terminal (e.g., `http://192.168.1.x:5005`)
2. Tap the settings icon in the app
3. Enter the server URL manually

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Server status |
| `/scan_mobile` | GET | Scan network for mobile devices |
| `/mobile_status` | GET | Get mobile connection status |
| `/send_command` | POST | Send prompt to AI |
| `/toggle_auto_run` | POST | Toggle auto-run |
| `/toggle_auto_allow` | POST | Toggle auto-allow |
| `/stats` | GET | Get usage statistics |
| `/ws` | WS | Stream AI responses |

## Troubleshooting

### Connection Issues

1. **Mobile cannot find server**:
   - Ensure mobile and server are on the same WiFi network
   - Check the server is running and note the IP address
   - Ensure no firewall is blocking port 5005

2. **"Connection refused"**:
   - Ensure the server is running
   - Check that port 5005 is not in use
   - Verify your firewall allows connections

3. **"Extension not found"**:
   - Install Antigravity Automation extension from Open VSX

4. **No AI responses**:
   - Ensure you're signed into Antigravity with an AI subscription
   - Check Antigravity's own chat works first

## Security Notes

- The server only allows connections from the local network
- No authentication by default (local network access is trusted)
- Ensure your network is secure

## License

MIT