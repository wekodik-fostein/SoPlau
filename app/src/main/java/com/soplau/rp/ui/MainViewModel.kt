package com.soplau.rp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.soplau.rp.SoPlauApp
import com.soplau.rp.data.db.CharacterEntity
import com.soplau.rp.data.repository.ChatRepository
import com.soplau.rp.util.ImageStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: ChatRepository,
    private val images: ImageStorage
) : ViewModel() {

    val characters: StateFlow<List<CharacterEntity>> = repository.observeCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun chatsFor(characterId: Long) = repository.observeChats(characterId)

    fun newChat(characterId: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.createChat(characterId)) }
    }

    fun renameChat(chatId: Long, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repository.renameChat(chatId, clean) }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch { repository.deleteChat(chatId) }
    }

    fun deleteCharacter(character: CharacterEntity) {
        viewModelScope.launch {
            repository.deleteCharacter(character.id)
            images.delete(character.avatarPath)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SoPlauApp
                MainViewModel(app.container.repository, app.container.images)
            }
        }
    }
}
