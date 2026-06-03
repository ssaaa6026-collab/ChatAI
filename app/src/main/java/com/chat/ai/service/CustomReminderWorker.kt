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
import java.util.Calendar

class CustomReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "CustomReminderWorker"
        const val CHANNEL_ID = "custom_reminder_channel"
    }

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong("custom_reminder_id", -1)
        val content = inputData.getString("custom_reminder_content") ?: return Result.failure()
        val weekDays = inputData.getString("custom_reminder_weekdays") ?: return Result.failure()

        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val todayInOurFormat = if (today == 1) 7 else today - 1
        val activeDays = weekDays.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (activeDays.isNotEmpty() && !activeDays.contains(todayInOurFormat)) {
            return Result.success()
        }

        val db = ServiceLocator.database
        val personaRepository = ServiceLocator.personaRepository()
        val personaName = personaRepository.getActivePersona()?.name ?: "AI"
        val systemPrompt = personaRepository.getSystemPrompt()

        val message = try {
            val textApi = ServiceLocator.textApi()
            val contextManager = ServiceLocator.contextManager()

            val contextMessages = contextManager.getContextMessages().toMutableList()
            contextMessages.add(MimoTextApi.Message("user", "请提醒我：$content"))

            val finalSystemPrompt = PromptTemplates.compose(
                systemPrompt,
                "【重要指令】${PromptTemplates.SHORT_RULE}",
                "【重要指令】${PromptTemplates.ACTION_RULE}",
                PromptTemplates.currentTime()
            )

            db.messageDao().insert(Message(role = "user", content = "[提醒] $content", isHidden = true))

            val aiMessage = textApi.sendMessage(finalSystemPrompt, contextMessages).getOrNull()
            if (!aiMessage.isNullOrBlank()) {
                db.messageDao().insert(Message(role = "assistant", content = aiMessage, isVoice = true))
                aiMessage
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "AI generation failed", e)
            null
        }

        if (message != null) {
            val prefs = applicationContext.getSharedPreferences("custom_reminder_voice", Context.MODE_PRIVATE)
            prefs.edit().putString("msg_$reminderId", message).apply()
            showVoiceNotification(message, personaName, reminderId)
        } else {
            showNotification("提醒：$content", personaName, reminderId)
        }
        return Result.success()
    }

    private fun showVoiceNotification(message: String, personaName: String, reminderId: Long) {
        NotificationHelper.ensureChannel(applicationContext, CHANNEL_ID, "自定义提醒", "自定义时间提醒")
        val notificationId = (3000 + reminderId).toInt()

        val playIntent = Intent(applicationContext, CustomReminderVoiceReceiver::class.java).apply {
            putExtra("reminder_message", message)
            putExtra("notification_id", notificationId)
        }
        val playPendingIntent = PendingIntent.getBroadcast(
            applicationContext, notificationId + 50000, playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            applicationContext, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationHelper.buildVoice(
            applicationContext, CHANNEL_ID, personaName, message,
            openPendingIntent, playPendingIntent, bigText = true
        )
        NotificationHelper.notify(applicationContext, notificationId, notification)
    }

    private fun showNotification(message: String, personaName: String, reminderId: Long) {
        NotificationHelper.ensureChannel(applicationContext, CHANNEL_ID, "自定义提醒", "自定义时间提醒")
        val notificationId = (3000 + reminderId).toInt()

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
        NotificationHelper.notify(applicationContext, notificationId, notification)
    }
}
