# UI 丝滑动画实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ChatAI app 添加 Compose 动画，让 UI 状态变化平滑自然

**Architecture:** 全部使用 Compose 自带的 `androidx.compose.animation`，不引入新库。改动集中在 3 个文件，每处改动独立且自包含。

**Tech Stack:** Jetpack Compose animation (AnimatedVisibility, animateScrollToItem, animateItem)

---

## 文件清单

| 操作 | 文件 | 改动内容 |
|------|------|---------|
| Modify | `app/src/main/java/com/chat/ai/ui/chat/ChatScreen.kt` | scrollToItem→animateScrollToItem, AnimatedVisibility for loading/banner/image |
| Modify | `app/src/main/java/com/chat/ai/ui/chat/MessageBubble.kt` | 加 Modifier.animateItem() |
| Modify | `app/src/main/java/com/chat/ai/ui/navigation/NavGraph.kt` | 加页面切换动画 |

---

### Task 1: ChatScreen 滚动动画

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/chat/ChatScreen.kt:71-86`

- [ ] **Step 1: 修改 LaunchedEffect 中的滚动调用**

找到第 71-86 行的 `LaunchedEffect(messages.size)` 块，将非首次加载时的 `scrollToItem` 改为 `animateScrollToItem`：

```kotlin
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
                    listState.animateScrollToItem(totalItems - 1)
                }
            }
        }
    }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/chat/ChatScreen.kt
git commit -m "feat: smooth scroll animation for new messages"
```

---

### Task 2: ChatScreen AnimatedVisibility

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/chat/ChatScreen.kt` — imports + screen banner + loading indicator + image preview

- [ ] **Step 1: 添加 animation import**

在 ChatScreen.kt 的 import 区域添加：

```kotlin
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
```

- [ ] **Step 2: 屏幕共享 banner 加 AnimatedVisibility**

将第 120-132 行的：

```kotlin
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
```

改为：

```kotlin
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
```

- [ ] **Step 3: Loading 指示器加 AnimatedVisibility**

将第 177-195 行的：

```kotlin
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
```

改为：

```kotlin
                if (isLoading) {
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(300)) + expandVertically(tween(300))
                        ) {
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
```

- [ ] **Step 4: 图片选择预览加 AnimatedVisibility**

将第 199-219 行的：

```kotlin
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
```

改为：

```kotlin
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
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/chat/ChatScreen.kt
git commit -m "feat: AnimatedVisibility for loading, banner, and image preview"
```

---

### Task 3: MessageBubble animateItem

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/chat/MessageBubble.kt`

- [ ] **Step 1: 给 MessageBubble 的 Column 外层加 animateItem**

找到 MessageBubble.kt 第 68 行的 Column（`Column(modifier = Modifier.fillMaxWidth()...`），在其 modifier 链最前面加 `.animateItem()`：

将第 68-71 行的：

```kotlin
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
```

改为：

```kotlin
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateItem(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/chat/MessageBubble.kt
git commit -m "feat: animateItem for smooth message list transitions"
```

---

### Task 4: NavGraph 页面切换动画

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/navigation/NavGraph.kt`

- [ ] **Step 1: 重写 NavGraph 添加过渡动画**

```kotlin
package com.chat.ai.ui.navigation

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chat.ai.ChatApplication
import com.chat.ai.ui.chat.ChatScreen
import com.chat.ai.ui.chat.ChatViewModel
import com.chat.ai.ui.persona.PersonaScreen
import com.chat.ai.ui.settings.SettingsScreen
import com.chat.ai.ui.voice.VoiceSettingsScreen
import com.chat.ai.ui.screen.ScreenShareScreen
import com.chat.ai.ui.reminder.CustomReminderScreen

private const val ANIM_DURATION = 300

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val db = (application as ChatApplication).database
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "chat",
        enterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { it } + fadeIn(tween(ANIM_DURATION)) },
        exitTransition = { slideOutHorizontally(tween(ANIM_DURATION)) { -it / 3 } + fadeOut(tween(ANIM_DURATION)) },
        popEnterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { -it / 3 } + fadeIn(tween(ANIM_DURATION)) },
        popExitTransition = { slideOutHorizontally(tween(ANIM_DURATION)) { it } + fadeOut(tween(ANIM_DURATION)) }
    ) {
        composable("chat") {
            ChatScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPersona = { navController.navigate("persona") },
                viewModel = chatViewModel
            )
        }
        composable("persona") {
            PersonaScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVoice = { navController.navigate("voice_settings") },
                onNavigateToScreenShare = { navController.navigate("screen_share") },
                onNavigateToCustomReminders = { navController.navigate("custom_reminders") }
            )
        }
        composable("voice_settings") {
            VoiceSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                voiceConfigDao = db.voiceConfigDao()
            )
        }
        composable("screen_share") {
            ScreenShareScreen(
                onNavigateBack = { navController.popBackStack() },
                chatViewModel = chatViewModel
            )
        }
        composable("custom_reminders") {
            CustomReminderScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/navigation/NavGraph.kt
git commit -m "feat: navigation slide + fade transitions between screens"
```
