package com.launcher360v2.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Focus Mode settings screen.
 * - Toggle focus mode on/off
 * - Configure distraction apps list
 * - Set schedule (optional timed mode)
 */
@Composable
fun FocusModeScreen(
    viewModel: FocusModeViewModel = hiltViewModel()
) {
    val schedule by viewModel.schedule.collectAsState()
    val isActive by viewModel.isActive.collectAsState()
    val allApps by viewModel.allApps.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Focus Mode", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                "Hides selected distraction apps from the drawer on demand or on a schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            // Manual toggle
            Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Focus Mode", color = Color.White)
                        Text(
                            if (isActive) "Active — distracting apps hidden" else "Off",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { viewModel.setFocusActive(it) }
                    )
                }
            }
        }

        item {
            Text(
                "Distraction Apps",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Show all apps with checkbox to mark as "distraction"
        items(allApps, key = { it.packageName }) { app ->
            val isDistraction = app.packageName in (schedule.distractingPackages)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(app.label, color = Color.White)
                Checkbox(
                    checked = isDistraction,
                    onCheckedChange = { checked ->
                        val newSet = if (checked)
                            schedule.distractingPackages + app.packageName
                        else
                            schedule.distractingPackages - app.packageName
                        viewModel.saveSchedule(schedule.copy(distractingPackages = newSet))
                    }
                )
            }
        }
    }
}
