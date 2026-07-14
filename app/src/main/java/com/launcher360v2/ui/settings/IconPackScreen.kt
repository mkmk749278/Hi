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
 * Icon Pack selection screen.
 * Shows all installed icon packs on the device.
 * Tapping one loads it via IconPackManager.
 */
@Composable
fun IconPackScreen(
    viewModel: IconPackViewModel = hiltViewModel()
) {
    val packs by viewModel.installedPacks.collectAsState()
    val activePack by viewModel.activePack.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Icon Packs", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            "Supports any icon pack using the Nova/ADW/Lawnicons standard.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // "System" option (no icon pack)
                item {
                    IconPackRow(
                        label = "System Icons (Default)",
                        packageName = "",
                        isActive = activePack.isEmpty(),
                        onClick = { viewModel.selectPack("") }
                    )
                }
                items(packs, key = { it.packageName }) { pack ->
                    IconPackRow(
                        label = pack.label,
                        packageName = pack.packageName,
                        isActive = activePack == pack.packageName,
                        onClick = { viewModel.selectPack(pack.packageName) }
                    )
                }
                if (packs.isEmpty()) {
                    item {
                        Text(
                            "No icon packs installed. Download Arcticons, Lawnicons, or Delta from Play Store.",
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPackRow(
    label: String,
    packageName: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                Color(0xFF2ECC71).copy(alpha = 0.15f)
            else
                Color.White.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, color = Color.White)
                if (packageName.isNotEmpty()) {
                    Text(packageName, color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (isActive) {
                Text("Active", color = Color(0xFF2ECC71), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
