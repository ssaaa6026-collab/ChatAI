package com.chat.ai.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chat.ai.util.PrefsManager
import com.chat.ai.util.ProactiveScheduler

@Composable
fun ProactiveSection() {
    val context = LocalContext.current
    var proactiveEnabled by remember { mutableStateOf(PrefsManager.isProactiveEnabled(context)) }
    var intervalMinutes by remember {
        mutableFloatStateOf(PrefsManager.getProactiveInterval(context).toFloat().coerceIn(1f, 30f))
    }

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
                        Log.e("ProactiveSection", "ProactiveScheduler.schedule failed", e)
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
                    Log.e("ProactiveSection", "Failed to schedule", e)
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
}
