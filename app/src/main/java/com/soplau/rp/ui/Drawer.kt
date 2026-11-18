package com.soplau.rp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.soplau.rp.data.db.CharacterEntity
import com.soplau.rp.data.db.ChatEntity
import java.io.File

/**
 * Navigation drawer body: characters, each with its own saved chats.
 */
@Composable
fun CharacterDrawerContent(
    viewModel: MainViewModel,
    activeChatId: Long?,
    onOpenChat: (Long) -> Unit,
    onNewCharacter: () -> Unit,
    onEditCharacter: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val characters by viewModel.characters.collectAsState()
    var renameTarget by remember { mutableStateOf<ChatEntity?>(null) }
    var deleteChatTarget by remember { mutableStateOf<ChatEntity?>(null) }
    var deleteCharacterTarget by remember { mutableStateOf<CharacterEntity?>(null) }

    ModalDrawerSheet {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("SoPlau", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(Modifier.weight(1f, fill = true)) {
            items(characters, key = { it.id }) { character ->
                CharacterSection(
                    character = character,
                    viewModel = viewModel,
                    activeChatId = activeChatId,
                    onOpenChat = {
                        onOpenChat(it)
                        onCloseDrawer()
                    },
                    onNewChat = { chatId ->
                        onOpenChat(chatId)
                        onCloseDrawer()
                    },
                    onEdit = { onEditCharacter(character.id) },
                    onDeleteCharacter = { deleteCharacterTarget = character },
                    onRenameChat = { renameTarget = it },
                    onDeleteChat = { deleteChatTarget = it }
                )
                HorizontalDivider()
            }
        }

        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text("Новый персонаж") },
            selected = false,
            onClick = {
                onNewCharacter()
                onCloseDrawer()
            },
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        NavigationDrawerItem(
            label = { Text("Настройки") },
            selected = false,
            onClick = {
                onOpenSettings()
                onCloseDrawer()
            },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(12.dp))
    }

    renameTarget?.let { chat ->
        RenameDialog(
            initial = chat.title,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                viewModel.renameChat(chat.id, title)
                renameTarget = null
            }
        )
    }

    deleteChatTarget?.let { chat ->
        ConfirmDialog(
            text = "Удалить чат «${chat.title}»? Сообщения пропадут без возврата.",
            onDismiss = { deleteChatTarget = null },
            onConfirm = {
                viewModel.deleteChat(chat.id)
                deleteChatTarget = null
            }
        )
    }

    deleteCharacterTarget?.let { character ->
        ConfirmDialog(
            text = "Удалить персонажа «${character.name}» и все его чаты?",
            onDismiss = { deleteCharacterTarget = null },
            onConfirm = {
                viewModel.deleteCharacter(character)
                deleteCharacterTarget = null
            }
        )
    }
}

@Composable
private fun CharacterSection(
    character: CharacterEntity,
    viewModel: MainViewModel,
    activeChatId: Long?,
    onOpenChat: (Long) -> Unit,
    onNewChat: (Long) -> Unit,
    onEdit: () -> Unit,
    onDeleteCharacter: () -> Unit,
    onRenameChat: (ChatEntity) -> Unit,
    onDeleteChat: (ChatEntity) -> Unit
) {
    val chats by viewModel.chatsFor(character.id).collectAsState(initial = emptyList())
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(character.avatarPath, Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                character.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.newChat(character.id, onNewChat) }) {
                Icon(Icons.Default.Add, contentDescription = "Новый чат")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Меню персонажа")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Редактировать") }, onClick = {
                        menuOpen = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = {
                        menuOpen = false
                        onDeleteCharacter()
                    })
                }
            }
        }

        chats.forEach { chat ->
            ChatRow(
                chat = chat,
                selected = chat.id == activeChatId,
                onOpen = { onOpenChat(chat.id) },
                onRename = { onRenameChat(chat) },
                onDelete = { onDeleteChat(chat) }
            )
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatEntity,
    selected: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    NavigationDrawerItem(
        label = { Text(chat.title, maxLines = 1) },
        selected = selected,
        onClick = onOpen,
        icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null) },
        badge = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Меню чата")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }, onClick = {
                        menuOpen = false
                        onRename()
                    })
                    DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }, onClick = {
                        menuOpen = false
                        onDelete()
                    })
                }
            }
        },
        modifier = Modifier.padding(start = 40.dp, end = 4.dp)
    )
}

@Composable
fun Avatar(path: String?, modifier: Modifier = Modifier) {
    val model: Any? = path?.let { File(it) }
    if (model == null) {
        Icon(Icons.Default.Person, contentDescription = null, modifier = modifier.clip(CircleShape))
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать чат") },
        text = {
            TextField(value = value, onValueChange = { value = it }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun ConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
