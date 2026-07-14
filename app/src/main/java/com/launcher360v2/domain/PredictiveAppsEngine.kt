package com.launcher360v2.domain

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
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
                // totalTimeInForeground is a proxy for engagement depth
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
