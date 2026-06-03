package com.chat.ai.data.db

import androidx.room.*
import com.chat.ai.data.model.Memory

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importance DESC, createdAt DESC")
    suspend fun getAll(): List<Memory>

    @Query("SELECT * FROM memories WHERE importance >= :minImportance ORDER BY importance DESC, createdAt DESC LIMIT :limit")
    suspend fun getImportant(minImportance: Int = 5, limit: Int = 30): List<Memory>

    @Query("SELECT * FROM memories WHERE type = :type ORDER BY importance DESC, createdAt DESC")
    suspend fun getByType(type: String): List<Memory>

    @Insert
    suspend fun insert(memory: Memory)

    @Update
    suspend fun update(memory: Memory)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getCount(): Int
}
