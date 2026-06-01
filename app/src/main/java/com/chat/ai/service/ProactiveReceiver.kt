package com.chat.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ProactiveReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ProactiveReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive called at ${System.currentTimeMillis()}")
        Log.d(TAG, "Intent: $intent")

        try {
            // 执行任务
            val workRequest = OneTimeWorkRequestBuilder<ProactiveWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "WorkManager task enqueued successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue work", e)
        }
    }
}
