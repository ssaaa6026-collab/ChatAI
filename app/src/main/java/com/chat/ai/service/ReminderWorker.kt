package com.chat.ai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chat.ai.ChatApplication
import com.chat.ai.MainActivity
import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.model.Message
import com.chat.ai.data.repository.PersonaRepository
import com.chat.ai.util.ContextManager
import com.chat.ai.util.PrefsManager

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
        Log.d(TAG, "doWork called")

        val reminderType = inputData.getString(KEY_REMINDER_TYPE) ?: return Result.failure()
        Log.d(TAG, "Reminder type: $reminderType")

        // Check if a custom reminder conflicts at this time
        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)

        val app = applicationContext as ChatApplication
        val db = app.database
        val customReminder = db.customReminderDao().getByTime(currentHour, currentMinute)
        if (customReminder != null && customReminder.isEnabled && customReminder.isToday()) {
            Log.d(TAG, "Custom reminder ${customReminder.id} conflicts with $reminderType at $currentHour:$currentMinute, skipping fixed reminder")
            return Result.success()
        }

        val personaRepository = PersonaRepository(db.personaDao(), db.voiceConfigDao())
        val personaName = personaRepository.getActivePersona()?.name ?: "AI"

        val reminderTopic = when (reminderType) {
            "breakfast" -> "该吃早餐了"
            "lunch" -> "该吃午饭了"
            "dinner" -> "该吃晚饭了"
            "sleep" -> "该睡觉了"
            else -> return Result.failure()
        }

        val apiKey = PrefsManager.getApiKey(applicationContext)

        // 尝试 AI 生成，失败则用硬编码文案
        val message = if (apiKey.isNotBlank()) {
            try {
                val textApi = MimoTextApi(apiKey)
                val contextManager = ContextManager(db.messageDao(), db.summaryDao(), textApi)
                val systemPrompt = personaRepository.getSystemPrompt()

                val contextMessages = contextManager.getContextMessages().toMutableList()
                contextMessages.add(MimoTextApi.Message("user", "请提醒我：$reminderTopic"))

                val currentTime = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())
                val finalSystemPrompt = "$systemPrompt\n\n【重要指令】你必须用非常简短的一两句话回复，不要展开。动作和神态描述必须用括号括起来。\n\n【当前时间】现在是 $currentTime"

                db.messageDao().insert(Message(role = "user", content = "[提醒] $reminderTopic", isHidden = true))

                val result = textApi.sendMessage(finalSystemPrompt, contextMessages)
                val aiMessage = result.getOrNull()
                Log.d(TAG, "AI generated message: $aiMessage")

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
        } else {
            null
        }

        val finalMessage = message ?: "$personaName 提醒你：$reminderTopic"
        showNotification(finalMessage, personaName)
        return Result.success()
    }

    private fun showNotification(message: String, personaName: String) {
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "定时提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "饭点、睡觉等定时提醒"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
