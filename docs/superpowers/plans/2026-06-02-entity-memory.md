# 实体记忆系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AI 能记住用户的关键事实（名字、偏好、习惯），跨对话持久化，不随摘要丢失

**Architecture:** 新增 `Memory` Room 实体存储独立事实。ContextManager 在生成摘要时同时提取记忆，注入到 system prompt 中。每条记忆独立存储，不压缩不覆盖。

**Tech Stack:** Room database, LLM extraction via MimoTextApi

---

### Task 1: Memory 实体和 DAO

**Files:**
- Create: `app/src/main/java/com/chat/ai/data/model/Memory.kt`
- Create: `app/src/main/java/com/chat/ai/data/db/MemoryDao.kt`
- Modify: `app/src/main/java/com/chat/ai/data/db/AppDatabase.kt`

- [ ] **Step 1: 创建 Memory 实体**

```kotlin
package com.chat.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: 创建 MemoryDao**

```kotlin
package com.chat.ai.data.db

import androidx.room.*
import com.chat.ai.data.model.Memory

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun getAll(): List<Memory>

    @Insert
    suspend fun insert(memory: Memory)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getCount(): Int
}
```

- [ ] **Step 3: 注册到 AppDatabase**

将 `AppDatabase` 的 entities 和 version 改为：

```kotlin
@Database(
    entities = [Message::class, Persona::class, Summary::class, VoiceConfig::class, CustomReminder::class, Memory::class],
    version = 10,
    exportSchema = false
)
```

添加抽象方法：

```kotlin
    abstract fun memoryDao(): MemoryDao
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/chat/ai/data/model/Memory.kt app/src/main/java/com/chat/ai/data/db/MemoryDao.kt app/src/main/java/com/chat/ai/data/db/AppDatabase.kt
git commit -m "feat: add Memory entity and MemoryDao"
```

---

### Task 2: ContextManager 集成记忆

**Files:**
- Modify: `app/src/main/java/com/chat/ai/util/ContextManager.kt`
- Modify: `app/src/main/java/com/chat/ai/util/ServiceLocator.kt`

- [ ] **Step 1: ContextManager 添加 MemoryDao 参数和记忆方法**

```kotlin
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

        // 添加记忆
        val memories = memoryDao.getAll()
        if (memories.isNotEmpty()) {
            val memoryText = memories.joinToString("\n") { "- ${it.content}" }
            messages.add(MimoTextApi.Message("user", "关于用户的记忆：\n$memoryText"))
            messages.add(MimoTextApi.Message("assistant", "好的，我记住了这些信息。"))
        }

        // 添加摘要作为长期记忆
        val summary = summaryDao.getLatest()
        if (summary != null) {
            messages.add(MimoTextApi.Message("user", "之前的对话摘要：${summary.content}"))
            messages.add(MimoTextApi.Message("assistant", "好的，我了解之前的对话内容。"))
        }

        // 只添加最近50条可见消息
        val recentMessages = messageDao.getRecentVisibleMessages(MAX_RECENT_MESSAGES)
        recentMessages.forEach { msg ->
            messages.add(MimoTextApi.Message(msg.role, msg.content))
        }

        return messages
    }

    suspend fun generateSummary() {
        val currentCount = messageDao.getMessageCount()
        val latest = summaryDao.getLatest()
        if (latest != null && currentCount - latest.messageCountAtSnapshot < 10) return

        val messages = messageDao.getRecentVisibleMessages(30)
        if (messages.size < 5) return

        val conversation = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        val prompt = "请用中文简洁地总结以下对话的要点（100字以内）：\n$conversation"

        val result = textApi.sendMessage("", listOf(MimoTextApi.Message("user", prompt)))
        result.onSuccess { summary ->
            summaryDao.insert(Summary(content = summary, messageCountAtSnapshot = currentCount))
        }

        // 同时提取记忆
        extractMemories(conversation)
    }

    private suspend fun extractMemories(conversation: String) {
        val existingMemories = memoryDao.getAll()
        val existingText = if (existingMemories.isNotEmpty()) {
            existingMemories.joinToString("\n") { "- ${it.content}" }
        } else {
            "（暂无）"
        }

        val prompt = """请从以下对话中提取关于用户的关键事实（名字、偏好、习惯、重要信息等）。
只提取新发现的事实，不要重复已有的记忆。
每条事实一行，用简洁的陈述句。如果没有新事实，回复"无"。

已有记忆：
$existingText

对话内容：
$conversation"""

        val result = textApi.sendMessage("", listOf(MimoTextApi.Message("user", prompt)))
        result.onSuccess { response ->
            if (response.isBlank() || response == "无") return@onSuccess
            val newFacts = response.lines()
                .map { it.trim().removePrefix("- ").removePrefix("* ") }
                .filter { it.isNotBlank() && it != "无" }

            newFacts.forEach { fact ->
                memoryDao.insert(Memory(content = fact))
            }
        }
    }
}
```

- [ ] **Step 2: ServiceLocator 更新 contextManager**

```kotlin
    fun contextManager() = ContextManager(database.messageDao(), database.summaryDao(), database.memoryDao(), textApi())
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/chat/ai/util/ContextManager.kt app/src/main/java/com/chat/ai/util/ServiceLocator.kt
git commit -m "feat: integrate entity memory into context system"
```
