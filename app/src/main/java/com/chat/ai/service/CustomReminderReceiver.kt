package com.chat.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class CustomReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CustomReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getLongExtra("custom_reminder_id", -1) ?: return
        val content = intent.getStringExtra("custom_reminder_content") ?: return
        val weekDays = intent.getStringExtra("custom_reminder_weekdays") ?: return

        Log.d(TAG, "onReceive: id=$reminderId, content=$content, weekDays=$weekDays")

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
