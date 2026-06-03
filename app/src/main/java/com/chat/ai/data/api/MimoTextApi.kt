package com.chat.ai.data.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MimoTextApi(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val model: String = "mimo-v2.5"
) {
    private val baseUrl = "https://token-plan-cn.xiaomimimo.com/anthropic"
    private val gson = Gson()

    data class Message(val role: String, val content: String)

    data class RequestBody(
        val model: String,
        @SerializedName("max_tokens") val maxTokens: Int = 2048,
        val messages: List<Message>,
        val system: String = "",
        val temperature: Double = 0.7,
        val stream: Boolean = false
    )

    data class ResponseContent(val type: String, val text: String)
    data class ApiResponse(val content: List<ResponseContent>)
    data class ErrorDetail(val message: String)
    data class ApiErrorResponse(val error: ErrorDetail)

    data class StreamDelta(val type: String?, val text: String?, val thinking: String?)
    data class StreamEvent(val type: String?, val delta: StreamDelta?)

    suspend fun sendMessage(
        systemPrompt: String,
        messages: List<Message>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = RequestBody(
                model = model,
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
                android.util.Log.e("MimoTextApi", "API error: $responseBody")
                val error = gson.fromJson(responseBody, ApiErrorResponse::class.java)
                Result.failure(Exception(error?.error?.message ?: "Unknown error: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendMessageStreaming(
        systemPrompt: String,
        messages: List<Message>
    ): Flow<String> = flow {
        val body = RequestBody(
            model = model,
            messages = messages,
            system = systemPrompt,
            stream = true
        )
        val request = Request.Builder()
            .url("$baseUrl/v1/messages")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            android.util.Log.e("MimoTextApi", "Streaming error: $errorBody")
            val error = gson.fromJson(errorBody, ApiErrorResponse::class.java)
            throw Exception(error?.error?.message ?: "Streaming failed: $errorBody")
        }

        response.body?.source()?.let { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val json = line.removePrefix("data: ").trim()
                    try {
                        val event = gson.fromJson(json, StreamEvent::class.java)
                        if (event.type == "content_block_delta" && event.delta?.type == "text_delta") {
                            event.delta?.text?.let { emit(it) }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MimoTextApi", "SSE parse error: $json", e)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
