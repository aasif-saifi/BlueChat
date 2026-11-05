package com.example.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.FileCategory
import com.example.ui.MainViewModel
import com.example.ui.transfers.formatFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
@Composable
fun CameraCaptureScreen(
    viewModel: MainViewModel,
    initialIsVideo: Boolean = false,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isVideoMode by remember { mutableStateOf(initialIsVideo) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashEnabled by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(false) }
    var recordDurationSec by remember { mutableIntStateOf(0) }

    var capturedFile by remember { mutableStateOf<File?>(null) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val recorder = remember { Recorder.Builder().build() }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (capturedFile == null) {
            // Live Camera Preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            if (isVideoMode) {
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    videoCapture
                                )
                            } else {
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Use case binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Controls Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { flashEnabled = !flashEnabled },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("flash_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        Button(
                            onClick = { isVideoMode = false },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (!isVideoMode) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Photo")
                        }
                        Button(
                            onClick = { isVideoMode = true },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (isVideoMode) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Video")
                        }
                    }
                }

                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("switch_camera_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }

            // Bottom Shutter Controls Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isRecording) {
                    Text(
                        text = String.format(Locale.US, "Recording: %02d:%02d", recordDurationSec / 60, recordDurationSec % 60),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (isVideoMode) {
                            if (isRecording) {
                                activeRecording?.stop()
                                isRecording = false
                            } else {
                                val videoFile = File(
                                    context.cacheDir,
                                    "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
                                )
                                val outputOptions = FileOutputOptions.Builder(videoFile).build()
                                isRecording = true

                                activeRecording = recorder.prepareRecording(context, outputOptions)
                                    .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                                        when (recordEvent) {
                                            is VideoRecordEvent.Finalize -> {
                                                if (!recordEvent.hasError()) {
                                                    capturedFile = videoFile
                                                    capturedUri = Uri.fromFile(videoFile)
                                                } else {
                                                    isRecording = false
                                                }
                                            }
                                        }
                                    }
                            }
                        } else {
                            val photoFile = File(
                                context.cacheDir,
                                "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
                            )
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            imageCapture.takePicture(
                                outputOptions,
                                cameraExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        capturedFile = photoFile
                                        capturedUri = Uri.fromFile(photoFile)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("CameraScreen", "Photo capture error", exception)
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color.Red else Color.White)
                        .testTag("shutter_button")
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else if (isVideoMode) Icons.Default.Videocam else Icons.Default.CameraAlt,
                        contentDescription = "Capture",
                        tint = if (isRecording) Color.White else Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        } else {
            // Preview & Send Card Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isVideoMode) "Send Captured Video?" else "Send Captured Photo?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${capturedFile?.name} (${formatFileSize(capturedFile?.length() ?: 0L)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    capturedFile = null
                                    capturedUri = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("retake_media_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Retake")
                            }

                            Button(
                                onClick = {
                                    val uri = capturedUri
                                    val file = capturedFile
                                    if (uri != null && file != null) {
                                        if (viewModel.connectionStatus.value != com.example.bluetooth.ConnectionStatus.CONNECTED) {
                                            val peer = viewModel.scannedDevices.value.firstOrNull() ?: com.example.bluetooth.ScannedDevice(
                                                address = "SIM_PIXEL_8",
                                                name = "Pixel 8 Pro (Nearby Peer)",
                                                isPaired = false,
                                                isSimulated = true
                                            )
                                            viewModel.connectToDevice(peer)
                                        }
                                        viewModel.offerFileTransfer(
                                            fileName = file.name,
                                            fileCategory = if (isVideoMode) FileCategory.VIDEO else FileCategory.PHOTO,
                                            fileSize = if (file.length() > 0) file.length() else 1024 * 1024L,
                                            uri = uri
                                        )
                                        onNavigateBack()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("send_media_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}
