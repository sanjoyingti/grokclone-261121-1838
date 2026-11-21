package com.arin.grokclone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arin.grokclone.GrokApp
import com.arin.grokclone.data.ApiException
import com.arin.grokclone.data.AppSettings
import com.arin.grokclone.data.ChatApi
import com.arin.grokclone.data.SettingsRepository
import com.arin.grokclone.model.OutgoingMessage
import com.arin.grokclone.model.buildChatRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TestResult(
    val running: Boolean = false,
    val success: Boolean = false,
    val message: String = ""
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as GrokApp).container
    private val repository: SettingsRepository = container.settings
    private val api: ChatApi = container.api

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    private val _testResult = MutableStateFlow(TestResult())
    val testResult: StateFlow<TestResult> = _testResult.asStateFlow()

    fun saveAll(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        stream: Boolean
    ) {
        viewModelScope.launch {
            repository.setBaseUrl(baseUrl)
            repository.setApiKey(apiKey)
            repository.setModel(model)
            repository.setSystemPrompt(systemPrompt)
            repository.setTemperature(temperature)
            repository.setMaxTokens(maxTokens)
            repository.setStream(stream)
        }
    }

    fun setStream(value: Boolean) {
        viewModelScope.launch { repository.setStream(value) }
    }

    fun setDarkTheme(value: Boolean) {
        viewModelScope.launch { repository.setDarkTheme(value) }
    }

    fun setTemperature(value: Float) {
        viewModelScope.launch { repository.setTemperature(value) }
    }

    fun setModel(value: String) {
        viewModelScope.launch { repository.setModel(value) }
    }

    fun clearTestResult() {
        _testResult.value = TestResult()
    }

    /**
     * Sends a tiny "ping" request using exactly the values currently on screen,
     * so the answer reflects what the user just typed rather than what was saved before.
     */
    fun testConnection(baseUrl: String, apiKey: String, model: String) {
        if (_testResult.value.running) return
        viewModelScope.launch {
            _testResult.value = TestResult(running = true, message = "Contacting the server…")
            val trimmedModel = model.trim().ifBlank { AppSettings.DEFAULT_MODEL }
            val body = buildChatRequest(
                model = trimmedModel,
                messages = listOf(OutgoingMessage("user", "ping")),
                temperature = 0f,
                maxTokens = 8,
                stream = false
            )
            try {
                val reply = api.complete(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    requestJson = body,
                    stream = false
                ) { }
                val preview = reply.trim().take(120).ifBlank { "(empty reply)" }
                _testResult.value = TestResult(
                    running = false,
                    success = true,
                    message = "Success — the server answered: \"$preview\""
                )
            } catch (e: CancellationException) {
                _testResult.value = TestResult(false, false, "Test cancelled.")
            } catch (e: ApiException) {
                _testResult.value = TestResult(
                    running = false,
                    success = false,
                    message = e.message ?: "The request failed."
                )
            } catch (e: Exception) {
                _testResult.value = TestResult(
                    running = false,
                    success = false,
                    message = "Unexpected error: ${e.message ?: e::class.java.simpleName}"
                )
            }
        }
    }
}
