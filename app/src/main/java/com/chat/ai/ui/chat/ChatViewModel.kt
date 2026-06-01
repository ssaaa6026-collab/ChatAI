package com.chat.ai.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chat.ai.ChatApplication
import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.api.MimoVisionApi
import com.chat.ai.data.model.Message
import com.chat.ai.data.repository.ChatRepository
import com.chat.ai.data.repository.PersonaRepository
import com.chat.ai.screen.ScreenCapture
import com.chat.ai.speech.TtsManager
import com.chat.ai.util.ContextManager
import com.chat.ai.util.PrefsManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as ChatApplication).database
    private val apiKey = PrefsManager.getApiKey(application)
    private val textApi = MimoTextApi(apiKey)
    private val visionApi = MimoVisionApi(apiKey)
    private val contextManager = ContextManager(db.messageDao(), db.summaryDao(), textApi)
    private val chatRepository = ChatRepository(db.messageDao(), textApi, contextManager)
    private val personaRepository = PersonaRepository(db.personaDao(), db.voiceConfigDao())
    private val ttsManager = TtsManager(application)

    private val _autoSpeak = MutableStateFlow(true)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    private val _personaName = MutableStateFlow("AI")
    val personaName: StateFlow<String> = _personaName.asStateFlow()

    var screenCapture: ScreenCapture? = null

    init {
        viewModelScope.launch {
            val persona = personaRepository.getActivePersona()
            _personaName.value = persona?.name?.takeIf { it.isNotBlank() } ?: "AI"

            // 生成对话摘要
            contextManager.generateSummary()
        }
    }

    val messages: StateFlow<List<Message>> = chatRepository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()

    fun sendMessage(content: String) {
        if (content.isBlank() || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            val systemPrompt = personaRepository.getSystemPrompt()

            // 检查是否有屏幕共享
            val screenBytes = screenCapture?.getLatestFrameBytes()
            Log.d("ChatViewModel", "Screen capture available: ${screenBytes != null}, size: ${screenBytes?.size ?: 0}")

            val result = if (screenBytes != null) {
                // 有屏幕共享，使用Vision API
                val fullPrompt = if (content.isNotBlank()) {
                    content
                } else {
                    "请描述你看到的屏幕内容"
                }

                Log.d("ChatViewModel", "Using Vision API with prompt: $fullPrompt")

                // 保存用户消息
                db.messageDao().insert(Message(role = "user", content = "[屏幕共享] $fullPrompt"))

                val currentTime = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())
                val visionResult = visionApi.analyzeImage(screenBytes, fullPrompt, "$systemPrompt\n\n【当前时间】现在是 $currentTime")
                visionResult.onSuccess { response ->
                    Log.d("ChatViewModel", "Vision API success: $response")
                    db.messageDao().insert(Message(role = "assistant", content = response))
                }
                visionResult.onFailure { e ->
                    Log.e("ChatViewModel", "Vision API failed", e)
                }
                visionResult
            } else {
                // 没有屏幕共享，使用普通聊天
                Log.d("ChatViewModel", "Using normal chat API")
                val responseLength = PrefsManager.getResponseLength(getApplication())
                chatRepository.sendMessage(content, systemPrompt, responseLength)
            }

            result.onSuccess { response ->
                if (_autoSpeak.value && response.isNotBlank()) {
                    val voiceConfig = personaRepository.getLatestVoiceConfig()
                    ttsManager.speak(response, voiceConfig).onFailure { e ->
                        Log.e("ChatViewModel", "TTS failed", e)
                    }
                }
            }
            result.onFailure { e ->
                _error.emit(e.message ?: "发送失败")
            }
            _isLoading.value = false
        }
    }

    fun toggleAutoSpeak() {
        _autoSpeak.value = !_autoSpeak.value
        if (!_autoSpeak.value) {
            ttsManager.stop()
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun clearMessages() {
        viewModelScope.launch {
            db.messageDao().deleteAll()
            db.summaryDao().deleteAll()
        }
    }

    fun speakText(text: String) {
        viewModelScope.launch {
            val voiceConfig = personaRepository.getLatestVoiceConfig()
            ttsManager.speak(text, voiceConfig)
        }
    }

    fun sendMessageWithImage(content: String, imageBytes: ByteArray) {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            val systemPrompt = personaRepository.getSystemPrompt()

            val fullPrompt = if (content.isNotBlank()) {
                content
            } else {
                "请描述这张图片的内容"
            }

            Log.d("ChatViewModel", "Sending message with image, prompt: $fullPrompt")

            // 保存用户消息
            db.messageDao().insert(Message(role = "user", content = "[图片] $fullPrompt"))

            // 获取上下文消息
            val contextMessages = contextManager.getContextMessages()
            val contextText = contextMessages.joinToString("\n") { "${it.role}: ${it.content}" }

            // 构建包含人设和上下文的系统提示
            val currentTime = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())
            val fullSystemPrompt = "$systemPrompt\n\n之前的对话上下文：\n$contextText\n\n请基于你的人设和对话上下文来分析这张图片。注意：动作和神态描述请用括号括起来，例如：（微笑）（点头）\n\n【当前时间】现在是 $currentTime"

            // 使用Vision API分析图片
            val result = visionApi.analyzeImage(imageBytes, fullPrompt, fullSystemPrompt)

            result.onSuccess { response ->
                Log.d("ChatViewModel", "Vision API success: $response")
                db.messageDao().insert(Message(role = "assistant", content = response))

                if (_autoSpeak.value && response.isNotBlank()) {
                    val voiceConfig = personaRepository.getLatestVoiceConfig()
                    ttsManager.speak(response, voiceConfig).onFailure { e ->
                        Log.e("ChatViewModel", "TTS failed", e)
                    }
                }
            }
            result.onFailure { e ->
                Log.e("ChatViewModel", "Vision API failed", e)
                _error.emit(e.message ?: "图片分析失败")
            }
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
