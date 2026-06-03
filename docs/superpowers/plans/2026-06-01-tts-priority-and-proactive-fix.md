# TTS 优先级与 ProactiveWorker 修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 TTS 互相打断问题（冲突1）和 ProactiveWorker 标记错误消息的竞态条件（冲突2）

**Architecture:**
- 冲突1: 在 TtsManager 中引入优先级机制，用户聊天 > 屏幕共享 > 提醒/主动消息。高优先级可打断低优先级，低优先级请求被跳过。
- 冲突2: 在 ChatRepository.sendMessage 中增加 `isVoice` 参数，让 ProactiveWorker 直接标记消息，消除 getLastMessage 竞态。

**Tech Stack:** Kotlin, Room, MediaPlayer, kotlinx.coroutines.sync.Mutex

---

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| Modify | `app/src/main/java/com/chat/ai/speech/TtsManager.kt` | 添加优先级、Mutex、speakOrSkip 语义 |
| Modify | `app/src/main/java/com/chat/ai/data/repository/ChatRepository.kt` | sendMessage 增加 isVoice 参数 |
| Modify | `app/src/main/java/com/chat/ai/service/ProactiveWorker.kt` | 用 isVoice=true 替代 getLastMessage 竞态写法 |
| Modify | `app/src/main/java/com/chat/ai/ui/chat/ChatViewModel.kt` | 传入 TTS 优先级 |
| Modify | `app/src/main/java/com/chat/ai/service/ScreenCaptureService.kt` | 传入 TTS 优先级 |
| Modify | `app/src/main/java/com/chat/ai/service/CustomReminderWorker.kt` | 传入 TTS 优先级 |

---

### Task 1: TtsManager 添加优先级机制

**Files:**
- Modify: `app/src/main/java/com/chat/ai/speech/TtsManager.kt`

- [ ] **Step 1: 重写 TtsManager**

```kotlin
package com.chat.ai.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.chat.ai.data.model.VoiceConfig
import com.chat.ai.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TtsManager(private val context: Context) {
    companion object {
        private const val TAG = "TtsManager"
        const val PRIORITY_LOW = 1      // 提醒、主动消息
        const val PRIORITY_MEDIUM = 2   // 屏幕共享
        const val PRIORITY_HIGH = 3     // 用户聊天
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentPriority = 0
    private val mutex = Mutex()

    /**
     * 优先级播报：高优先级打断低优先级，低优先级被跳过。
     * @return true 实际播放了，false 被跳过
     */
    suspend fun speak(text: String, voiceConfig: VoiceConfig?, priority: Int = PRIORITY_LOW): Boolean {
        return mutex.withLock {
            if (priority < currentPriority) {
                Log.d(TAG, "Skipped: priority $priority < current $currentPriority")
                return@withLock false
            }
            currentPriority = priority
            doSpeak(text, voiceConfig)
        }
    }

    private suspend fun doSpeak(text: String, voiceConfig: VoiceConfig?): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanText = text
                .replace(Regex("[（(][^）)]*[）)]"), "")
                .replace(Regex("【[^】]*】"), "")
                .trim()

            if (cleanText.isBlank()) return@withContext true

            val ttsApi = ServiceLocator.ttsApi()
            val audioBytes = when (voiceConfig?.type) {
                "design" -> {
                    ttsApi.synthesizeDesign(cleanText, voiceConfig.designPrompt).getOrThrow()
                }
                "clone" -> {
                    val cloneMimeType = voiceConfig.cloneMimeType.ifEmpty { "audio/wav" }
                    val audioPath = voiceConfig.cloneAudioPath
                    if (audioPath.isEmpty()) throw Exception("未设置克隆音频文件")
                    val audioFile = File(audioPath)
                    if (!audioFile.exists()) throw Exception("克隆音频文件不存在: $audioPath")
                    val audioBase64 = android.util.Base64.encodeToString(audioFile.readBytes(), android.util.Base64.NO_WRAP)
                    ttsApi.synthesizeClone(cleanText, audioBase64, cloneMimeType).getOrThrow()
                }
                else -> {
                    val voiceId = voiceConfig?.voiceId ?: "冰糖"
                    val styleTags = voiceConfig?.styleTags?.takeIf { it.isNotBlank() }
                    ttsApi.synthesizeBuiltin(cleanText, voiceId, styleTags).getOrThrow()
                }
            }

            val tempFile = File(context.cacheDir, "tts_output.wav")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            withContext(Dispatchers.Main) {
                playAudio(tempFile.absolutePath)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed", e)
            false
        }
    }

    private fun playAudio(filePath: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEPH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build())
            setDataSource(filePath)
            prepare()
            start()
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentPriority = 0
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/speech/TtsManager.kt
git commit -m "feat: TTS priority system - high priority interrupts low"
```

