package com.chat.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderType = intent?.getStringExtra(ReminderWorker.KEY_REMINDER_TYPE) ?: return

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_TYPE to reminderType))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
