package com.chat.ai.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.chat.ai.service.ProactiveReceiver
import com.chat.ai.service.ProactiveService

object ProactiveScheduler {
    private const val TAG = "ProactiveScheduler"
    private const val ALARM_REQUEST_CODE = 1002

    fun schedule(context: Context, intervalMinutes: Long = 5) {
        try {
            Log.d(TAG, "=== schedule START ===")
            Log.d(TAG, "Interval: $intervalMinutes minutes")

            // 先取消之前的任务
            cancel(context)

            // 启动前台服务保持后台运行
            Log.d(TAG, "Starting ProactiveService...")
            val serviceIntent = Intent(context, ProactiveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "ProactiveService started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ProactiveService", e)
            }

            // 使用AlarmManager实现短间隔
            Log.d(TAG, "Scheduling alarm...")
            scheduleAlarm(context, intervalMinutes)
            Log.d(TAG, "=== schedule END ===")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule", e)
        }
    }

    private fun scheduleAlarm(context: Context, intervalMinutes: Long) {
        Log.d(TAG, "=== scheduleAlarm START ===")
        Log.d(TAG, "Interval: $intervalMinutes minutes")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        Log.d(TAG, "AlarmManager: $alarmManager")

        val intent = Intent(context, ProactiveReceiver::class.java)
        Log.d(TAG, "Intent: $intent")

        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.d(TAG, "PendingIntent: $pendingIntent")

        val intervalMillis = intervalMinutes * 60 * 1000L
        val triggerTime = SystemClock.elapsedRealtime() + intervalMillis
        Log.d(TAG, "intervalMillis: $intervalMillis, triggerTime: $triggerTime")

        try {
            // 使用setRepeating实现重复闹钟
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                intervalMillis,
                pendingIntent
            )
            Log.d(TAG, "Alarm set successfully!")
            Log.d(TAG, "=== scheduleAlarm END ===")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
        }
    }

    fun cancel(context: Context) {
        Log.d(TAG, "Cancelling all scheduled tasks")
        cancelAlarm(context)

        // 停止前台服务
        val serviceIntent = Intent(context, ProactiveService::class.java)
        context.stopService(serviceIntent)
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ProactiveReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
