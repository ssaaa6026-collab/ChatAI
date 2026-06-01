package com.chat.ai.ui.persona

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chat.ai.util.PrefsManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonaViewModel = viewModel()
) {
    val activePersona by viewModel.activePersona.collectAsState()
    var name by remember { mutableStateOf(activePersona?.name ?: "") }
    var gender by remember { mutableStateOf(activePersona?.gender ?: "") }
    var personality by remember { mutableStateOf(activePersona?.personality ?: "") }
    var style by remember { mutableStateOf(activePersona?.style ?: "") }
    var backstory by remember { mutableStateOf(activePersona?.backstory ?: "") }
    var relationship by remember { mutableStateOf(activePersona?.relationship ?: "") }
    var customSettings by remember { mutableStateOf(activePersona?.customSettings ?: "") }

    LaunchedEffect(activePersona) {
        activePersona?.let {
            name = it.name
            gender = it.gender
            personality = it.personality
            style = it.style
            backstory = it.backstory
            relationship = it.relationship
            customSettings = it.customSettings
        }
    }

    // 自动保存函数
    val saveAndBack = {
        viewModel.savePersona(
            name = name,
            gender = gender,
            personality = personality,
            style = style,
            backstory = backstory,
            relationship = relationship,
            customSettings = customSettings
        )
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("人物设定") },
                navigationIcon = {
                    IconButton(onClick = saveAndBack) {
                        Text("←")
                    }
                },
                actions = {
                    TextButton(onClick = saveAndBack) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        val context = LocalContext.current
        var aiAvatarPath by remember { mutableStateOf(PrefsManager.getAiAvatar(context)) }
        val aiAvatarLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bytes = stream.readBytes()
                    val avatarFile = File(context.filesDir, "ai_avatar.jpg")
                    avatarFile.writeBytes(bytes)
                    aiAvatarPath = avatarFile.absolutePath
                    PrefsManager.setAiAvatar(context, avatarFile.absolutePath)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // AI头像
            Text("AI头像", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (aiAvatarPath.isNotBlank() && File(aiAvatarPath).exists()) {
                    val bitmap = BitmapFactory.decodeFile(aiAvatarPath)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "AI头像",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .clickable { aiAvatarLauncher.launch("image/*") },
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { aiAvatarLauncher.launch("image/*") }
                    ) {
                        Text("选择AI头像")
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("点击头像可更换")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名字") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = gender,
                onValueChange = { gender = it },
                label = { Text("性别") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it },
                label = { Text("性格特点") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedTextField(
                value = style,
                onValueChange = { style = it },
                label = { Text("说话风格") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedTextField(
                value = backstory,
                onValueChange = { backstory = it },
                label = { Text("背景故事") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )
            OutlinedTextField(
                value = relationship,
                onValueChange = { relationship = it },
                label = { Text("与用户的关系") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = customSettings,
                onValueChange = { customSettings = it },
                label = { Text("其他设定") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )
        }
    }
}
