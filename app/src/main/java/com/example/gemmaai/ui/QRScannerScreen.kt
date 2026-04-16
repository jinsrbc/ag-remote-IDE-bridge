package com.example.gemmaai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.gemmaai.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private const val TAG = "QRScanner"

private fun extractUrl(value: String): String? {
    Log.d(TAG, "Extracting URL from: $value")
    val trimmed = value.trim()
    
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed.substringBefore("\n").trimEnd('/')
    }
    
    if (trimmed.contains("trycloudflare.com") || trimmed.contains("ngrok")) {
        val httpMatch = Regex("""https?://[^\s]+""").find(trimmed)
        return httpMatch?.value?.substringBefore("\n")?.trimEnd('/')
    }
    
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onDismiss: () -> Unit,
    onQRCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(true) }
    var showPermissionDenied by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrator error: ${e.message}")
            null
        }
    }

    fun vibrate() {
        try {
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(100)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate error: ${e.message}")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            showPermissionDenied = true
        }
    }

    LaunchedEffect(Unit) {
        try {
            val permission = Manifest.permission.CAMERA
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                hasCameraPermission = true
            } else {
                permissionLauncher.launch(permission)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Permission check error: ${e.message}")
            errorMessage = "Error checking camera permission"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraExecutor.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Executor shutdown error: ${e.message}")
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceElevated)
                        .padding(16.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = PurpleAccent
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Scan QR Code",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }

                if (!hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Camera permission required",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                            ) {
                                Text("Grant Permission")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onDismiss) {
                                Text("Cancel", color = TextSecondary)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        var cameraError by remember { mutableStateOf<String?>(null) }
                        
                        if (cameraError != null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Camera Error",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = OfflineRed
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = cameraError ?: "Unknown error",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = onDismiss) {
                                        Text("Close")
                                    }
                                }
                            }
                        } else {
                            AndroidView(
                                factory = { ctx ->
                                    try {
                                        val previewView = PreviewView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                            scaleType = PreviewView.ScaleType.FILL_CENTER
                                        }

                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            try {
                                                val cameraProvider = cameraProviderFuture.get()
                                                
                                                val preview = Preview.Builder().build().also {
                                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                                }

                                                val imageAnalysis = ImageAnalysis.Builder()
                                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                    .build()

                                                val options = BarcodeScannerOptions.Builder()
                                                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                                    .build()
                                                val scanner = BarcodeScanning.getClient(options)

                                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                                    if (!isScanning) {
                                                        imageProxy.close()
                                                        return@setAnalyzer
                                                    }
                                                    
                                                    val mediaImage = imageProxy.image
                                                    if (mediaImage != null) {
                                                        try {
                                                            val inputImage = InputImage.fromMediaImage(
                                                                mediaImage,
                                                                imageProxy.imageInfo.rotationDegrees
                                                            )
                                                            
                                                            scanner.process(inputImage)
                                                                .addOnSuccessListener { barcodes ->
                                                                    if (isScanning && barcodes.isNotEmpty()) {
                                                                        for (barcode in barcodes) {
                                                                            barcode.rawValue?.let { value ->
                                                                                val url = extractUrl(value)
                                                                                if (url != null) {
                                                                                    isScanning = false
                                                                                    vibrate()
                                                                                    onQRCodeScanned(url)
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                .addOnFailureListener { e ->
                                                                    Log.e(TAG, "Scan error: ${e.message}")
                                                                }
                                                                .addOnCompleteListener {
                                                                    imageProxy.close()
                                                                }
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "Image processing error: ${e.message}")
                                                            imageProxy.close()
                                                        }
                                                    } else {
                                                        imageProxy.close()
                                                    }
                                                }

                                                cameraProvider.unbindAll()
                                                cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                                    preview,
                                                    imageAnalysis
                                                )
                                                Log.d(TAG, "Camera initialized")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Camera bind error: ${e.message}")
                                                cameraError = e.message
                                            }
                                        }, ContextCompat.getMainExecutor(ctx))
                                        
                                        previewView
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Camera init error: ${e.message}")
                                        cameraError = e.message
                                        PreviewView(ctx)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Text(
                    text = "Point camera at QR code to scan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                )

                errorMessage?.let { error ->
                    Text(
                        text = "Error: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = OfflineRed,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}
