package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.codetrio.spatialflow.ui.PlayerSheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

internal data class FullPlayerCompositionPolicy(
    val shouldRenderFullPlayer: Boolean
)

/**
 * Decides whether the full-player composable tree should be kept in composition.
 *
 * Pre-warms the full player composables in the background shortly after a song starts
 * (or when collapsed) so that when the user taps or drags the mini player to expand,
 * the entire composable hierarchy is ALREADY mounted in the slot table.
 *
 * This completely eliminates cold-mount latency and frame freezes (200-500ms jank)
 * on touch start.
 */
@Composable
internal fun rememberFullPlayerCompositionPolicy(
    currentSongId: Any?,
    currentSheetState: PlayerSheetState,
    expansionFraction: Animatable<Float, AnimationVector1D>,
    collapsedWarmDelayMs: Long = 120L
): FullPlayerCompositionPolicy {
    var keepFullPlayerComposed by remember(currentSongId) { mutableStateOf(false) }

    LaunchedEffect(currentSongId, currentSheetState) {
        if (currentSongId == null) {
            keepFullPlayerComposed = false
            return@LaunchedEffect
        }

        if (currentSheetState == PlayerSheetState.EXPANDED) {
            keepFullPlayerComposed = true
        } else {
            // Warm the hidden full-player tree after the collapsed state settles.
            // This moves the expensive first composition out of the expand animation.
            delay(collapsedWarmDelayMs)
            keepFullPlayerComposed = true
        }
    }

    // Monitor expansion fraction via snapshotFlow. Once expansion starts (> 0.015f)
    // or warm timer finishes, lock keepFullPlayerComposed to true.
    LaunchedEffect(currentSongId) {
        if (currentSongId == null) return@LaunchedEffect
        snapshotFlow {
            keepFullPlayerComposed || expansionFraction.value > 0.015f
        }.first { it }
        if (!keepFullPlayerComposed) keepFullPlayerComposed = true
    }

    // Read expansion fraction inside derivedStateOf so that changes only trigger
    // recomposition when the boolean state actually transitions.
    val shouldRenderFullPlayer by remember(currentSongId, currentSheetState) {
        derivedStateOf {
            currentSongId != null && (
                currentSheetState == PlayerSheetState.EXPANDED ||
                    expansionFraction.value > 0.005f ||
                    keepFullPlayerComposed
            )
        }
    }

    return FullPlayerCompositionPolicy(
        shouldRenderFullPlayer = shouldRenderFullPlayer
    )
}
