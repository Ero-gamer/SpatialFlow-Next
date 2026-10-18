package com.codetrio.spatialflow.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Computes the player background color from the extracted palette and stored preference.
 *
 * - Static theme: picks the most vibrant swatch, clamps saturation and lightness for readability.
 * - Dynamic/fluid theme: uses the track's stored background color, clamping luminance to ~15.5%.
 */
fun resolvePlayerBackgroundColor(
    storedBackgroundColor: Int,
    isStatic: Boolean,
    vibrant: Color,
    darkVibrant: Color,
    darkMuted: Color,
): Color {
    return if (isStatic) {
        val baseColor = when {
            vibrant != Color.Black -> vibrant
            darkVibrant != Color.Black -> darkVibrant
            darkMuted != Color.Black -> darkMuted
            else -> Color(storedBackgroundColor)
        }
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
        hsl[1] = hsl[1].coerceIn(0.25f, 0.65f)
        hsl[2] = hsl[2].coerceIn(0.12f, 0.45f)
        Color(ColorUtils.HSLToColor(hsl))
    } else {
        val baseColor = Color(storedBackgroundColor)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
        val isMonochrome = hsl[1] < 0.06f
        hsl[2] = 0.155f
        hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.32f, 0.54f)
        Color(ColorUtils.HSLToColor(hsl))
    }
}

/**
 * Computes the dynamic accent color, ensuring legibility against the current background.
 * - Grayscale/monochrome accents are replaced with pure black/white based on background lightness.
 * - On light backgrounds the accent is darkened and its saturation boosted.
 */
fun resolveDynamicAccentColor(
    accentColor: Color,
    isLightBackground: Boolean,
): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accentColor.toArgb(), hsl)
    return if (hsl[1] < 0.08f) {
        // Monochromatic / Grayscale — fall back to high-contrast
        if (isLightBackground) Color(0xFF0F0F14) else Color.White
    } else {
        if (!isLightBackground) {
            accentColor
        } else {
            hsl[2] = hsl[2].coerceAtMost(0.40f)
            hsl[1] = hsl[1].coerceAtLeast(0.6f)
            Color(ColorUtils.HSLToColor(hsl))
        }
    }
}

/**
 * Returns a [Pair] of (contentColor, contentSecondary) adapted for the current background luminance.
 */
fun resolveContentColors(bgLuminance: Double): Pair<Color, Color> {
    val isLight = bgLuminance > 0.45
    val primary = if (isLight) Color(0xFF0F0F14) else Color.White
    val secondary = primary.copy(alpha = 0.65f)
    return primary to secondary
}
