package com.chat.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chat.ai.ui.chat.FloatingChatBubble

class FloatingChatService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val CHANNEL_ID = "floating_chat_channel"
        const val NOTIFICATION_ID = 9999

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FloatingChatService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingChatService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        removeBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingChatService)
            setViewTreeSavedStateRegistryOwner(this@FloatingChatService)
            setContent {
                FloatingChatBubble(onClose = { stopSelf() })
            }
        }

        composeView = view
        windowManager.addView(view, params)
    }

    private fun removeBubble() {
        composeView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        composeView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "悬浮聊天", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI 悬浮球已开启")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}
