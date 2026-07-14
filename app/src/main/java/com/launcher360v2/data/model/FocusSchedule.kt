package com.launcher360v2.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FocusSchedule(
    val enabled: Boolean = false,
    val timedMode: Boolean = false,       // true = auto-activate on schedule
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 17,
    val endMinute: Int = 0,
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5), // 1=Mon … 7=Sun
    val distractingPackages: Set<String> = emptySet()
)
