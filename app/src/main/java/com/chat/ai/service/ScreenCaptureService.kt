package com.chat.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chat.ai.data.model.Message
import com.chat.ai.screen.ScreenCapture
import com.chat.ai.speech.TtsManager
import com.chat.ai.util.PrefsManager
import com.chat.ai.util.PromptTemplates
import com.chat.ai.util.ServiceLocator
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {
    companion object {
        const val CHANNEL_ID = "screen_capture_channel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "ScreenCaptureService"
        private const val ANALYSIS_INTERVAL = 60000L

        var screenCapture: ScreenCapture? = null
        var isRunning = false
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var analysisJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true

        startPeriodicAnalysis()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        analysisJob?.cancel()
        serviceScope.cancel()
        ServiceLocator.ttsManager.stop()
        screenCapture?.stopCapture()
        screenCapture = null
    }

    private fun startPeriodicAnalysis() {
        analysisJob = serviceScope.launch {
            while (isActive) {
                delay(ANALYSIS_INTERVAL)
                analyzeScreen()
            }
        }
    }

    private suspend fun analyzeScreen() {
        val capture = screenCapture ?: return
        val screenBytes = capture.getLatestFrameBytes() ?: return

        val personaRepository = ServiceLocator.personaRepository()
        val systemPrompt = personaRepository.getSystemPrompt()
        val voiceConfig = personaRepository.getLatestVoiceConfig()
        val responseLength = PrefsManager.getResponseLength(this)
        val lengthRule = PromptTemplates.lengthRule(responseLength)

        val prompts = listOf(
            "如果有有趣或重要的内容请提醒用户。",
            "用你独特的视角描述屏幕内容。",
            "如果看到有趣的内容，请分享你的看法。",
            "请描述屏幕内容，如果有值得注意的地方请提醒。",
            "用你的方式描述你看到的一切。"
        )
        val randomPrompt = prompts.random()

        val db = ServiceLocator.database
        db.messageDao().insert(Message(
            role = "user",
            content = "[屏幕共享]",
            isVoice = false,
            isHidden = true
        ))

        val result = ServiceLocator.visionApi().analyzeImage(
            screenBytes,
            randomPrompt,
            PromptTemplates.compose(
                lengthRule,
                systemPrompt,
                "你正在观看用户的屏幕，请用你的个性和风格描述屏幕内容。${PromptTemplates.ACTION_RULE}",
                PromptTemplates.currentTime()
            )
        )

        result.onSuccess { response ->
            db.messageDao().insert(Message(
                role = "assistant",
                content = response,
                isVoice = false
            ))

            withContext(Dispatchers.Main) {
                ServiceLocator.ttsManager.speak(response, voiceConfig, TtsManager.PRIORITY_MEDIUM)
            }
        }

        result.onFailure { e ->
            Log.e(TAG, "Analysis failed", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕共享",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕共享中")
            .setContentText("AI正在观看你的屏幕，每分钟自动分析")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
}
