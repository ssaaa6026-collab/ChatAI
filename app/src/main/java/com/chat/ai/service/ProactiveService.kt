package com.chat.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chat.ai.ChatApplication
import com.chat.ai.data.repository.PersonaRepository
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
        Log.d(TAG, "=== onCreate ===")
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        Log.d(TAG, "Foreground service started")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "=== onStartCommand ===")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "主动消息",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI主动发送的消息通知"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val app = application as ChatApplication
        val personaRepository = PersonaRepository(app.database.personaDao(), app.database.voiceConfigDao())

        // 使用runBlocking获取AI名字
        val personaName = runBlocking(Dispatchers.IO) {
            try {
                val persona = personaRepository.getActivePersona()
                persona?.name ?: "AI"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get persona name", e)
                "AI"
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$personaName 运行中")
            .setContentText("主动消息功能已启用")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
