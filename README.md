# Antigravity Remote IDE App

## Overview
The **Antigravity Remote IDE App** is a highly-optimized, lightning-fast Pure Remote IDE Automation Bridge. It connects an Android mobile device to a local desktop development machine running an Antigravity AI Agent over a robust WebSocket connection. 

It functions exclusively as a mobile UI terminal for remote interactions with a desktop-based Python backend, enabling real-time bidirectional command execution, monitoring, and file sharing securely within your local network (LAN).

## Key Features
- **Real-Time Remote Control**: Stream chat commands directly to your Antigravity desktop IDE agent over an ultra-low latency WebSocket connection.
- **Multimodal Feedback**: Instantly view generated AI images (via `/artifact/` routes) streamed back to your mobile device using native Coil integration.
- **File Uploading**: Fully integrated native Android file picker to instantly upload documents and scripts directly to your PC workspace.
- **Type Injection Engine**: Automatic OS-level keyboard injection on the desktop utilizing `PyAutoGUI`, allowing the mobile app to type commands directly into your desktop IDE terminal window automatically.
- **Native Text Extraction**: Seamless long-press text extraction and copying integrated directly into the Android Compose UI.
- **Zero-Config Auto-Discovery**: The Mobile front-end automatically scans subnets to discover and connect to the desktop server on the local network automatically without the need for manual IP configuration.

## Architecture & Implementation Stack

### Mobile Frontend (`app/`)
- **Framework**: Native Android UI built 100% in Kotlin using **Jetpack Compose** (`ChatScreen.kt`, `MessageBubble.kt`, `QRScannerScreen.kt`).
- **Network Layer**: Specialized `AntigravityClient.kt` implementing raw WebSockets and HTTP POST endpoints over `OkHttp3`.
- **Image Rendering**: Utilizes `Coil` for asynchronous image loading to render dynamically generated visualization artifacts from the remote agent.

### Desktop Backend (`server/`)
- **Framework**: High-performance Python server built using `FastAPI` and `Uvicorn`.
- **System Invocation Engine**: Relies on asynchronous `subprocess.PIPE` shells and `pyautogui` for bare-metal IDE keystroke automations and shell executions.
- **State Synchronization**: Continuous `asyncio` directory watchers actively observe `agent_response.json` tracking files to securely broadcast UI updates and agent responses out to all connected WebSocket clients on the LAN.

## Getting Started

### 1. Start the Desktop Server
*Requirements: Python 3.9+*
```bash
cd server
pip install -r requirements.txt
python antigravity_server.py
```
This will launch the UDP-broadcasting FastAPI backend on port `5005` in local network mode.

### 2. Connect the Mobile Frontend
- Install the Android APK on a device connected to the **same Wi-Fi Network**.
- Open the application. The system will auto-scan the underlying IP range (e.g., `192.168.1.X:5005`), discover the host, and securely establish the WebSocket tunnel.

## Security & Privacy
This repository is sanitized for public open-source usage. All local LLM bindings, heavy C++ environments (`llama_jni.cpp`), and custom filesystem dependencies have been detached. It operates completely off-grid without requiring external cloud relay services or access tokens in standard mode.
