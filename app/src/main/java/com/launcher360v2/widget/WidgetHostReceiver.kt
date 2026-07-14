package com.launcher360v2.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Stub receiver for APPWIDGET_UPDATE broadcasts. Widget rendering is driven by
 * [WidgetHostManager]/[AppWidgetComposable]; this receiver simply satisfies the
 * manifest declaration and can be extended to trigger host refreshes if needed.
 */
class WidgetHostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("WidgetHostReceiver", "Widget update: ${intent?.action}")
        // No-op stub — AppWidgetHost handles view updates while listening.
    }
}
