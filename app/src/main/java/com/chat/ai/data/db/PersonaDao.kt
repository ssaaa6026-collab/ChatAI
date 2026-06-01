package com.chat.ai.data.db

import androidx.room.*
import com.chat.ai.data.model.Persona

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Persona?

    @Query("SELECT * FROM personas ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): Persona?

    @Insert
    suspend fun insert(persona: Persona)

    @Update
    suspend fun update(persona: Persona)

    @Query("SELECT * FROM personas")
    suspend fun getAll(): List<Persona>
}
