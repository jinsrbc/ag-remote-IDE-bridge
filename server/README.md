# Antigravity Server

A FastAPI + WebSocket server for remote IDE control via local network connection.

## Quick Start

```bash
# Install dependencies
pip install -r requirements.txt

# Start the server (mobile connects automatically on same network)
python antigravity_server.py
```

## Installation

### 1. Install Python Dependencies

```bash
cd server
pip install -r requirements.txt
```

## Usage

### Start Server (Local Network Mode)

```bash
python antigravity_server.py
```

Output:
```
═══════════════════════════════════════════════════════════════
           ANTIGRAVITY REMOTE IDE SERVER v4.0               
═══════════════════════════════════════════════════════════════════════
  Mode: Local Network (Mobile in same WiFi)
  Connect mobile to same network for IDE access
═══════════════════════════════════════════════════════════════

  [LOCAL] Same network access:
    http://192.168.1.x:5005

  [MOBILE] Connect your mobile device to the same WiFi network
    Server will auto-detect mobile devices on the network

  QR Code (Local Network URL):
  ───────────────────────

  ████████████████████████████████
  ██                            ██
  ██  ██  ██  ██  ██  ██  ██  ██
  ...
  ████████████████████████████████

  Server running on port 5005
  Press Ctrl+C to stop
```

### Custom Port

```bash
python antigravity_server.py --port 8080
```

## Mobile Connection

The mobile app automatically scans the local network to find and connect to the server:

1. Ensure mobile and server are on the same WiFi network
2. Start the server on your PC
3. Open the mobile app - it will auto-discover and connect

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Server info |
| `/scan_mobile` | GET | Scan network for mobile devices |
| `/mobile_status` | GET | Get mobile connection status |
| `/send_command` | POST | Send text command, get AI response |
| `/toggle_auto_run` | POST | Toggle auto-run mode |
| `/toggle_auto_allow` | POST | Toggle auto-allow mode |
| `/stats` | GET | Get server statistics |
| `/ws` | WebSocket | Real-time messaging |

## Troubleshooting

### Mobile Cannot Connect

1. Ensure mobile and server are on the same WiFi network
2. Check the server is running and note the IP address displayed
3. Ensure no firewall is blocking port 5005
4. Try manually entering the server IP in the mobile app settings

### Port Already in Use

```bash
python antigravity_server.py --port 8080
```

## Environment Variables

Create a `.env` file in the server directory:

```
# No configuration needed - runs in local network mode by default
```

## Security Notes

- The server allows connections from any origin on the local network
- No authentication by default (local network access only)
- Ensure your network is trusted

## License

MIT License