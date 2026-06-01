package com.chat.ai.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chat.ai.data.model.Message
import com.chat.ai.speech.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: Message,
    onPlayVoice: ((String) -> Unit)? = null,
    userAvatarBitmap: ImageBitmap? = null,
    aiAvatarBitmap: ImageBitmap? = null,
    ttsManager: TtsManager? = null
) {
    val context = LocalContext.current
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val avatarBitmap = if (isUser) userAvatarBitmap else aiAvatarBitmap

    // 语音播放状态
    var isPlaying by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // 时间格式
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser && avatarBitmap != null) {
            // AI头像在左边
            Image(
                bitmap = avatarBitmap,
                contentDescription = "AI头像",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (message.isVoice) {
            // 语音消息样式
            Box(
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bubbleColor)
                    .clickable {
                        if (isPlaying) {
                            ttsManager?.stop()
                            isPlaying = false
                        } else {
                            isPlaying = true
                            coroutineScope.launch(Dispatchers.IO) {
                                val personaRepository = com.chat.ai.data.repository.PersonaRepository(
                                    (context.applicationContext as com.chat.ai.ChatApplication).database.personaDao(),
                                    (context.applicationContext as com.chat.ai.ChatApplication).database.voiceConfigDao()
                                )
                                val voiceConfig = personaRepository.getLatestVoiceConfig()
                                ttsManager?.speak(message.content, voiceConfig)
                                isPlaying = false
                            }
                        }
                    }
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "停止" else "播放",
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "语音消息",
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // 转文字按钮
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (showText) message.content else "转文字",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { showText = !showText }
            )
        } else {
            // 普通文本消息样式
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bubbleColor)
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (isUser && avatarBitmap != null) {
            // 用户头像在右边
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                bitmap = avatarBitmap,
                contentDescription = "用户头像",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }

    // 时间戳
    Text(
        text = timeStr,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
    } // Column
}
