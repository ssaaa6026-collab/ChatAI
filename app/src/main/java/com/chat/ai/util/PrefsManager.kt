package com.chat.ai.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PrefsManager {
    private const val PREFS_NAME = "chat_ai_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_TTS_API_KEY = "tts_api_key"
    private const val DEFAULT_API_KEY = ""

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
    }

    fun setApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, key).apply()
    }

    fun getTtsApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_TTS_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
    }

    fun setTtsApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_TTS_API_KEY, key).apply()
    }

    private const val KEY_PROACTIVE_ENABLED = "proactive_enabled"
    private const val KEY_PROACTIVE_INTERVAL = "proactive_interval"

    fun isProactiveEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PROACTIVE_ENABLED, false)
    }

    fun setProactiveEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PROACTIVE_ENABLED, enabled).apply()
    }

    fun getProactiveInterval(context: Context): Long {
        return getPrefs(context).getLong(KEY_PROACTIVE_INTERVAL, 30)
    }

    fun setProactiveInterval(context: Context, interval: Long) {
        getPrefs(context).edit().putLong(KEY_PROACTIVE_INTERVAL, interval).apply()
    }

    private const val KEY_RESPONSE_LENGTH = "response_length"

    fun getResponseLength(context: Context): String {
        return getPrefs(context).getString(KEY_RESPONSE_LENGTH, "normal") ?: "normal"
    }

    fun setResponseLength(context: Context, length: String) {
        getPrefs(context).edit().putString(KEY_RESPONSE_LENGTH, length).apply()
    }

    private const val KEY_USER_AVATAR = "user_avatar_path"
    private const val KEY_AI_AVATAR = "ai_avatar_path"

    fun getUserAvatar(context: Context): String {
        return getPrefs(context).getString(KEY_USER_AVATAR, "") ?: ""
    }

    fun setUserAvatar(context: Context, path: String) {
        getPrefs(context).edit().putString(KEY_USER_AVATAR, path).apply()
    }

    fun getAiAvatar(context: Context): String {
        return getPrefs(context).getString(KEY_AI_AVATAR, "") ?: ""
    }

    fun setAiAvatar(context: Context, path: String) {
        getPrefs(context).edit().putString(KEY_AI_AVATAR, path).apply()
    }

    private const val KEY_REMINDER_ENABLED = "reminder_enabled"

    fun isReminderEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REMINDER_ENABLED, false)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }
}
