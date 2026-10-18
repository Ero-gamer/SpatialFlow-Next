package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.codetrio.spatialflow.ui.PlayerSheetState

data class FullPlayerRuntimePolicy(
    val allowRealtimeUpdates: Boolean
)

/**
 * Gates high-frequency UI updates (progress bar sampling, animations, canvas video decoding)
 * behind conditions that only flip at expansion thresholds — not on every frame.
 *
 * Pattern directly adopted from PixelPlayer:
 * [expansionFraction] is read inside [derivedStateOf], producing a Boolean that
 * changes only when crossing the 0.985 threshold. This completely avoids per-frame
 * recomposition of the caller and seekbar during gestures.
 */
@Composable
fun rememberFullPlayerRuntimePolicy(
    currentSheetState: PlayerSheetState,
    expansionFraction: Animatable<Float, AnimationVector1D>,
    isDragging: Boolean = false
): FullPlayerRuntimePolicy {
    val allowRealtimeUpdates by remember(currentSheetState, isDragging) {
        derivedStateOf {
            val ef = expansionFraction.value
            currentSheetState == PlayerSheetState.EXPANDED &&
                ef >= 0.985f &&
                !isDragging
        }
    }

    return FullPlayerRuntimePolicy(
        allowRealtimeUpdates = allowRealtimeUpdates
    )
}
