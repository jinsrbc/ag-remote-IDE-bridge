package com.example.gemmaai.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AttachFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.gemmaai.data.MessageType
import com.example.gemmaai.ui.components.*
import com.example.gemmaai.ui.theme.*
import com.example.gemmaai.viewmodel.RemoteViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: RemoteViewModel,
    onVoiceClick: () -> Unit = {},
    isListening: Boolean = false
) {
    val messages = viewModel.messages
    val isGenerating by viewModel.isGenerating
    val connectionStatus by viewModel.connectionStatus
    val isConnected by viewModel.isConnected
    val autoRunEnabled by viewModel.autoRunEnabled
    val autoAllowEnabled by viewModel.autoAllowEnabled

    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf("") }
    var httpPort by remember { mutableStateOf("5005") }
    var wsPort by remember { mutableStateOf("5005") }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        var fileName = "attachment"

                        // Try to get actual file name
                        context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }

                        viewModel.uploadFile(fileName, bytes)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val lastMessageContent by remember {
        derivedStateOf { messages.lastOrNull()?.content }
    }

    LaunchedEffect(Unit) {
        val (url, http, ws) = viewModel.getSavedSettings()
        serverUrl = url
        httpPort = http.toString()
        wsPort = ws.toString()
    }

    LaunchedEffect(messages.size, lastMessageContent) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showSettings) {
        PortConfigDialog(
            serverUrl = serverUrl,
            httpPort = httpPort,
            wsPort = wsPort,
            onServerUrlChange = { serverUrl = it },
            onHttpPortChange = { httpPort = it },
            onWsPortChange = { wsPort = it },
            onDismiss = { showSettings = false },
            onSave = {
                viewModel.updateServerUrl(serverUrl, httpPort.toIntOrNull() ?: 5000, wsPort.toIntOrNull() ?: 9812)
                viewModel.clearChat()
                showSettings = false
            }
        )
    }

    if (showQRScanner) {
        QRScannerScreen(
            onDismiss = { showQRScanner = false },
            onQRCodeScanned = { rawUrl ->
                val url = rawUrl.trim()
                    .substringBefore("\n")
                    .trimEnd('/')
                
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    serverUrl = url
                } else {
                    serverUrl = "https://$url"
                }
                
                viewModel.updateServerUrl(serverUrl, 5000, 5000)
                viewModel.clearChat()
                showQRScanner = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Surface(
            color = DarkSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .statusBarsPadding()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(PurpleDark, PurpleAccent)
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remote IDE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isConnected -> OnlineGreen
                                        else -> OfflineRed
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = connectionStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = { showQRScanner = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR Code",
                        tint = if (isConnected) PurpleAccent else TextTertiary
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleAutoRun() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Toggle Auto-Run",
                        tint = if (autoRunEnabled) GreenSuccess else TextTertiary
                    )
                }

                IconButton(
                    onClick = { viewModel.reconnect() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Connection",
                        tint = TextSecondary
                    )
                }

                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = messages,
                key = { it.id }
            ) { message ->
                MessageBubble(message = message)
            }
        }

        Surface(
            color = InputBarBg,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach File",
                        tint = TextTertiary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Send command...",
                            color = TextTertiary
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = InputFieldBg,
                        unfocusedContainerColor = InputFieldBg,
                        disabledContainerColor = InputFieldBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = PurpleAccent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                    enabled = !isGenerating
                )

                Spacer(modifier = Modifier.width(8.dp))

                val canSend = inputText.isNotBlank() && !isGenerating

                IconButton(
                    onClick = {
                        if (canSend) {
                            viewModel.send(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                Brush.linearGradient(listOf(PurpleDark, PurpleAccent))
                            } else {
                                Brush.linearGradient(listOf(DarkSurfaceElevated, DarkSurfaceElevated))
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) Color.White else TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PortConfigDialog(
    serverUrl: String,
    httpPort: String,
    wsPort: String,
    onServerUrlChange: (String) -> Unit,
    onHttpPortChange: (String) -> Unit,
    onWsPortChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var serverUrlValue by remember { mutableStateOf(serverUrl) }
    var httpPortValue by remember { mutableStateOf(httpPort) }
    var wsPortValue by remember { mutableStateOf(wsPort) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Connection Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = serverUrlValue,
                    onValueChange = { serverUrlValue = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleAccent,
                        unfocusedBorderColor = TextTertiary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = PurpleAccent
                    ),
                    placeholder = {
                        Text("http://192.168.x.x:5000", color = TextTertiary)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = httpPortValue,
                    onValueChange = { httpPortValue = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("HTTP Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleAccent,
                        unfocusedBorderColor = TextTertiary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = PurpleAccent
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = wsPortValue,
                    onValueChange = { wsPortValue = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("WebSocket Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleAccent,
                        unfocusedBorderColor = TextTertiary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = PurpleAccent
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Default: HTTP 5000, WS 5000",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onServerUrlChange(serverUrlValue)
                            onHttpPortChange(httpPortValue)
                            onWsPortChange(wsPortValue)
                            onSave()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleAccent
                        )
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
