# UI 优化功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ChatAI 添加 Pull-to-Refresh、Shimmer Loading、Swipe-to-Dismiss、Markdown 渲染四个 UI 优化功能

**Architecture:** 4 个独立功能，各自改动范围小且互不影响。Shimmer 用 Compose 原生动画手写（不引入新库），Markdown 用 mikepenz 库。Pull-to-Refresh 和 Swipe-to-Dismiss 使用 Material3/Material 自带 API。

**Tech Stack:** Compose pullRefresh (material), SwipeToDismiss (material, deprecated but available in BOM 2023.08.00), mikepenz/multiplatform-markdown-renderer 0.10.0

---

## 文件清单

| 操作 | 文件 | 改动内容 |
|------|------|---------|
| Modify | `app/build.gradle.kts` | 添加 markdown-renderer 依赖 |
| Modify | `app/src/main/java/com/chat/ai/data/db/MessageDao.kt` | 添加 `deleteById()` 和 `getMessagesBefore()` |
| Modify | `app/src/main/java/com/chat/ai/data/repository/ChatRepository.kt` | 添加 `deleteMessage()` 和 `loadMoreMessages()` |
| Modify | `app/src/main/java/com/chat/ai/ui/chat/ChatViewModel.kt` | 添加 `deleteMessage()` 和 `loadMoreMessages()` + 状态 |
| Modify | `app/src/main/java/com/chat/ai/ui/chat/ChatScreen.kt` | Pull-to-Refresh 包裹 LazyColumn |
| Modify | `app/src/main/java/com/chat/ai/ui/chat/MessageBubble.kt` | Shimmer 占位 + Swipe-to-Dismiss + Markdown |

---

### Task 1: 添加依赖 + DAO 方法

**Files:**
- Modify: `app/build.gradle.kts:81-128`
- Modify: `app/src/main/java/com/chat/ai/data/db/MessageDao.kt`
- Modify: `app/src/main/java/com/chat/ai/data/repository/ChatRepository.kt`

- [ ] **Step 1: 添加 markdown-renderer 依赖**

在 `app/build.gradle.kts` 的 dependencies 块中，`implementation("androidx.compose.material:material-icons-extended")` 之后添加：

```kotlin
    // Markdown
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.10.0")
```

- [ ] **Step 2: MessageDao 添加新方法**

在 `MessageDao.kt` 中添加两个方法：

```kotlin
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM messages WHERE timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesBefore(beforeTimestamp: Long, limit: Int): List<Message>
```

- [ ] **Step 3: ChatRepository 添加方法**

在 `ChatRepository.kt` 中添加：

```kotlin
    suspend fun deleteMessage(id: Long) {
        messageDao.deleteById(id)
    }

    suspend fun loadMoreMessages(beforeTimestamp: Long, limit: Int = 30): List<Message> {
        return messageDao.getMessagesBefore(beforeTimestamp, limit)
    }
```

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/chat/ai/data/db/MessageDao.kt app/src/main/java/com/chat/ai/data/repository/ChatRepository.kt
git commit -m "feat: add markdown dependency, deleteById and getMessagesBefore DAO methods"
```

---

### Task 2: ChatViewModel 添加状态和方法

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/chat/ChatViewModel.kt`

- [ ] **Step 1: 添加 loadMore 和 delete 相关状态和方法**

在 `ChatViewModel.kt` 的 `_error` 声明之后添加：

```kotlin
    private val _displayLimit = MutableStateFlow(100)
    val displayLimit: StateFlow<Int> = _displayLimit.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
```

在 `clearMessages()` 方法之后添加：

```kotlin
    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            chatRepository.deleteMessage(id)
        }
    }

    fun loadMoreMessages() {
        val currentMessages = messages.value
        if (currentMessages.isEmpty()) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val oldestTimestamp = currentMessages.first().timestamp
            val older = chatRepository.loadMoreMessages(oldestTimestamp)
            if (older.isNotEmpty()) {
                _displayLimit.value += older.size
            }
            _isRefreshing.value = false
        }
    }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/chat/ChatViewModel.kt
git commit -m "feat: add deleteMessage and loadMoreMessages to ViewModel"
```

---

