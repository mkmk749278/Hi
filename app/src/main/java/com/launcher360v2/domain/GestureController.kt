package com.launcher360v2.domain

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Classifies raw drag gestures into launcher actions.
 * Stateless — call classify() with each gesture delta.
 *
 * Zones:
 *   Left edge (0 to EDGE_ZONE)  + rightward drag → OPEN_FEED
 *   Any position + strong upward drag             → OPEN_DRAWER
 *   Double tap                                     → CUSTOM (user-configurable)
 *   Pinch                                          → OVERVIEW
 */
object GestureController {

    // Thresholds in pixels — caller should convert from dp
    private const val FEED_DRAG_PX = 80f
    private const val DRAWER_DRAG_PX = 100f
    private const val EDGE_ZONE_RATIO = 0.12f   // left 12% of screen width

    fun classify(
        dx: Float,
        dy: Float,
        startX: Float,
        startY: Float,
        screenWidth: Int,
        screenHeight: Int
    ): SwipeAction {
        val edgeZone = screenWidth * EDGE_ZONE_RATIO
        val angleRad = atan2(abs(dy).toDouble(), abs(dx).toDouble())
        val angleDeg = Math.toDegrees(angleRad)

        return when {
            // Strong upward swipe from anywhere
            dy < -DRAWER_DRAG_PX && angleDeg > 55.0 -> SwipeAction.OPEN_DRAWER

            // Rightward drag from left edge only → feed
            dx > FEED_DRAG_PX && startX < edgeZone -> SwipeAction.OPEN_FEED

            // Leftward drag from right edge → next page (placeholder)
            dx < -FEED_DRAG_PX && startX > screenWidth - edgeZone -> SwipeAction.NEXT_PAGE

            // Downward drag from top of screen → notification shade
            dy > DRAWER_DRAG_PX && startY < screenHeight * 0.1f -> SwipeAction.NOTIFICATIONS

            else -> SwipeAction.NONE
        }
    }

    /**
     * Normalize drag progress for the feed panel (0.0 = home, 1.0 = feed full).
     * startX is where the touch began; currentX is current touch X.
     */
    fun feedProgress(startX: Float, currentX: Float, screenWidth: Int): Float {
        val delta = currentX - startX
        return (delta / (screenWidth * 0.6f)).coerceIn(0f, 1f)
    }
}

enum class SwipeAction {
    OPEN_DRAWER,
    OPEN_FEED,
    NEXT_PAGE,
    NOTIFICATIONS,
    NONE
}
