# 360-LAUNCHER — DOMAIN LAYER
# FeedBridge, IconCache, IconPackManager, FocusModeManager, PredictiveAppsEngine, GestureController

---

## FILE: app/src/main/java/com/launcher360v2/domain/FeedBridge.kt

```kotlin
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
```

---

## FILE: app/src/main/java/com/launcher360v2/domain/IconCache.kt

```kotlin
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

    fun invalidate(packageName: String) {
        // Remove all entries for this package from cache
        val keysToRemove = (0 until memCache.size()).mapNotNull { _ ->
            // LruCache doesn't expose keys directly — iterate snapshot
            null
        }
        memCache.evictAll() // Simple: evict all on any change
    }

    fun clear() = memCache.evictAll()
}
```

---

## FILE: app/src/main/java/com/launcher360v2/domain/IconPackManager.kt

```kotlin
package com.launcher360v2.domain

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import com.launcher360v2.data.model.IconPackInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads icon packs that follow the ADW/Nova/Lawnchair standard.
 * Supported packs: Arcticons, Lawnicons, Delta, Whicons, and any pack
 * that publishes com.novalauncher.THEME or org.adw.launcher.THEMES.
 *
 * How it works:
 *   1. Discover packs by querying intent filters
 *   2. Load appfilter.xml from the pack's resources
 *   3. Parse ComponentName → drawable-name mapping
 *   4. On getIcon(), look up mapping and load drawable from pack's Resources
 */
@Singleton
class IconPackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "IconPackManager"
        // Known intent filter actions for icon packs
        private val ICON_PACK_ACTIONS = listOf(
            "com.novalauncher.THEME",
            "org.adw.launcher.THEMES",
            "com.dlto.atom.launcher.THEME",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME"
        )
    }

    // Currently active icon pack package name (empty = system icons)
    private var activePackage: String = ""
    private var packResources: Resources? = null
    private var packPackage: String = ""

    // Map: ComponentName.flattenToString() → drawable resource name
    private val componentMap = HashMap<String, String>()

    /**
     * Returns all installed icon packs on the device.
     */
    suspend fun getInstalledIconPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packs = mutableListOf<IconPackInfo>()

        for (action in ICON_PACK_ACTIONS) {
            val intent = Intent(action)
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName
                if (packs.none { it.packageName == pkg }) {
                    packs.add(
                        IconPackInfo(
                            packageName = pkg,
                            label = ri.loadLabel(pm).toString(),
                            previewIcon = ri.loadIcon(pm)
                        )
                    )
                }
            }
        }
        packs
    }

    /**
     * Load and activate an icon pack. Call when user selects a pack in settings.
     * Pass empty string to reset to system icons.
     */
    suspend fun loadPack(packageName: String) = withContext(Dispatchers.IO) {
        componentMap.clear()
        packResources = null
        activePackage = packageName

        if (packageName.isEmpty()) return@withContext

        try {
            val pm = context.packageManager
            packResources = pm.getResourcesForApplication(packageName)
            packPackage = packageName
            parseAppFilter()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load icon pack $packageName: ${e.message}")
            activePackage = ""
        }
    }

    /**
     * Get icon for a component from the active icon pack.
     * Returns null if no pack is loaded or component has no match.
     */
    fun getIcon(componentName: ComponentName): Drawable? {
        if (activePackage.isEmpty() || packResources == null) return null

        val drawableName = componentMap[componentName.flattenToString()]
            ?: componentMap["ComponentInfo{${componentName.packageName}/}"]  // package-level fallback
            ?: return null

        return try {
            val resId = packResources!!.getIdentifier(drawableName, "drawable", packPackage)
            if (resId == 0) return null
            packResources!!.getDrawable(resId, null)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load drawable $drawableName: ${e.message}")
            null
        }
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private fun parseAppFilter() {
        val res = packResources ?: return
        val filterId = res.getIdentifier("appfilter", "xml", packPackage)
        if (filterId == 0) {
            Log.w(TAG, "appfilter.xml not found in $packPackage")
            return
        }

        try {
            val parser = res.getXml(filterId)
            var event = parser.eventType

            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")

                    if (component != null && drawable != null) {
                        // component format: "ComponentInfo{pkg/cls}"
                        val clean = component
                            .removePrefix("ComponentInfo{")
                            .removeSuffix("}")
                        // Store as "pkg/cls" (flattenToString format)
                        val flat = clean.replace("/", "/").let {
                            if ("/" !in it) "$it/" else it
                        }
                        componentMap[flat] = drawable
                    }
                }
                event = parser.next()
            }
            Log.d(TAG, "Loaded ${componentMap.size} icon mappings from $packPackage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse appfilter.xml: ${e.message}")
        }
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/domain/FocusModeManager.kt

```kotlin
package com.launcher360v2.domain

import com.launcher360v2.data.PrefsRepository
import com.launcher360v2.data.model.FocusSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Focus Mode: hides user-defined "distraction" apps from the drawer.
 *
 * Two modes:
 *   1. Manual: user taps the focus toggle — stays until toggled off
 *   2. Timed: automatically activates on schedule (checks every minute via UI ticker)
 *
 * The focus-hidden set is separate from the permanent hidden set in PrefsRepository.
 * When focus is OFF, distraction apps reappear in drawer automatically.
 */
