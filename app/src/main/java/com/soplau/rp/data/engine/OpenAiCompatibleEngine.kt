package com.soplau.rp.data.engine

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Works with any OpenAI-compatible /chat/completions endpoint:
 * OpenRouter, DeepSeek, OpenAI, or a local llama.cpp / Termux server.
 * Streaming is handled through Server-Sent Events.
 */
class OpenAiCompatibleEngine(
    private val baseUrl: String,
    private val apiKey: String?,
    private val model: String,
    private val client: OkHttpClient = defaultClient
) : LlmEngine {

    override fun stream(messages: List<PromptMessage>, params: GenerationParams): Flow<String> =
        callbackFlow {
            val body = buildBody(messages, params).toRequestBody(JSON_MEDIA)
            val url = baseUrl.trimEnd('/') + "/chat/completions"

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Accept", "text/event-stream")
                .apply {
                    if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey")
                }
                .build()

            val listener = object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == "[DONE]") {
                        close()
                        return
                    }
                    val delta = parseDelta(data)
                    if (!delta.isNullOrEmpty()) trySend(delta)
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val error = if (response != null) {
                        val text = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                        EngineException.Http(response.code, text.take(500))
                    } else {
                        EngineException.Network(t?.message ?: "Network error")
                    }
                    close(error)
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            }

            val source = EventSources.createFactory(client).newEventSource(request, listener)
            awaitClose { source.cancel() }
        }

    private fun buildBody(messages: List<PromptMessage>, params: GenerationParams): String {
        val payload = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("temperature", params.temperature.toDouble())
            put("max_tokens", params.maxTokens)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
        }
        return payload.toString()
    }

    private fun parseDelta(data: String): String? = runCatching {
        val root: JsonObject = Json.parseToJsonElement(data).jsonObject
        val choices: JsonArray = root["choices"]?.jsonArray ?: return@runCatching null
        val first = choices.firstOrNull()?.jsonObject ?: return@runCatching null
        val delta = first["delta"]?.jsonObject ?: return@runCatching null
        delta["content"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
