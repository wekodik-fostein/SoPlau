package com.soplau.rp.data.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Wrapper around a native llama.cpp build that loads a .gguf file from device storage.
 *
 * The JNI symbols below are the contract. Until a native library named
 * "soplau_llama" is compiled and packaged into the APK, [isNativeAvailable]
 * returns false and the engine reports a clear error instead of crashing.
 */
class GgufEngine(private val modelPath: String) : LlmEngine {

    override fun stream(messages: List<PromptMessage>, params: GenerationParams): Flow<String> =
        callbackFlow {
            if (!isNativeAvailable) {
                close(EngineException.NotAvailable(
                    "Native llama.cpp library is not packaged in this build. Use a local server (127.0.0.1:8080) instead."
                ))
                return@callbackFlow
            }
            val file = File(modelPath)
            if (!file.exists()) {
                close(EngineException.NotAvailable("GGUF file not found: $modelPath"))
                return@callbackFlow
            }

            val handle = nativeLoad(modelPath)
            if (handle == 0L) {
                close(EngineException.NotAvailable("Failed to load GGUF model"))
                return@callbackFlow
            }
            try {
                val prompt = buildChatPrompt(messages)
                nativeGenerate(handle, prompt, params.temperature, params.maxTokens) { token ->
                    trySend(token)
                }
            } finally {
                nativeFree(handle)
            }
            close()
            awaitClose { }
        }.flowOn(Dispatchers.IO)

    private fun buildChatPrompt(messages: List<PromptMessage>): String = buildString {
        messages.forEach { m ->
            append("<|").append(m.role).append("|>\n")
            append(m.content).append("\n")
        }
        append("<|assistant|>\n")
    }

    companion object {
        val isNativeAvailable: Boolean = runCatching {
            System.loadLibrary("soplau_llama")
        }.isSuccess

        @JvmStatic private external fun nativeLoad(path: String): Long
        @JvmStatic private external fun nativeFree(handle: Long)
        @JvmStatic private external fun nativeGenerate(
            handle: Long,
            prompt: String,
            temperature: Float,
            maxTokens: Int,
            onToken: (String) -> Unit
        )
    }
}
