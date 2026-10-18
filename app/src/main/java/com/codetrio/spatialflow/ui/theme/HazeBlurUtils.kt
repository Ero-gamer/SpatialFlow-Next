package com.codetrio.spatialflow.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

// ─────────────────────────────────────────────
// Composition locals
// ─────────────────────────────────────────────

/**
 * CompositionLocal providing access to the primary HazeState across the application window.
 */
val LocalAppHazeState = compositionLocalOf<HazeState?> { null }

/**
 * CompositionLocal providing access to the primary Backdrop layer across the application window.
 */
val LocalAppBackdrop = compositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

/**
 * CompositionLocal providing access to the FullPlayer artwork HazeState.
 */
val LocalPlayerHazeState = compositionLocalOf<HazeState?> { null }

// ─────────────────────────────────────────────
// Device tier helpers
// ─────────────────────────────────────────────

/** Simple device RAM tier determined once per session. */
enum class DeviceTier { HIGH, MID, LOW }

private fun Context.getDeviceTier(): DeviceTier {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return DeviceTier.MID
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    val totalRamMb = info.totalMem / (1024L * 1024L)
    return when {
        totalRamMb >= 6000 -> DeviceTier.HIGH
        totalRamMb >= 3500 -> DeviceTier.MID
        else               -> DeviceTier.LOW
    }
}

/**
 * Returns the optimal blur radius for the current device tier and API level:
 *  - High-end  + Android 12+ → 20dp
 *  - Mid-range + Android 12+ → 16dp
 *  - Low-end   or pre-12    → 10dp
 */
@Composable
fun rememberAdaptiveBlurRadius(): Dp {
    val context = LocalContext.current
    return remember(context) {
        val tier = context.getDeviceTier()
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> 10.dp
            tier == DeviceTier.HIGH -> 20.dp
            tier == DeviceTier.MID  -> 16.dp
            else                    -> 10.dp
        }
    }
}

// ─────────────────────────────────────────────
// HazeState helpers
// ─────────────────────────────────────────────

/**
 * Creates and remembers a [HazeState]. Returns null on pre-Android 12 low-end devices
 * so blur is skipped entirely, saving GPU time.
 */
@Composable
fun rememberSpatialHazeState(enabled: Boolean = true): HazeState? {
    val context = LocalContext.current
    val tier = remember(context) { context.getDeviceTier() }
    val shouldEnable = enabled && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && (tier != DeviceTier.LOW)
    return if (shouldEnable) remember { HazeState() } else null
}

// ─────────────────────────────────────────────
// Style helpers
// ─────────────────────────────────────────────

/**
 * Remembered Haze Glassmorphism Style for SpatialFlow UI elements (prevents allocation on recomposition).
 */
@Composable
fun rememberSpatialHazeStyle(
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 16.dp,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
): HazeStyle = remember(backgroundColor, blurRadius, tint) {
    HazeStyle(
        backgroundColor = backgroundColor,
        blurRadius = blurRadius,
        tint = HazeDefaults.tint(tint)
    )
}

// ─────────────────────────────────────────────
// Modifier extensions
// ─────────────────────────────────────────────

/**
 * Extension to safely apply hazeSource if enabled and state is non-null.
 */
fun Modifier.spatialHazeSource(
    state: HazeState?,
    enabled: Boolean = true
): Modifier = if (enabled && state != null) {
    this.hazeSource(state = state)
} else {
    this
}

/**
 * Extension to safely apply hazeEffect if enabled and state is non-null.
 * Automatically selects the optimal blur radius based on device RAM tier and API level.
 */
@Composable
fun Modifier.spatialHazeEffect(
    state: HazeState?,
    style: HazeStyle? = null,
    backgroundColor: Color = Color(0xFF121212),
    tint: Color? = null,
    shape: Shape? = null,
    enabled: Boolean = true
): Modifier {
    val adaptiveRadius = rememberAdaptiveBlurRadius()
    val effectiveTint = tint ?: backgroundColor.copy(alpha = 0.65f)
    val finalStyle = style ?: rememberSpatialHazeStyle(
        backgroundColor = backgroundColor,
        blurRadius = adaptiveRadius,
        tint = effectiveTint
    )
    if (!enabled || state == null) return this
    val withShape = if (shape != null) this.clip(shape) else this
    return withShape.hazeEffect(state = state, style = finalStyle)
}
