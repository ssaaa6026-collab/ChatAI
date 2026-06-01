package com.chat.ai.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chat.ai.screen.ScreenCapture
import com.chat.ai.service.ScreenCaptureService
import com.chat.ai.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenShareScreen(
    onNavigateBack: () -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    var isCapturing by remember { mutableStateOf(false) }
    var currentFrame by remember { mutableStateOf<Bitmap?>(null) }
    var screenCapture by remember { mutableStateOf<ScreenCapture?>(null) }
    var resultCode by remember { mutableIntStateOf(0) }
    var resultData by remember { mutableStateOf<Intent?>(null) }
    val scope = rememberCoroutineScope()

    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            resultCode = result.resultCode
            resultData = result.data
            isCapturing = true

            val serviceIntent = Intent(context, ScreenCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    LaunchedEffect(isCapturing) {
        if (isCapturing && resultData != null) {
            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = mpManager.getMediaProjection(resultCode, resultData!!)

            val capture = ScreenCapture(context, mediaProjection)
            screenCapture = capture
            chatViewModel.screenCapture = capture
            ScreenCaptureService.screenCapture = capture

            capture.startCapture { bitmap ->
                scope.launch(Dispatchers.Main) {
                    currentFrame = bitmap
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 不停止屏幕共享
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("屏幕共享") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isCapturing) {
                Text(
                    "屏幕共享中",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "AI 正在观看你的屏幕，每分钟自动分析并语音播报",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))

                currentFrame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "屏幕捕获",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        screenCapture?.stopCapture()
                        chatViewModel.screenCapture = null
                        ScreenCaptureService.screenCapture = null
                        context.stopService(Intent(context, ScreenCaptureService::class.java))
                        isCapturing = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("停止共享")
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "屏幕共享",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "共享屏幕给 AI，让 AI 可以看到你的屏幕内容",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        val captureIntent = projectionManager.createScreenCaptureIntent()
                        captureLauncher.launch(captureIntent)
                    }
                ) {
                    Text("开始共享")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
