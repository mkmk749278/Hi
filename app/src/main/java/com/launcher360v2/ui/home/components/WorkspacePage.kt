package com.launcher360v2.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.launcher360v2.data.model.CellType
import com.launcher360v2.data.model.HomeCell
import com.launcher360v2.ui.common.AppIcon
import com.launcher360v2.ui.home.HomeViewModel

/**
 * Renders one home-screen page as a 4-column grid of cells.
 *
 * Supports APP, WIDGET, and FOLDER cell types. APP cells resolve their
 * [com.launcher360v2.data.model.AppItem] through [HomeViewModel] so the shared
 * async icon pipeline (IconCache) is reused. WIDGET rendering is delegated to the
 * widget host elsewhere — here we show a labelled placeholder so the layout is stable.
 *
 * A strong upward drag anywhere on the (mostly empty) page opens the app drawer.
 */
@Composable
fun WorkspacePage(
    cells: List<HomeCell>,
    onCellClick: (HomeCell) -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectVerticalDragGestures { _, dragAmount ->
                if (dragAmount < -40f) onSwipeUp()
            }
        }
    ) {
        if (cells.isEmpty()) return@Box

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            items(cells, key = { it.id }) { cell ->
                when (cell.type) {
                    CellType.APP -> {
                        val app = cell.componentFlat?.let { viewModel.resolveApp(it) }
                        if (app != null) {
                            AppIcon(
                                app = app,
                                showLabel = true,
                                onClick = { onCellClick(cell) }
                            )
                        }
                    }
                    CellType.FOLDER -> FolderCell(onClick = { onCellClick(cell) })
                    CellType.WIDGET -> WidgetPlaceholder()
                }
            }
        }
    }
}

@Composable
private fun FolderCell(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text("📁", color = Color.White)
    }
}

@Composable
private fun WidgetPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        Text("Widget", color = Color.White.copy(alpha = 0.5f))
    }
}
