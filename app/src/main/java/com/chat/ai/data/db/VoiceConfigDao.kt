package com.chat.ai.data.db

import androidx.room.*
import com.chat.ai.data.model.VoiceConfig

@Dao
interface VoiceConfigDao {
    @Query("SELECT * FROM voice_configs WHERE id = :id")
    suspend fun getById(id: Long): VoiceConfig?

    @Query("SELECT * FROM voice_configs ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): VoiceConfig?

    @Insert
    suspend fun insert(voiceConfig: VoiceConfig): Long

    @Update
    suspend fun update(voiceConfig: VoiceConfig)
}