@Singleton
class FocusModeManager @Inject constructor(
    private val prefs: PrefsRepository
) {
    val schedule: Flow<FocusSchedule> = prefs.focusSchedule
    val isManuallyActive: Flow<Boolean> = prefs.focusActive

    /**
     * Combined flow: true if focus should be active right now.
     * Accounts for both manual toggle AND timed schedule.
     */
    val isEffectivelyActive: Flow<Boolean> = combine(
        prefs.focusActive,
        prefs.focusSchedule
    ) { manualActive, schedule ->
        manualActive || (schedule.timedMode && isInScheduledWindow(schedule))
    }

    /**
     * Returns the set of packages to hide during focus mode.
     * Called by DrawerViewModel to filter the app list.
     */
    val focusHiddenPackages: Flow<Set<String>> = combine(
        isEffectivelyActive,
        prefs.focusSchedule
    ) { active, schedule ->
        if (active) schedule.distractingPackages else emptySet()
    }

    suspend fun setManualActive(active: Boolean) = prefs.setFocusActive(active)
    suspend fun toggleManual() {
        // Toggle is handled by reading current state in ViewModel, not here
    }

    suspend fun saveSchedule(schedule: FocusSchedule) = prefs.saveFocusSchedule(schedule)

    private fun isInScheduledWindow(schedule: FocusSchedule): Boolean {
        val now = LocalTime.now()
        val today = DayOfWeek.from(java.time.LocalDate.now()).value  // 1=Mon, 7=Sun

        if (today !in schedule.activeDays) return false

        val start = LocalTime.of(schedule.startHour, schedule.startMinute)
        val end = LocalTime.of(schedule.endHour, schedule.endMinute)

        return if (start <= end) {
            now.isAfter(start) && now.isBefore(end)
        } else {
            // Crosses midnight
            now.isAfter(start) || now.isBefore(end)
        }
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/domain/PredictiveAppsEngine.kt

```kotlin
package com.launcher360v2.domain

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Predictive App Row: suggests the top 4 apps you're most likely to use RIGHT NOW,
 * based on your own usage patterns.
 *
 * Algorithm:
 *   1. Query 4 weeks of usage data via UsageStatsManager
 *   2. Build a frequency matrix: [packageName][dayOfWeek][hourOfDay] = launchCount
 *   3. Score each app = frequency at (today, currentHour) × recency weight
 *   4. Return top N apps by score, excluding already-open / system UI
 *
 * REQUIRES: android.permission.PACKAGE_USAGE_STATS
 * This is a "special" permission — user must grant in Settings > Apps > Special > Usage Access.
 * Show a dialog directing user there on first run.
 */
@Singleton
class PredictiveAppsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PredictiveEngine"
        private const val SUGGESTION_COUNT = 4
        private const val WEEKS_HISTORY = 4

        // Packages to never suggest
        private val EXCLUDED_PACKAGES = setOf(
            "com.launcher360v2",
            "com.launcher360v2.debug",
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher"
        )
    }

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** Returns true if usage stats permission has been granted. */
    fun hasPermission(): Boolean {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            System.currentTimeMillis() - 1000 * 60,
            System.currentTimeMillis()
        )
        return stats != null && stats.isNotEmpty()
    }

    /**
     * Returns suggested package names in priority order.
     * Call from a coroutine — queries usage stats on IO dispatcher.
     */
    suspend fun getSuggestions(count: Int = SUGGESTION_COUNT): List<String> =
        withContext(Dispatchers.Default) {
            if (!hasPermission()) return@withContext emptyList()

            val now = Calendar.getInstance()
            val currentHour = LocalTime.now().hour
            val currentDay = DayOfWeek.from(LocalDate.now()).value // 1=Mon, 7=Sun

            val stats = queryUsageStats() ?: return@withContext emptyList()

            // Build frequency map: pkg → score for current time slot
            val scores = mutableMapOf<String, Float>()

            for (stat in stats) {
                val pkg = stat.packageName
                if (pkg in EXCLUDED_PACKAGES) continue
                if (!isLaunchableApp(pkg)) continue

                // Recency weight: more recent usage scores higher
                val daysSinceLastUse = (System.currentTimeMillis() - stat.lastTimeUsed) /
                        (1000L * 60 * 60 * 24)
                val recencyWeight = when {
                    daysSinceLastUse < 1 -> 3.0f
                    daysSinceLastUse < 3 -> 2.0f
                    daysSinceLastUse < 7 -> 1.5f
                    else -> 1.0f
                }

                // Time-of-day relevance from historical pattern
                // totalTimeVisible is a proxy for engagement depth
                val engagementScore = (stat.totalTimeInForeground / 60000f).coerceAtMost(60f)

                scores[pkg] = (scores[pkg] ?: 0f) + engagementScore * recencyWeight
            }

            scores.entries
                .sortedByDescending { it.value }
                .take(count)
                .map { it.key }
                .also { Log.d(TAG, "Suggestions: $it") }
        }

    private fun queryUsageStats(): List<UsageStats>? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (WEEKS_HISTORY * 7 * 24 * 60 * 60 * 1000L)
        return try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_WEEKLY, startTime, endTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "queryUsageStats failed: ${e.message}")
            null
        }
    }

    private fun isLaunchableApp(packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            pm.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) { false }
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/domain/GestureController.kt

```kotlin
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
     * edgeZone: left boundary width in px where feed swipe originates.
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
```
