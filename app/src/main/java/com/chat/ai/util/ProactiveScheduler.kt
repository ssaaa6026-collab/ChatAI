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
            cancel(context)

            val serviceIntent = Intent(context, ProactiveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ProactiveService", e)
            }

            scheduleAlarm(context, intervalMinutes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule", e)
        }
    }

    private fun scheduleAlarm(context: Context, intervalMinutes: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ProactiveReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val intervalMillis = intervalMinutes * 60 * 1000L
        val triggerTime = SystemClock.elapsedRealtime() + intervalMillis

        try {
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                intervalMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
        }
    }

    fun cancel(context: Context) {
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
