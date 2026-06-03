package com.chat.ai.data.repository

import com.chat.ai.data.db.PersonaDao
import com.chat.ai.data.db.VoiceConfigDao
import com.chat.ai.data.model.Persona
import com.chat.ai.data.model.VoiceConfig

class PersonaRepository(
    private val personaDao: PersonaDao,
    private val voiceConfigDao: VoiceConfigDao
) {
    suspend fun getActivePersona(): Persona? = personaDao.getActive()

    suspend fun getSystemPrompt(): String {
        val persona = personaDao.getActive() ?: personaDao.getLatest()
        if (persona == null) return "用口语化的自然表达聊天，不要提及自己是AI。不确定的事就说不知道。"

        // 如果有完整的 systemPrompt，追加反幻觉规则后使用
        if (persona.systemPrompt.isNotBlank()) {
            return persona.systemPrompt + "\n\n请保持角色身份，不要提及自己是AI，不确定的事如实回答。"
        }

        // 否则组合各字段生成完整 prompt
        return buildString {
            if (persona.name.isNotBlank()) {
                append("你是${persona.name}。")
            }
            if (persona.gender.isNotBlank()) {
                append("你的性别是${persona.gender}。")
            }
            if (persona.personality.isNotBlank()) {
                append("你的性格：${persona.personality}。")
            }
            if (persona.style.isNotBlank()) {
                append("你的说话风格：${persona.style}。")
            }
            if (persona.backstory.isNotBlank()) {
                append("你的背景：${persona.backstory}。")
            }
            if (persona.relationship.isNotBlank()) {
                append("你和用户的关系：${persona.relationship}。")
            }
            if (persona.customSettings.isNotBlank()) {
                append(persona.customSettings)
            }

            append("\n\n")
            append("【角色设定】\n")
            append("1. 你是${persona.name ?: "这个人"}，请始终保持这个身份\n")
            append("2. 用角色身份回答所有问题，不要提及自己是AI或语言模型\n")
            append("3. 不确定的事情请说不知道，如实回答\n")
            append("4. 请保持人设中设定的性格和背景\n")
            append("\n")
            append("【对话风格】\n")
            append("- 用口语化表达，像朋友聊天\n")
            append("- 可以用语气词、省略句\n")
            append("- 可以反问、表达好奇、分享感受\n")
        }
    }

    suspend fun getLatestVoiceConfig(): VoiceConfig? = voiceConfigDao.getLatest()

    suspend fun savePersona(persona: Persona) {
        val existing = personaDao.getLatest()
        if (existing != null) {
            personaDao.update(persona.copy(id = existing.id))
        } else {
            personaDao.insert(persona)
        }
    }
}
