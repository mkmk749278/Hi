package com.launcher360v2.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Composable wrapper for an AppWidgetHostView.
 * Handles the View system ↔ Compose boundary.
 */
@Composable
fun AppWidgetComposable(
    widgetId: Int,
    widgetHostManager: WidgetHostManager,
    modifier: Modifier = Modifier
) {
    val widgetView = remember(widgetId) {
        widgetHostManager.createWidgetView(widgetId)
    }

    if (widgetView != null) {
        AndroidView(
            factory = { widgetView },
            modifier = modifier
        )
    }
}
