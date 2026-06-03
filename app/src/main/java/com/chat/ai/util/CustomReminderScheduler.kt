package com.chat.ai.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.chat.ai.data.model.CustomReminder
import com.chat.ai.service.CustomReminderReceiver
import java.util.Calendar

object CustomReminderScheduler {

    fun schedule(context: Context, reminder: CustomReminder) {
        if (!reminder.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CustomReminderReceiver::class.java).apply {
            putExtra("custom_reminder_id", reminder.id)
            putExtra("custom_reminder_content", reminder.content)
            putExtra("custom_reminder_weekdays", reminder.weekDays)
        }

        val requestCode = (reminder.id + 100000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )

    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CustomReminderReceiver::class.java)
        val requestCode = (reminderId + 100000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAll(context: Context, reminders: List<CustomReminder>) {
        reminders.forEach { reminder ->
            cancel(context, reminder.id)
            if (reminder.isEnabled) {
                schedule(context, reminder)
            }
        }
    }
}
