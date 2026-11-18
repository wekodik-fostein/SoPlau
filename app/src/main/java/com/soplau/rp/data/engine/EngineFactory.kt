package com.soplau.rp.data.engine

import com.soplau.rp.data.settings.AppSettings
import com.soplau.rp.data.settings.EngineMode

object EngineFactory {
    fun create(settings: AppSettings): LlmEngine = when (settings.mode) {
        EngineMode.ONLINE -> OpenAiCompatibleEngine(
            baseUrl = settings.apiBaseUrl,
            apiKey = settings.apiKey,
            model = settings.apiModel
        )
        EngineMode.OFFLINE -> {
            if (settings.ggufPath.isNotBlank() && GgufEngine.isNativeAvailable) {
                GgufEngine(settings.ggufPath)
            } else {
                // Termux / llama.cpp server on the same device, or any LAN host.
                OpenAiCompatibleEngine(
                    baseUrl = settings.localBaseUrl,
                    apiKey = null,
                    model = settings.localModel
                )
            }
        }
    }
}
