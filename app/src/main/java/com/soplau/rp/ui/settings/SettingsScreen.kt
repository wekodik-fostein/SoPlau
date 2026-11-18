package com.soplau.rp.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.soplau.rp.SoPlauApp
import com.soplau.rp.data.settings.AppSettings
import com.soplau.rp.data.settings.AppTheme
import com.soplau.rp.data.settings.EngineMode
import com.soplau.rp.data.settings.SettingsStore
import com.soplau.rp.data.engine.GgufEngine
import com.soplau.rp.ui.theme.LocalAppBackground
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context

class SettingsViewModel(
    private val store: SettingsStore,
    private val context: Context
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    init {
        viewModelScope.launch { store.settings.collect { _settings.value = it } }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        viewModelScope.launch { store.save(next) }
    }

    /** Copies a GGUF picked via SAF into app storage so llama.cpp can open it by path. */
    fun importGguf(uri: Uri) {
        viewModelScope.launch {
            _status.value = "Копирую модель..."
            val target = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "models").apply { mkdirs() }
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "model.gguf"
                    val out = File(dir, name.ifEmpty { "model.gguf" })
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    } ?: error("Не удалось открыть файл")
                    out.absolutePath
                }.getOrNull()
            }
            if (target != null) {
                update { it.copy(ggufPath = target) }
                _status.value = "Модель сохранена: $target"
            } else {
                _status.value = "Не удалось скопировать модель"
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SoPlauApp
                SettingsViewModel(app.container.settings, app)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsState()
    val status by viewModel.status.collectAsState()

    val ggufPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importGguf(uri) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("Тема")
            RadioRow("Розовый градиент", s.theme == AppTheme.PINK_GRADIENT) {
                viewModel.update { it.copy(theme = AppTheme.PINK_GRADIENT) }
            }
            RadioRow("Черная", s.theme == AppTheme.BLACK) {
                viewModel.update { it.copy(theme = AppTheme.BLACK) }
            }

            SectionTitle("Режим генерации")
            RadioRow("Online API (OpenAI-совместимый)", s.mode == EngineMode.ONLINE) {
                viewModel.update { it.copy(mode = EngineMode.ONLINE) }
            }
            RadioRow("Offline (локальный сервер / GGUF)", s.mode == EngineMode.OFFLINE) {
                viewModel.update { it.copy(mode = EngineMode.OFFLINE) }
            }

            if (s.mode == EngineMode.ONLINE) {
                SectionTitle("Online API")
                Field("Base URL", s.apiBaseUrl) { v -> viewModel.update { it.copy(apiBaseUrl = v) } }
                Field("API Key", s.apiKey, secret = true) { v -> viewModel.update { it.copy(apiKey = v) } }
                Field("Model Name", s.apiModel) { v -> viewModel.update { it.copy(apiModel = v) } }
            } else {
                SectionTitle("Offline")
                Field("Локальный сервер (Base URL)", s.localBaseUrl) { v ->
                    viewModel.update { it.copy(localBaseUrl = v) }
                }
                Field("Model Name", s.localModel) { v -> viewModel.update { it.copy(localModel = v) } }
                Text(
                    if (s.ggufPath.isBlank()) "GGUF-файл не выбран"
                    else "GGUF: ${s.ggufPath.substringAfterLast('/')}" +
                        if (GgufEngine.isNativeAvailable) "" else " (нативная библиотека не собрана — будет использован сервер)",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { ggufPicker.launch(arrayOf("*/*")) }) {
                        Text("Выбрать GGUF")
                    }
                    if (s.ggufPath.isNotBlank()) {
                        Spacer(Modifier.height(0.dp))
                        Button(onClick = { viewModel.update { it.copy(ggufPath = "") } }) {
                            Text("Сбросить")
                        }
                    }
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            SectionTitle("Генерация")
            Text("Temperature: ${"%.2f".format(s.temperature)}")
            Slider(
                value = s.temperature,
                onValueChange = { v -> viewModel.update { it.copy(temperature = v) } },
                valueRange = 0f..2f
            )
            Field("Max Tokens", s.maxTokens.toString()) { v ->
                v.toIntOrNull()?.let { n -> viewModel.update { it.copy(maxTokens = n.coerceIn(1, 8192)) } }
            }
            Text("Контекст: последних сообщений ${s.contextMessages}")
            Slider(
                value = s.contextMessages.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(contextMessages = v.toInt()) } },
                valueRange = 2f..100f
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    secret: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
}
