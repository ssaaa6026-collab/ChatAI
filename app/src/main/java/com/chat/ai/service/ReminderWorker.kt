package com.chat.ai.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chat.ai.MainActivity
import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.model.Message
import com.chat.ai.ui.common.NotificationHelper
import com.chat.ai.util.PromptTemplates
import com.chat.ai.util.ServiceLocator

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ReminderWorker"
        const val CHANNEL_ID = "reminder_channel"
        const val NOTIFICATION_ID = 2001
        const val KEY_REMINDER_TYPE = "reminder_type"
    }

    override suspend fun doWork(): Result {
        val reminderType = inputData.getString(KEY_REMINDER_TYPE) ?: return Result.failure()

        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)

        val db = ServiceLocator.database
        val customReminder = db.customReminderDao().getByTime(currentHour, currentMinute)
        if (customReminder != null && customReminder.isEnabled && customReminder.isToday()) {
            return Result.success()
        }

        val personaRepository = ServiceLocator.personaRepository()
        val personaName = personaRepository.getActivePersona()?.name ?: "AI"

        val reminderTopic = when (reminderType) {
            "breakfast" -> "该吃早餐了"
            "lunch" -> "该吃午饭了"
            "dinner" -> "该吃晚饭了"
            "sleep" -> "该睡觉了"
            else -> return Result.failure()
        }

        val message = generateAiMessage(reminderTopic) ?: "$personaName 提醒你：$reminderTopic"
        showNotification(message, personaName)
        return Result.success()
    }

    private suspend fun generateAiMessage(reminderTopic: String): String? {
        return try {
            val db = ServiceLocator.database
            val textApi = ServiceLocator.textApi()
            val contextManager = ServiceLocator.contextManager()
            val personaRepository = ServiceLocator.personaRepository()

            val systemPrompt = personaRepository.getSystemPrompt()
            val contextMessages = contextManager.getContextMessages().toMutableList()
            contextMessages.add(MimoTextApi.Message("user", "请提醒我：$reminderTopic"))

            val finalSystemPrompt = PromptTemplates.compose(
                systemPrompt,
                "【重要指令】${PromptTemplates.SHORT_RULE}${PromptTemplates.ACTION_RULE}",
                PromptTemplates.currentTime()
            )

            db.messageDao().insert(Message(role = "user", content = "[提醒] $reminderTopic", isHidden = true))

            val aiMessage = textApi.sendMessage(finalSystemPrompt, contextMessages).getOrNull()
            if (!aiMessage.isNullOrBlank()) {
                db.messageDao().insert(Message(role = "assistant", content = aiMessage, isVoice = true))
                aiMessage
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI generation failed, using fallback", e)
            null
        }
    }

    private fun showNotification(message: String, personaName: String) {
        NotificationHelper.ensureChannel(applicationContext, CHANNEL_ID, "定时提醒", "饭点、睡觉等定时提醒")

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationHelper.buildBasic(
            applicationContext, CHANNEL_ID, personaName, message, pendingIntent
        )
        NotificationHelper.notify(applicationContext, NOTIFICATION_ID, notification)
    }
}
