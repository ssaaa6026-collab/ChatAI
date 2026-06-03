package com.chat.ai.util

import android.content.Context
import com.chat.ai.ChatApplication
import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.api.MimoTtsApi
import com.chat.ai.data.api.MimoVisionApi
import com.chat.ai.data.db.AppDatabase
import com.chat.ai.data.repository.ChatRepository
import com.chat.ai.data.repository.PersonaRepository
import com.chat.ai.speech.TtsManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ServiceLocator {
    private lateinit var appContext: Context

    fun init(app: ChatApplication) {
        appContext = app.applicationContext
    }

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var cachedTextKey: String? = null
    @Volatile private var cachedTextApi: MimoTextApi? = null

    fun textApi(): MimoTextApi {
        val key = PrefsManager.getApiKey(appContext)
        val cached = cachedTextApi
        if (cached != null && cachedTextKey == key) return cached
        return synchronized(this) {
            val again = cachedTextApi
            if (again != null && cachedTextKey == key) again
            else MimoTextApi(key, httpClient, "mimo-v2.5-pro").also {
                cachedTextApi = it
                cachedTextKey = key
            }
        }
    }

    @Volatile private var cachedVisionKey: String? = null
    @Volatile private var cachedVisionApi: MimoVisionApi? = null

    fun visionApi(): MimoVisionApi {
        val key = PrefsManager.getApiKey(appContext)
        val cached = cachedVisionApi
        if (cached != null && cachedVisionKey == key) return cached
        return synchronized(this) {
            val again = cachedVisionApi
            if (again != null && cachedVisionKey == key) again
            else MimoVisionApi(key, httpClient).also {
                cachedVisionApi = it
                cachedVisionKey = key
            }
        }
    }

    @Volatile private var cachedTtsKey: String? = null
    @Volatile private var cachedTtsApi: MimoTtsApi? = null

    fun ttsApi(): MimoTtsApi {
        val key = PrefsManager.getTtsApiKey(appContext)
        val cached = cachedTtsApi
        if (cached != null && cachedTtsKey == key) return cached
        return synchronized(this) {
            val again = cachedTtsApi
            if (again != null && cachedTtsKey == key) again
            else MimoTtsApi(key, httpClient).also {
                cachedTtsApi = it
                cachedTtsKey = key
            }
        }
    }

    fun contextManager() = ContextManager(database.messageDao(), database.summaryDao(), database.memoryDao(), textApi())

    fun chatRepository() = ChatRepository(database.messageDao(), textApi(), contextManager())

    fun personaRepository() = PersonaRepository(database.personaDao(), database.voiceConfigDao())

    val ttsManager: TtsManager by lazy { TtsManager(appContext) }
}
