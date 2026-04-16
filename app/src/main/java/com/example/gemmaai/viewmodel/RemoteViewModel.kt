package com.example.gemmaai.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemmaai.data.ChatMessage
import com.example.gemmaai.data.MessageType
import com.example.gemmaai.data.Role
import com.example.gemmaai.socket.AntigravityClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class RemoteViewModel(private val context: Context) : ViewModel() {

    private val prefs = context.getSharedPreferences("gemma_prefs", Context.MODE_PRIVATE)
    private val client = AntigravityClient()

    val messages = mutableStateListOf<ChatMessage>()
    val isConnected = mutableStateOf(false)
    val isGenerating = mutableStateOf(false)
    val connectionStatus = mutableStateOf("Scanning network...")
    val autoRunEnabled = mutableStateOf(false)
    val autoAllowEnabled = mutableStateOf(false)
    val savedServerUrl = mutableStateOf("")
    val savedHttpPort = mutableStateOf("5000")

    private var currentResponseId: String? = null

    init {
        viewModelScope.launch {
            tryConnectSaved()
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt == 0) return null
            return String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private suspend fun tryConnectSaved() {
        val savedUrl = prefs.getString("server_url", "") ?: ""
        if (savedUrl.isNotBlank()) {
            connectionStatus.value = "Trying saved server..."
            client.updateServerAddress(savedUrl)
            client.updatePorts(5005, 5005)
            savedServerUrl.value = savedUrl
            savedHttpPort.value = "5005"
            setupClient()
            return
        }
        autoDiscoverServer()
    }

    private suspend fun autoDiscoverServer() = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            connectionStatus.value = "No WiFi. Connect to same network."
            return@withContext
        }

        val localIp = getLocalIpAddress()
        if (localIp == null) {
            connectionStatus.value = "Cannot get local IP"
            return@withContext
        }

        connectionStatus.value = "Scanning network..."

        val baseIp = localIp.substringBeforeLast(".")
        val targetPort = 5005

        for (lastOctet in 1..254) {
            val targetIp = "$baseIp.$lastOctet"
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(targetIp, targetPort), 80)
                socket.close()
                connectionStatus.value = "Found server at $targetIp!"
                prefs.edit().putString("server_url", "http://$targetIp").apply()
                client.updateServerAddress("http://$targetIp")
                client.updatePorts(targetPort, targetPort)
                savedServerUrl.value = "http://$targetIp"
                savedHttpPort.value = targetPort.toString()
                setupClient()
                return@withContext
            } catch (e: Exception) {
                continue
            }
        }

        connectionStatus.value = "Server not found. Check server is running."
    }

    fun getSavedSettings(): Triple<String, Int, Int> {
        val url = savedServerUrl.value.ifBlank { prefs.getString("server_url", "") ?: "" }
        val httpPort = prefs.getInt("http_port", 5005)
        val wsPort = prefs.getInt("ws_port", 5005)
        return Triple(url, httpPort, wsPort)
    }

    fun updateServerUrl(url: String, httpPort: Int, wsPort: Int) {
        client.disconnectWebSocket()
        
        prefs.edit()
            .putString("server_url", url)
            .putInt("http_port", httpPort)
            .putInt("ws_port", wsPort)
            .apply()
        
        client.updateServerAddress(url)
        client.updatePorts(httpPort, wsPort)
        savedServerUrl.value = url
        savedHttpPort.value = httpPort.toString()
        connectionStatus.value = "Connecting..."
        
        setupClient()
    }

    fun reconnect() {
        client.disconnectWebSocket()
        viewModelScope.launch {
            delay(500)
            connectionStatus.value = "Reconnecting..."
            client.connectWebSocket()
        }
    }

    private fun setupClient() {
        client.onConnected = {
            isConnected.value = true
            connectionStatus.value = "Connected"
            fetchStats()
            addSystemMessage("Connected to Remote IDE!")
        }

        client.onDisconnected = {
            isConnected.value = false
        }

        client.onError = { error ->
            if (!connectionStatus.value.contains("Connecting")) {
                connectionStatus.value = error
            }
        }

        client.onMessage = { title, content, imageUrl ->
            if (content.isNotBlank() || imageUrl != null) {
                handleResponse(title, content, imageUrl)
            }
        }

        client.connectWebSocket()
    }

    private fun fetchStats() {
        viewModelScope.launch {
            delay(500)
            client.getStats().onSuccess { stats ->
                autoRunEnabled.value = stats.autoRun
                autoAllowEnabled.value = stats.autoAllow
            }
        }
    }

    private fun handleResponse(title: String, content: String, imageUrl: String? = null) {
        val newType = if (imageUrl != null) MessageType.IMAGE else MessageType.TEXT
        
        if (currentResponseId != null) {
            val idx = messages.indexOfFirst { it.id == currentResponseId }
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(
                    content = content, 
                    type = newType,
                    imageUri = imageUrl
                )
            }
            isGenerating.value = false
            currentResponseId = null // clear out so we can receive new messages correctly
        } else {
            if (title == "Antigravity") {
                // New unsolicited response from AI
                messages.add(ChatMessage(
                    role = Role.ASSISTANT,
                    content = content,
                    type = newType,
                    imageUri = imageUrl
                ))
            } else {
                // Unsolicited WebSocket message — show as system message
                addSystemMessage(if (title.isNotBlank()) "[$title] $content" else content)
            }
        }
    }

    private fun addSystemMessage(content: String) {
        messages.add(ChatMessage(role = Role.SYSTEM, content = content, type = MessageType.TEXT))
    }

    fun send(command: String) {
        if (command.isBlank()) return

        messages.add(ChatMessage(role = Role.USER, content = command, type = MessageType.TEXT))

        if (!isConnected.value) {
            addSystemMessage("Not connected. Enter server URL in settings.")
            return
        }

        val responseId = java.util.UUID.randomUUID().toString()
        currentResponseId = responseId

        messages.add(ChatMessage(id = responseId, role = Role.ASSISTANT, content = "", type = MessageType.LOADING))
        isGenerating.value = true

        viewModelScope.launch {
            client.sendCommand(command)
                .onSuccess { response ->
                    // If the IDE queued the command, keep loading — response comes via WebSocket
                    if (response.status == "queued") {
                        // Update the loading message to show it's queued
                        val idx = messages.indexOfFirst { it.id == currentResponseId }
                        if (idx >= 0) {
                            messages[idx] = messages[idx].copy(
                                content = "Processing...",
                                type = MessageType.LOADING
                            )
                        }
                        // Don't set isGenerating = false; WebSocket handler will do it
                    } else {
                        // Direct response (non-queued)
                        isGenerating.value = false
                        val idx = messages.indexOfFirst { it.id == currentResponseId }
                        if (idx >= 0) {
                            messages[idx] = messages[idx].copy(
                                content = response.content.ifBlank { response.title.ifBlank { "Done" } },
                                type = if (response.status == "error") MessageType.ERROR else MessageType.TEXT
                            )
                        }
                    }
                }
                .onFailure { error ->
                    isGenerating.value = false
                    val idx = messages.indexOfFirst { it.id == currentResponseId }
                    if (idx >= 0) {
                        messages[idx] = messages[idx].copy(
                            content = "Error: ${error.message}",
                            type = MessageType.ERROR
                        )
                    }
                }
        }
    }

    fun uploadFile(fileName: String, fileBytes: ByteArray) {
        if (!isConnected.value) {
            addSystemMessage("Not connected. Cannot upload file.")
            return
        }

        messages.add(ChatMessage(role = Role.USER, content = "Uploading: $fileName", type = MessageType.TEXT))
        addSystemMessage("Starting upload of $fileName...")

        viewModelScope.launch {
            client.uploadFile(fileName, fileBytes)
                .onSuccess { msg ->
                    addSystemMessage(msg)
                }
                .onFailure { error ->
                    messages.add(
                        ChatMessage(
                            role = Role.SYSTEM,
                            content = "Failed to upload $fileName: ${error.message}",
                            type = MessageType.ERROR
                        )
                    )
                }
        }
    }

    fun toggleAutoRun() {
        if (!isConnected.value) return
        viewModelScope.launch {
            client.toggleAutoRun().onSuccess { enabled ->
                autoRunEnabled.value = enabled
                addSystemMessage("Auto-run ${if (enabled) "enabled" else "disabled"}")
            }
        }
    }

    fun toggleAutoAllow() {
        if (!isConnected.value) return
        viewModelScope.launch {
            client.toggleAutoAllow().onSuccess { enabled ->
                autoAllowEnabled.value = enabled
                addSystemMessage("Auto-allow ${if (enabled) "enabled" else "disabled"}")
            }
        }
    }

    fun clearChat() {
        messages.clear()
        currentResponseId = null
    }

    override fun onCleared() {
        super.onCleared()
        client.disconnectWebSocket()
    }
}