---

### Task 2: 各调用方传入优先级

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/chat/ChatViewModel.kt:66-70`
- Modify: `app/src/main/java/com/chat/ai/service/ScreenCaptureService.kt:108-109`
- Modify: `app/src/main/java/com/chat/ai/service/CustomReminderWorker.kt:62`

- [ ] **Step 1: ChatViewModel — 用户聊天用 PRIORITY_HIGH**

找到 ChatViewModel 中两处 `ttsManager.speak` 调用（sendMessage 和 sendMessageWithImage 的回调），改为：

```kotlin
ttsManager.speak(response, voiceConfig, TtsManager.PRIORITY_HIGH)
```

- [ ] **Step 2: ScreenCaptureService — 屏幕共享用 PRIORITY_MEDIUM**

找到 `analyzeScreen()` 中的 `ServiceLocator.ttsManager.speak(response, voiceConfig)`，改为：

```kotlin
ServiceLocator.ttsManager.speak(response, voiceConfig, TtsManager.PRIORITY_MEDIUM)
```

- [ ] **Step 3: CustomReminderWorker — 提醒用 PRIORITY_LOW**

找到 `generateAiMessage()` 中的 TTS 调用（如果有的话），改为：

```kotlin
ttsManager.speak(message, voiceConfig, TtsManager.PRIORITY_LOW)
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/chat/ChatViewModel.kt \
       app/src/main/java/com/chat/ai/service/ScreenCaptureService.kt \
       app/src/main/java/com/chat/ai/service/CustomReminderWorker.kt
git commit -m "feat: pass TTS priority to all callers"
```

---

### Task 3: 修复 ProactiveWorker 竞态条件

**Files:**
- Modify: `app/src/main/java/com/chat/ai/data/repository/ChatRepository.kt:17-37`
- Modify: `app/src/main/java/com/chat/ai/service/ProactiveWorker.kt:54-72`

- [ ] **Step 1: ChatRepository.sendMessage 增加 isVoice 参数**

```kotlin
suspend fun sendMessage(
    content: String,
    systemPrompt: String,
    saveUserMessage: Boolean = true,
    saveAssistantMessage: Boolean = true,
    displayContent: String = content,
    isVoice: Boolean = false
): Result<String> {
    val contextMessages = contextManager.getContextMessages().toMutableList()
    contextMessages.add(MimoTextApi.Message("user", content))

    if (saveUserMessage) {
        messageDao.insert(Message(role = "user", content = displayContent))
    }

    val result = textApi.sendMessage(systemPrompt, contextMessages)
    result.onSuccess { response ->
        if (saveAssistantMessage) {
            messageDao.insert(Message(role = "assistant", content = response, isVoice = isVoice))
        }
    }
    return result
}
```

- [ ] **Step 2: ProactiveWorker 用 isVoice=true 替代 getLastMessage 竞态写法**

将 ProactiveWorker `doWork()` 中的：

```kotlin
val isVoice = (0..1).random() == 0

val result = chatRepository.sendMessage(
    content = "",
    systemPrompt = systemPrompt,
    saveUserMessage = false,
    saveAssistantMessage = true
)
// ...
if (result.isSuccess) {
    val response = result.getOrNull()
    if (!response.isNullOrBlank()) {
        if (isVoice) {
            val lastMessage = db.messageDao().getLastMessage()
            if (lastMessage != null) {
                db.messageDao().update(lastMessage.copy(isVoice = true))
            }
            showVoiceNotification(response, personaName)
        } else {
            showNotification(response, personaName)
        }
    }
}
```

改为：

```kotlin
val isVoice = (0..1).random() == 0

val result = chatRepository.sendMessage(
    content = "",
    systemPrompt = systemPrompt,
    saveUserMessage = false,
    saveAssistantMessage = true,
    isVoice = isVoice
)

if (result.isSuccess) {
    val response = result.getOrNull()
    if (!response.isNullOrBlank()) {
        if (isVoice) {
            showVoiceNotification(response, personaName)
        } else {
            showNotification(response, personaName)
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/chat/ai/data/repository/ChatRepository.kt \
       app/src/main/java/com/chat/ai/service/ProactiveWorker.kt
git commit -m "fix: eliminate ProactiveWorker getLastMessage race condition"
```
