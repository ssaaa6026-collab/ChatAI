package com.chat.ai.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.chat.ai.data.api.MimoTtsApi
import com.chat.ai.data.model.VoiceConfig
import com.chat.ai.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TtsManager(private val context: Context) {
    companion object {
        private const val TAG = "TtsManager"
    }

    private val ttsApiKey = PrefsManager.getTtsApiKey(context)
    private val ttsApi = MimoTtsApi(ttsApiKey)
    private var mediaPlayer: MediaPlayer? = null

    suspend fun speak(text: String, voiceConfig: VoiceConfig?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 过滤掉括号里的内容
            val cleanText = text
                .replace(Regex("[（(][^）)]*[）)]"), "")  // 中文和英文括号
                .replace(Regex("【[^】]*】"), "")  // 中文方括号
                .trim()

            if (cleanText.isBlank()) {
                return@withContext Result.success(Unit)
            }

            Log.d(TAG, "Starting TTS, apiKey: ${ttsApiKey.take(10)}..., voiceConfig type: ${voiceConfig?.type}")

            val audioBytes = when (voiceConfig?.type) {
                "design" -> {
                    Log.d(TAG, "Using design voice: ${voiceConfig.designPrompt}")
                    ttsApi.synthesizeDesign(cleanText, voiceConfig.designPrompt).getOrThrow()
                }
                "clone" -> {
                    val cloneMimeType = voiceConfig.cloneMimeType.ifEmpty { "audio/wav" }
                    val audioPath = voiceConfig.cloneAudioPath
                    Log.d(TAG, "Using clone voice, mimeType: $cloneMimeType, path: $audioPath")
                    if (audioPath.isEmpty()) {
                        throw Exception("未设置克隆音频文件")
                    }
                    val audioFile = File(audioPath)
                    if (!audioFile.exists()) {
                        throw Exception("克隆音频文件不存在: $audioPath")
                    }
                    val audioBase64 = android.util.Base64.encodeToString(audioFile.readBytes(), android.util.Base64.NO_WRAP)
                    ttsApi.synthesizeClone(cleanText, audioBase64, cloneMimeType).getOrThrow()
                }
                else -> {
                    val voiceId = voiceConfig?.voiceId ?: "冰糖"
                    val styleTags = voiceConfig?.styleTags?.takeIf { it.isNotBlank() }
                    Log.d(TAG, "Using builtin voice: $voiceId, style: $styleTags")
                    ttsApi.synthesizeBuiltin(cleanText, voiceId, styleTags).getOrThrow()
                }
            }

            Log.d(TAG, "Got audio bytes: ${audioBytes.size}")

            val tempFile = File(context.cacheDir, "tts_output.wav")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            withContext(Dispatchers.Main) {
                playAudio(tempFile.absolutePath)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed", e)
            Result.failure(e)
        }
    }

    private fun playAudio(filePath: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build())
            setDataSource(filePath)
            prepare()
            start()
            Log.d(TAG, "Playing audio from $filePath")
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
