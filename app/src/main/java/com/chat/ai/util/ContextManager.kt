package com.chat.ai.util

import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.db.MessageDao
import com.chat.ai.data.db.SummaryDao
import com.chat.ai.data.model.Summary

class ContextManager(
    private val messageDao: MessageDao,
    private val summaryDao: SummaryDao,
    private val textApi: MimoTextApi
) {
    companion object {
        private const val MAX_RECENT_MESSAGES = 50
    }

    suspend fun getContextMessages(): List<MimoTextApi.Message> {
        val messages = mutableListOf<MimoTextApi.Message>()

        // 添加摘要作为长期记忆
        val summary = summaryDao.getLatest()
        if (summary != null) {
            messages.add(MimoTextApi.Message("user", "之前的对话摘要：${summary.content}"))
            messages.add(MimoTextApi.Message("assistant", "好的，我了解之前的对话内容。"))
        }

        // 只添加最近50条消息
        val recentMessages = messageDao.getRecentMessages(MAX_RECENT_MESSAGES)
        recentMessages.forEach { msg ->
            messages.add(MimoTextApi.Message(msg.role, msg.content))
        }

        return messages
    }

    suspend fun generateSummary() {
        val messages = messageDao.getRecentMessages(30)
        if (messages.size < 5) return

        val conversation = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        val prompt = "请用中文简洁地总结以下对话的要点（100字以内）：\n$conversation"

        val result = textApi.sendMessage("", listOf(MimoTextApi.Message("user", prompt)))
        result.onSuccess { summary ->
            summaryDao.insert(Summary(content = summary))
        }
    }
}
