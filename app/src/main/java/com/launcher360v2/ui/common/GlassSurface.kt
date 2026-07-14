package com.launcher360v2.ui.common

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer

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
