package com.launcher360v2.ui.folder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.ui.common.AppIcon

/**
 * Bottom sheet that shows a folder's contents as a small grid.
 * Room-backed folders are resolved to [AppItem]s by the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSheet(
    name: String,
    apps: List<AppItem>,
    onAppClick: (AppItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = name,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (apps.isEmpty()) {
                Text(
                    "This folder is empty.",
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                ) {
                    items(apps, key = { it.key }) { app ->
                        AppIcon(
                            app = app,
                            showLabel = true,
                            onClick = { onAppClick(app) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
