package com.arin.grokclone.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.grokDataStore: DataStore<Preferences> by preferencesDataStore(name = "grok_settings")

data class AppSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val stream: Boolean = true,
    val darkTheme: Boolean = true
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val MAX_INPUT_CHARS = 4000
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val STREAM = booleanPreferencesKey("stream")
        val DARK = booleanPreferencesKey("dark_theme")
    }

    val settings: Flow<AppSettings> = context.grokDataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[Keys.BASE_URL] ?: AppSettings.DEFAULT_BASE_URL,
            apiKey = prefs[Keys.API_KEY] ?: "",
            model = prefs[Keys.MODEL] ?: AppSettings.DEFAULT_MODEL,
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: "",
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 2048,
            stream = prefs[Keys.STREAM] ?: true,
            darkTheme = prefs[Keys.DARK] ?: true
        )
    }

    suspend fun setBaseUrl(value: String) = edit { it[Keys.BASE_URL] = value.trim() }

    suspend fun setApiKey(value: String) = edit { it[Keys.API_KEY] = value.trim() }

    suspend fun setModel(value: String) = edit { it[Keys.MODEL] = value.trim() }

    suspend fun setSystemPrompt(value: String) = edit { it[Keys.SYSTEM_PROMPT] = value }

    suspend fun setTemperature(value: Float) = edit { it[Keys.TEMPERATURE] = value.coerceIn(0f, 2f) }

    suspend fun setMaxTokens(value: Int) = edit { it[Keys.MAX_TOKENS] = value.coerceIn(1, 200_000) }

    suspend fun setStream(value: Boolean) = edit { it[Keys.STREAM] = value }

    suspend fun setDarkTheme(value: Boolean) = edit { it[Keys.DARK] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.grokDataStore.edit { prefs -> block(prefs) }
    }
}
