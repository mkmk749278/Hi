package com.launcher360v2.domain

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 2-tier icon cache: in-memory LRU → decode from LauncherActivityInfo / icon pack.
 * Never decodes on the main thread.
 */
@Singleton
class IconCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val iconPackManager: IconPackManager
) {
    // ~200 icons × ~50KB each ≈ 10MB max — safe for F6's 8GB RAM
    private val memCache = LruCache<String, Drawable>(200)
    private val density = context.resources.displayMetrics.densityDpi

    /**
     * Get icon for an app. Returns icon pack icon if available, else system icon.
     * Suspending — always call from a coroutine.
     */
    suspend fun getIcon(info: LauncherActivityInfo): Drawable = withContext(Dispatchers.Default) {
        val key = info.componentName.flattenToString()

        memCache.get(key)?.let { return@withContext it }

        // Try icon pack first
        val packIcon = iconPackManager.getIcon(info.componentName)
        val icon = packIcon ?: info.getIcon(density)

        memCache.put(key, icon)
        icon
    }

    suspend fun getIcon(componentFlat: String, user: android.os.UserHandle): Drawable? =
        withContext(Dispatchers.Default) {
            memCache.get(componentFlat) ?: run {
                val launcherApps = context.getSystemService(LauncherApps::class.java)
                val cn = android.content.ComponentName.unflattenFromString(componentFlat) ?: return@withContext null
                val info = launcherApps.getActivityList(cn.packageName, user)
                    .firstOrNull { it.componentName == cn } ?: return@withContext null
                getIcon(info)
            }
        }

    /** Icon-pack change or package update — simplest correct behaviour is to evict all. */
    fun invalidate(packageName: String) {
        memCache.evictAll()
    }

    fun clear() = memCache.evictAll()
}
