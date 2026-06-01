package com.chat.ai.data.db

import androidx.room.*
import com.chat.ai.data.model.CustomReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomReminderDao {
    @Query("SELECT * FROM custom_reminders ORDER BY hour, minute")
    fun getAll(): Flow<List<CustomReminder>>

    @Query("SELECT * FROM custom_reminders WHERE isEnabled = 1 ORDER BY hour, minute")
    suspend fun getEnabled(): List<CustomReminder>

    @Insert
    suspend fun insert(reminder: CustomReminder)

    @Update
    suspend fun update(reminder: CustomReminder)

    @Delete
    suspend fun delete(reminder: CustomReminder)

    @Query("SELECT * FROM custom_reminders WHERE hour = :hour AND minute = :minute AND isEnabled = 1")
    suspend fun getByTime(hour: Int, minute: Int): CustomReminder?
}
