# 360-LAUNCHER — DI MODULES, WIDGET SYSTEM, SETTINGS & MASTER AI PROMPT

---

## FILE: app/src/main/java/com/launcher360v2/di/AppModule.kt

```kotlin
package com.launcher360v2.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
}
```

---

## FILE: app/src/main/java/com/launcher360v2/di/DatabaseModule.kt

```kotlin
package com.launcher360v2.di

import android.content.Context
import androidx.room.Room
import com.launcher360v2.data.db.FolderDao
import com.launcher360v2.data.db.HomeCellDao
import com.launcher360v2.data.db.LauncherDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LauncherDatabase =
        Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher_db")
            .fallbackToDestructiveMigration()   // OK for personal use
            .build()

    @Provides fun provideHomeCellDao(db: LauncherDatabase): HomeCellDao = db.homeCellDao()
    @Provides fun provideFolderDao(db: LauncherDatabase): FolderDao = db.folderDao()
}
```

---

## FILE: app/src/main/java/com/launcher360v2/widget/WidgetHostManager.kt

```kotlin
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
```

---

## FILE: app/src/main/java/com/launcher360v2/widget/WidgetView.kt

```kotlin
package com.launcher360v2.widget

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import javax.inject.Inject

/**
 * Composable wrapper for an AppWidgetHostView.
 * Handles the View system ↔ Compose boundary.
 */
@Composable
fun AppWidgetComposable(
    widgetId: Int,
    widgetHostManager: WidgetHostManager,
    modifier: Modifier = Modifier
) {
    val widgetView = remember(widgetId) {
        widgetHostManager.createWidgetView(widgetId)
    }

    if (widgetView != null) {
        AndroidView(
            factory = { widgetView },
            modifier = modifier
        )
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/settings/HiddenAppsScreen.kt

```kotlin
package com.launcher360v2.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.ui.common.AppIcon

/**
 * Screen to manage hidden apps.
 * Shows all installed apps with a toggle — hidden apps show a "hidden" indicator.
 */
@Composable
fun HiddenAppsScreen(
    viewModel: HiddenAppsViewModel = hiltViewModel()
) {
    val allApps by viewModel.allApps.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Hidden Apps",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            "Hidden apps won't appear in the drawer but stay on your home screen and folders.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(allApps, key = { it.packageName }) { app ->
                val isHidden = app.packageName in hiddenApps
                HiddenAppRow(
                    app = app,
                    isHidden = isHidden,
                    onToggle = {
                        if (isHidden) viewModel.showApp(app.packageName)
                        else viewModel.hideApp(app.packageName)
                    }
                )
            }
        }
    }
}

