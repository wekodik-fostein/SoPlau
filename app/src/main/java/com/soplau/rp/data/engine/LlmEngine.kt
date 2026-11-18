package com.soplau.rp.data.engine

import kotlinx.coroutines.flow.Flow

data class PromptMessage(val role: String, val content: String)

data class GenerationParams(
    val temperature: Float,
    val maxTokens: Int
)

sealed class EngineException(message: String) : Exception(message) {
    class Network(message: String) : EngineException(message)
    class Http(val code: Int, val body: String) : EngineException("HTTP $code: $body")
    class NotAvailable(message: String) : EngineException(message)
}

/**
 * Common contract for every backend (online API, local server, native GGUF).
 * Emits text deltas as they arrive so the UI can print the reply word by word.
 */
interface LlmEngine {
    fun stream(messages: List<PromptMessage>, params: GenerationParams): Flow<String>
}
