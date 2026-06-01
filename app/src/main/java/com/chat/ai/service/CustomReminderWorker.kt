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

        Log.d(TAG, "doWork: id=$reminderId, content=$content, weekDays=$weekDays")

        // Check if today matches the weekday schedule
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val todayInOurFormat = if (today == 1) 7 else today - 1
        val activeDays = weekDays.split(",").mapNotNull { it.trim().toIntOrNull() }

        if (activeDays.isNotEmpty() && !activeDays.contains(todayInOurFormat)) {
            Log.d(TAG, "Today ($todayInOurFormat) not in active days ($activeDays), skipping")
            return Result.success()
        }

        val app = applicationContext as ChatApplication
        val db = app.database
        val apiKey = PrefsManager.getApiKey(applicationContext)

        if (apiKey.isBlank()) {
            Log.e(TAG, "API key is blank")
            showNotification(content, "AI", reminderId)
            return Result.success()
        }

        val textApi = MimoTextApi(apiKey)
        val contextManager = ContextManager(db.messageDao(), db.summaryDao(), textApi)
        val personaRepository = PersonaRepository(db.personaDao(), db.voiceConfigDao())
        val personaName = personaRepository.getActivePersona()?.name ?: "AI"
        val systemPrompt = personaRepository.getSystemPrompt()

        // Get context messages
        val contextMessages = contextManager.getContextMessages().toMutableList()

        // Add user reminder message
        val userMessage = "请提醒我：$content"
        contextMessages.add(MimoTextApi.Message("user", userMessage))

        val currentTime = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        val lengthPrompt = "\n\n【重要指令】你必须用非常简短的一两句话回复，不要展开。"
        val actionPrompt = "\n\n【重要指令】动作和神态描述必须用括号括起来，例如：（微笑）（点头）（思考）"
        val timePrompt = "\n\n【当前时间】现在是 $currentTime"
        val finalSystemPrompt = systemPrompt + lengthPrompt + actionPrompt + timePrompt

        // Save user message to database
        db.messageDao().insert(Message(role = "user", content = "[提醒] $content", isHidden = true))

        // Call API with context
        val result = textApi.sendMessage(finalSystemPrompt, contextMessages)

        val message = result.getOrNull()
        Log.d(TAG, "AI generated message: $message")

        if (!message.isNullOrBlank()) {
            // Save AI response to database
            db.messageDao().insert(Message(role = "assistant", content = message, isVoice = true))

            // Save to SharedPreferences for notification playback
            val prefs = applicationContext.getSharedPreferences("custom_reminder_voice", Context.MODE_PRIVATE)
            prefs.edit().putString("msg_$reminderId", message).apply()

            showVoiceNotification(message, personaName, reminderId)
        } else {
            Log.e(TAG, "AI returned empty response, using fallback")
            showNotification("提醒：$content", personaName, reminderId)
        }

        return Result.success()
    }

    private fun showVoiceNotification(message: String, personaName: String, reminderId: Long) {
        createNotificationChannel()
        val notificationId = (3000 + reminderId).toInt()

        // Play voice action
        val playIntent = Intent(applicationContext, CustomReminderVoiceReceiver::class.java).apply {
            putExtra("reminder_message", message)
            putExtra("notification_id", notificationId)
        }
        val playPendingIntent = PendingIntent.getBroadcast(
            applicationContext, notificationId + 50000, playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open app action
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            applicationContext, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(personaName)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "播放语音", playPendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    private fun showNotification(message: String, personaName: String, reminderId: Long) {
        createNotificationChannel()
        val notificationId = (3000 + reminderId).toInt()

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
        manager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "自定义提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "自定义时间提醒"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
