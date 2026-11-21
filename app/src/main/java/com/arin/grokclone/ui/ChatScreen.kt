package com.arin.grokclone.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.grokclone.data.AppSettings
import com.arin.grokclone.data.Conversation
import com.arin.grokclone.data.ImageUtils
import com.arin.grokclone.viewmodel.ChatViewModel
import com.arin.grokclone.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showModelPicker by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.Default) {
                    ImageUtils.uriToBase64Jpeg(context, uri)
                }
                if (encoded == null) {
                    viewModel.showMessage("That image could not be read. Please pick another one.")
                } else {
                    viewModel.setPendingImage(encoded)
                }
            }
        }
    }

    LaunchedEffect(state.snackbar?.id) {
        val event = state.snackbar ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = event.text,
            actionLabel = if (event.canRetry) "Retry" else null,
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed && event.canRetry) {
            viewModel.regenerateLast()
        }
        viewModel.dismissSnackbar()
    }

    // The list is laid out bottom-anchored, so item 0 is always the newest bubble.
    LaunchedEffect(state.currentConversationId, state.messages.size, state.isGenerating) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val currentTitle = state.conversations
        .firstOrNull { it.id == state.currentConversationId }?.title

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                ConversationDrawer(
                    conversations = state.conversations,
                    currentId = state.currentConversationId,
                    onNewChat = {
                        viewModel.newChat()
                        scope.launch { drawerState.close() }
                    },
                    onSelect = { id ->
                        viewModel.selectConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onRename = { conversation -> renameTarget = conversation },
                    onDelete = { conversation -> viewModel.deleteConversation(conversation.id) },
                    onDeleteAll = { showDeleteAll = true }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentTitle ?: "Grok Clone",
                            maxLines = 1,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open chat list")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.newChat() }) {
                            Icon(Icons.Filled.Add, contentDescription = "New chat")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (state.messages.isEmpty() && !state.isGenerating) {
                        EmptyChatState()
                    } else {
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = state.messages.asReversed(),
                                key = { it.id }
                            ) { message ->
                                val isStreaming = message.id == state.streamingMessageId
                                val text = if (isStreaming) state.streamingText else message.content
                                MessageBubble(
                                    message = message,
                                    text = text,
                                    isStreaming = isStreaming,
                                    onCopy = {
                                        copyToClipboard(context, "message", message.content)
                                        viewModel.showMessage("Message copied")
                                    },
                                    onShare = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, message.content)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(intent, "Share message")
                                        )
                                    },
                                    onDelete = { viewModel.deleteMessage(message.id) },
                                    onRetry = { viewModel.retryMessage(message.id) }
                                )
                            }
                        }
                    }
                }

                ChatInputBar(
                    input = state.input,
                    model = state.settings.model,
                    pendingImageBase64 = state.pendingImageBase64,
                    isGenerating = state.isGenerating,
                    canRegenerate = state.messages.any { it.role == "assistant" },
                    charLimit = AppSettings.MAX_INPUT_CHARS,
                    onInputChange = { viewModel.updateInput(it) },
                    onSend = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.send()
                    },
                    onStop = { viewModel.stop() },
                    onRegenerate = { viewModel.regenerateLast() },
                    onPickImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onClearImage = { viewModel.setPendingImage(null) },
                    onModelClick = { showModelPicker = true }
                )
            }
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            currentModel = state.settings.model,
            onDismiss = { showModelPicker = false },
            onConfirm = { chosen ->
                showModelPicker = false
                if (chosen.isNotBlank()) {
                    settingsViewModel.setModel(chosen)
                    viewModel.showMessage("Model set to $chosen")
                }
            }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            initialTitle = target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                viewModel.renameConversation(target.id, newTitle)
                renameTarget = null
            }
        )
    }

    if (showDeleteAll) {
        DeleteAllDialog(
            onDismiss = { showDeleteAll = false },
            onConfirm = {
                viewModel.deleteAllConversations()
                showDeleteAll = false
                scope.launch { drawerState.close() }
            }
        )
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Ask anything",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Replies come from the API endpoint you configure in Settings. " +
                "Your chats stay on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
