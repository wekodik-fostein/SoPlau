package com.soplau.rp.data.settings

enum class EngineMode { ONLINE, OFFLINE }

enum class AppTheme { PINK_GRADIENT, BLACK }

data class AppSettings(
    val mode: EngineMode = EngineMode.ONLINE,
    val theme: AppTheme = AppTheme.PINK_GRADIENT,

    // Online API (OpenAI-compatible)
    val apiBaseUrl: String = "https://openrouter.ai/api/v1",
    val apiKey: String = "",
    val apiModel: String = "openai/gpt-4o-mini",

    // Offline: either a local OpenAI-compatible server (Termux / llama.cpp server)
    // or a GGUF file loaded through the native wrapper.
    val localBaseUrl: String = "http://127.0.0.1:8080/v1",
    val localModel: String = "local",
    val ggufPath: String = "",

    // Generation
    val temperature: Float = 0.8f,
    val maxTokens: Int = 512,
    val contextMessages: Int = 20
)
