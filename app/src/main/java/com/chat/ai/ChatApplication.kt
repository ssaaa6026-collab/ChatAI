package com.chat.ai

import android.app.Application
import com.chat.ai.data.db.AppDatabase

class ChatApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