### Task 3: MessageBubble — Shimmer + Swipe-to-Dismiss + Markdown

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/chat/MessageBubble.kt`

- [ ] **Step 1: 重写 MessageBubble**

```kotlin
package com.chat.ai.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chat.ai.data.model.Message
import com.chat.ai.speech.TtsManager
import com.mikepenz.markdown.compose.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: Message,
    onPlayVoice: ((String) -> Unit)? = null,
    userAvatarBitmap: ImageBitmap? = null,
    aiAvatarBitmap: ImageBitmap? = null,
    ttsManager: TtsManager? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val avatarBitmap = if (isUser) userAvatarBitmap else aiAvatarBitmap

    var isPlaying by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    val dismissState = rememberDismissState(
        confirmStateChange = {
            if (it == DismissValue.DismissedToStart && onDelete != null) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart),
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        },
        dismissContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    if (!isUser && avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap,
                            contentDescription = "AI头像",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (message.isVoice) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 120.dp, max = 280.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bubbleColor)
                                .clickable {
                                    if (isPlaying) {
                                        ttsManager?.stop()
                                        isPlaying = false
                                    } else {
                                        isPlaying = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val voiceConfig = com.chat.ai.util.ServiceLocator.personaRepository().getLatestVoiceConfig()
                                            ttsManager?.speak(message.content, voiceConfig, TtsManager.PRIORITY_HIGH)
                                            isPlaying = false
                                        }
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "停止" else "播放",
                                    tint = textColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "语音消息",
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (showText) message.content else "转文字",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { showText = !showText }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bubbleColor)
                                .padding(12.dp)
                        ) {
                            if (isUser) {
                                Text(
                                    text = message.content,
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                Markdown(
                                    content = message.content,
                                    colors = com.mikepenz.markdown.model.MarkdownColors(
                                        text = textColor,
                                        codeText = textColor,
                                        linkText = textColor,
                                        codeBackground = MaterialTheme.colorScheme.surface,
                                        dividerColor = MaterialTheme.colorScheme.outline,
                                        inlineCodeBackground = MaterialTheme.colorScheme.surface
                                    ),
                                    typography = com.mikepenz.markdown.model.MarkdownTypography(
                                        text = MaterialTheme.typography.bodyLarge,
                                        code = MaterialTheme.typography.bodyMedium,
                                        h1 = MaterialTheme.typography.headlineMedium,
                                        h2 = MaterialTheme.typography.headlineSmall,
                                        h3 = MaterialTheme.typography.titleLarge,
                                        h4 = MaterialTheme.typography.titleMedium,
                                        h5 = MaterialTheme.typography.titleSmall,
                                        h6 = MaterialTheme.typography.bodyLarge,
                                        quote = MaterialTheme.typography.bodyLarge,
                                        ordered = MaterialTheme.typography.bodyLarge,
                                        bullet = MaterialTheme.typography.bodyLarge,
                                        list = MaterialTheme.typography.bodyLarge
                                    )
                                )
                            }
                        }
                    }

                    if (isUser && avatarBitmap != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Image(
                            bitmap = avatarBitmap,
                            contentDescription = "用户头像",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
    )
}

@Composable
fun ShimmerMessageBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    val shimmerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = shimmerAlpha.value)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(shimmerColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerColor)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerColor)
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/chat/MessageBubble.kt
git commit -m "feat: markdown rendering, swipe-to-delete, shimmer loading placeholder"
```

---

### Task 4: ChatScreen — Pull-to-Refresh + Shimmer + onDelete

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/chat/ChatScreen.kt`

- [ ] **Step 1: 添加 pullRefresh import**

在 ChatScreen.kt 的 import 区域添加：

```kotlin
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
```

- [ ] **Step 2: 添加 isRefreshing 状态收集和 pullRefresh state**

在 `val isScreenSharing = viewModel.screenCapture != null` 之后添加：

```kotlin
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val displayLimit by viewModel.displayLimit.collectAsState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.loadMoreMessages() }
    )
```

- [ ] **Step 3: 修改 displayMessages 使用 displayLimit**

将：
```kotlin
            val displayMessages = remember(messages) {
                if (messages.size > 100) messages.takeLast(100) else messages
            }
```

改为：
```kotlin
            val displayMessages = remember(messages, displayLimit) {
                if (messages.size > displayLimit) messages.takeLast(displayLimit) else messages
            }
```

- [ ] **Step 4: LazyColumn 包裹 pullRefresh**

将：
```kotlin
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
```

改为：
```kotlin
            Box(modifier = Modifier.weight(1f).fillMaxWidth().pullRefresh(pullRefreshState)) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
```

在 LazyColumn 闭合 `}` 之后添加：

```kotlin
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
```

- [ ] **Step 5: items 块添加 Shimmer 和 onDelete**

将 items 块中的 MessageBubble 调用改为：

```kotlin
                items(
                    items = displayMessages,
                    key = { it.id },
                    contentType = { if (it.isVoice) "voice" else "text" }
                ) { message ->
                    Box(modifier = Modifier.animateItemPlacement()) {
                    MessageBubble(
                        message = message,
                        onPlayVoice = onPlayVoice,
                        userAvatarBitmap = userAvatarBitmap,
                        aiAvatarBitmap = aiAvatarBitmap,
                        ttsManager = ttsManager,
                        onDelete = { viewModel.deleteMessage(message.id) }
                    )
                    }
                }
```

将 loading 指示器改为 Shimmer：

```kotlin
                if (isLoading) {
                    item {
                        ShimmerMessageBubble()
                        ShimmerMessageBubble()
                        ShimmerMessageBubble()
                    }
                }
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/chat/ChatScreen.kt
git commit -m "feat: pull-to-refresh, shimmer loading, swipe-to-delete integration"
```
