package com.launcher360v2.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.ui.common.AppIcon

/**
 * Screen to manage hidden apps.
 * Shows all installed apps with a toggle — hidden apps show a "hidden" indicator.
 */
@Composable
fun HiddenAppsScreen(
    viewModel: HiddenAppsViewModel = hiltViewModel()
) {
    val allApps by viewModel.allApps.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Hidden Apps",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            "Hidden apps won't appear in the drawer but stay on your home screen and folders.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(allApps, key = { it.packageName }) { app ->
                val isHidden = app.packageName in hiddenApps
                HiddenAppRow(
                    app = app,
                    isHidden = isHidden,
                    onToggle = {
                        if (isHidden) viewModel.showApp(app.packageName)
                        else viewModel.hideApp(app.packageName)
                    }
                )
            }
        }
    }
}

@Composable
private fun HiddenAppRow(
    app: AppItem,
    isHidden: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(app = app, showLabel = false, onClick = {}, modifier = Modifier.size(48.dp))
            Column {
                Text(app.label, color = Color.White)
                if (isHidden) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                        Text("Hidden from drawer", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Switch(checked = !isHidden, onCheckedChange = { onToggle() })
    }
}
