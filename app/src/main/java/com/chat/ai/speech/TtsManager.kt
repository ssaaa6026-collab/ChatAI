package com.chat.ai.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.chat.ai.data.model.VoiceConfig
import com.chat.ai.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TtsManager(private val context: Context) {
    companion object {
        private const val TAG = "TtsManager"
        const val PRIORITY_LOW = 1      // 提醒、主动消息
        const val PRIORITY_MEDIUM = 2   // 屏幕共享
        const val PRIORITY_HIGH = 3     // 用户聊天
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentPriority = 0
    private val mutex = Mutex()

    /**
     * 优先级播报：高优先级打断低优先级，低优先级被跳过。
     * @return true 实际播放了，false 被跳过
     */
    suspend fun speak(text: String, voiceConfig: VoiceConfig?, priority: Int = PRIORITY_LOW): Boolean {
        return mutex.withLock {
            if (priority < currentPriority) {
                Log.d(TAG, "Skipped: priority $priority < current $currentPriority")
                return@withLock false
            }
            currentPriority = priority
            doSpeak(text, voiceConfig)
        }
    }

    private suspend fun doSpeak(text: String, voiceConfig: VoiceConfig?): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanText = text
                .replace(Regex("[（(][^）)]*[）)]"), "")
                .replace(Regex("【[^】]*】"), "")
                .trim()

            if (cleanText.isBlank()) return@withContext true

            val ttsApi = ServiceLocator.ttsApi()
            val audioBytes = when (voiceConfig?.type) {
                "design" -> {
                    ttsApi.synthesizeDesign(cleanText, voiceConfig.designPrompt).getOrThrow()
                }
                "clone" -> {
                    val cloneMimeType = voiceConfig.cloneMimeType.ifEmpty { "audio/wav" }
                    val audioPath = voiceConfig.cloneAudioPath
                    if (audioPath.isEmpty()) throw Exception("未设置克隆音频文件")
                    val audioFile = File(audioPath)
                    if (!audioFile.exists()) throw Exception("克隆音频文件不存在: $audioPath")
                    val audioBase64 = android.util.Base64.encodeToString(audioFile.readBytes(), android.util.Base64.NO_WRAP)
                    ttsApi.synthesizeClone(cleanText, audioBase64, cloneMimeType).getOrThrow()
                }
                else -> {
                    val voiceId = voiceConfig?.voiceId ?: "冰糖"
                    val styleTags = voiceConfig?.styleTags?.takeIf { it.isNotBlank() }
                    ttsApi.synthesizeBuiltin(cleanText, voiceId, styleTags).getOrThrow()
                }
            }

            val tempFile = File(context.cacheDir, "tts_output.wav")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            withContext(Dispatchers.Main) {
                playAudio(tempFile.absolutePath)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed: ${e.message}", e)
            false
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
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentPriority = 0
    }
}
