package com.launcher360v2.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Android AppWidgets for the home screen.
 *
 * NOTE: AppWidgets use the traditional View system — they CANNOT be
 * rendered in pure Compose. We wrap AppWidgetHostView in AndroidView.
 *
 * Widget lifecycle:
 *   1. User opens widget picker → pickWidget(activity)
 *   2. System returns widgetId in onActivityResult (APPWIDGET_CONFIGURE_RESULT)
 *   3. Call addWidget(widgetId, ...) to persist to Room and show on screen
 *   4. On destroy: call stopListening() to avoid memory leaks
 */
@Singleton
class WidgetHostManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val HOST_ID = 1337
        const val REQUEST_PICK_WIDGET = 9001
        const val REQUEST_CONFIGURE_WIDGET = 9002
    }

    private val widgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, HOST_ID)

    fun startListening() = host.startListening()
    fun stopListening() = host.stopListening()

    /**
     * Opens the system widget picker. Result comes back in Activity.onActivityResult()
     * with requestCode = REQUEST_PICK_WIDGET.
     */
    fun pickWidget(activity: Activity) {
        val widgetId = host.allocateAppWidgetId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        activity.startActivityForResult(intent, REQUEST_PICK_WIDGET)
    }

    /**
     * Creates an AppWidgetHostView for a given widget ID.
     * This View must be wrapped in AndroidView in Compose.
     */
    fun createWidgetView(widgetId: Int): AppWidgetHostView? {
        val info = widgetManager.getAppWidgetInfo(widgetId) ?: return null
        return host.createView(context, widgetId, info)
    }

    /** Get info (dimensions, resize modes) for a widget. */
    fun getWidgetInfo(widgetId: Int): AppWidgetProviderInfo? =
        widgetManager.getAppWidgetInfo(widgetId)

    /** Remove a widget, freeing the ID. */
    fun removeWidget(widgetId: Int) = host.deleteAppWidgetId(widgetId)

    /** Returns all available widget providers on the device. */
    fun getAllProviders(): List<AppWidgetProviderInfo> =
        widgetManager.installedProviders
}
