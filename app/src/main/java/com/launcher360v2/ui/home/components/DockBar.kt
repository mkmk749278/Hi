package com.launcher360v2.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.data.model.HomeCell
import com.launcher360v2.ui.common.AppIcon

/**
 * The persistent bottom dock — a translucent row of the apps pinned to the
 * dock "page" (page == -1). Styled like [PredictiveBar] but taller. An upward
 * drag on the dock also opens the app drawer.
 *
 * Renders nothing when no dock apps are configured, keeping the wallpaper clean.
 */
@Composable
fun DockBar(
    cells: List<HomeCell>,
    resolveApp: (String) -> AppItem?,
    onAppClick: (AppItem) -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val apps = cells.mapNotNull { it.componentFlat?.let(resolveApp) }
    if (apps.isEmpty()) return

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -40f) onSwipeUp()
                }
            },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEach { app ->
            AppIcon(
                app = app,
                showLabel = false,
                onClick = { onAppClick(app) },
                modifier = Modifier.size(52.dp)
            )
        }
    }
}
