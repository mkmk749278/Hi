package com.launcher360v2.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
                shadow = Shadow(
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
