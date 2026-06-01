package com.chat.ai.data.model

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Stable
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,  // "user" or "assistant"
    val content: String,
    val isVoice: Boolean = false,  // 是否为语音消息
    val isHidden: Boolean = false,  // 是否在界面上隐藏
    val timestamp: Long = System.currentTimeMillis()
)
