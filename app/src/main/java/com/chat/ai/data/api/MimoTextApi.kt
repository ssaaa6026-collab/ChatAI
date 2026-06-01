package com.chat.ai.data.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MimoTextApi(private val apiKey: String) {
    private val baseUrl = "https://token-plan-cn.xiaomimimo.com/anthropic"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    data class Message(val role: String, val content: String)

    data class RequestBody(
        val model: String = "mimo-v2.5",
        @SerializedName("max_tokens") val maxTokens: Int = 2048,
        val messages: List<Message>,
        val system: String = "",
        val temperature: Double = 0.7
    )

    data class ResponseContent(val type: String, val text: String)
    data class ApiResponse(val content: List<ResponseContent>)
    data class ErrorDetail(val message: String)
    data class ApiErrorResponse(val error: ErrorDetail)

    suspend fun sendMessage(
        systemPrompt: String,
        messages: List<Message>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = RequestBody(
                messages = messages,
                system = systemPrompt
            )
            val request = Request.Builder()
                .url("$baseUrl/v1/messages")
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val apiResponse = gson.fromJson(responseBody, ApiResponse::class.java)
                Result.success(apiResponse.content.firstOrNull()?.text ?: "")
            } else {
                val error = gson.fromJson(responseBody, ApiErrorResponse::class.java)
                Result.failure(Exception(error?.error?.message ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
