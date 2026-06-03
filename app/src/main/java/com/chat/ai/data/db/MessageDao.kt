package com.chat.ai.data.db

import androidx.room.*
import com.chat.ai.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE isHidden = 0 ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<Message>>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessagesForContext(): Flow<List<Message>>

    @Insert
    suspend fun insert(message: Message)

    @Update
    suspend fun update(message: Message)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<Message>

    @Query("SELECT * FROM messages WHERE isHidden = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentVisibleMessages(limit: Int): List<Message>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(): Message?

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM messages WHERE timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesBefore(beforeTimestamp: Long, limit: Int): List<Message>
}
