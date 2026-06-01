package com.chat.ai.ui.voice

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chat.ai.data.api.MimoTtsApi
import com.chat.ai.data.db.VoiceConfigDao
import com.chat.ai.data.model.VoiceConfig
import com.chat.ai.speech.TtsManager
import com.chat.ai.util.PrefsManager
import kotlinx.coroutines.launch
import android.util.Log
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    onNavigateBack: () -> Unit,
    voiceConfigDao: VoiceConfigDao
) {
    val context = LocalContext.current
    val ttsApiKey = remember { PrefsManager.getTtsApiKey(context) }
    val ttsApi = remember { MimoTtsApi(ttsApiKey) }
    val ttsManager = remember { TtsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf("builtin") }
    var selectedVoice by remember { mutableStateOf("冰糖") }
    var designPrompt by remember { mutableStateOf("") }
    var cloneAudioPath by remember { mutableStateOf("") }
    var cloneFileName by remember { mutableStateOf("") }
    var styleTags by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cloneMimeType by remember { mutableStateOf("audio/wav") }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val savedConfig = voiceConfigDao.getLatest()
            if (savedConfig != null) {
                selectedType = savedConfig.type
                selectedVoice = savedConfig.voiceId.ifEmpty { "冰糖" }
                designPrompt = savedConfig.designPrompt
                cloneAudioPath = savedConfig.cloneAudioPath
                styleTags = savedConfig.styleTags
                cloneMimeType = savedConfig.cloneMimeType
                if (cloneAudioPath.isNotEmpty()) {
                    cloneFileName = File(cloneAudioPath).name
                }
            }
        } catch (_: Exception) {}
        isLoaded = true
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size <= 10 * 1024 * 1024) {
                    // 保存到应用内部存储
                    val audioFile = File(context.filesDir, "clone_audio_${System.currentTimeMillis()}.wav")
                    audioFile.writeBytes(bytes)
                    cloneAudioPath = audioFile.absolutePath
                    cloneFileName = it.lastPathSegment ?: "已选择音频"
                    val resolvedType = context.contentResolver.getType(it) ?: ""
                    cloneMimeType = when {
                        resolvedType.contains("mpeg") || resolvedType.contains("mp3") -> "audio/mpeg"
                        resolvedType.contains("wav") -> "audio/wav"
                        else -> "audio/wav"
                    }
                    Log.d("VoiceSettings", "Saved to: $cloneAudioPath, mimeType: $cloneMimeType")
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { ttsManager.stop() } }

    if (!isLoaded) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音色设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Text("←") }
                },
                actions = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val existing = voiceConfigDao.getLatest()
                            val config = VoiceConfig(
                                id = existing?.id ?: 0,
                                type = selectedType,
                                voiceId = if (selectedType == "builtin") selectedVoice else "",
                                designPrompt = if (selectedType == "design") designPrompt else "",
                                cloneAudioPath = if (selectedType == "clone") cloneAudioPath else "",
                                styleTags = styleTags,
                                cloneMimeType = if (selectedType == "clone") cloneMimeType else "audio/wav"
                            )
                            if (existing != null) {
                                voiceConfigDao.update(config)
                            } else {
                                voiceConfigDao.insert(config)
                            }
                        }
                        onNavigateBack()
                    }) { Text("保存") }
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
            Text("选择音色方式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedType == "builtin", onClick = { selectedType = "builtin" }, label = { Text("内置音色") })
                FilterChip(selected = selectedType == "design", onClick = { selectedType = "design" }, label = { Text("声音设计") })
                FilterChip(selected = selectedType == "clone", onClick = { selectedType = "clone" }, label = { Text("声音克隆") })
            }

            when (selectedType) {
                "builtin" -> {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = ttsApi.builtinVoices.find { it.first == selectedVoice }?.second ?: selectedVoice,
                            onValueChange = {}, readOnly = true,
                            label = { Text("选择音色") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            ttsApi.builtinVoices.forEach { (id, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { selectedVoice = id; expanded = false })
                            }
                        }
                    }
                }
                "design" -> {
                    OutlinedTextField(value = designPrompt, onValueChange = { designPrompt = it }, label = { Text("声音描述") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                }
                "clone" -> {
                    OutlinedButton(onClick = { filePicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (cloneFileName.isBlank()) "选择音频文件" else cloneFileName)
                    }
                }
            }

            OutlinedTextField(value = styleTags, onValueChange = { styleTags = it }, label = { Text("风格标签（可选）") }, placeholder = { Text("如：温柔,撒娇") }, modifier = Modifier.fillMaxWidth())

            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) }

            Button(
                onClick = {
                    isTesting = true; errorMessage = null
                    coroutineScope.launch {
                        val result = ttsManager.speak("你好呀，今天过得怎么样？", VoiceConfig(type = selectedType, voiceId = selectedVoice, designPrompt = designPrompt, cloneAudioPath = cloneAudioPath, styleTags = styleTags, cloneMimeType = cloneMimeType))
                        result.onFailure { e -> errorMessage = "语音合成失败: ${e.message}" }
                        isTesting = false
                    }
                },
                enabled = !isTesting,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isTesting) "生成中..." else "试听") }
        }
    }
}
