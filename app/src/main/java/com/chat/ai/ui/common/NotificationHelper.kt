package com.chat.ai.ui.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    fun ensureChannel(
        context: Context,
        channelId: String,
        name: String,
        description: String,
        importance: Int = NotificationManager.IMPORTANCE_HIGH
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(channelId, name, importance).apply {
            this.description = description
        }
        manager(context).createNotificationChannel(channel)
    }

    fun buildBasic(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        contentIntent: PendingIntent,
        bigText: Boolean = false
    ): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        if (bigText) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        return builder.build()
    }

    fun buildVoice(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        contentIntent: PendingIntent,
        playIntent: PendingIntent? = null,
        bigText: Boolean = true
    ): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        if (bigText) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        if (playIntent != null) {
            builder.addAction(android.R.drawable.ic_media_play, "播放语音", playIntent)
        }
        return builder.build()
    }

    fun notify(context: Context, id: Int, notification: Notification) {
        manager(context).notify(id, notification)
    }

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
