package com.launcher360v2.domain

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.android.launcher3.ILauncherOverlay
import com.android.launcher3.ILauncherOverlayCallback

/**
 * Bridges the launcher to Google's Discover Feed via ILauncherOverlay AIDL.
 * Requires Google Search app (com.google.android.googlequicksearchbox) installed.
 *
 * Usage:
 *   1. Call connect() in Activity.onStart()
 *   2. Call onScrollStart() / onScrollProgress() / onScrollEnd() as user swipes
 *   3. Call onPause() / onResume() with Activity lifecycle
 *   4. Call disconnect() in Activity.onDestroy()
 */
class FeedBridge(private val activity: Activity) : ServiceConnection {

    companion object {
        private const val TAG = "FeedBridge"
        private const val OVERLAY_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"
        private const val OVERLAY_FLAGS = Context.BIND_AUTO_CREATE or
                Context.BIND_NOT_FOREGROUND or
                Context.BIND_ABOVE_CLIENT
    }

    private var overlay: ILauncherOverlay? = null
    var isConnected = false
        private set

    var onFeedAvailableChanged: ((Boolean) -> Unit)? = null

    fun connect() {
        if (!isGoogleAppInstalled()) {
            Log.w(TAG, "Google Search not installed — feed unavailable")
            return
        }
        val intent = Intent(OVERLAY_ACTION).apply { `package` = OVERLAY_PACKAGE }
        try {
            activity.bindService(intent, this, OVERLAY_FLAGS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind feed service: ${e.message}")
        }
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        Log.d(TAG, "Feed service connected")
        overlay = ILauncherOverlay.Stub.asInterface(binder)
        isConnected = true

        // Attach the launcher's window to the overlay
        try {
            val bundle = Bundle()
            bundle.putParcelable("layout_params", activity.window.attributes)
            bundle.putInt("client_options", 3)   // flags: 1=has_overlay, 2=can_slide
            overlay?.windowAttached2(bundle, overlayCallback)
        } catch (e: Exception) {
            Log.e(TAG, "windowAttached2 failed: ${e.message}")
        }
        onFeedAvailableChanged?.invoke(hasFeed)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        Log.w(TAG, "Feed service disconnected")
        overlay = null
        isConnected = false
        onFeedAvailableChanged?.invoke(false)
    }

    // ── Scroll Coordination ──────────────────────────────────────────────────

    /** Call when user begins swipe toward the feed (left-most page). */
    fun onScrollStart() {
        safeCall { overlay?.startScroll() }
    }

    /**
     * Call continuously as user drags. progress: 0.0 = home, 1.0 = feed fully visible.
     * Must be called between onScrollStart() and onScrollEnd().
     */
    fun onScrollProgress(progress: Float) {
        safeCall { overlay?.onScroll(progress.coerceIn(0f, 1f)) }
    }

    /** Call when user releases the swipe gesture. */
    fun onScrollEnd() {
        safeCall { overlay?.endScroll() }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    fun onPause() = safeCall { overlay?.onPause() }
    fun onResume() = safeCall { overlay?.onResume() }

    fun disconnect() {
        if (isConnected) {
            safeCall { overlay?.windowDetached(false) }
            try { activity.unbindService(this) } catch (_: Exception) {}
            isConnected = false
            overlay = null
        }
    }

    // ── State ────────────────────────────────────────────────────────────────

    /** True only if feed is connected AND has displayable content. */
    val hasFeed: Boolean
        get() = isConnected && safeCallReturn(false) { overlay?.hasOverlayContent() == true }

    // ── Callback from Google Overlay ─────────────────────────────────────────

    private val overlayCallback = object : ILauncherOverlayCallback.Stub() {
        override fun overlayScrollChanged(progress: Float) {
            // Google feed is scrolling — update launcher pager to match
            // Post to main thread for UI updates
            activity.runOnUiThread {
                // Callback to HomeScreen — connect via shared ViewModel or callback
            }
        }

        override fun overlayStatusChanged(status: Int) {
            Log.d(TAG, "Feed status changed: $status")
            // status: 0 = no content, 1 = has content, 2 = loading
            activity.runOnUiThread {
                onFeedAvailableChanged?.invoke(status > 0 && isConnected)
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun isGoogleAppInstalled(): Boolean {
        return try {
            activity.packageManager.getApplicationInfo(OVERLAY_PACKAGE, 0)
            true
        } catch (_: Exception) { false }
    }

    private fun safeCall(block: () -> Unit) {
        try { block() } catch (e: Exception) {
            Log.e(TAG, "Feed AIDL call failed: ${e.message}")
        }
    }

    private fun <T> safeCallReturn(default: T, block: () -> T): T {
        return try { block() } catch (_: Exception) { default }
    }
}
