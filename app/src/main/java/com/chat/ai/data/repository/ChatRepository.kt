package com.chat.ai.data.repository

import android.util.Log
import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.db.MessageDao
import com.chat.ai.data.model.Message
import com.chat.ai.util.ContextManager
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val messageDao: MessageDao,
    private val textApi: MimoTextApi,
    private val contextManager: ContextManager
) {
    val messages: Flow<List<Message>> = messageDao.getAllMessages()

    suspend fun sendMessage(
        content: String,
        systemPrompt: String,
        responseLength: String = "normal",
        saveUserMessage: Boolean = true,
        saveAssistantMessage: Boolean = true
    ): Result<String> {
        // 获取当前日期时间
        val currentTime = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())

        // 获取上下文消息（在保存用户消息之前）
        val contextMessages = contextManager.getContextMessages()

        // 添加当前用户消息到上下文
        val allMessages = contextMessages.toMutableList()
        allMessages.add(MimoTextApi.Message("user", content))

        // 保存用户消息到数据库
        if (saveUserMessage) {
            messageDao.insert(Message(role = "user", content = content))
        }

        val lengthInstruction = when (responseLength) {
            "short" -> "简短回复，一两句话即可。"
            "long" -> "详细回复，展开说明。"
            else -> "正常回复。"
        }
        val finalSystemPrompt = buildString {
            append(systemPrompt)
            append("\n\n规则：$lengthInstruction 动作神态用括号描述，如（微笑）。现在是 $currentTime。")
        }
        Log.d("ChatRepository", "Response length: $responseLength, prompt: $finalSystemPrompt")

        // 调用API
        val result = textApi.sendMessage(finalSystemPrompt, allMessages)

        result.onSuccess { response ->
            // 保存AI回复
            if (saveAssistantMessage) {
                messageDao.insert(Message(role = "assistant", content = response))
            }
        }

        return result
    }
}
