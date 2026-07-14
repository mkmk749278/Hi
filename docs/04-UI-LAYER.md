# 360-LAUNCHER — UI LAYER
# LauncherActivity, HomeScreen, DrawerViewModel, AppDrawer, AppIcon, Theme

---

## FILE: app/src/main/java/com/launcher360v2/Launcher360App.kt

```kotlin
package com.launcher360v2

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Launcher360App : Application()
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/LauncherActivity.kt

```kotlin
package com.launcher360v2.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.launcher360v2.data.PrefsRepository
import com.launcher360v2.domain.FeedBridge
import com.launcher360v2.ui.theme.Launcher360Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    @Inject lateinit var prefs: PrefsRepository

    private lateinit var feedBridge: FeedBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow content to draw behind system bars (required for wallpaper + gestures)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        feedBridge = FeedBridge(this)

        // Register feed availability listener
        feedBridge.onFeedAvailableChanged = { available ->
            // This will be consumed by LauncherRoot via a state holder
        }

        setContent {
            Launcher360Theme {
                LauncherRoot(feedBridge = feedBridge)
            }
        }

        // First-run: ask for battery optimization exemption
        lifecycleScope.launch {
            val asked = prefs.batteryOptAsked.first()
            if (!asked) {
                checkBatteryOptimization()
            }
        }

        // First-run: check UsageStats permission for predictive row
        checkUsageStatsPermission()
    }

    override fun onStart() {
        super.onStart()
        feedBridge.connect()
    }

    override fun onResume() {
        super.onResume()
        feedBridge.onResume()
    }

    override fun onPause() {
        super.onPause()
        feedBridge.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        feedBridge.disconnect()
    }

    // Intercept back press — launcher should not go back to itself
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing — or collapse drawer if open (handled in LauncherRoot)
    }

    private fun checkBatteryOptimization() {
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            lifecycleScope.launch { prefs.markBatteryOptAsked() }
            // Show AlertDialog in Compose via a SharedFlow/event bus
            // For now: direct to settings
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                // Some HyperOS builds don't support direct intent — open general battery settings
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun checkUsageStatsPermission() {
        val usm = getSystemService(android.app.usage.UsageStatsManager::class.java)
        val stats = usm.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            System.currentTimeMillis() - 60_000,
            System.currentTimeMillis()
        )
        if (stats.isNullOrEmpty()) {
            // Guide user: Settings > Apps > Special app access > Usage access
            // Show a one-time dialog via Compose event
        }
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/LauncherRoot.kt

```kotlin
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
                        val action = GestureController.classify(
                            dx = 0f, dy = 0f,
                            startX = offset.x, startY = offset.y,
                            screenWidth = screenWidthPx, screenHeight = screenHeightPx
                        )
                        // Pre-classify: only start feed scroll if starting from left edge
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
                            return@detectDragGestures
                        }

                        val action = GestureController.classify(
                            dx = dx, dy = dy,
                            startX = change.position.x, startY = change.position.y,
                            screenWidth = screenWidthPx, screenHeight = screenHeightPx
                        )
                        when (action) {
                            SwipeAction.OPEN_DRAWER -> drawerVisible = true
                            else -> {}
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
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/home/HomeViewModel.kt

```kotlin
package com.launcher360v2.ui.home

import android.content.ComponentName
import android.os.UserHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher360v2.data.AppRepository
import com.launcher360v2.data.HomeRepository
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.data.model.HomeCell
import com.launcher360v2.domain.IconCache
import com.launcher360v2.domain.PredictiveAppsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepo: HomeRepository,
    private val appRepo: AppRepository,
    private val iconCache: IconCache,
    private val predictive: PredictiveAppsEngine
) : ViewModel() {

    // Full home layout from Room
    val homeCells: StateFlow<List<HomeCell>> = homeRepo.observeHomeCells()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All apps (resolved for icon lookup)
    private val allApps: StateFlow<List<AppItem>> = appRepo.allApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Predictive bar suggestions
    val predictiveSuggestions: StateFlow<List<AppItem>> = flow {
        val packages = predictive.getSuggestions(4)
        val resolved = packages.mapNotNull { pkg ->
            allApps.value.firstOrNull { it.packageName == pkg }
        }
        emit(resolved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Helper: resolve a ComponentName to AppItem for rendering
    fun resolveApp(componentFlat: String): AppItem? {
        val cn = ComponentName.unflattenFromString(componentFlat) ?: return null
        return allApps.value.firstOrNull { it.componentName == cn }
    }

    fun launchApp(item: AppItem) {
        appRepo.launchApp(item)
    }

    fun addToHome(componentFlat: String, page: Int, row: Int, col: Int) {
        viewModelScope.launch {
            homeRepo.addAppToHome(componentFlat, page, row, col)
        }
    }

    fun removeFromHome(componentFlat: String) {
        viewModelScope.launch {
            homeRepo.removeFromHome(componentFlat)
        }
    }

    fun refreshPredictions() {
        viewModelScope.launch {
            val packages = predictive.getSuggestions(4)
            val resolved = packages.mapNotNull { pkg ->
                allApps.value.firstOrNull { it.packageName == pkg }
            }
            _predictiveRefresh.value = resolved
        }
    }

    private val _predictiveRefresh = MutableStateFlow<List<AppItem>>(emptyList())
    val predictiveRefreshState: StateFlow<List<AppItem>> = _predictiveRefresh.asStateFlow()
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/home/HomeScreen.kt

```kotlin
package com.launcher360v2.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.launcher360v2.ui.home.components.DockBar
import com.launcher360v2.ui.home.components.PredictiveBar
import com.launcher360v2.ui.home.components.SmartClock
import com.launcher360v2.ui.home.components.WorkspacePage

/**
 * The home screen. Contains:
 *   - Page 0: Google Feed (positioned left of page 1, scrolled by feedProgress)
 *   - Page 1+: Home pages (icon grids)
 *   - Floating: SmartClock, PredictiveBar, DockBar
 *
 * feedProgress (0.0→1.0) is driven by gesture in LauncherRoot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    feedProgress: Float,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val homeCells by viewModel.homeCells.collectAsState()
    val predictions by viewModel.predictiveSuggestions.collectAsState()

    // Group cells by page
    val pageCount = (homeCells.maxOfOrNull { it.page } ?: 0) + 1
    val pagerState = rememberPagerState(initialPage = 0) { pageCount.coerceAtLeast(1) }

    Box(modifier = modifier.fillMaxSize()) {

        // ── Page Grid ──────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val cellsOnPage = homeCells.filter { it.page == pageIndex }
            WorkspacePage(
                cells = cellsOnPage,
                onCellClick = { cell ->
                    cell.componentFlat?.let { flat ->
                        viewModel.resolveApp(flat)?.let { app ->
                            viewModel.launchApp(app)
                        }
                    }
                },
                onSwipeUp = onSwipeUp,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Floating UI — bottom stack ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Predictive bar (above dock)
            if (predictions.isNotEmpty()) {
                PredictiveBar(
                    apps = predictions,
                    onAppClick = { viewModel.launchApp(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Dock
            DockBar(
                cells = homeCells.filter { it.page == -1 }, // page = -1 reserved for dock
                resolveApp = { viewModel.resolveApp(it) },
                onAppClick = { viewModel.launchApp(it) },
                onSwipeUp = onSwipeUp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Smart Clock (top-left) ──────────────────────────────────────────
        SmartClock(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 24.dp, top = 16.dp)
        )

        // ── Pager Dots ──────────────────────────────────────────────────────
        if (pageCount > 1) {
            PageIndicator(
                pageCount = pageCount,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { index ->
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(6.dp)
            ) {
                drawCircle(
                    color = if (index == currentPage)
                        androidx.compose.ui.graphics.Color.White
                    else
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/drawer/DrawerViewModel.kt

```kotlin
package com.launcher360v2.ui.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher360v2.data.AppRepository
import com.launcher360v2.data.PrefsRepository
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.domain.FocusModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val appRepo: AppRepository,
    private val prefs: PrefsRepository,
    private val focusMode: FocusModeManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Apps shown in the drawer — hidden apps and focus-mode-hidden apps are excluded.
     * Also filters by search query.
     */
    val drawerApps: StateFlow<List<AppItem>> = combine(
        appRepo.allApps,
        prefs.hiddenApps,
        focusMode.focusHiddenPackages,
        _searchQuery
    ) { apps, hidden, focusHidden, query ->
        apps
            .filter { it.packageName !in hidden }
            .filter { it.packageName !in focusHidden }
            .filter {
                query.isEmpty() ||
                        it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val focusActive: StateFlow<Boolean> = focusMode.isEffectivelyActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun launchApp(item: AppItem) = appRepo.launchApp(item)

    fun hideApp(packageName: String) = viewModelScope.launch {
        prefs.addHiddenApp(packageName)
    }

    fun openAppInfo(item: AppItem) = appRepo.openAppInfo(item)

    fun toggleFocusMode() = viewModelScope.launch {
        val current = focusActive.value
        focusMode.setManualActive(!current)
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/drawer/AppDrawer.kt

```kotlin
package com.launcher360v2.ui.drawer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.ui.common.AppIcon
import com.launcher360v2.ui.common.GlassSurface

/**
 * Full-screen app drawer, slides up from the bottom.
 * Glassmorphism surface with search bar and 4-column app grid.
 */
@Composable
fun AppDrawer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DrawerViewModel = hiltViewModel()
) {
    val apps by viewModel.drawerApps.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val focusActive by viewModel.focusActive.collectAsState()

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        GlassSurface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Drag Handle ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                            .clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Search Bar ───────────────────────────────────────────────────
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = {
                        Text(
                            "Search apps…",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 15.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedBorderColor = Color.White.copy(alpha = 0.3f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                // ── Focus Mode Chip ───────────────────────────────────────────
                if (focusActive || true) {  // always show toggle for easy access
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = focusActive,
                            onClick = { viewModel.toggleFocusMode() },
                            label = {
                                Text(
                                    if (focusActive) "Focus ON" else "Focus OFF",
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2ECC71).copy(alpha = 0.8f),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── App Grid ─────────────────────────────────────────────────
                if (apps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No apps found", color = Color.White.copy(alpha = 0.4f))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ) {
                        items(
                            items = apps,
                            key = { it.key },
                            contentType = { "app" }
                        ) { app ->
                            AppIcon(
                                app = app,
                                showLabel = true,
                                onClick = {
                                    viewModel.launchApp(app)
                                    onDismiss()
                                },
                                onLongClick = {
                                    // Show context menu: Open / Hide / App Info
                                },
                                modifier = Modifier.animateItemPlacement()
                            )
                        }
                    }
                }
            }
        }
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/common/AppIcon.kt

```kotlin
package com.launcher360v2.ui.common

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.domain.IconCache
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.ImageBitmap
import androidx.hilt.EntryPointAccessors
import dagger.hilt.android.EntryPointAccessors as EPAK

/**
 * Single app icon with optional label and badge count.
 * Loads icon asynchronously via IconCache.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppItem,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var iconBitmap by remember(app.key) { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()

    // Icon is loaded by the ViewModel/Repository — here we observe app.icon
    // If app.icon is pre-loaded, convert it; otherwise show placeholder
    LaunchedEffect(app.key, app.icon) {
        app.icon?.let { drawable ->
            scope.launch {
                iconBitmap = drawable.toBitmap(width = 108, height = 108).asImageBitmap()
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp)
    ) {
        // ── Icon ───────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = iconBitmap!!,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder while loading
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.12f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                    )
                }
            }

            // Badge count
            if (app.badgeCount > 0) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .then(
                            Modifier.offset(x = 4.dp, y = (-4).dp)
                        )
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                Modifier.background(
                                    color = Color(0xFFE74C3C),
                                    shape = RoundedCornerShape(9.dp)
                                )
                            )
                            .also { } // import androidx.compose.foundation.background
                    ) {
                        Text(
                            text = if (app.badgeCount > 99) "99+" else app.badgeCount.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        // ── Label ─────────────────────────────────────────────────────────────
        if (showLabel) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 64.dp),
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        blurRadius = 4f
                    )
                )
            )
        }
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/common/GlassSurface.kt

```kotlin
package com.launcher360v2.ui.common

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

/**
 * Frosted glass surface using Android 12+ RenderEffect blur.
 * Falls back to semi-transparent dark on older Android.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.then(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.graphicsLayer {
                    // BlurEffect is Android 12+ only
                    renderEffect = BlurEffect(
                        radiusX = 32f,
                        radiusY = 32f,
                        edgeTreatment = TileMode.Clamp
                    )
                }
            } else {
                Modifier
            }
        )
    ) {
        // Dark tinted layer behind content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A).copy(alpha = 0.72f))
        )
        content()
    }
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/theme/Theme.kt

```kotlin
package com.launcher360v2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LauncherColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFFB0B0B0),
    background = Color.Transparent,
    surface = Color(0x1AFFFFFF),       // 10% white — for cards/surfaces
    onSurface = Color.White,
    onBackground = Color.White,
    outline = Color(0x33FFFFFF)
)

@Composable
fun Launcher360Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LauncherColorScheme,
        typography = LauncherTypography,
        content = content
    )
}
```

---

## FILE: app/src/main/java/com/launcher360v2/ui/theme/Type.kt

```kotlin
package com.launcher360v2.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val LauncherTypography = Typography(
    // Used for large clock display
    displayLarge = TextStyle(
        fontSize = 72.sp,
        fontWeight = FontWeight.Thin,
        letterSpacing = (-0.03).em,
        lineHeight = 72.sp
    ),
    // Used for date string
    titleMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.06.em
    ),
    // App icon labels
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.02.em
    ),
    // Search bar placeholder
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Light
    )
)
```
