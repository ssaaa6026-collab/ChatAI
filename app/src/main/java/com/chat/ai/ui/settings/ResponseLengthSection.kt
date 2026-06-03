package com.chat.ai.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chat.ai.util.PrefsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponseLengthSection() {
    val context = LocalContext.current
    var responseLength by remember { mutableStateOf(PrefsManager.getResponseLength(context)) }

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
}
