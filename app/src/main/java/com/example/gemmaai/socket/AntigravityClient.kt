package com.example.gemmaai.socket

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AntigravityClient(
    private var httpPort: Int = 5005,
    private var wsPort: Int = 5005
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var wsListener: WSListener? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 15
    private var reconnectJob: Job? = null
    private var isIntentionalDisconnect = false

    private var useHttps = true
    private var serverAddress = ""

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onMessage: ((String, String, String?) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun updatePorts(httpPort: Int, wsPort: Int) {
        this.httpPort = httpPort
        this.wsPort = wsPort
    }

    fun updateServerAddress(address: String) {
        useHttps = address.startsWith("https://")
        val withoutScheme = address
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
            .substringBefore("/")
            .substringBefore("?")

        // Extract host and port if present (e.g., "192.168.x.x:5005")
        if (withoutScheme.contains(":")) {
            serverAddress = withoutScheme.substringBefore(":")
            val parsedPort = withoutScheme.substringAfter(":").toIntOrNull()
            if (parsedPort != null) {
                httpPort = parsedPort
                wsPort = parsedPort
            }
        } else {
            serverAddress = withoutScheme
        }
    }

    fun isConnected(): Boolean = ws != null

    private fun buildUrl(path: String): String {
        val protocol = if (useHttps) "https" else "http"
        // For HTTPS tunnels (loca.lt etc.), omit port. For LAN (http), always include port.
        val portStr = if (useHttps) "" else ":$httpPort"
        return "$protocol://$serverAddress$portStr$path"
    }

    private fun buildWsUrl(): String {
        val protocol = if (useHttps) "wss" else "ws"
        val portStr = if (useHttps) "" else ":$wsPort"
        return "$protocol://$serverAddress$portStr/ws"
    }

    suspend fun sendCommand(text: String): Result<CommandResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        
        for (attempt in 1..3) {
            try {
                val json = JSONObject().put("text", text)
                val body = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(buildUrl("/send_command"))
                    .post(body)
                    .header("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.code == 200 && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    return@withContext Result.success(
                        CommandResponse(
                            status = jsonResponse.optString("status", "unknown"),
                            title = jsonResponse.optString("title", ""),
                            content = jsonResponse.optString("content", ""),
                            usage = null
                        )
                    )
                }
                lastException = Exception("HTTP ${response.code}")
            } catch (e: Exception) {
                lastException = e
                if (attempt < 3) delay(1500L * attempt)
            }
        }
        Result.failure(lastException ?: Exception("Request failed"))
    }

    suspend fun uploadFile(fileName: String, fileBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    fileName,
                    fileBytes.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(buildUrl("/upload_file"))
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                return@withContext Result.success(JSONObject(responseBody).optString("message", "Uploaded successfully"))
            }
            Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleAutoRun(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = "{}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(buildUrl("/toggle_auto_run"))
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                return@withContext Result.success(JSONObject(responseBody).optBoolean("auto_run", false))
            }
            Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleAutoAllow(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = "{}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(buildUrl("/toggle_auto_allow"))
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                return@withContext Result.success(JSONObject(responseBody).optBoolean("auto_allow", false))
            }
            Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStats(): Result<StatsResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(buildUrl("/stats"))
                .get()
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                return@withContext Result.success(
                    StatsResponse(
                        autoRun = json.optBoolean("auto_run", false),
                        autoAllow = json.optBoolean("auto_allow", false),
                        remoteCommandsUsed = json.optInt("remote_commands_used", 0),
                        autoClicksUsed = json.optInt("auto_clicks_used", 0)
                    )
                )
            }
            Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun connectWebSocket() {
        if (serverAddress.isBlank()) {
            onError?.invoke("Server address not set. Enter URL in settings.")
            return
        }

        isIntentionalDisconnect = false
        reconnectJob?.cancel()

        val request = Request.Builder()
            .url(buildWsUrl())
            .build()

        wsListener = WSListener()
        ws = client.newWebSocket(request, wsListener!!)
    }

    fun disconnectWebSocket() {
        isIntentionalDisconnect = true
        reconnectJob?.cancel()
        reconnectAttempts = 0
        ws?.close(1000, "Disconnect")
        ws = null
        wsListener = null
    }

    private fun scheduleReconnect() {
        if (isIntentionalDisconnect) return
        
        reconnectAttempts++
        if (reconnectAttempts >= maxReconnectAttempts) {
            onError?.invoke("Unable to connect. Check internet and restart server.")
            return
        }

        val delayMs = (1000L * minOf(reconnectAttempts, 8)).coerceAtMost(8000L)
        
        reconnectJob = CoroutineScope(Dispatchers.Main).launch {
            delay(delayMs)
            if (!isIntentionalDisconnect && ws == null) {
                onError?.invoke("Retrying... (${reconnectAttempts}/${maxReconnectAttempts})")
                connectWebSocket()
            }
        }
    }

    inner class WSListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts = 0
            onConnected?.invoke()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                val imageUrl = json.optString("imageUrl", "").takeIf { it.isNotBlank() }
                onMessage?.invoke(json.optString("title", ""), json.optString("content", ""), imageUrl)
            } catch (e: Exception) {
                if (text.isNotBlank()) onMessage?.invoke("", text, null)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            onDisconnected?.invoke()
            if (!isIntentionalDisconnect) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val error = t.message ?: "Connection failed"
            onDisconnected?.invoke()
            if (!isIntentionalDisconnect) {
                onError?.invoke(error)
                scheduleReconnect()
            }
        }
    }

    data class CommandResponse(
        val status: String,
        val title: String,
        val content: String,
        val usage: String?
    )

    data class StatsResponse(
        val autoRun: Boolean,
        val autoAllow: Boolean,
        val remoteCommandsUsed: Int,
        val autoClicksUsed: Int
    )
}
