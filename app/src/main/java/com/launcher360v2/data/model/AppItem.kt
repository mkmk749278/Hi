package com.launcher360v2.data.model

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.UserHandle

data class AppItem(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val user: UserHandle,
    var icon: Drawable? = null,      // loaded asynchronously by IconCache
    var badgeCount: Int = 0          // updated by NotificationBadgeService
) {
    val key: String get() = componentName.flattenToString()
}
