package com.chat.ai.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chat.ai.data.model.Message
import com.chat.ai.screen.ScreenCapture
import com.chat.ai.speech.TtsManager
import com.chat.ai.util.PrefsManager
import com.chat.ai.util.PromptTemplates
import com.chat.ai.util.ServiceLocator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val chatRepository = ServiceLocator.chatRepository()
    private val personaRepository = ServiceLocator.personaRepository()
    private val ttsManager = ServiceLocator.ttsManager

    private val _autoSpeak = MutableStateFlow(true)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    private val _personaName = MutableStateFlow("AI")
    val personaName: StateFlow<String> = _personaName.asStateFlow()

    var screenCapture: ScreenCapture? = null

    init {
        viewModelScope.launch {
            val persona = personaRepository.getActivePersona()
            _personaName.value = persona?.name?.takeIf { it.isNotBlank() } ?: "AI"

            ServiceLocator.contextManager().generateSummary()
        }
    }

    val messages: StateFlow<List<Message>> = chatRepository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()

    private val _displayLimit = MutableStateFlow(100)
    val displayLimit: StateFlow<Int> = _displayLimit.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    fun sendMessage(content: String) {
        if (content.isBlank() || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            val systemPrompt = personaRepository.getSystemPrompt()
            val screenBytes = screenCapture?.getLatestFrameBytes()

            val result = if (screenBytes != null) {
                sendImageInternal(
                    content = content.ifBlank { "请描述你看到的屏幕内容" },
                    imageBytes = screenBytes,
                    systemPrompt = systemPrompt,
                    userMessagePrefix = "[屏幕共享] "
                )
            } else {
                sendTextInternal(content, systemPrompt)
            }

            result.onSuccess { response ->
                if (_autoSpeak.value && response.isNotBlank()) {
                    val voiceConfig = personaRepository.getLatestVoiceConfig()
                    ttsManager.speak(response, voiceConfig, TtsManager.PRIORITY_HIGH)
                }
            }
            result.onFailure { e ->
                _error.emit(e.message ?: "发送失败")
            }
            _isLoading.value = false
            ServiceLocator.contextManager().generateSummary()
        }
    }

    fun sendMessageWithImage(content: String, imageBytes: ByteArray) {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _streamingText.value = ""
            val systemPrompt = personaRepository.getSystemPrompt()
            val fullContent = content.ifBlank { "请描述这张图片的内容" }

            val result = sendImageInternal(fullContent, imageBytes, systemPrompt)

            result.onSuccess { response ->
                if (_autoSpeak.value && response.isNotBlank()) {
                    val voiceConfig = personaRepository.getLatestVoiceConfig()
                    ttsManager.speak(response, voiceConfig, TtsManager.PRIORITY_HIGH)
                }
            }
            result.onFailure { e ->
                _error.emit(e.message ?: "图片分析失败")
            }
            _isLoading.value = false
            ServiceLocator.contextManager().generateSummary()
        }
    }

    private suspend fun sendTextInternal(content: String, systemPrompt: String): Result<String> {
        val responseLength = PrefsManager.getResponseLength(getApplication())
        val lengthRule = PromptTemplates.lengthRule(responseLength)
        val fullSystemPrompt = PromptTemplates.compose(
            lengthRule,
            systemPrompt,
            PromptTemplates.ACTION_RULE,
            PromptTemplates.currentTime()
        )
        val apiContent = if (responseLength != "normal") {
            "$content\n\n$lengthRule"
        } else {
            content
        }

        _isStreaming.value = true
        _streamingText.value = ""

        return try {
            val fullResponse = StringBuilder()
            chatRepository.sendStreamingMessage(apiContent, fullSystemPrompt, displayContent = content)
                .collect { token ->
                    fullResponse.append(token)
                    _streamingText.value = fullResponse.toString()
                    kotlinx.coroutines.delay(30)
                }
            _isStreaming.value = false
            // 等 Room 消息列表更新后再清空，最多等 2 秒
            withTimeoutOrNull(2000L) {
                snapshotFlow { messages.value }
                    .first { msgs ->
                        msgs.lastOrNull()?.role == "assistant" &&
                        msgs.lastOrNull()?.content == fullResponse.toString()
                    }
            }
            _streamingText.value = ""
            Result.success(fullResponse.toString())
        } catch (e: Exception) {
            _isStreaming.value = false
            _streamingText.value = ""
            Result.failure(e)
        }
    }

    private suspend fun sendImageInternal(
        content: String,
        imageBytes: ByteArray,
        systemPrompt: String,
        userMessagePrefix: String = "[图片] "
    ): Result<String> {
        val visionApi = ServiceLocator.visionApi()
        val responseLength = PrefsManager.getResponseLength(getApplication())
        val lengthRule = PromptTemplates.lengthRule(responseLength)
        val fullSystemPrompt = PromptTemplates.compose(lengthRule, systemPrompt, PromptTemplates.ACTION_RULE, PromptTemplates.currentTime())
        return chatRepository.sendImageMessage(
            content = content,
            imageBytes = imageBytes,
            systemPrompt = fullSystemPrompt,
            visionApi = visionApi,
            userMessagePrefix = userMessagePrefix
        )
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
            ServiceLocator.database.messageDao().deleteAll()
            ServiceLocator.database.summaryDao().deleteAll()
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            chatRepository.deleteMessage(id)
        }
    }

    fun loadMoreMessages() {
        val currentMessages = messages.value
        if (currentMessages.isEmpty()) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val oldestTimestamp = currentMessages.first().timestamp
            val older = chatRepository.loadMoreMessages(oldestTimestamp)
            if (older.isNotEmpty()) {
                _displayLimit.value += older.size
            }
            _isRefreshing.value = false
        }
    }

    fun speakText(text: String) {
        viewModelScope.launch {
            val voiceConfig = personaRepository.getLatestVoiceConfig()
            ttsManager.speak(text, voiceConfig, TtsManager.PRIORITY_HIGH)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
