package com.soplau.rp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.soplau.rp.SoPlauApp
import com.soplau.rp.data.db.ChatEntity
import com.soplau.rp.data.db.MessageEntity
import com.soplau.rp.data.db.MessageRole
import com.soplau.rp.data.engine.EngineFactory
import com.soplau.rp.data.engine.EngineException
import com.soplau.rp.data.engine.GenerationParams
import com.soplau.rp.data.repository.ChatRepository
import com.soplau.rp.data.settings.AppSettings
import com.soplau.rp.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val chat: ChatEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val streamingText: String? = null,
    val isGenerating: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val chatId: Long,
    private val repository: ChatRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val transient = MutableStateFlow(TransientState())

    private var generationJob: Job? = null

    val state: StateFlow<ChatUiState> = run {
        val chatFlow = kotlinx.coroutines.flow.flow { emit(repository.getChat(chatId)) }
        kotlinx.coroutines.flow.combine(
            chatFlow,
            repository.observeMessages(chatId),
            transient
        ) { chat, msgs, t ->
            ChatUiState(
                chat = chat,
                messages = msgs,
                streamingText = t.streamingText,
                isGenerating = t.isGenerating,
                error = t.error
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())
    }

    private data class TransientState(
        val streamingText: String? = null,
        val isGenerating: Boolean = false,
        val error: String? = null
    )

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || transient.value.isGenerating) return
        viewModelScope.launch {
            repository.addMessage(chatId, MessageRole.USER, clean)
            generate()
        }
    }

    /** Deletes the last assistant reply and asks the model for a new one. */
    fun regenerate() {
        if (transient.value.isGenerating) return
        viewModelScope.launch {
            val last = repository.lastAssistantMessage(chatId) ?: return@launch
            repository.deleteMessage(last.id)
            generate()
        }
    }

    fun editMessage(message: MessageEntity, newContent: String) {
        val clean = newContent.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.updateMessage(message.copy(content = clean))
        }
    }

    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch { repository.deleteMessage(message.id) }
    }

    fun stop() {
        generationJob?.cancel()
    }

    fun clearError() {
        transient.value = transient.value.copy(error = null)
    }

    private suspend fun generate() {
        val cfg: AppSettings = settings.settings.first()
        transient.value = TransientState(streamingText = "", isGenerating = true)

        generationJob = viewModelScope.launch {
            val buffer = StringBuilder()
            try {
                val prompt = repository.buildPrompt(chatId, cfg.contextMessages)
                val engine = EngineFactory.create(cfg)
                engine.stream(prompt, GenerationParams(cfg.temperature, cfg.maxTokens))
                    .collect { delta ->
                        buffer.append(delta)
                        transient.value = transient.value.copy(streamingText = buffer.toString())
                    }
                if (buffer.toString().isNotBlank()) {
                    repository.addMessage(chatId, MessageRole.ASSISTANT, buffer.toString().trim())
                }
                transient.value = TransientState()
            } catch (e: CancellationException) {
                // User pressed stop: keep whatever was already streamed.
                withContext(NonCancellable) {
                    if (buffer.toString().isNotBlank()) {
                        repository.addMessage(chatId, MessageRole.ASSISTANT, buffer.toString().trim())
                    }
                }
                transient.value = TransientState()
                throw e
            } catch (e: EngineException) {
                transient.value = TransientState(error = e.message ?: "Ошибка генерации")
            } catch (e: Exception) {
                transient.value = TransientState(error = e.message ?: "Неизвестная ошибка")
            }
        }
    }

    companion object {
        fun factory(chatId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SoPlauApp
                ChatViewModel(chatId, app.container.repository, app.container.settings)
            }
        }
    }
}

