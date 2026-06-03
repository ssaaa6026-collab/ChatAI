package com.chat.ai.data.api

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MimoVisionApi(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl = "https://token-plan-cn.xiaomimimo.com/anthropic"
    private val gson = Gson()

    data class ImageSource(val type: String, val media_type: String, val data: String)
    data class ContentBlock(val type: String, val text: String? = null, val source: ImageSource? = null)
    data class VisionMessage(val role: String, val content: List<ContentBlock>)

    data class VisionRequestBody(
        val model: String = "mimo-v2.5",
        @SerializedName("max_tokens") val maxTokens: Int = 2048,
        val messages: List<VisionMessage>,
        val system: String = ""
    )

    data class ResponseContent(val type: String, val text: String)
    data class ApiResponse(val content: List<ResponseContent>)

    suspend fun analyzeImage(
        imageBytes: ByteArray,
        prompt: String,
        systemPrompt: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val message = VisionMessage(
                role = "user",
                content = listOf(
                    ContentBlock(
                        type = "image",
                        source = ImageSource(
                            type = "base64",
                            media_type = "image/jpeg",
                            data = base64Image
                        )
                    ),
                    ContentBlock(type = "text", text = prompt)
                )
            )
            val body = VisionRequestBody(
                messages = listOf(message),
                system = systemPrompt
            )
            val requestBody = gson.toJson(body)

            val request = Request.Builder()
                .url("$baseUrl/v1/messages")
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val apiResponse = gson.fromJson(responseBody, ApiResponse::class.java)
                Result.success(apiResponse.content.firstOrNull()?.text ?: "")
            } else {
                Result.failure(Exception("Vision API error: ${response.code} $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
