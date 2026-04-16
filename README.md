# Gemma Remote IDE App

## Overview
This application is a **Pure Remote IDE Automation Bridge**. It provides real-time, bidirectional command execution and monitoring connecting an Android mobile device to a local development machine running an Antigravity AI Agent. 

While the project was initially conceived with an onboard Android local LLM (incorporating `llama_jni.cpp` and Gemma GGUF models), **the local LLM capabilities have been completely removed** to streamline the footprint. The application now functions exclusively as a highly optimized, lightning-fast UI terminal for interacting with the desktop-based Python backend.

## Features
- **Real-Time Remote Control**: Stream chat commands directly to your Antigravity desktop IDE agent over a robust WebSocket connection.
- **Multimodal Capabilities**: Instantly view generated AI images streamed back to your device via Coil integration.
- **File Uploading**: Fully integrated native Android file picker to upload files instantly to your PC workspace.
- **Text Selection**: Long-press text extraction and copying natively integrated.
- **Auto-Discovery**: Mobile automatically finds and connects to the server on the local network.

## Architecture
- **Mobile Frontend (Kotlin/Jetpack Compose)**: `com.example.gemmaai.MainActivity` serves as the entry point, drawing the UI using purely Compose elements (`ChatScreen.kt`, `MessageBubble.kt`).
- **Network Layer (`AntigravityClient.kt`)**: Utilizes `OkHttp3` for HTTP Post endpoints and WebSockets for real-time text and payload streaming.
- **Desktop Backend** (`antigravity_server.py`): A Python `FastAPI` instance managing background tunnel creation, handling multipart uploads, and streaming `agent_response.json` states to connected mobile clients. 

## Removal of Legacy Unused Files
- The entire `c++` ndk codebase (`llama.cpp` wrapper) and references in the build pipeline have been wiped to ensure pure reliance on the remote desktop system.

---
*Verified accessible by Antigravity Agent*
