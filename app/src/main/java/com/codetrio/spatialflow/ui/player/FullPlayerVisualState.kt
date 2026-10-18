package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.util.lerp

/**
 * Holds references needed to compute full-player visual properties lazily.
 *
 * Design pattern from PixelPlayer: [contentAlpha] and [translationY] are computed
 * on-demand via getters that read [Animatable.value]. When called inside `graphicsLayer { }`,
 * these reads happen purely during the draw phase and trigger re-draw only — not recomposition.
 * This eliminates per-frame recomposition of parent and sibling composables during gestures.
 */
class FullPlayerVisualState(
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val initialOffsetY: Float
) {
    /** Full-player fade-in: invisible until 20% expanded, fully opaque at 100%. */
    val contentAlpha: Float
        get() {
            val f = expansionFraction.value
            return (f - 0.20f).coerceIn(0f, 0.80f) / 0.80f
        }

    /** Slide-up entrance driven by [contentAlpha]. */
    val translationY: Float
        get() = lerp(initialOffsetY, 0f, contentAlpha)
}

@Composable
fun rememberFullPlayerVisualState(
    expansionFraction: Animatable<Float, AnimationVector1D>,
    initialOffsetY: Float
): FullPlayerVisualState {
    return remember(expansionFraction, initialOffsetY) {
        FullPlayerVisualState(expansionFraction, initialOffsetY)
    }
}
