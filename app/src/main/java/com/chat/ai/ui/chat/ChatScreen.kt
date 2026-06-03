package com.chat.ai.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chat.ai.speech.TtsManager
import com.chat.ai.util.PrefsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPersona: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val autoSpeak by viewModel.autoSpeak.collectAsState()
    val personaName by viewModel.personaName.collectAsState()
    val error by viewModel.error.collectAsState(initial = null)
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isScreenSharing = viewModel.screenCapture != null
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val displayLimit by viewModel.displayLimit.collectAsState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.loadMoreMessages() }
    )
    var showError by remember { mutableStateOf<String?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageName by remember { mutableStateOf<String?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                selectedImageBytes = stream.readBytes()
                selectedImageName = it.lastPathSegment ?: "已选择图片"
            }
        }
    }

    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            context.contentResolver.openInputStream(cameraUri!!)?.use { stream ->
                selectedImageBytes = stream.readBytes()
                selectedImageName = "拍照"
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            cameraUri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            cameraLauncher.launch(cameraUri!!)
        }
    }

    LaunchedEffect(error) {
        error?.let {
            showError = it
        }
    }

    var isInitialLoad by remember { mutableStateOf(true) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (isInitialLoad) {
                listState.scrollToItem(messages.size - 1)
                isInitialLoad = false
            } else {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(showError) {
        showError?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            showError = null
        }
    }

    val userAvatarPath = remember { PrefsManager.getUserAvatar(context) }
    val aiAvatarPath = remember { PrefsManager.getAiAvatar(context) }
    val userAvatarBitmap = remember(userAvatarPath) {
        if (userAvatarPath.isNotBlank() && java.io.File(userAvatarPath).exists()) {
            android.graphics.BitmapFactory.decodeFile(userAvatarPath)?.asImageBitmap()
        } else null
    }
    val aiAvatarBitmap = remember(aiAvatarPath) {
        if (aiAvatarPath.isNotBlank() && java.io.File(aiAvatarPath).exists()) {
            android.graphics.BitmapFactory.decodeFile(aiAvatarPath)?.asImageBitmap()
        } else null
    }

    Scaffold(
        topBar = {
            val subtitle = buildList {
                if (isScreenSharing) add("屏幕共享中")
            }.joinToString(" · ")

            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (aiAvatarBitmap != null) {
                            Image(
                                bitmap = aiAvatarBitmap,
                                contentDescription = "AI头像",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    personaName.take(1),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(personaName)
                            if (subtitle.isNotBlank()) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleAutoSpeak() }) {
                        Icon(
                            if (autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (autoSpeak) "关闭自动朗读" else "开启自动朗读",
                            tint = if (autoSpeak) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToPersona) {
                        Icon(Icons.Default.Person, contentDescription = "人设")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = isScreenSharing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "屏幕共享中 - AI可以看到你的屏幕",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            val displayMessages = remember(messages, displayLimit) {
                if (messages.size > displayLimit) messages.takeLast(displayLimit) else messages
            }

            val ttsManager = remember { TtsManager(context) }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().pullRefresh(pullRefreshState)) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = displayMessages,
                    key = { it.id },
                    contentType = { if (it.isVoice) "voice" else "text" }
                ) { message ->
                    Box(modifier = Modifier.animateItemPlacement()) {
                        MessageBubble(
                            message = message,
                            userAvatarBitmap = userAvatarBitmap,
                            aiAvatarBitmap = aiAvatarBitmap,
                            ttsManager = ttsManager,
                            onDelete = { viewModel.deleteMessage(message.id) },
                            personaName = personaName
                        )
                    }
                }
                val lastMsg = displayMessages.lastOrNull()
                val streamingDone = !isStreaming && lastMsg?.role == "assistant" && lastMsg.content == streamingText
                if (streamingText.isNotBlank() && !streamingDone) {
                    item {
                        // 光标闪烁效果
                        var cursorVisible by remember { mutableStateOf(true) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                kotlinx.coroutines.delay(500)
                                cursorVisible = !cursorVisible
                            }
                        }
                        val displayText = streamingText + if (cursorVisible) "▌" else ""

                        Box(modifier = Modifier.animateItemPlacement()) {
                        MessageBubble(
                            message = com.chat.ai.data.model.Message(
                                role = "assistant",
                                content = displayText
                            ),
                            userAvatarBitmap = userAvatarBitmap,
                            aiAvatarBitmap = aiAvatarBitmap,
                            personaName = personaName
                        )
                        }
                    }
                }
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "思考中...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // 显示已选择的图片
            AnimatedVisibility(
                visible = selectedImageBytes != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选择: $selectedImageName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        selectedImageBytes = null
                        selectedImageName = null
                    }) {
                        Text("取消")
                    }
                }
            }

            val sendMessage = {
                if (selectedImageBytes != null) {
                    viewModel.sendMessageWithImage(inputText, selectedImageBytes!!)
                    selectedImageBytes = null
                    selectedImageName = null
                } else {
                    viewModel.sendMessage(inputText)
                }
                inputText = ""
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showImageSourceDialog = true },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Image, contentDescription = "选择图片")
                }

                if (showImageSourceDialog) {
                    AlertDialog(
                        onDismissRequest = { showImageSourceDialog = false },
                        title = { Text("选择图片来源") },
                        text = { Text("从哪里获取图片？") },
                        confirmButton = {
                            TextButton(onClick = {
                                showImageSourceDialog = false
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }) { Text("拍照") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showImageSourceDialog = false
                                imagePickerLauncher.launch("image/*")
                            }) { Text("从相册选择") }
                        }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        if (selectedImageBytes != null) {
                            Text("描述图片或提问...")
                        } else if (isScreenSharing) {
                            Text("问问AI看到了什么...")
                        } else {
                            Text("说点什么...")
                        }
                    },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() })
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { sendMessage() },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Send, contentDescription = "发送")
                }
            }
        }
    }
}
