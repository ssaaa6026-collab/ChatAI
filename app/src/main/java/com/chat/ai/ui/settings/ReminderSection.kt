package com.chat.ai.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chat.ai.util.PrefsManager
import com.chat.ai.util.ReminderScheduler

@Composable
fun ReminderSection(
    onNavigateToCustomReminders: () -> Unit
) {
    val context = LocalContext.current
    var reminderEnabled by remember { mutableStateOf(PrefsManager.isReminderEnabled(context)) }

    Text("定时提醒", style = MaterialTheme.typography.titleMedium)
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
                    ReminderScheduler.scheduleAllReminders(context)
                } else {
                    ReminderScheduler.cancelAllReminders(context)
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
}
