package com.chat.ai.data.api

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MimoTtsApi(private val apiKey: String) {
    companion object {
        private const val TAG = "MimoTtsApi"
    }

    private val baseUrl = "https://api.xiaomimimo.com/v1"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .serializeNulls()
        .create()

    data class TtsMessage(val role: String, val content: String)
    data class AudioConfig(val format: String, val voice: String)

    data class TtsRequestBody(
        val model: String,
        val messages: List<TtsMessage>,
        val audio: AudioConfig? = null
    )

    // 内置音色列表
    val builtinVoices = listOf(
        "冰糖" to "冰糖（女/中文）",
        "茉莉" to "茉莉（女/中文）",
        "苏打" to "苏打（男/中文）",
        "白桦" to "白桦（男/中文）",
        "Mia" to "Mia（女/英文）",
        "Chloe" to "Chloe（女/英文）",
        "Milo" to "Milo（男/英文）",
        "Dean" to "Dean（男/英文）"
    )

    // 内置音色合成
    suspend fun synthesizeBuiltin(
        text: String,
        voice: String = "冰糖",
        styleInstruction: String? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val messages = mutableListOf<TtsMessage>()
            if (styleInstruction != null) {
                messages.add(TtsMessage("user", styleInstruction))
            }
            messages.add(TtsMessage("assistant", text))

            val body = TtsRequestBody(
                model = "mimo-v2.5-tts",
                messages = messages,
                audio = AudioConfig(format = "wav", voice = voice)
            )
            callTtsApi(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 声音设计合成
    suspend fun synthesizeDesign(
        text: String,
        voiceDescription: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val body = TtsRequestBody(
                model = "mimo-v2.5-tts-voicedesign",
                messages = listOf(
                    TtsMessage("user", voiceDescription),
                    TtsMessage("assistant", text)
                ),
                audio = AudioConfig(format = "wav", voice = "")
            )
            callTtsApi(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 声音克隆合成
    suspend fun synthesizeClone(
        text: String,
        audioBase64: String,
        mimeType: String = "audio/wav"
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val voiceValue = "data:$mimeType;base64,$audioBase64"
            val format = when (mimeType) {
                "audio/mpeg", "audio/mp3" -> "mp3"
                "audio/wav" -> "wav"
                "audio/ogg" -> "ogg"
                "audio/flac" -> "flac"
                "audio/mp4", "audio/m4a" -> "m4a"
                else -> "wav"
            }
            val body = TtsRequestBody(
                model = "mimo-v2.5-tts-voiceclone",
                messages = listOf(
                    TtsMessage("user", ""),
                    TtsMessage("assistant", text)
                ),
                audio = AudioConfig(format = format, voice = voiceValue)
            )
            callTtsApi(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callTtsApi(body: TtsRequestBody): Result<ByteArray> {
        // 手动构建JSON避免Gson序列化问题
        val jsonBody = org.json.JSONObject().apply {
            put("model", body.model)
            put("messages", org.json.JSONArray().apply {
                body.messages.forEach { msg ->
                    put(org.json.JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
            body.audio?.let { audio ->
                put("audio", org.json.JSONObject().apply {
                    put("format", audio.format)
                    put("voice", audio.voice)
                })
            }
        }
        val requestBody = jsonBody.toString()
        Log.d(TAG, "Request: $requestBody")

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        Log.d(TAG, "Response code: ${response.code}")
        Log.d(TAG, "Response body (first 500 chars): ${responseBody.take(500)}")

        if (response.isSuccessful) {
            return try {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java)
                val choices = jsonResponse["choices"] as? List<*>
                val firstChoice = choices?.firstOrNull() as? Map<*, *>
                val message = firstChoice?.get("message") as? Map<*, *>
                val audio = message?.get("audio") as? Map<*, *>
                val audioData = audio?.get("data") as? String

                if (!audioData.isNullOrEmpty()) {
                    Log.d(TAG, "Found audio data, length: ${audioData.length}")
                    val audioBytes = Base64.decode(audioData, Base64.DEFAULT)
                    Log.d(TAG, "Decoded audio bytes: ${audioBytes.size}")
                    Result.success(audioBytes)
                } else {
                    Log.e(TAG, "No audio data found in response")
                    Log.d(TAG, "Response keys: ${jsonResponse.keys}")
                    Result.failure(Exception("No audio data in response"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "JSON parse failed", e)
                Result.failure(Exception("Failed to parse response: ${e.message}"))
            }
        } else {
            return Result.failure(Exception("TTS API error: ${response.code} $responseBody"))
        }
    }
}
