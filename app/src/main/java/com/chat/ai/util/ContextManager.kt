package com.chat.ai.util

import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.db.MemoryDao
import com.chat.ai.data.db.MessageDao
import com.chat.ai.data.db.SummaryDao
import com.chat.ai.data.model.Memory
import com.chat.ai.data.model.Summary

class ContextManager(
    private val messageDao: MessageDao,
    private val summaryDao: SummaryDao,
    private val memoryDao: MemoryDao,
    private val textApi: MimoTextApi
) {
    companion object {
        private const val MAX_RECENT_MESSAGES = 50
    }

    suspend fun getContextMessages(): List<MimoTextApi.Message> {
        val messages = mutableListOf<MimoTextApi.Message>()

        // 添加记忆（按重要性排序，尽可能多注入）
        val memories = memoryDao.getImportant(minImportance = 3, limit = 100)
        if (memories.isNotEmpty()) {
            val semantic = memories.filter { it.type == "semantic" }
            val episodic = memories.filter { it.type == "episodic" }
            val reflections = memories.filter { it.type == "reflection" }

            val memoryBuilder = StringBuilder()
            if (semantic.isNotEmpty()) {
                memoryBuilder.appendLine("【事实与偏好】")
                semantic.forEach { memoryBuilder.appendLine("- ${it.content}") }
            }
            if (episodic.isNotEmpty()) {
                memoryBuilder.appendLine("【经历与事件】")
                episodic.forEach { memoryBuilder.appendLine("- ${it.content}") }
            }
            if (reflections.isNotEmpty()) {
                memoryBuilder.appendLine("【洞察与总结】")
                reflections.forEach { memoryBuilder.appendLine("- ${it.content}") }
            }

            messages.add(MimoTextApi.Message("user", "[系统信息] 以下是关于用户的记忆，请在回复时参考但不要直接提及：\n$memoryBuilder"))
            messages.add(MimoTextApi.Message("assistant", "好的，我了解了。"))
        }

        // 添加摘要作为长期记忆
        val summary = summaryDao.getLatest()
        if (summary != null) {
            messages.add(MimoTextApi.Message("user", "之前的对话摘要：${summary.content}"))
            messages.add(MimoTextApi.Message("assistant", "好的，我了解之前的对话内容。"))
        }

        // 添加最近50条可见消息
        val recentMessages = messageDao.getRecentVisibleMessages(MAX_RECENT_MESSAGES)
        recentMessages.forEach { msg ->
            messages.add(MimoTextApi.Message(msg.role, msg.content))
        }

        return messages
    }

    suspend fun generateSummary() {
        val currentCount = messageDao.getMessageCount()
        val latest = summaryDao.getLatest()
        if (latest != null && currentCount - latest.messageCountAtSnapshot < 7) return

        val messages = messageDao.getRecentVisibleMessages(30)
        if (messages.size < 2) return

        val conversation = messages.joinToString("\n") { "${it.role}: ${it.content}" }

        // 提取记忆（不依赖摘要结果）
        extractMemories(conversation)

        // 生成摘要
        val now = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        val prompt = "现在是$now。请用中文简洁地总结以下对话的要点（100字以内），包括对话发生的大致时间：\n$conversation"
        val result = textApi.sendMessage("", listOf(MimoTextApi.Message("user", prompt)))
        result.onSuccess { summary ->
            summaryDao.insert(Summary(content = summary, messageCountAtSnapshot = currentCount))
        }

        // 每20条消息触发一次反思
        val memoryCount = memoryDao.getCount()
        if (memoryCount >= 5 && currentCount >= 20 && currentCount % 20 < 7) {
            consolidateMemories()
        }
    }

    private suspend fun extractMemories(conversation: String) {
        val existingMemories = memoryDao.getAll()
        val existingText = if (existingMemories.isNotEmpty()) {
            existingMemories.joinToString("\n") { "- ${it.content}" }
        } else {
            "（暂无）"
        }

        val now = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        val prompt = """现在是$now。请从以下对话中提取关于用户的关键信息。
只提取新发现的事实，不要重复已有的记忆。

对每条信息，输出格式为：[类型] 重要性分数 | 内容
- 类型 semantic：用户的固定事实、偏好、习惯（如：名字、职业、喜欢什么）
- 类型 episodic：用户经历的事件、做过的事（如：昨天加班、在学Kotlin、去了哪里）
- 重要性分数：1-10（10=极其重要，1=可有可无）

注意：如果用户提到自己做了什么、经历了什么、正在做什么，请标记为 episodic，并注明时间。
如果没有新信息，回复"无"。最多提取10条。

已有记忆：
$existingText

对话内容：
$conversation"""

        val result = textApi.sendMessage("", listOf(MimoTextApi.Message("user", prompt)))
        result.onSuccess { response ->
            parseMemoryLines(response, defaultType = "semantic", defaultImportance = 5)
        }
        result.onFailure { e ->
            android.util.Log.e("MemorySystem", "extractMemories failed: ${e.message}")
        }
    }

    private suspend fun consolidateMemories() {
        val memories = memoryDao.getAll()
        if (memories.size < 5) return

        val memoryText = memories.joinToString("\n") { "[${it.type}/${it.importance}] ${it.content}" }
        val prompt = """请回顾以下关于用户的记忆，提炼出 1-3 条更高层次的洞察或总结。
这些洞察应该是从多条具体事实中归纳出来的，而不是重复具体事实。
例如：从"用户学Kotlin"、"用户问Compose问题"可以归纳出"用户正在学习Android开发"。

每条洞察格式：重要性分数 | 内容

记忆列表：
$memoryText"""

        val result = textApi.sendMessage("", listOf(MimoTextApi.Message("user", prompt)))
        result.onSuccess { response ->
            parseMemoryLines(response, defaultType = "reflection", defaultImportance = 7)
        }
    }

    private suspend fun parseMemoryLines(response: String, defaultType: String, defaultImportance: Int) {
        if (response.isBlank() || response.contains("无")) return
        val lines = response.lines()
            .map { it.trim().removePrefix("- ").removePrefix("* ") }
            .filter { it.isNotBlank() && !it.contains("无") && it.contains("|") }

        lines.forEach { line ->
            try {
                val parts = line.split("|", limit = 2)
                if (parts.size < 2) return@forEach
                val meta = parts[0].trim()
                val content = parts[1].trim()

                val type = when {
                    meta.contains("episodic") -> "episodic"
                    meta.contains("reflection") -> "reflection"
                    else -> defaultType
                }
                val importance = Regex("(\\d+)").find(meta)?.groupValues?.get(1)?.toIntOrNull() ?: defaultImportance

                if (content.isNotBlank()) {
                    memoryDao.insert(Memory(content = content, type = type, importance = importance.coerceIn(1, 10)))
                }
            } catch (_: Exception) {}
        }
    }
}
