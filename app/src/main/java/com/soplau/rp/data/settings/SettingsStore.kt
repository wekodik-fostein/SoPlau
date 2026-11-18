package com.soplau.rp.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "soplau_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val MODE = stringPreferencesKey("mode")
        val THEME = stringPreferencesKey("theme")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val API_MODEL = stringPreferencesKey("api_model")
        val LOCAL_BASE_URL = stringPreferencesKey("local_base_url")
        val LOCAL_MODEL = stringPreferencesKey("local_model")
        val GGUF_PATH = stringPreferencesKey("gguf_path")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val CONTEXT_MESSAGES = intPreferencesKey("context_messages")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            mode = p[Keys.MODE]?.let { runCatching { EngineMode.valueOf(it) }.getOrNull() } ?: d.mode,
            theme = p[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: d.theme,
            apiBaseUrl = p[Keys.API_BASE_URL] ?: d.apiBaseUrl,
            apiKey = p[Keys.API_KEY] ?: d.apiKey,
            apiModel = p[Keys.API_MODEL] ?: d.apiModel,
            localBaseUrl = p[Keys.LOCAL_BASE_URL] ?: d.localBaseUrl,
            localModel = p[Keys.LOCAL_MODEL] ?: d.localModel,
            ggufPath = p[Keys.GGUF_PATH] ?: d.ggufPath,
            temperature = p[Keys.TEMPERATURE] ?: d.temperature,
            maxTokens = p[Keys.MAX_TOKENS] ?: d.maxTokens,
            contextMessages = p[Keys.CONTEXT_MESSAGES] ?: d.contextMessages
        )
    }

    suspend fun save(s: AppSettings) {
        context.settingsDataStore.edit { p ->
            p[Keys.MODE] = s.mode.name
            p[Keys.THEME] = s.theme.name
            p[Keys.API_BASE_URL] = s.apiBaseUrl
            p[Keys.API_KEY] = s.apiKey
            p[Keys.API_MODEL] = s.apiModel
            p[Keys.LOCAL_BASE_URL] = s.localBaseUrl
            p[Keys.LOCAL_MODEL] = s.localModel
            p[Keys.GGUF_PATH] = s.ggufPath
            p[Keys.TEMPERATURE] = s.temperature
            p[Keys.MAX_TOKENS] = s.maxTokens
            p[Keys.CONTEXT_MESSAGES] = s.contextMessages
        }
    }
}
