package com.launcher360v2.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            // Direct the user to the system exemption prompt.
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
