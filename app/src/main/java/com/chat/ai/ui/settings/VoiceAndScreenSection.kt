package com.chat.ai.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun VoiceAndScreenSection(
    onNavigateToVoice: () -> Unit,
    onNavigateToScreenShare: () -> Unit
) {
    Text("语音设置", style = MaterialTheme.typography.titleMedium)
    OutlinedButton(
        onClick = onNavigateToVoice,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("音色定制")
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text("屏幕共享", style = MaterialTheme.typography.titleMedium)
    OutlinedButton(
        onClick = onNavigateToScreenShare,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("屏幕共享设置")
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text("悬浮球", style = MaterialTheme.typography.titleMedium)
    val context = LocalContext.current
    var floatingEnabled by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("在其他应用上显示悬浮球")
        Switch(
            checked = floatingEnabled,
            onCheckedChange = { enabled ->
                floatingEnabled = enabled
                if (enabled) {
                    if (android.provider.Settings.canDrawOverlays(context)) {
                        com.chat.ai.service.FloatingChatService.start(context)
                    } else {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                } else {
                    com.chat.ai.service.FloatingChatService.stop(context)
                }
            }
        )
    }
}
