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
