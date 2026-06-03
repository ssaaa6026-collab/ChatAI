package com.chat.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class CustomReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getLongExtra("custom_reminder_id", -1) ?: return
        val content = intent.getStringExtra("custom_reminder_content") ?: return
        val weekDays = intent.getStringExtra("custom_reminder_weekdays") ?: return

        val workRequest = OneTimeWorkRequestBuilder<CustomReminderWorker>()
            .setInputData(workDataOf(
                "custom_reminder_id" to reminderId,
                "custom_reminder_content" to content,
                "custom_reminder_weekdays" to weekDays
            ))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
