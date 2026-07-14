package com.launcher360v2.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.launcher360v2.ui.theme.Launcher360Theme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the launcher's settings screens: Hidden Apps, Focus Mode, Icon Packs.
 * Reached from a long-press menu on the home screen (see [com.launcher360v2.ui.LauncherActivity]).
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Launcher360Theme {
                SettingsRoot(onClose = { finish() })
            }
        }
    }
}

private object SettingsRoutes {
    const val HOME = "settings_home"
    const val HIDDEN = "hidden_apps"
    const val FOCUS = "focus_mode"
    const val ICON_PACKS = "icon_packs"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRoot(onClose: () -> Unit) {
    val navController = rememberNavController()

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            TopAppBar(
                title = { Text("360 Launcher Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!navController.popBackStack()) onClose()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = SettingsRoutes.HOME,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF0A0A0A))
        ) {
            composable(SettingsRoutes.HOME) {
                SettingsMenu(
                    onHidden = { navController.navigate(SettingsRoutes.HIDDEN) },
                    onFocus = { navController.navigate(SettingsRoutes.FOCUS) },
                    onIconPacks = { navController.navigate(SettingsRoutes.ICON_PACKS) }
                )
            }
            composable(SettingsRoutes.HIDDEN) { HiddenAppsScreen() }
            composable(SettingsRoutes.FOCUS) { FocusModeScreen() }
            composable(SettingsRoutes.ICON_PACKS) { IconPackScreen() }
        }
    }
}

@Composable
private fun SettingsMenu(
    onHidden: () -> Unit,
    onFocus: () -> Unit,
    onIconPacks: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsEntry("Hidden Apps", "Choose which apps stay out of the drawer", onHidden)
        SettingsEntry("Focus Mode", "Hide distracting apps on demand or a schedule", onFocus)
        SettingsEntry("Icon Packs", "Apply an installed Nova/ADW icon pack", onIconPacks)
    }
}

@Composable
private fun SettingsEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
