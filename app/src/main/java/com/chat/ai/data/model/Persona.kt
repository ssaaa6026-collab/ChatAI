package com.chat.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personas")
data class Persona(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "AI",
    val gender: String = "",
    val personality: String = "",
    val style: String = "",
    val backstory: String = "",
    val relationship: String = "",
    val customSettings: String = "",
    val systemPrompt: String = "",
    val isActive: Boolean = false
)
