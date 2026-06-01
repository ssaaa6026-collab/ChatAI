package com.chat.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive called")

        val reminderType = intent?.getStringExtra(ReminderWorker.KEY_REMINDER_TYPE) ?: return
        Log.d(TAG, "Reminder type: $reminderType")

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_TYPE to reminderType))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "WorkManager task enqueued for $reminderType")
    }
}
