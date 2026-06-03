package com.chat.ai.ui.navigation

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chat.ai.ChatApplication
import com.chat.ai.ui.chat.ChatScreen
import com.chat.ai.ui.chat.ChatViewModel
import com.chat.ai.ui.persona.PersonaScreen
import com.chat.ai.ui.settings.SettingsScreen
import com.chat.ai.ui.voice.VoiceSettingsScreen
import com.chat.ai.ui.screen.ScreenShareScreen
import com.chat.ai.ui.reminder.CustomReminderScreen

private const val ANIM_DURATION = 300

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val db = (application as ChatApplication).database
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "chat",
        enterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { it } + fadeIn(tween(ANIM_DURATION)) },
        exitTransition = { slideOutHorizontally(tween(ANIM_DURATION)) { -it / 3 } + fadeOut(tween(ANIM_DURATION)) },
        popEnterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { -it / 3 } + fadeIn(tween(ANIM_DURATION)) },
        popExitTransition = { slideOutHorizontally(tween(ANIM_DURATION)) { it } + fadeOut(tween(ANIM_DURATION)) }
    ) {
        composable("chat") {
            ChatScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPersona = { navController.navigate("persona") },
                viewModel = chatViewModel
            )
        }
        composable("persona") {
            PersonaScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVoice = { navController.navigate("voice_settings") },
                onNavigateToScreenShare = { navController.navigate("screen_share") },
                onNavigateToCustomReminders = { navController.navigate("custom_reminders") }
            )
        }
        composable("voice_settings") {
            VoiceSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                voiceConfigDao = db.voiceConfigDao()
            )
        }
        composable("screen_share") {
            ScreenShareScreen(
                onNavigateBack = { navController.popBackStack() },
                chatViewModel = chatViewModel
            )
        }
        composable("custom_reminders") {
            CustomReminderScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
