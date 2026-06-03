package com.chat.ai

import android.app.Application
import com.chat.ai.data.db.AppDatabase
import com.chat.ai.util.ServiceLocator

class ChatApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
