package com.chat.ai.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.chat.ai.ui.common.NotificationHelper
import com.chat.ai.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ProactiveService : Service() {
    companion object {
        private const val TAG = "ProactiveService"
        private const val CHANNEL_ID = "proactive_message_channel"
        private const val NOTIFICATION_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(
            this, CHANNEL_ID, "主动消息", "AI主动发送的消息通知",
            NotificationManager.IMPORTANCE_LOW
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun createNotification(): Notification {
        val personaName = runBlocking(Dispatchers.IO) {
            try {
                ServiceLocator.personaRepository().getActivePersona()?.name ?: "AI"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get persona name", e)
                "AI"
            }
        }
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$personaName 运行中")
            .setContentText("主动消息功能已启用")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
