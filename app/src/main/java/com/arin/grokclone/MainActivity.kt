package com.arin.grokclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.arin.grokclone.ui.ChatScreen
import com.arin.grokclone.ui.SettingsScreen
import com.arin.grokclone.ui.theme.GrokCloneTheme
import com.arin.grokclone.viewmodel.ChatViewModel

private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrokCloneRoot()
        }
    }
}

@Composable
private fun GrokCloneRoot() {
    val chatViewModel: ChatViewModel = viewModel()
    val state by chatViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var autoNavigated by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.settingsLoaded) {
        if (state.settingsLoaded && !autoNavigated && state.settings.apiKey.isBlank()) {
            autoNavigated = true
            navController.navigate(ROUTE_SETTINGS)
        }
    }

    GrokCloneTheme(darkTheme = state.settings.darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(navController = navController, startDestination = ROUTE_CHAT) {
                composable(ROUTE_CHAT) {
                    ChatScreen(
                        viewModel = chatViewModel,
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
