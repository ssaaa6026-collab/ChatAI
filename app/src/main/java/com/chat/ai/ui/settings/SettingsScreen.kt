package com.chat.ai.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToScreenShare: () -> Unit,
    onNavigateToCustomReminders: () -> Unit = {}
) {
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
            ApiConfigSection()

            Divider()

            UserAvatarSection()

            Divider()

            ResponseLengthSection()

            Divider()

            VoiceAndScreenSection(
                onNavigateToVoice = onNavigateToVoice,
                onNavigateToScreenShare = onNavigateToScreenShare
            )

            Divider()

            ProactiveSection()

            Divider()

            ReminderSection(
                onNavigateToCustomReminders = onNavigateToCustomReminders
            )

            Divider()

            DataManagementSection()
        }
    }
}
