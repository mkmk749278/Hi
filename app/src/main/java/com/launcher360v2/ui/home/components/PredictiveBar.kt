package com.launcher360v2.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.ui.common.AppIcon

/**
 * A translucent row of 4 predicted apps shown above the dock.
 * Refreshes on launcher resume via HomeViewModel.
 */
@Composable
fun PredictiveBar(
    apps: List<AppItem>,
    onAppClick: (AppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.take(4).forEach { app ->
            AppIcon(
                app = app,
                showLabel = false,
                onClick = { onAppClick(app) },
                modifier = Modifier.size(44.dp)
            )
        }
    }
}
