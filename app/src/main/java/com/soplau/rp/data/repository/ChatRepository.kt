package com.soplau.rp.data.repository

import com.soplau.rp.data.db.AppDatabase
import com.soplau.rp.data.db.CharacterEntity
import com.soplau.rp.data.db.ChatEntity
import com.soplau.rp.data.db.MessageEntity
import com.soplau.rp.data.db.MessageRole
import com.soplau.rp.data.engine.PromptMessage
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val db: AppDatabase) {

    private val characters = db.characterDao()
    private val chats = db.chatDao()
    private val messages = db.messageDao()

    // ----- Characters -----

    fun observeCharacters(): Flow<List<CharacterEntity>> = characters.observeAll()

    suspend fun getCharacter(id: Long): CharacterEntity? = characters.getById(id)

    suspend fun saveCharacter(character: CharacterEntity): Long =
        if (character.id == 0L) characters.insert(character).also { id ->
            // Every new character starts with a first message so the chat is never empty.
            if (character.firstMessage.isNotBlank()) {
                val chatId = chats.insert(ChatEntity(characterId = id, title = "Новый чат"))
                messages.insert(MessageEntity(chatId = chatId, role = MessageRole.ASSISTANT, content = character.firstMessage))
            }
        } else {
            characters.update(character)
            character.id
        }

    suspend fun deleteCharacter(id: Long) = characters.deleteById(id)

    // ----- Chats -----

    fun observeChats(characterId: Long): Flow<List<ChatEntity>> = chats.observeForCharacter(characterId)

    suspend fun getChat(id: Long): ChatEntity? = chats.getById(id)

    suspend fun createChat(characterId: Long, title: String = "Новый чат"): Long {
        val character = characters.getById(characterId) ?: error("Character $characterId not found")
        val chatId = chats.insert(ChatEntity(characterId = characterId, title = title))
        if (character.firstMessage.isNotBlank()) {
            messages.insert(MessageEntity(chatId = chatId, role = MessageRole.ASSISTANT, content = character.firstMessage))
        }
        return chatId
    }

    suspend fun renameChat(chatId: Long, title: String) = chats.rename(chatId, title)

    suspend fun deleteChat(chatId: Long) = chats.deleteById(chatId)

    // ----- Messages -----

    fun observeMessages(chatId: Long): Flow<List<MessageEntity>> = messages.observeForChat(chatId)

    suspend fun addMessage(chatId: Long, role: String, content: String): Long {
        chats.touch(chatId, System.currentTimeMillis())
        return messages.insert(MessageEntity(chatId = chatId, role = role, content = content))
    }

    suspend fun updateMessage(message: MessageEntity) = messages.update(message)

    suspend fun deleteMessage(messageId: Long) = messages.deleteById(messageId)

    /** Returns the last assistant message of a chat, or null if the chat ends with the user. */
    suspend fun lastAssistantMessage(chatId: Long): MessageEntity? =
        messages.getForChat(chatId).lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }

    /**
     * Builds the request the model sees: character card first, then the last [limit] messages.
     */
    suspend fun buildPrompt(chatId: Long, limit: Int): List<PromptMessage> {
        val chat = chats.getById(chatId) ?: return emptyList()
        val character = characters.getById(chat.characterId) ?: return emptyList()
        val history = messages.getForChat(chatId)
            .filter { it.role != MessageRole.SYSTEM }
            .takeLast(limit.coerceAtLeast(1))

        val system = buildString {
            if (character.systemPrompt.isNotBlank()) append(character.systemPrompt.trim()).append("\n\n")
            append("Ты играешь роль персонажа по имени ").append(character.name).append(".\n")
            if (character.personality.isNotBlank()) append("Характер: ").append(character.personality.trim()).append("\n")
            if (character.scenario.isNotBlank()) append("Сценарий: ").append(character.scenario.trim()).append("\n")
            append("Отвечай только от лица персонажа. Текст в *звездочках* — действия, текст в \"кавычках\" — речь.")
        }

        return buildList {
            add(PromptMessage("system", system))
            history.forEach { add(PromptMessage(it.role, it.content)) }
        }
    }
}
