package com.chat.ai.ui.persona

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chat.ai.ChatApplication
import com.chat.ai.data.model.Persona
import com.chat.ai.data.repository.PersonaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PersonaState(
    val name: String = "",
    val gender: String = "",
    val personality: String = "",
    val style: String = "",
    val backstory: String = "",
    val relationship: String = "",
    val customSettings: String = "",
    val systemPrompt: String = ""
)

class PersonaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as ChatApplication).database
    private val personaRepository = PersonaRepository(db.personaDao(), db.voiceConfigDao())

    private val _activePersona = MutableStateFlow<PersonaState?>(null)
    val activePersona: StateFlow<PersonaState?> = _activePersona.asStateFlow()

    private val _personaName = MutableStateFlow("AI")
    val personaName: StateFlow<String> = _personaName.asStateFlow()

    init {
        viewModelScope.launch {
            val persona = personaRepository.getActivePersona()
            if (persona != null) {
                _personaName.value = persona.name
                _activePersona.value = PersonaState(
                    name = persona.name,
                    gender = persona.gender,
                    personality = persona.personality,
                    style = persona.style,
                    backstory = persona.backstory,
                    relationship = persona.relationship,
                    customSettings = persona.customSettings,
                    systemPrompt = persona.systemPrompt
                )
            }
        }
    }

    fun savePersona(
        name: String,
        gender: String = "",
        personality: String = "",
        style: String = "",
        backstory: String = "",
        relationship: String = "",
        customSettings: String = ""
    ) {
        viewModelScope.launch {
            val prompt = buildString {
                if (personality.isNotBlank()) append("性格：$personality\n")
                if (style.isNotBlank()) append("说话风格：$style\n")
                if (backstory.isNotBlank()) append("背景：$backstory\n")
                if (relationship.isNotBlank()) append("关系：$relationship\n")
                if (customSettings.isNotBlank()) append("其他：$customSettings\n")
            }
            personaRepository.savePersona(
                Persona(
                    name = name,
                    gender = gender,
                    personality = personality,
                    style = style,
                    backstory = backstory,
                    relationship = relationship,
                    customSettings = customSettings,
                    systemPrompt = prompt.trim(),
                    isActive = true
                )
            )
            _personaName.value = name
            _activePersona.value = PersonaState(
                name = name,
                gender = gender,
                personality = personality,
                style = style,
                backstory = backstory,
                relationship = relationship,
                customSettings = customSettings,
                systemPrompt = prompt.trim()
            )
        }
    }
}
