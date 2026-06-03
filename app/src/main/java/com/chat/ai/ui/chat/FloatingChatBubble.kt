package com.chat.ai.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FloatingChatBubble(onClose: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<String>()) }

    AnimatedVisibility(
        visible = !isExpanded,
        enter = scaleIn(),
        exit = scaleOut()
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { isExpanded = true },
            contentAlignment = Alignment.Center
        ) {
            Text("AI", color = MaterialTheme.colorScheme.onPrimary, fontSize = 18.sp)
        }
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AI 对话", style = MaterialTheme.typography.titleSmall)
                    Row {
                        IconButton(onClick = { isExpanded = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "收起", modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = 8.dp)
                ) {
                    messages.forEach { msg ->
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("说点什么...", fontSize = 14.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                messages = messages + inputText
                                inputText = ""
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Send, "发送", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
