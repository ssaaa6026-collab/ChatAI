package com.chat.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: String = "semantic",       // semantic: 事实偏好, episodic: 事件, reflection: 反思洞察
    val importance: Int = 5,             // 1-10, 越高越重要
    val createdAt: Long = System.currentTimeMillis()
)
