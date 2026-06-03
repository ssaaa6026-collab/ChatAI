# 流式回复 + 悬浮气泡 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

---

## Part 1: 流式回复（Streaming Responses）

**Goal:** AI 回复逐 token 实时显示，像打字一样出现

**Architecture:** MimoTextApi 新增 `sendMessageStreaming()` 返回 `Flow<String>`，用 OkHttp SSE 逐行读取 OpenAI 格式的流式响应。ChatRepository 收集 Flow 增量更新。ChatViewModel 管理流式状态。ChatScreen 实时显示。

**Tech Stack:** OkHttp streaming response body, Kotlin Flow, SSE (OpenAI format)

**MiMo SSE 格式（OpenAI 兼容）：**
```
data: {"choices":[{"delta":{"content":"你"}}]}
data: {"choices":[{"delta":{"content":"好"}}]}
data: [DONE]
```

---

### Task 1.1: MimoTextApi 添加流式方法

**Files:**
- Modify: `app/src/main/java/com/chat/ai/data/api/MimoTextApi.kt`

- [ ] **Step 1: 添加 Flow import**

在文件顶部 import 区域添加：

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
```

- [ ] **Step 2: 在 RequestBody 中添加 stream 字段**

将 `RequestBody` data class 改为：

```kotlin
    data class RequestBody(
        val model: String = "mimo-v2.5",
        @SerializedName("max_tokens") val maxTokens: Int = 2048,
        val messages: List<Message>,
        val system: String = "",
        val temperature: Double = 0.7,
        val stream: Boolean = false
    )
```

- [ ] **Step 3: 添加 OpenAI 格式的 StreamEvent 数据类**

在 `ApiErrorResponse` 之后添加：

```kotlin
    data class StreamDelta(val content: String?)
    data class StreamChoice(val delta: StreamDelta?)
    data class StreamEvent(val choices: List<StreamChoice>?)
```

- [ ] **Step 4: 在 sendMessage 方法之后添加流式方法**

```kotlin
    fun sendMessageStreaming(
        systemPrompt: String,
        messages: List<Message>
    ): Flow<String> = flow {
        val body = RequestBody(
            messages = messages,
            system = systemPrompt,
            stream = true
        )
        val request = Request.Builder()
            .url("$baseUrl/v1/messages")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            val error = gson.fromJson(errorBody, ApiErrorResponse::class.java)
            throw Exception(error?.error?.message ?: "Streaming failed")
        }

        response.body?.source()?.let { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val json = line.removePrefix("data: ").trim()
                    if (json == "[DONE]") break
                    try {
                        val event = gson.fromJson(json, StreamEvent::class.java)
                        val token = event.choices?.firstOrNull()?.delta?.content
                        if (!token.isNullOrEmpty()) emit(token)
                    } catch (_: Exception) {}
                }
            }
        }
    }.flowOn(Dispatchers.IO)
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/chat/ai/data/api/MimoTextApi.kt
git commit -m "feat: add sendMessageStreaming with OpenAI SSE format"
```

---

### Task 1.2: ChatRepository 添加流式方法

**Files:**
- Modify: `app/src/main/java/com/chat/ai/data/repository/ChatRepository.kt`

- [ ] **Step 1: 添加 Flow import**

在文件顶部 import 区域添加：

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
```

- [ ] **Step 2: 在 sendImageMessage 之前添加 sendStreamingMessage**

```kotlin
    fun sendStreamingMessage(
        content: String,
        systemPrompt: String,
        displayContent: String = content
    ): Flow<String> = flow {
        val contextMessages = contextManager.getContextMessages().toMutableList()
        contextMessages.add(MimoTextApi.Message("user", content))

        messageDao.insert(Message(role = "user", content = displayContent))

        val fullResponse = StringBuilder()
        textApi.sendMessageStreaming(systemPrompt, contextMessages).collect { token ->
            fullResponse.append(token)
            emit(token)
        }

        if (fullResponse.isNotEmpty()) {
            messageDao.insert(Message(role = "assistant", content = fullResponse.toString()))
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/chat/ai/data/repository/ChatRepository.kt
git commit -m "feat: add sendStreamingMessage to ChatRepository"
```

---

### Task 1.3: ChatViewModel 添加流式状态

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/chat/ChatViewModel.kt`

- [ ] **Step 1: 添加流式文本状态**

在 `_isRefreshing` 声明之后添加：

```kotlin
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()
```

- [ ] **Step 2: 修改 sendTextInternal 使用流式**

将 `sendTextInternal` 方法改为：

```kotlin
    private suspend fun sendTextInternal(content: String, systemPrompt: String): Result<String> {
        val responseLength = PrefsManager.getResponseLength(getApplication())
        val lengthRule = PromptTemplates.lengthRule(responseLength)
        val fullSystemPrompt = PromptTemplates.compose(
            lengthRule,
            systemPrompt,
            PromptTemplates.currentTime()
        )
        val apiContent = if (responseLength != "normal") {
            "$content\n\n$lengthRule"
        } else {
            content
        }

        _isStreaming.value = true
        _streamingText.value = ""

        return try {
            val fullResponse = StringBuilder()
            chatRepository.sendStreamingMessage(apiContent, fullSystemPrompt, displayContent = content)
                .collect { token ->
                    fullResponse.append(token)
                    _streamingText.value = fullResponse.toString()
                }
            _isStreaming.value = false
            _streamingText.value = ""
            Result.success(fullResponse.toString())
        } catch (e: Exception) {
            _isStreaming.value = false
            _streamingText.value = ""
            Result.failure(e)
        }
    }
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/chat/ChatViewModel.kt
git commit -m "feat: add streaming state to ChatViewModel"
```

---

### Task 1.4: ChatScreen 显示流式文本

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/chat/ChatScreen.kt`

