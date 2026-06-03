package com.chat.ai.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chat.ai.MainActivity
import com.chat.ai.ui.common.NotificationHelper
import com.chat.ai.util.PromptTemplates
import com.chat.ai.util.ServiceLocator

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
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ChatAI:ProactiveWorker"
        )
        wakeLock.acquire(60 * 1000L)

        try {
            val db = ServiceLocator.database
            val chatRepository = ServiceLocator.chatRepository()
            val personaRepository = ServiceLocator.personaRepository()

            val basePrompt = personaRepository.getSystemPrompt()
            val topics = listOf(
                "请用一两句话主动问候用户。",
                "请用一两句话关心用户。",
                "请用一两句话分享一个有趣的想法。",
                "请用一两句话问问用户在做什么。",
                "请用一两句话表达对用户的想念。"
            )
            val systemPrompt = PromptTemplates.compose(
                basePrompt,
                topics.random(),
                "【重要】回复必须简短，只用一两句话。${PromptTemplates.ACTION_RULE}",
                PromptTemplates.currentTime()
            )

            val isVoice = (0..1).random() == 0

            val result = chatRepository.sendMessage(
                content = "",
                systemPrompt = systemPrompt,
                saveUserMessage = false,
                saveAssistantMessage = true,
                isVoice = isVoice
            )

            val personaName = personaRepository.getActivePersona()?.name ?: "AI"

            return if (result.isSuccess) {
                val response = result.getOrNull()
                if (!response.isNullOrBlank()) {
                    if (isVoice) {
                        showVoiceNotification(response, personaName)
                    } else {
                        showNotification(response, personaName)
                    }
                }
                Result.success()
            } else {
                Log.e(TAG, "Failed to send message", result.exceptionOrNull())
                Result.retry()
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun showNotification(message: String, personaName: String) {
        NotificationHelper.ensureChannel(applicationContext, CHANNEL_ID, "主动消息", "AI主动发送的消息通知")

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationHelper.buildBasic(
            applicationContext, CHANNEL_ID, personaName, message, pendingIntent, bigText = true
        )
        NotificationHelper.notify(applicationContext, NOTIFICATION_ID, notification)
    }

    private fun showVoiceNotification(message: String, personaName: String) {
        NotificationHelper.ensureChannel(applicationContext, CHANNEL_ID, "主动消息", "AI主动发送的消息通知")

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

        val notification = NotificationHelper.buildVoice(
            applicationContext, CHANNEL_ID,
            "$personaName 发送了一条语音", "点击播放语音",
            pendingIntent, bigText = false
        )
        NotificationHelper.notify(applicationContext, NOTIFICATION_ID, notification)
    }
}
