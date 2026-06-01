package com.chat.ai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chat.ai.ChatApplication
import com.chat.ai.MainActivity
import com.chat.ai.R
import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.repository.ChatRepository
import com.chat.ai.data.repository.PersonaRepository
import com.chat.ai.util.ContextManager
import com.chat.ai.util.PrefsManager

class ProactiveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ProactiveWorker"
        const val CHANNEL_ID = "proactive_message_channel"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork called at ${System.currentTimeMillis()}")

        // 获取WakeLock防止系统杀死进程
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ChatAI:ProactiveWorker"
        )
        wakeLock.acquire(60 * 1000L) // 最多保持60秒

        try {
            val app = applicationContext as ChatApplication
            val db = app.database
            val apiKey = PrefsManager.getApiKey(applicationContext)
            Log.d(TAG, "API key: ${apiKey.take(10)}...")

            if (apiKey.isBlank()) {
                Log.e(TAG, "API key is blank")
                return Result.failure()
            }

        val textApi = MimoTextApi(apiKey)
        val contextManager = ContextManager(db.messageDao(), db.summaryDao(), textApi)
        val chatRepository = ChatRepository(db.messageDao(), textApi, contextManager)
        val personaRepository = PersonaRepository(db.personaDao(), db.voiceConfigDao())

        val basePrompt = personaRepository.getSystemPrompt()
        val topics = listOf(
            "请用一两句话主动问候用户。",
            "请用一两句话关心用户。",
            "请用一两句话分享一个有趣的想法。",
            "请用一两句话问问用户在做什么。",
            "请用一两句话表达对用户的想念。"
        )
        val randomTopic = topics.random()
        val currentTime = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        val systemPrompt = "$basePrompt\n$randomTopic\n\n【重要】回复必须简短，只用一两句话。动作和神态描述必须用括号括起来，例如：（微笑）（点头）\n\n【当前时间】现在是 $currentTime"

        Log.d(TAG, "Sending message with prompt: $systemPrompt")

        // 先决定是否为语音消息
        val isVoice = (0..1).random() == 0
        Log.d(TAG, "Is voice message: $isVoice")

        // 调用API，不保存用户消息，直接基于上下文
        val result = chatRepository.sendMessage(
            content = "",
            systemPrompt = systemPrompt,
            saveUserMessage = false,
            saveAssistantMessage = true
        )
        Log.d(TAG, "Message result: ${result.isSuccess}")

        val personaName = personaRepository.getActivePersona()?.name ?: "AI"

        return if (result.isSuccess) {
            val response = result.getOrNull()
            Log.d(TAG, "Response: $response")
            if (!response.isNullOrBlank()) {
                if (isVoice) {
                    Log.d(TAG, "Sending as voice message")
                    // 更新最后一条消息为语音消息
                    val lastMessage = db.messageDao().getLastMessage()
                    if (lastMessage != null) {
                        db.messageDao().update(lastMessage.copy(isVoice = true))
                    }
                    showVoiceNotification(response, personaName)
                } else {
                    Log.d(TAG, "Sending as text message")
                    showNotification(response, personaName)
                }
            }
            Result.success()
        } else {
            Log.e(TAG, "Failed to send message: ${result.exceptionOrNull()?.message}")
            Result.retry()
        }
        } finally {
            // 释放WakeLock
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "WakeLock released")
            }
        }
    }

    private fun showNotification(message: String, personaName: String) {
        Log.d(TAG, "Showing text notification: $message")
        createNotificationChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(personaName)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showVoiceNotification(message: String, personaName: String) {
        Log.d(TAG, "Showing voice notification: $message")
        createNotificationChannel()

        // 保存语音消息内容，供打开应用时使用
        val prefs = applicationContext.getSharedPreferences("proactive_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_voice_message", message).apply()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("play_voice", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$personaName 发送了一条语音")
            .setContentText("点击播放语音")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "Creating notification channel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "主动消息",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI主动发送的消息通知"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }
}
