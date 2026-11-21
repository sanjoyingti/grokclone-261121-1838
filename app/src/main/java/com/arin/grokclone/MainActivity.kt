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
import androidx.compose.runtime.remember
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
import com.arin.grokclone.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setTheme(R.style.Theme_GrokClone)
        setContent {
            GrokCloneRoot()
        }
    }
}

@Composable
private fun GrokCloneRoot() {
    val chatViewModel: ChatViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val state by chatViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var autoNavigated by rememberSaveable { mutableStateOf(false) }

    GrokCloneTheme(darkTheme = state.settings.darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(navController = navController, startDestination = "chat") {
                composable("chat") {
                    ChatScreen(
                        viewModel = chatViewModel,
                        onOpenSettings = { navController.navigate("settings") }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    LaunchedEffect(state.settingsLoaded) {
        if (state.settingsLoaded && !autoNavigated && state.settings.apiKey.isBlank()) {
            autoNavigated = true
            navController.navigate("settings")
        }
    }
}
