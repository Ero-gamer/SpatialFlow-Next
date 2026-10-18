package com.codetrio.spatialflow.ui.player

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Checks system accessibility settings to see if reduced motion is requested.
 */
@Composable
internal fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } else {
            false
        }
    }
}


/**
 * Renders a high-performance, dynamic fluid mesh gradient background.
 * Uses irrational frequency ratios on sinusoidal paths so blobs drift continuously
 * without any visible snap or reset. Each blob moves on its own unique Lissajous orbit.
 */
@Composable
fun Modifier.animatedFluidBackground(
    backgroundColor: Color,
    vibrant: Color,
    darkVibrant: Color,
    darkMuted: Color,
    isAnimated: Boolean = true
): Modifier {
    return this.fluidShaderBackground(
        backgroundColor = backgroundColor,
        vibrant = vibrant,
        darkVibrant = darkVibrant,
        darkMuted = darkMuted,
        isAnimated = isAnimated,
        isRapidSkipping = false
    )
}
