package com.soplau.rp.ui.character

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.soplau.rp.SoPlauApp
import com.soplau.rp.data.db.CharacterEntity
import com.soplau.rp.data.repository.ChatRepository
import com.soplau.rp.ui.theme.LocalAppBackground
import com.soplau.rp.util.ImageStorage
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CharacterForm(
    val id: Long = 0,
    val name: String = "",
    val avatarPath: String? = null,
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val systemPrompt: String = ""
)

class CharacterEditViewModel(
    private val characterId: Long,
    private val repository: ChatRepository,
    private val images: ImageStorage
) : ViewModel() {

    private val _form = MutableStateFlow(CharacterForm())
    val form: StateFlow<CharacterForm> = _form.asStateFlow()

    private var originalAvatar: String? = null
    private var loaded = false

    init {
        viewModelScope.launch {
            if (characterId != 0L) {
                repository.getCharacter(characterId)?.let { c ->
                    originalAvatar = c.avatarPath
                    _form.value = CharacterForm(
                        id = c.id,
                        name = c.name,
                        avatarPath = c.avatarPath,
                        personality = c.personality,
                        scenario = c.scenario,
                        firstMessage = c.firstMessage,
                        systemPrompt = c.systemPrompt
                    )
                }
            }
            loaded = true
        }
    }

    fun update(transform: (CharacterForm) -> CharacterForm) {
        _form.value = transform(_form.value)
    }

    fun setAvatar(uri: Uri) {
        val path = images.importAvatar(uri) ?: return
        // A freshly imported avatar replaces the previous unsaved one.
        if (_form.value.avatarPath != originalAvatar) images.delete(_form.value.avatarPath)
        _form.value = _form.value.copy(avatarPath = path)
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.name.isBlank() || !loaded) return
        viewModelScope.launch {
            repository.saveCharacter(
                CharacterEntity(
                    id = f.id,
                    name = f.name.trim(),
                    avatarPath = f.avatarPath,
                    personality = f.personality.trim(),
                    scenario = f.scenario.trim(),
                    firstMessage = f.firstMessage.trim(),
                    systemPrompt = f.systemPrompt.trim()
                )
            )
            // The avatar was replaced during editing: drop the old file.
            if (originalAvatar != null && originalAvatar != f.avatarPath) images.delete(originalAvatar)
            onDone()
        }
    }

    companion object {
        fun factory(characterId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SoPlauApp
                CharacterEditViewModel(characterId, app.container.repository, app.container.images)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditScreen(
    viewModel: CharacterEditViewModel,
    onBack: () -> Unit
) {
    val form by viewModel.form.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.setAvatar(uri) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (form.id == 0L) "Новый персонаж" else "Редактирование") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onBack) }) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(LocalAppBackground.current)
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val model: Any? = form.avatarPath?.let { File(it) }
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(CircleShape)
                    )
                } else {
                    Spacer(Modifier.size(88.dp))
                }
                Spacer(Modifier.width(16.dp))
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Выбрать аватарку") }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("Имя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.personality,
                onValueChange = { v -> viewModel.update { it.copy(personality = v) } },
                label = { Text("Характер / Личность") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.scenario,
                onValueChange = { v -> viewModel.update { it.copy(scenario = v) } },
                label = { Text("Сценарий") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.firstMessage,
                onValueChange = { v -> viewModel.update { it.copy(firstMessage = v) } },
                label = { Text("Первое сообщение") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.systemPrompt,
                onValueChange = { v -> viewModel.update { it.copy(systemPrompt = v) } },
                label = { Text("Системный промт") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.save(onBack) },
                enabled = form.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить персонажа", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
