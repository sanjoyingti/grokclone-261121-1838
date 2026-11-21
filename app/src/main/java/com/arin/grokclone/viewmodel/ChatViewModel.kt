package com.arin.grokclone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arin.grokclone.GrokApp
import com.arin.grokclone.data.ApiException
import com.arin.grokclone.data.AppSettings
import com.arin.grokclone.data.ChatApi
import com.arin.grokclone.data.ChatDao
import com.arin.grokclone.data.Conversation
import com.arin.grokclone.data.MessageEntity
import com.arin.grokclone.data.SettingsRepository
import com.arin.grokclone.model.OutgoingMessage
import com.arin.grokclone.model.buildChatRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SnackbarEvent(
    val id: Long,
    val text: String,
    val canRetry: Boolean
)

data class ChatUiState(
    val settings: AppSettings = AppSettings(),
    val settingsLoaded: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: Long? = null,
    val messages: List<MessageEntity> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val pendingImageBase64: String? = null,
    val streamingMessageId: Long? = null,
    val streamingText: String = "",
    val snackbar: SnackbarEvent? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as GrokApp).container
    private val dao: ChatDao = container.chatDao
    private val api: ChatApi = container.api
    private val settingsRepository: SettingsRepository = container.settings

    private val _state = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _state.asStateFlow()

    private var generationJob: Job? = null
    private var snackCounter = 0L

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings, settingsLoaded = true) }
            }
        }
        viewModelScope.launch {
            dao.observeConversations().collect { list ->
                _state.update { it.copy(conversations = list) }
            }
        }
        viewModelScope.launch {
            _state.map { it.currentConversationId }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else dao.observeMessages(id)
                }
                .collect { messages ->
                    _state.update { it.copy(messages = messages) }
                }
        }
    }

    // ---------- input ----------

    fun updateInput(text: String) {
        val trimmed = if (text.length > AppSettings.MAX_INPUT_CHARS) {
            text.substring(0, AppSettings.MAX_INPUT_CHARS)
        } else {
            text
        }
        _state.update { it.copy(input = trimmed) }
    }

    fun setPendingImage(base64: String?) {
        _state.update { it.copy(pendingImageBase64 = base64) }
    }

    fun showMessage(text: String) {
        _state.update { it.copy(snackbar = SnackbarEvent(++snackCounter, text, false)) }
    }

    fun dismissSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }

    // ---------- conversations ----------

    fun newChat() {
        if (_state.value.isGenerating) return
        _state.update { it.copy(currentConversationId = null, messages = emptyList(), input = "") }
    }

    fun selectConversation(id: Long) {
        if (_state.value.isGenerating) return
        _state.update { it.copy(currentConversationId = id) }
    }

    fun renameConversation(id: Long, title: String) {
        val clean = title.trim().ifBlank { "Untitled chat" }
        viewModelScope.launch {
            dao.renameConversation(id, clean.take(80), System.currentTimeMillis())
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            dao.deleteConversation(id)
            if (_state.value.currentConversationId == id) {
                _state.update { it.copy(currentConversationId = null, messages = emptyList()) }
            }
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            dao.deleteAllConversations()
            _state.update { it.copy(currentConversationId = null, messages = emptyList()) }
        }
    }

    fun deleteMessage(id: Long) {
        val streamingId = _state.value.streamingMessageId
        if (id == streamingId) {
            stop()
        }
        viewModelScope.launch { dao.deleteMessage(id) }
    }

    // ---------- sending ----------

    fun send() {
        val current = _state.value
        if (current.isGenerating) return
        val text = current.input.trim()
        val image = current.pendingImageBase64
        if (text.isEmpty() && image == null) return
        _state.update { it.copy(input = "", pendingImageBase64 = null) }
        generationJob = viewModelScope.launch {
            runSend(text, image)
        }
    }

    fun stop() {
        generationJob?.cancel()
    }

    fun regenerateLast() {
        val current = _state.value
        if (current.isGenerating) return
        val convId = current.currentConversationId ?: return
        generationJob = viewModelScope.launch {
            val rows = dao.getMessages(convId)
            val lastAssistant = rows.lastOrNull { it.role == "assistant" }
            if (lastAssistant != null) {
                dao.deleteMessage(lastAssistant.id)
            }
            generate(convId, _state.value.settings)
        }
    }

    fun retryMessage(messageId: Long) {
        val current = _state.value
        if (current.isGenerating) return
        val convId = current.currentConversationId ?: return
        generationJob = viewModelScope.launch {
            dao.deleteMessage(messageId)
            generate(convId, _state.value.settings)
        }
    }

    private suspend fun runSend(text: String, imageBase64: String?) {
        val conversationId = ensureConversation(text)
        val now = System.currentTimeMillis()
        dao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = "user",
                content = text,
                timestamp = now,
                imageBase64 = imageBase64
            )
        )
        dao.touchConversation(conversationId, now)
        generate(conversationId, _state.value.settings)
    }

    private suspend fun ensureConversation(firstText: String): Long {
        val existing = _state.value.currentConversationId
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val title = firstText.trim().take(40).ifBlank { "Image chat" }
        val id = dao.insertConversation(
            Conversation(title = title, createdAt = now, updatedAt = now)
        )
        _state.update { it.copy(currentConversationId = id) }
        return id
    }

    private suspend fun generate(conversationId: Long, settings: AppSettings) {
        val rows = dao.getMessages(conversationId)
        val history = rows
            .filter { it.role == "user" || it.role == "assistant" }
            .filter { !it.isError }
            .filter { it.content.isNotBlank() || !it.imageBase64.isNullOrBlank() }
            .map { OutgoingMessage(it.role, it.content, it.imageBase64) }

        if (history.isEmpty()) return

        val outgoing = if (settings.systemPrompt.isBlank()) {
            history
        } else {
            listOf(OutgoingMessage("system", settings.systemPrompt)) + history
        }

        val assistantId = dao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = "assistant",
                content = "",
                timestamp = System.currentTimeMillis()
            )
        )

        val builder = StringBuilder()
        var lastSaved = 0
        _state.update {
            it.copy(isGenerating = true, streamingMessageId = assistantId, streamingText = "")
        }

        val requestJson = buildChatRequest(
            model = settings.model,
            messages = outgoing,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            stream = settings.stream
        )

        var ticker: Job? = null
        try {
            if (settings.stream) {
                ticker = viewModelScope.launch {
                    while (true) {
                        delay(700)
                        if (builder.length > lastSaved) {
                            lastSaved = builder.length
                            dao.updateMessage(assistantId, builder.toString(), false)
                        }
                    }
                }
            }

            val full = api.complete(
                baseUrl = settings.baseUrl,
                apiKey = settings.apiKey,
                requestJson = requestJson,
                stream = settings.stream
            ) { delta ->
                builder.append(delta)
                _state.update { it.copy(streamingText = builder.toString()) }
            }

            val finalText = if (settings.stream) builder.toString() else full
            withContext(NonCancellable) {
                if (finalText.isBlank()) {
                    dao.updateMessage(assistantId, "The model returned an empty reply.", true)
                } else {
                    dao.updateMessage(assistantId, finalText, false)
                    dao.touchConversation(conversationId, System.currentTimeMillis())
                }
            }
        } catch (e: CancellationException) {
            ticker?.cancel()
            val partial = builder.toString()
            withContext(NonCancellable) {
                if (partial.isBlank()) {
                    dao.deleteMessage(assistantId)
                } else {
                    dao.updateMessage(assistantId, "$partial\n\n(stopped)", false)
                    dao.touchConversation(conversationId, System.currentTimeMillis())
                }
            }
            throw e
        } catch (e: Exception) {
            ticker?.cancel()
            val message = friendlyMessage(e)
            withContext(NonCancellable) {
                dao.updateMessage(assistantId, message, true)
            }
            _state.update {
                it.copy(snackbar = SnackbarEvent(++snackCounter, message, canRetry = true))
            }
        } finally {
            ticker?.cancel()
            _state.update {
                it.copy(isGenerating = false, streamingMessageId = null, streamingText = "")
            }
        }
    }

    private fun friendlyMessage(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "The request failed."
        is java.net.UnknownHostException -> "Network error — the server name could not be found."
        is java.net.SocketTimeoutException -> "Timeout — the server took too long to answer."
        is java.io.IOException -> "Network error — ${e.message ?: "the connection failed"}"
        else -> "Something went wrong: ${e.message ?: e::class.java.simpleName}"
    }
}
