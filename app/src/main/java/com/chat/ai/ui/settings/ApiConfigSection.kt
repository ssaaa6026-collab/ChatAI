package com.chat.ai.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chat.ai.util.PrefsManager

@Composable
fun ApiConfigSection() {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(PrefsManager.getApiKey(context)) }
    var ttsApiKey by remember { mutableStateOf(PrefsManager.getTtsApiKey(context)) }

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
}
