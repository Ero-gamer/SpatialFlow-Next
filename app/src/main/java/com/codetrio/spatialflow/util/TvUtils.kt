package com.codetrio.spatialflow.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Android TV & Google TV Detection and D-Pad Focus Utilities.
 */
object TvUtils {

    /**
     * Checks whether the current host device is an Android TV / Google TV device.
     */
    @Suppress("DEPRECATION")
    fun isTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isUiModeTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTelevision = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        return isUiModeTv || hasLeanback || hasTelevision
    }
}

/**
 * Composable helper returning true if running on Android TV.
 */
@Composable
fun isTvDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) { TvUtils.isTv(context) }
}

/**
 * Adds an expressive D-Pad focus indicator (scaling and glowing border) when focused on Android TV.
 * On mobile/touch devices, this safely behaves as standard Compose without unwanted borders.
 */
fun Modifier.tvFocusHighlight(
    shape: Shape = RoundedCornerShape(16.dp),
    focusedBorderColor: Color = Color(0xFF00E5FF),
    focusedBorderWidth: Dp = 2.dp,
    scaleOnFocus: Float = 1.04f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val isTv = isTvDevice()
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by source.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isTv && isFocused) scaleOnFocus else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "TvFocusScale"
    )

    this
        .scale(scale)
        .focusable(interactionSource = source)
        .then(
            if (isTv && isFocused) {
                Modifier.border(
                    width = focusedBorderWidth,
                    color = focusedBorderColor,
                    shape = shape
                )
            } else {
                Modifier
            }
        )
}
