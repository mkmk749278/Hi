package com.launcher360v2.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.launcher360v2.domain.FeedBridge
import com.launcher360v2.domain.GestureController
import com.launcher360v2.domain.SwipeAction
import com.launcher360v2.ui.drawer.AppDrawer
import com.launcher360v2.ui.home.HomeScreen
import kotlinx.coroutines.launch

/**
 * Root composable. Owns the gesture layer — dispatches to feed, drawer, or pager.
 */
@Composable
fun LauncherRoot(feedBridge: FeedBridge) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()

    val scope = rememberCoroutineScope()

    // ── State ────────────────────────────────────────────────────────────────
    var feedProgress by remember { mutableFloatStateOf(0f) }
    var drawerVisible by remember { mutableStateOf(false) }
    var feedSwiping by remember { mutableStateOf(false) }

    val feedAnim by animateFloatAsState(
        targetValue = feedProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "feed_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Only start feed scroll if the drag begins at the left edge
                        if (offset.x < screenWidthPx * 0.12f) {
                            feedBridge.onScrollStart()
                            feedSwiping = true
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x
                        val dy = dragAmount.y

                        if (feedSwiping) {
                            feedProgress = (feedProgress + dx / screenWidthPx)
                                .coerceIn(0f, 1f)
                            feedBridge.onScrollProgress(feedProgress)
                        } else {
                            val action = GestureController.classify(
                                dx = dx, dy = dy,
                                startX = change.position.x, startY = change.position.y,
                                screenWidth = screenWidthPx, screenHeight = screenHeightPx
                            )
                            if (action == SwipeAction.OPEN_DRAWER) drawerVisible = true
                        }
                    },
                    onDragEnd = {
                        if (feedSwiping) {
                            feedBridge.onScrollEnd()
                            // Snap: if > 50% → go to feed, else snap back
                            scope.launch {
                                feedProgress = if (feedProgress > 0.5f) 1f else 0f
                            }
                            feedSwiping = false
                        }
                    },
                    onDragCancel = {
                        if (feedSwiping) {
                            feedBridge.onScrollEnd()
                            feedProgress = 0f
                            feedSwiping = false
                        }
                    }
                )
            }
    ) {
        // Home screen (pages)
        HomeScreen(
            feedProgress = feedAnim,
            onSwipeUp = { drawerVisible = true },
            modifier = Modifier.fillMaxSize()
        )

        // App Drawer (bottom sheet, overlays home screen)
        if (drawerVisible) {
            AppDrawer(
                onDismiss = { drawerVisible = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
