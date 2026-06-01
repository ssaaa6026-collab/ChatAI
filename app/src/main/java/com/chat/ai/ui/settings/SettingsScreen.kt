package com.chat.ai.ui.settings

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chat.ai.ChatApplication
import com.chat.ai.util.PrefsManager
import com.chat.ai.util.ProactiveScheduler
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToScreenShare: () -> Unit,
    onNavigateToCustomReminders: () -> Unit = {}
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(PrefsManager.getApiKey(context)) }
    var ttsApiKey by remember { mutableStateOf(PrefsManager.getTtsApiKey(context)) }
    var proactiveEnabled by remember { mutableStateOf(PrefsManager.isProactiveEnabled(context)) }
    var intervalMinutes by remember { mutableFloatStateOf(PrefsManager.getProactiveInterval(context).toFloat().coerceIn(1f, 30f)) }
    var responseLength by remember { mutableStateOf(PrefsManager.getResponseLength(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key
            Text("API 配置", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("聊天 API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { PrefsManager.setApiKey(context, apiKey) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存聊天 API Key")
            }

            OutlinedTextField(
                value = ttsApiKey,
                onValueChange = { ttsApiKey = it },
                label = { Text("TTS API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { PrefsManager.setTtsApiKey(context, ttsApiKey) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存 TTS API Key")
            }

            Divider()

            // 用户头像设置
            Text("用户头像", style = MaterialTheme.typography.titleMedium)
            var userAvatarPath by remember { mutableStateOf(PrefsManager.getUserAvatar(context)) }
            val userAvatarLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val bytes = stream.readBytes()
                        val avatarFile = File(context.filesDir, "user_avatar.jpg")
                        avatarFile.writeBytes(bytes)
                        userAvatarPath = avatarFile.absolutePath
                        PrefsManager.setUserAvatar(context, avatarFile.absolutePath)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                if (userAvatarPath.isNotBlank() && File(userAvatarPath).exists()) {
                    val bitmap = BitmapFactory.decodeFile(userAvatarPath)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "用户头像",
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .clickable { userAvatarLauncher.launch("image/*") },
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { userAvatarLauncher.launch("image/*") }
                    ) {
                        Text("选择头像")
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("点击头像可更换")
            }

            Divider()

            // 回复长短设置
            Text("回复设置", style = MaterialTheme.typography.titleMedium)
            Text("回复长短", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = responseLength == "short",
                    onClick = {
                        responseLength = "short"
                        PrefsManager.setResponseLength(context, "short")
                    },
                    label = { Text("简短") }
                )
                FilterChip(
                    selected = responseLength == "normal",
                    onClick = {
                        responseLength = "normal"
                        PrefsManager.setResponseLength(context, "normal")
                    },
                    label = { Text("正常") }
                )
                FilterChip(
                    selected = responseLength == "long",
                    onClick = {
                        responseLength = "long"
                        PrefsManager.setResponseLength(context, "long")
                    },
                    label = { Text("详细") }
                )
            }

            Divider()

            // 音色设置入口
            Text("语音设置", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onNavigateToVoice,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("音色定制")
            }

            Divider()

            // 屏幕共享入口
            Text("屏幕共享", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onNavigateToScreenShare,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("屏幕共享设置")
            }

            Divider()

            // 主动消息
            Text("主动消息", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("启用主动消息")
                Switch(
                    checked = proactiveEnabled,
                    onCheckedChange = { newValue ->
                        proactiveEnabled = newValue
                        PrefsManager.setProactiveEnabled(context, newValue)
                        if (newValue) {
                            val minutes = intervalMinutes.toLong()
                            PrefsManager.setProactiveInterval(context, minutes)
                            try {
                                ProactiveScheduler.schedule(context, minutes)
                            } catch (e: Exception) {
                                android.util.Log.e("SettingsScreen", "ProactiveScheduler.schedule failed", e)
                            }
                        } else {
                            ProactiveScheduler.cancel(context)
                        }
                    }
                )
            }
            Text("消息间隔：${intervalMinutes.toInt()} 分钟")
            Slider(
                value = intervalMinutes,
                onValueChange = { intervalMinutes = it },
                onValueChangeFinished = {
                    val minutes = intervalMinutes.toLong()
                    PrefsManager.setProactiveInterval(context, minutes)
                    if (proactiveEnabled) {
                        try {
                            ProactiveScheduler.schedule(context, minutes)
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsScreen", "Failed to schedule", e)
                        }
                    }
                },
                valueRange = 1f..30f,
                steps = 29,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1分钟", style = MaterialTheme.typography.bodySmall)
                Text("30分钟", style = MaterialTheme.typography.bodySmall)
            }

            Divider()

            // 定时提醒
            Text("定时提醒", style = MaterialTheme.typography.titleMedium)
            var reminderEnabled by remember { mutableStateOf(PrefsManager.isReminderEnabled(context)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("启用定时提醒")
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { newValue ->
                        reminderEnabled = newValue
                        PrefsManager.setReminderEnabled(context, newValue)
                        if (newValue) {
                            com.chat.ai.util.ReminderScheduler.scheduleAllReminders(context)
                        } else {
                            com.chat.ai.util.ReminderScheduler.cancelAllReminders(context)
                        }
                    }
                )
            }
            Text("早餐 7:30 | 午餐 12:00 | 晚餐 18:00 | 睡觉 00:00", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateToCustomReminders,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("自定义提醒管理")
            }

            Divider()

            // 清除聊天记录
            Text("数据管理", style = MaterialTheme.typography.titleMedium)
            var showClearDialog by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            OutlinedButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("清除聊天记录")
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
    }
}