- [ ] **Step 1: 收集流式状态**

在 `val isRefreshing by viewModel.isRefreshing.collectAsState()` 之后添加：

```kotlin
    val streamingText by viewModel.streamingText.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
```

- [ ] **Step 2: 在 LazyColumn 的 Shimmer 之前添加流式消息**

在 `if (isLoading)` 块之前添加：

```kotlin
                if (isStreaming && streamingText.isNotBlank()) {
                    item {
                        Box(modifier = Modifier.animateItemPlacement()) {
                        MessageBubble(
                            message = com.chat.ai.data.model.Message(
                                role = "assistant",
                                content = streamingText
                            ),
                            userAvatarBitmap = userAvatarBitmap,
                            aiAvatarBitmap = aiAvatarBitmap
                        )
                        }
                    }
                }
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/chat/ChatScreen.kt
git commit -m "feat: display streaming response in real-time"
```

---

## Part 2: 悬浮气泡（Floating Chat Head）

**Goal:** 在任何 app 上都能通过悬浮气泡快速跟 AI 对话

**Architecture:** 前台服务管理 overlay window，ComposeView 渲染可拖拽气泡和迷你聊天 UI。需要 `SYSTEM_ALERT_WINDOW` 权限。

**Tech Stack:** WindowManager overlay, Foreground Service, ComposeView

---

### Task 2.1: 添加权限和注册

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: AndroidManifest 添加权限和服务**

在 `<uses-permission>` 区域添加：

```xml
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

在 `</application>` 之前添加：

```xml
        <service
            android:name=".service.FloatingChatService"
            android:foregroundServiceType="specialUse"
            android:exported="false" />
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add SYSTEM_ALERT_WINDOW permission and FloatingChatService"
```

---

### Task 2.2: 创建 FloatingChatService

**Files:**
- Create: `app/src/main/java/com/chat/ai/service/FloatingChatService.kt`

- [ ] **Step 1: 创建 FloatingChatService**

```kotlin
package com.chat.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chat.ai.ui.chat.FloatingChatBubble

class FloatingChatService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val CHANNEL_ID = "floating_chat_channel"
        const val NOTIFICATION_ID = 9999

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FloatingChatService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingChatService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        removeBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingChatService)
            setViewTreeSavedStateRegistryOwner(this@FloatingChatService)
            setContent {
                FloatingChatBubble(onClose = { stopSelf() })
            }
        }

        composeView = view
        windowManager.addView(view, params)
    }

    private fun removeBubble() {
        composeView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        composeView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "悬浮聊天", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI 悬浮球已开启")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/service/FloatingChatService.kt
git commit -m "feat: FloatingChatService with lifecycle and overlay support"
```

---

### Task 2.3: 创建 FloatingChatBubble Composable

**Files:**
- Create: `app/src/main/java/com/chat/ai/ui/chat/FloatingChatBubble.kt`

- [ ] **Step 1: 创建 FloatingChatBubble**

```kotlin
package com.chat.ai.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FloatingChatBubble(onClose: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<String>()) }

    AnimatedVisibility(
        visible = !isExpanded,
        enter = scaleIn(),
        exit = scaleOut()
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { isExpanded = true },
            contentAlignment = Alignment.Center
        ) {
            Text("AI", color = MaterialTheme.colorScheme.onPrimary, fontSize = 18.sp)
        }
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AI 对话", style = MaterialTheme.typography.titleSmall)
                    Row {
                        IconButton(onClick = { isExpanded = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "收起", modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = 8.dp)
                ) {
                    messages.forEach { msg ->
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("说点什么...", fontSize = 14.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                messages = messages + inputText
                                inputText = ""
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Send, "发送", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/chat/FloatingChatBubble.kt
git commit -m "feat: FloatingChatBubble with expand/collapse and mini chat"
```

---

### Task 2.4: 设置页添加启动入口

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/settings/VoiceAndScreenSection.kt`

- [ ] **Step 1: 在 VoiceAndScreenSection 中添加悬浮球开关**

在文件末尾的 `}` 之前（Column 内部）添加：

```kotlin
    Spacer(modifier = Modifier.height(12.dp))
    val context = LocalContext.current
    var floatingEnabled by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("悬浮球", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = floatingEnabled,
            onCheckedChange = { enabled ->
                floatingEnabled = enabled
                if (enabled) {
                    if (android.provider.Settings.canDrawOverlays(context)) {
                        com.chat.ai.service.FloatingChatService.start(context)
                    } else {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}"))
                        )
                    }
                } else {
                    com.chat.ai.service.FloatingChatService.stop(context)
                }
            }
        )
    }
```

需要确保文件顶部有以下 import：

```kotlin
import android.content.Intent
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/settings/VoiceAndScreenSection.kt
git commit -m "feat: floating chat bubble toggle in settings"
```
