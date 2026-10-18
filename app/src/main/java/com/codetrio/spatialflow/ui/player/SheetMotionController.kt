package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Centralizes sheet motion updates so animation/snap logic lives in one place.
 * Design adopted from PixelPlayer: Uses MutatorMutex so that gesture drags and fling animations
 * cleanly cancel and coordinate without race conditions, touch fighting, or stuck sheets.
 */
class SheetMotionController(
    private val sheetProgress: Animatable<Float, AnimationVector1D>,
    private val mutex: MutatorMutex,
    private val defaultOpenSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f
    ),
    private val defaultCloseSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
) {
    suspend fun animateTo(
        targetExpanded: Boolean,
        animationSpec: AnimationSpec<Float>? = null,
        initialVelocity: Float = 0f
    ) {
        val targetFraction = if (targetExpanded) 1f else 0f
        if (sheetProgress.value == targetFraction && !sheetProgress.isRunning) return

        val spec = animationSpec ?: if (targetExpanded) defaultOpenSpec else defaultCloseSpec

        mutex.mutate {
            sheetProgress.animateTo(
                targetValue = targetFraction,
                initialVelocity = initialVelocity,
                animationSpec = spec
            )
        }
    }

    suspend fun snapTo(fraction: Float) {
        mutex.mutate {
            sheetProgress.snapTo(fraction.coerceIn(0f, 1f))
        }
    }

    suspend fun stop() {
        sheetProgress.stop()
    }
}