@Composable
private fun HiddenAppRow(
    app: AppItem,
    isHidden: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(app = app, showLabel = false, onClick = {}, modifier = Modifier.size(48.dp))
            Column {
                Text(app.label, color = Color.White)
                if (isHidden) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                        Text("Hidden from drawer", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Switch(checked = !isHidden, onCheckedChange = { onToggle() })
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/settings/FocusModeScreen.kt

```kotlin
package com.launcher360v2.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.launcher360v2.data.model.AppItem

/**
 * Focus Mode settings screen.
 * - Toggle focus mode on/off
 * - Configure distraction apps list
 * - Set schedule (optional timed mode)
 */
@Composable
fun FocusModeScreen(
    viewModel: FocusModeViewModel = hiltViewModel()
) {
    val schedule by viewModel.schedule.collectAsState()
    val isActive by viewModel.isActive.collectAsState()
    val allApps by viewModel.allApps.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Focus Mode", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                "Hides selected distraction apps from the drawer on demand or on a schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            // Manual toggle
            Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Focus Mode", color = Color.White)
                        Text(
                            if (isActive) "Active — distracting apps hidden" else "Off",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { viewModel.setFocusActive(it) }
                    )
                }
            }
        }

        item {
            Text(
                "Distraction Apps",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Show all apps with checkbox to mark as "distraction"
        items(allApps, key = { it.packageName }) { app ->
            val isDistraction = app.packageName in (schedule.distractingPackages)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(app.label, color = Color.White)
                Checkbox(
                    checked = isDistraction,
                    onCheckedChange = { checked ->
                        val newSet = if (checked)
                            schedule.distractingPackages + app.packageName
                        else
                            schedule.distractingPackages - app.packageName
                        viewModel.saveSchedule(schedule.copy(distractingPackages = newSet))
                    }
                )
            }
        }
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/settings/IconPackScreen.kt

```kotlin
package com.launcher360v2.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.launcher360v2.data.model.IconPackInfo

/**
 * Icon Pack selection screen.
 * Shows all installed icon packs on the device.
 * Tapping one loads it via IconPackManager.
 */
@Composable
fun IconPackScreen(
    viewModel: IconPackViewModel = hiltViewModel()
) {
    val packs by viewModel.installedPacks.collectAsState()
    val activePack by viewModel.activePack.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Icon Packs", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            "Supports any icon pack using the Nova/ADW/Lawnicons standard.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // "System" option (no icon pack)
                item {
                    IconPackRow(
                        label = "System Icons (Default)",
                        packageName = "",
                        isActive = activePack.isEmpty(),
                        onClick = { viewModel.selectPack("") }
                    )
                }
                items(packs, key = { it.packageName }) { pack ->
                    IconPackRow(
                        label = pack.label,
                        packageName = pack.packageName,
                        isActive = activePack == pack.packageName,
                        onClick = { viewModel.selectPack(pack.packageName) }
                    )
                }
                if (packs.isEmpty()) {
                    item {
                        Text(
                            "No icon packs installed. Download Arcticons, Lawnicons, or Delta from Play Store.",
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPackRow(
    label: String,
    packageName: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                Color(0xFF2ECC71).copy(alpha = 0.15f)
            else
                Color.White.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, color = Color.White)
                if (packageName.isNotEmpty()) {
                    Text(packageName, color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (isActive) {
                Text("Active", color = Color(0xFF2ECC71), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/home/components/SmartClock.kt

```kotlin
package com.launcher360v2.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SmartClock(modifier: Modifier = Modifier) {
    var timeStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("h:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            timeStr = timeFmt.format(now)
            dateStr = dateFmt.format(now)
            delay(10_000L)   // update every 10 seconds
        }
    }

    Column(modifier = modifier) {
        Text(
            text = timeStr,
            style = TextStyle(
                fontSize = 72.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-2).sp,
                color = Color.White,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.4f),
                    blurRadius = 8f
                )
            )
        )
        Text(
            text = dateStr,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.75f),
                letterSpacing = 1.sp
            )
        )
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/home/components/PredictiveBar.kt

```kotlin
package com.launcher360v2.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.ui.common.AppIcon

/**
 * A translucent row of 4 predicted apps shown above the dock.
 * Refreshes on launcher resume via HomeViewModel.
 */
@Composable
fun PredictiveBar(
    apps: List<AppItem>,
    onAppClick: (AppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.take(4).forEach { app ->
            AppIcon(
                app = app,
                showLabel = false,
                onClick = { onAppClick(app) },
                modifier = Modifier.size(44.dp)
            )
        }
    }
}
```

---

# ═══════════════════════════════════════════════════════════════
# MASTER AI IMPLEMENTATION PROMPT
# Copy everything below this line and use it as the system prompt
# for your AI coder (e.g. Claude Code, Cursor, Windsurf, etc.)
# ═══════════════════════════════════════════════════════════════

## MASTER PROMPT FOR AI CODE GENERATOR

```
You are implementing a production-quality Android launcher app called "360 Launcher" for a Poco F6 (HyperOS, Android 14). This is for personal sideload — not Play Store.

## Project Reference Files
You have been given 5 specification files:
  01-BUILD-AND-MANIFEST.md  — Gradle build files, AndroidManifest, AIDL
  02-DATA-LAYER.md          — Room database, models, DataStore, repositories
  03-DOMAIN-LAYER.md        — FeedBridge, IconCache, IconPackManager, PredictiveEngine, GestureController
  04-UI-LAYER.md            — LauncherActivity, HomeScreen, AppDrawer, AppIcon, Theme
  05-DI-WIDGETS-SETTINGS-PROMPT.md — Hilt DI, widget host, settings screens

## Your Job
1. Create a new Android Studio project: Empty Activity, Kotlin, package com.launcher360v2, minSdk 29
2. Replace ALL generated files with the exact files from the specification
3. Create every file listed in every spec file. Do NOT skip any file.
4. For files marked with TODOs or incomplete sections, implement them fully using the patterns established in the spec
5. Fix any import errors — all imports should resolve once all files are created

## Files You Must Complete (not fully specified, implement based on patterns):
- HiddenAppsViewModel.kt (mirrors DrawerViewModel pattern, exposes allApps + hiddenApps flows)
- FocusModeViewModel.kt (wraps FocusModeManager, exposes schedule + isActive)
- IconPackViewModel.kt (wraps IconPackManager, exposes installedPacks + activePack)
- SettingsActivity.kt (simple ComponentActivity hosting a NavHost with 3 destinations: HiddenApps, FocusMode, IconPacks)
- WorkspacePage.kt (LazyVerticalGrid rendering HomeCells, supporting APP / WIDGET / FOLDER types)
- DockBar.kt (horizontal row of 4-5 icons at bottom, styled like the PredictiveBar but taller)
- FolderSheet.kt (bottom sheet showing folder contents as a small grid)
- NotificationBadgeService.kt (NotificationListenerService that updates AppItem.badgeCount via a shared Flow)
- BootReceiver.kt (BroadcastReceiver for BOOT_COMPLETED — no-op, launcher auto-starts as HOME app)
- WidgetHostReceiver.kt (BroadcastReceiver stub for APPWIDGET_UPDATE)

## Critical Rules:
- NEVER block the main thread — all data loading uses Dispatchers.Default or .IO
- Use spring() animations, never tween() — springs feel fluid at 120Hz
- The launcher window must be transparent (windowShowWallpaper=true) — never set a solid background on the root composable
- Hidden apps are ONLY filtered in DrawerViewModel — HomeViewModel NEVER filters by hidden list
- FeedBridge.connect() is called in Activity.onStart(), disconnect() in Activity.onDestroy()
- All DataStore reads are wrapped in .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
- Icon loading is always async — AppIcon composable uses LaunchedEffect to load icons, never loads in composition
- HyperOS kills background processes — the battery optimization dialog MUST be shown on first launch
- The AIDL files must be placed in src/main/aidl/com/android/launcher3/ exactly as specified

## Feature Priority if Anything Breaks:
P0 (must work): HOME intent filter, app drawer, launch apps, app hiding, gesture navigation
P1 (core value): Google Discover feed slide, icon pack loading, focus mode toggle
P2 (enhancement): Predictive bar (degrades gracefully if no usage stats permission), widget support

## Testing Checklist Before Signing APK:
□ Launcher appears in "Choose default launcher" dialog
□ Home screen shows wallpaper (transparent window working)
□ Swipe up opens app drawer
□ All installed apps appear in drawer
□ Hide an app → it disappears from drawer but stays on home screen
□ Swipe from left edge → Google Discover feed slides in
□ Icon pack applies to all drawer icons
□ Focus mode toggle hides selected apps
□ Predictive row shows 4 apps above dock
□ Back button from home screen does nothing
□ Battery optimization dialog appears on first launch
□ App survives HyperOS background kill (test: open another app, wait 5 min, press home)
```
