package com.chat.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_configs")
data class VoiceConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,          // "builtin", "design", "clone"
    val voiceId: String = "",  // 内置音色ID，如 "冰糖"
    val designPrompt: String = "", // 声音设计描述文本
    val cloneAudioPath: String = "", // 克隆音频的文件路径
    val styleTags: String = "", // 风格标签，如 "温柔,撒娇"
    val cloneMimeType: String = "audio/wav" // 克隆音频的MIME类型
)
