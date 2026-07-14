package com.launcher360v2.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens for posted/removed notifications and publishes a per-package badge
 * count as a shared [StateFlow]. UI observes [badgeCounts] and maps counts onto
 * [com.launcher360v2.data.model.AppItem.badgeCount].
 *
 * Requires the user to grant Notification Access
 * (Settings > Apps > Special app access > Notification access).
 */
class NotificationBadgeService : NotificationListenerService() {

    companion object {
        private val _badgeCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

        /** package name → number of active notifications. */
        val badgeCounts: StateFlow<Map<String, Int>> = _badgeCounts.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        recompute()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recompute()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        recompute()
    }

    private fun recompute() {
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            // Access can be revoked at runtime — fail closed.
            emptyArray()
        } ?: emptyArray()

        _badgeCounts.value = active
            .filter { !it.isOngoing }
            .groupingBy { it.packageName }
            .eachCount()
    }
}
