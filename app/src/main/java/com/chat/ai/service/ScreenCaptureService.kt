package com.chat.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chat.ai.ChatApplication
import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.api.MimoVisionApi
import com.chat.ai.data.repository.PersonaRepository
import com.chat.ai.screen.ScreenCapture
import com.chat.ai.speech.TtsManager
import com.chat.ai.util.PrefsManager
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {
    companion object {
        const val CHANNEL_ID = "screen_capture_channel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "ScreenCaptureService"
        private const val ANALYSIS_INTERVAL = 60000L // 1分钟

        var screenCapture: ScreenCapture? = null
        var isRunning = false
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var analysisJob: Job? = null
    private var ttsManager: TtsManager? = null
    private var visionApi: MimoVisionApi? = null
    private var personaRepository: PersonaRepository? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true

        val apiKey = PrefsManager.getApiKey(this)
        visionApi = MimoVisionApi(apiKey)
        ttsManager = TtsManager(this)

        val app = application as ChatApplication
        personaRepository = PersonaRepository(app.database.personaDao(), app.database.voiceConfigDao())

        startPeriodicAnalysis()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        analysisJob?.cancel()
        serviceScope.cancel()
        ttsManager?.stop()
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
        val api = visionApi ?: return
        val tts = ttsManager ?: return

        val screenBytes = capture.getLatestFrameBytes() ?: return

        Log.d(TAG, "Analyzing screen...")

        val systemPrompt = personaRepository?.getSystemPrompt() ?: "你是一个AI助手"
        val voiceConfig = personaRepository?.getLatestVoiceConfig()
        val responseLength = PrefsManager.getResponseLength(this)

        val lengthPrompt = when (responseLength) {
            "short" -> "请用简短的一两句话描述。"
            "long" -> "请详细描述你看到的内容。"
            else -> "请用你的风格简洁描述你在屏幕上看到的内容，用一两句话概括。"
        }

        val prompts = listOf(
            "$lengthPrompt 如果有有趣或重要的内容请提醒用户。",
            "$lengthPrompt 用你独特的视角描述屏幕内容。",
            "$lengthPrompt 如果看到有趣的内容，请分享你的看法。",
            "$lengthPrompt 请描述屏幕内容，如果有值得注意的地方请提醒。",
            "$lengthPrompt 用你的方式描述你看到的一切。"
        )
        val randomPrompt = prompts.random()

        // 获取上下文消息
        val app = application as ChatApplication
        val textApi = MimoTextApi(PrefsManager.getApiKey(this))
        val contextManager = com.chat.ai.util.ContextManager(app.database.messageDao(), app.database.summaryDao(), textApi)
        val contextMessages = contextManager.getContextMessages()

        // 保存屏幕共享消息到数据库（隐藏显示）
        app.database.messageDao().insert(com.chat.ai.data.model.Message(
            role = "user",
            content = "[屏幕共享]",
            isVoice = false,
            isHidden = true
        ))

        val currentTime = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        val result = api.analyzeImage(
            screenBytes,
            randomPrompt,
            "$systemPrompt\n\n你正在观看用户的屏幕，请用你的个性和风格描述屏幕内容。动作和神态描述必须用括号括起来，例如：（微笑）（点头）\n\n【当前时间】现在是 $currentTime"
        )

        result.onSuccess { response ->
            Log.d(TAG, "Analysis result: $response")

            // 保存AI回复到数据库
            app.database.messageDao().insert(com.chat.ai.data.model.Message(
                role = "assistant",
                content = response,
                isVoice = false
            ))

            withContext(Dispatchers.Main) {
                tts.speak(response, voiceConfig)
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
