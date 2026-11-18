package com.soplau.rp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.soplau.rp.data.db.MessageEntity
import com.soplau.rp.data.db.MessageRole
import com.soplau.rp.ui.Avatar
import com.soplau.rp.ui.format.RpFormatter
import com.soplau.rp.ui.theme.LocalActionColor
import com.soplau.rp.ui.theme.LocalAppBackground
import com.soplau.rp.data.db.CharacterEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    character: CharacterEntity?,
    onOpenDrawer: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    var deleting by remember { mutableStateOf<MessageEntity?>(null) }

    // Keep the newest text in view while streaming.
    val lastIndex = state.messages.size + if (state.streamingText != null) 0 else -1
    LaunchedEffect(state.messages.size, state.streamingText?.length) {
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex.coerceAtLeast(0))
    }

    val lastAssistantId = state.messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(character?.avatarPath, Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                character?.name ?: "Чат",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Text(
                                state.chat?.title.orEmpty(),
                                fontSize = 12.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Меню")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(LocalAppBackground.current)
                .padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            avatarPath = character?.avatarPath,
                            showRegenerate = message.id == lastAssistantId && !state.isGenerating,
                            onRegenerate = { viewModel.regenerate() },
                            onEdit = { editing = message },
                            onDelete = { deleting = message }
                        )
                    }
                    state.streamingText?.let { partial ->
                        item(key = "streaming") {
                            BubbleContainer(
                                isUser = false,
                                avatarPath = character?.avatarPath
                            ) {
                                Text(
                                    RpFormatter.format(
                                        partial.ifEmpty { "…" },
                                        LocalActionColor.current
                                    ),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                state.error?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Напиши действие или реплику...") },
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 5
                    )
                    Spacer(Modifier.width(8.dp))
                    if (state.isGenerating) {
                        IconButton(onClick = { viewModel.stop() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Остановить")
                        }
                    } else {
                        IconButton(onClick = {
                            viewModel.send(input)
                            input = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                        }
                    }
                }
            }
        }
    }

    editing?.let { msg ->
        EditMessageDialog(
            initial = msg.content,
            onDismiss = { editing = null },
            onSave = { newText ->
                viewModel.editMessage(msg, newText)
                editing = null
            }
        )
    }

    deleting?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text("Удалить это сообщение?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(msg)
                    deleting = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    avatarPath: String?,
    showRegenerate: Boolean,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isUser = message.role == MessageRole.USER
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        BubbleContainer(
            isUser = isUser,
            avatarPath = avatarPath,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = { menuOpen = true }
            )
        ) {
            Text(
                RpFormatter.format(message.content, LocalActionColor.current),
                style = MaterialTheme.typography.bodyLarge
            )
            if (showRegenerate) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onRegenerate) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Перегенерировать")
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Редактировать") }, leadingIcon = {
                Icon(Icons.Default.Edit, contentDescription = null)
            }, onClick = {
                menuOpen = false
                onEdit()
            })
            if (showRegenerate) {
                DropdownMenuItem(text = { Text("Перегенерировать") }, leadingIcon = {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }, onClick = {
                    menuOpen = false
                    onRegenerate()
                })
            }
            DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = {
                Icon(Icons.Default.Delete, contentDescription = null)
            }, onClick = {
                menuOpen = false
                onDelete()
            })
        }
    }
}

@Composable
private fun BubbleContainer(
    isUser: Boolean,
    avatarPath: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Avatar(avatarPath, Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Column(Modifier.padding(12.dp)) { content() }
        }
    }
}
