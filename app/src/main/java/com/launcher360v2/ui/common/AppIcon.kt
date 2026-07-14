package com.launcher360v2.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.domain.IconCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entry point used to pull the singleton [IconCache] into composables, which cannot
 * receive constructor injection. Icon decoding therefore stays out of composition.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface IconCacheEntryPoint {
    fun iconCache(): IconCache
}

/**
 * Single app icon with optional label and badge count.
 * Loads the icon asynchronously via IconCache (never on the main thread).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppItem,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconCache = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, IconCacheEntryPoint::class.java)
            .iconCache()
    }

    var iconBitmap by remember(app.key) { mutableStateOf<ImageBitmap?>(null) }

    // Load the icon off the main thread. Uses a pre-set app.icon if present,
    // otherwise resolves + decodes through IconCache.
    LaunchedEffect(app.key) {
        val drawable = app.icon ?: iconCache.getIcon(app.key, app.user)
        if (drawable != null) {
            iconBitmap = withContext(Dispatchers.Default) {
                drawable.toBitmap(width = 108, height = 108).asImageBitmap()
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp)
    ) {
        // ── Icon ───────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = iconBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder while loading
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.12f),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )
                }
            }

            // Badge count
            if (app.badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
                        .background(color = Color(0xFFE74C3C), shape = RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (app.badgeCount > 99) "99+" else app.badgeCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // ── Label ─────────────────────────────────────────────────────────────
        if (showLabel) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 64.dp),
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        blurRadius = 4f
                    )
                )
            )
        }
    }
}
