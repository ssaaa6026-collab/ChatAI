package com.chat.ai.ui.chat

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showError by remember { mutableStateOf<String?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageName by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(error) {
        error?.let {
            showError = it
        }
    }

    var isInitialLoad by remember { mutableStateOf(true) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (isInitialLoad) {
                // 首次加载，直接滚到底部
                listState.scrollToItem(messages.size - 1)
                isInitialLoad = false
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = messages.size
                // 只有用户在底部附近时才自动滚动
                if (lastVisibleIndex >= totalItems - 5) {
                    listState.scrollToItem(totalItems - 1)
                }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(personaName) },
                actions = {
                    IconButton(onClick = { viewModel.toggleAutoSpeak() }) {
                        Icon(
                            if (autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (autoSpeak) "关闭自动朗读" else "开启自动朗读"
                        )
                    }
                    TextButton(onClick = onNavigateToPersona) { Text("人设") }
                    TextButton(onClick = onNavigateToSettings) { Text("设置") }
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
            if (isScreenSharing) {
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

            // 只显示最近100条消息
            val displayMessages = remember(messages) {
                if (messages.size > 100) messages.takeLast(100) else messages
            }

            val onPlayVoice: (String) -> Unit = remember(viewModel) {
                { text -> viewModel.speakText(text) }
            }

            // 在屏幕级别读取头像路径和解码，避免每条消息重复读取
            val ttsManager = remember { TtsManager(context) }
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

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = displayMessages,
                    key = { it.id },
                    contentType = { if (it.isVoice) "voice" else "text" }
                ) { message ->
                    MessageBubble(
                        message = message,
                        onPlayVoice = onPlayVoice,
                        userAvatarBitmap = userAvatarBitmap,
                        aiAvatarBitmap = aiAvatarBitmap,
                        ttsManager = ttsManager
                    )
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

            // 显示已选择的图片
            if (selectedImageBytes != null) {
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
                    onClick = { imagePickerLauncher.launch("image/*") },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Image, contentDescription = "选择图片")
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
