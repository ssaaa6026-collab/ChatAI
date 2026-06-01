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
        return persona?.systemPrompt ?: "你是一个友好的AI助手。"
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
