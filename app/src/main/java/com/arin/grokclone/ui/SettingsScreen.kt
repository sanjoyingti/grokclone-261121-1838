package com.arin.grokclone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arin.grokclone.data.AppSettings
import com.arin.grokclone.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var baseUrlEdit by remember { mutableStateOf<String?>(null) }
    var apiKeyEdit by remember { mutableStateOf<String?>(null) }
    var modelEdit by remember { mutableStateOf<String?>(null) }
    var promptEdit by remember { mutableStateOf<String?>(null) }
    var maxTokensEdit by remember { mutableStateOf<String?>(null) }
    var showKey by remember { mutableStateOf(false) }

    val baseUrl = baseUrlEdit ?: settings.baseUrl
    val apiKey = apiKeyEdit ?: settings.apiKey
    val model = modelEdit ?: settings.model
    val systemPrompt = promptEdit ?: settings.systemPrompt
    val maxTokensText = maxTokensEdit ?: settings.maxTokens.toString()

    val parsedMaxTokens = maxTokensText.trim().toIntOrNull()
    val maxTokensValid = parsedMaxTokens != null && parsedMaxTokens in 1..200_000
    val baseUrlValid = baseUrl.trim().isNotEmpty()

    fun currentMaxTokens(): Int = if (maxTokensValid) parsedMaxTokens!! else settings.maxTokens

    fun saveAll() {
        viewModel.saveAll(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            temperature = settings.temperature,
            maxTokens = currentMaxTokens(),
            stream = settings.stream
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to chat"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Grok Clone talks to your own API. Nothing is sent anywhere except the " +
                        "server address you type below, and your chats never leave this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrlEdit = it
                    viewModel.clearTestResult()
                },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                isError = !baseUrlValid,
                supportingText = {
                    Text(
                        if (baseUrlValid) {
                            "Requests go to {this}/chat/completions"
                        } else {
                            "Enter the address of your OpenAI-compatible server"
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKeyEdit = it
                    viewModel.clearTestResult()
                },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (showKey) "Hide key" else "Show key"
                        )
                    }
                },
                supportingText = {
                    Text("Sent as: Authorization: Bearer <key>. Leave empty if your server needs no key.")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        saveAll()
                        viewModel.testConnection(
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            model = model.trim().ifBlank { AppSettings.DEFAULT_MODEL }
                        )
                    },
                    enabled = baseUrlValid && maxTokensValid && !testResult.running
                ) {
                    if (testResult.running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test connection")
                }
            }

            if (testResult.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = testResult.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (testResult.running) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (testResult.success) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = model,
                onValueChange = {
                    modelEdit = it
                    viewModel.clearTestResult()
                },
                label = { Text("Model") },
                placeholder = { Text("gpt-4o-mini") },
                singleLine = true,
                supportingText = { Text("Any model name your server understands.") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { promptEdit = it },
                label = { Text("System prompt") },
                placeholder = { Text("You are a helpful assistant…") },
                minLines = 3,
                maxLines = 6,
                supportingText = { Text("Sent as the first message of every request.") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Temperature: ${String.format(Locale.US, "%.2f", settings.temperature)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = settings.temperature,
                onValueChange = { viewModel.setTemperature(it) },
                valueRange = 0f..2f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "0.0 is focused and repeatable, 2.0 is wild.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = maxTokensText,
                onValueChange = {
                    maxTokensEdit = it.filter { ch -> ch.isDigit() }.take(6)
                    viewModel.clearTestResult()
                },
                label = { Text("Max tokens") },
                singleLine = true,
                isError = !maxTokensValid,
                supportingText = {
                    Text(
                        if (maxTokensValid) {
                            "Largest reply length, 1 to 200000."
                        } else {
                            "Enter a whole number between 1 and 200000."
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            SwitchRow(
                title = "Stream responses",
                subtitle = "Show the reply word by word as the server sends it.",
                checked = settings.stream,
                onCheckedChange = { viewModel.setStream(it) }
            )

            Spacer(Modifier.height(8.dp))

            SwitchRow(
                title = "Dark theme",
                subtitle = "Black and white Grok-style colours.",
                checked = settings.darkTheme,
                onCheckedChange = { viewModel.setDarkTheme(it) }
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    saveAll()
                    scope.launch { snackbarHostState.showSnackbar("Settings saved") }
                },
                enabled = baseUrlValid && maxTokensValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save settings")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
