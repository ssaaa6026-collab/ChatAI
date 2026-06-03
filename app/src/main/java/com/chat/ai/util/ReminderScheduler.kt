package com.chat.ai.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chat.ai.service.ReminderReceiver
import com.chat.ai.service.ReminderWorker
import java.util.Calendar

object ReminderScheduler {

    // 提醒时间配置
    private data class ReminderTime(val hour: Int, val minute: Int, val type: String)

    private val reminderTimes = listOf(
        ReminderTime(7, 30, "breakfast"),
        ReminderTime(12, 0, "lunch"),
        ReminderTime(18, 0, "dinner"),
        ReminderTime(0, 0, "sleep")  // 24:00 = 00:00
    )

    fun scheduleAllReminders(context: Context) {
        reminderTimes.forEach { scheduleReminder(context, it) }
    }

    private fun scheduleReminder(context: Context, reminder: ReminderTime) {

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderWorker.KEY_REMINDER_TYPE, reminder.type)
        }

        val requestCode = reminder.type.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 计算下一次提醒时间
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)

            // 如果今天的时间已过，设置为明天
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // 设置每天重复
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )

    }

    fun cancelAllReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        reminderTimes.forEach { reminder ->
            val intent = Intent(context, ReminderReceiver::class.java)
            val requestCode = reminder.type.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
