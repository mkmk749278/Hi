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
