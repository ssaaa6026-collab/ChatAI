package com.chat.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

class CustomReminderVoiceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val message = intent?.getStringExtra("reminder_message") ?: return

        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "custom_reminder")
            }
        }
    }
}
