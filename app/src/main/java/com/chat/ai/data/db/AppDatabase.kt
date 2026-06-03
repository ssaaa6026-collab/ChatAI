package com.chat.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.chat.ai.data.model.*

@Database(
    entities = [Message::class, Persona::class, Summary::class, VoiceConfig::class, CustomReminder::class, Memory::class],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun personaDao(): PersonaDao
    abstract fun summaryDao(): SummaryDao
    abstract fun voiceConfigDao(): VoiceConfigDao
    abstract fun customReminderDao(): CustomReminderDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_ai_database"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
