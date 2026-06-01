package com.chat.ai.data.db

import androidx.room.*
import com.chat.ai.data.model.Summary

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): Summary?

    @Insert
    suspend fun insert(summary: Summary)

    @Query("DELETE FROM summaries")
    suspend fun deleteAll()
}
