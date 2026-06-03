package com.chat.ai.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chat.ai.ChatApplication
import com.chat.ai.data.model.Memory
import kotlinx.coroutines.launch

@Composable
fun DataManagementSection() {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var memories by remember { mutableStateOf(listOf<Memory>()) }
    val coroutineScope = rememberCoroutineScope()

    Text("数据管理", style = MaterialTheme.typography.titleMedium)

    OutlinedButton(
        onClick = {
            coroutineScope.launch {
                val app = context.applicationContext as ChatApplication
                memories = app.database.memoryDao().getAll()
                showMemoryDialog = true
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("查看记忆")
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = { showClearDialog = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text("清除聊天记录")
    }

    if (showMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showMemoryDialog = false },
            title = { Text("AI 的记忆（${memories.size} 条）") },
            text = {
                if (memories.isEmpty()) {
                    Text("暂无记忆")
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val grouped = memories.groupBy { it.type }
                        val typeNames = mapOf(
                            "semantic" to "事实与偏好",
                            "episodic" to "经历与事件",
                            "reflection" to "洞察与总结"
                        )
                        grouped.forEach { (type, items) ->
                            Text(
                                typeNames[type] ?: type,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            items.forEach { memory ->
                                Text(
                                    "• ${memory.content}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMemoryDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清除") },
            text = { Text("确定要清除所有聊天记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val app = context.applicationContext as ChatApplication
                            app.database.messageDao().deleteAll()
                            app.database.summaryDao().deleteAll()
                            app.database.memoryDao().deleteAll()
                        }
                        showClearDialog = false
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
