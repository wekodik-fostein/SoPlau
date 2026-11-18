package com.soplau.rp.data.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Receives text chunks from native code. Called on the generation thread. */
fun interface TokenSink {
    fun onToken(token: String)
}

/**
 * JNI entry points, implemented in app/src/main/cpp/soplau_llama.cpp.
 * The library is loaded by [GgufEngine.isNativeAvailable]; never call these without checking it.
 */
internal object LlamaNative {
    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Long
    external fun nativeFree(handle: Long)
    external fun nativeGenerate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        temperature: Float,
        maxTokens: Int,
        sink: TokenSink
    ): Int
}

/**
 * Runs a .gguf model through the native llama.cpp build (soplau_llama).
 * The loaded model is cached between messages, so only the first reply pays the load cost.
 */
class GgufEngine(private val modelPath: String) : LlmEngine {

    override fun stream(messages: List<PromptMessage>, params: GenerationParams): Flow<String> =
        channelFlow {
            if (!isNativeAvailable) {
                throw EngineException.NotAvailable(
                    "Нативная библиотека llama.cpp не собрана в этом APK. Используй локальный сервер."
                )
            }
            if (!File(modelPath).isFile) {
                throw EngineException.NotAvailable("GGUF-файл не найден: $modelPath")
            }
            withContext(Dispatchers.IO) {
                generateLocked(messages, params, this@channelFlow)
            }
        }

    private suspend fun generateLocked(
        messages: List<PromptMessage>,
        params: GenerationParams,
        producer: ProducerScope<String>
    ) {
        lock.withLock {
            val handle = ensureLoaded(modelPath)

            val sink = TokenSink { token ->
                val result = producer.trySendBlocking(token)
                // Collector went away (user pressed stop): abort native generation.
                if (result.isFailure) throw CancellationException("Generation stopped")
            }

            val code = LlamaNative.nativeGenerate(
                handle = handle,
                roles = Array(messages.size) { messages[it].role },
                contents = Array(messages.size) { messages[it].content },
                temperature = params.temperature,
                maxTokens = params.maxTokens,
                sink = sink
            )
            when (code) {
                0 -> Unit
                CODE_PROMPT_TOO_LONG -> throw EngineException.NotAvailable(
                    "Промпт не помещается в контекст. Уменьши лимит контекста в настройках."
                )
                CODE_TOKENIZE -> throw EngineException.NotAvailable("Не удалось токенизировать промпт.")
                CODE_DECODE -> throw EngineException.NotAvailable(
                    "Ошибка llama.cpp при генерации. Скорее всего не хватает памяти."
                )
                CODE_CALLBACK -> Unit  // stop requested by the user, not an error
                else -> throw EngineException.NotAvailable("Неизвестная ошибка llama.cpp: $code")
            }
        }
    }

    companion object {
        private const val N_CTX = 4096
        private const val CODE_PROMPT_TOO_LONG = -2
        private const val CODE_TOKENIZE = -3
        private const val CODE_DECODE = -4
        private const val CODE_CALLBACK = -5

        private val lock = Mutex()
        private var loadedPath: String? = null
        private var loadedHandle: Long = 0L

        val isNativeAvailable: Boolean by lazy {
            runCatching { System.loadLibrary("soplau_llama") }.isSuccess
        }

        /** Frees the cached model, e.g. when the user switches to another GGUF file. */
        suspend fun unload() = lock.withLock {
            if (loadedHandle != 0L) LlamaNative.nativeFree(loadedHandle)
            loadedHandle = 0L
            loadedPath = null
        }

        // Caller must hold [lock].
        private fun ensureLoaded(path: String): Long {
            if (loadedHandle != 0L && loadedPath == path) return loadedHandle
            if (loadedHandle != 0L) LlamaNative.nativeFree(loadedHandle)

            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            val handle = LlamaNative.nativeLoad(path, N_CTX, threads)
            if (handle == 0L) {
                loadedHandle = 0L
                loadedPath = null
                throw EngineException.NotAvailable(
                    "Не удалось загрузить GGUF. Файл повреждён или не хватает памяти."
                )
            }
            loadedHandle = handle
            loadedPath = path
            return handle
        }
    }
}
