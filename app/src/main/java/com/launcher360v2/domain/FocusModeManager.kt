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
