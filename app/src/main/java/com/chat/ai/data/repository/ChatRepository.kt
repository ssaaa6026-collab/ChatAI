package com.chat.ai.data.repository

import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.api.MimoVisionApi
import com.chat.ai.data.db.MessageDao
import com.chat.ai.data.model.Message
import com.chat.ai.util.ContextManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ChatRepository(
    private val messageDao: MessageDao,
    private val textApi: MimoTextApi,
    private val contextManager: ContextManager
) {
    val messages: Flow<List<Message>> = messageDao.getAllMessages()

    suspend fun sendMessage(
        content: String,
        systemPrompt: String,
        saveUserMessage: Boolean = true,
        saveAssistantMessage: Boolean = true,
        displayContent: String = content,
        isVoice: Boolean = false
    ): Result<String> {
        val contextMessages = contextManager.getContextMessages().toMutableList()
        contextMessages.add(MimoTextApi.Message("user", content))

        if (saveUserMessage) {
            messageDao.insert(Message(role = "user", content = displayContent))
        }

        val result = textApi.sendMessage(systemPrompt, contextMessages)
        result.onSuccess { response ->
            if (saveAssistantMessage) {
                messageDao.insert(Message(role = "assistant", content = response, isVoice = isVoice))
            }
        }
        return result
    }

    suspend fun deleteMessage(id: Long) {
        messageDao.deleteById(id)
    }

    suspend fun loadMoreMessages(beforeTimestamp: Long, limit: Int = 30): List<Message> {
        return messageDao.getMessagesBefore(beforeTimestamp, limit)
    }

    fun sendStreamingMessage(
        content: String,
        systemPrompt: String,
        displayContent: String = content
    ): Flow<String> = flow {
        val contextMessages = contextManager.getContextMessages().toMutableList()
        contextMessages.add(MimoTextApi.Message("user", content))

        messageDao.insert(Message(role = "user", content = displayContent))

        val fullResponse = StringBuilder()
        textApi.sendMessageStreaming(systemPrompt, contextMessages).collect { token ->
            fullResponse.append(token)
            emit(token)
        }

        if (fullResponse.isNotEmpty()) {
            messageDao.insert(Message(role = "assistant", content = fullResponse.toString()))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun sendImageMessage(
        content: String,
        imageBytes: ByteArray,
        systemPrompt: String,
        visionApi: MimoVisionApi,
        userMessagePrefix: String = "[图片] "
    ): Result<String> {
        messageDao.insert(Message(role = "user", content = "$userMessagePrefix$content"))

        val result = visionApi.analyzeImage(imageBytes, content, systemPrompt)
        result.onSuccess { response ->
            messageDao.insert(Message(role = "assistant", content = response))
        }
        return result
    }
}
