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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
    userAvatarBitmap: ImageBitmap? = null,
    aiAvatarBitmap: ImageBitmap? = null,
    ttsManager: TtsManager? = null,
    onDelete: (() -> Unit)? = null,
    personaName: String = "AI"
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

    var isPlaying by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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

            if (!isUser) {
                // AI消息：名字 + 气泡
                Column {
                    Text(
                        personaName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                    if (message.isVoice) {
                        VoiceBubbleContent(
                            message = message,
                            isPlaying = isPlaying,
                            textColor = textColor,
                            bubbleColor = bubbleColor,
                            showText = showText,
                            ttsManager = ttsManager,
                            onPlayToggle = { isPlaying = it },
                            onShowTextToggle = { showText = it }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(bubbleColor)
                                .padding(12.dp)
                        ) {
                            MarkdownText(
                                text = message.content,
                                color = textColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            } else {
                // 用户消息：只有气泡
                if (message.isVoice) {
                    VoiceBubbleContent(
                        message = message,
                        isPlaying = isPlaying,
                        textColor = textColor,
                        bubbleColor = bubbleColor,
                        showText = showText,
                        ttsManager = ttsManager,
                        onPlayToggle = { isPlaying = it },
                        onShowTextToggle = { showText = it }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(20.dp))
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
            }

            if (isUser && avatarBitmap != null) {
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

        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun VoiceBubbleContent(
    message: Message,
    isPlaying: Boolean,
    textColor: Color,
    bubbleColor: Color,
    showText: Boolean,
    ttsManager: TtsManager?,
    onPlayToggle: (Boolean) -> Unit,
    onShowTextToggle: (Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .widthIn(min = 120.dp, max = 280.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bubbleColor)
            .clickable {
                if (isPlaying) {
                    ttsManager?.stop()
                    onPlayToggle(false)
                } else {
                    onPlayToggle(true)
                    coroutineScope.launch(Dispatchers.IO) {
                        val voiceConfig = com.chat.ai.util.ServiceLocator.personaRepository().getLatestVoiceConfig()
                        ttsManager?.speak(message.content, voiceConfig, TtsManager.PRIORITY_HIGH)
                        onPlayToggle(false)
                    }
                }
            }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = if (showText) message.content else "转文字",
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.clickable { onShowTextToggle(!showText) }
    )
}

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    Text(
        text = parseMarkdown(text, color, codeBackground),
        style = style.copy(color = color),
        modifier = modifier
    )
}

private fun parseMarkdown(text: String, baseColor: Color, codeBackground: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        when {
            // **bold**
            i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    val boldText = text.substring(i + 2, end)
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(boldText)
                    }
                    i = end + 2
                } else {
                    builder.append(text[i])
                    i++
                }
            }
            // *italic*
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && end - i > 1) {
                    val italicText = text.substring(i + 1, end)
                    builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(italicText)
                    }
                    i = end + 1
                } else {
                    builder.append(text[i])
                    i++
                }
            }
            // `code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1 && end - i > 1) {
                    val codeText = text.substring(i + 1, end)
                    builder.withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = baseColor,
                            background = codeBackground
                        )
                    ) {
                        append(codeText)
                    }
                    i = end + 1
                } else {
                    builder.append(text[i])
                    i++
                }
            }
            else -> {
                builder.append(text[i])
                i++
            }
        }
    }
    return builder.toAnnotatedString()
}
