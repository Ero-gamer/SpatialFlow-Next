package com.codetrio.spatialflow.ui.player

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork
import kotlin.math.abs

/**
 * Expressive 3D "Shuffle" album art transition.
 *
 * Mathematical & Choreographic Specifications:
 * 1. Total Duration: Exactly 980ms
 * 2. 3D Perspective: perspective 900px on ~200px card (ratio 4.5x card dimension) -> cameraDistance = 4.5f * size.width
 * 3. Dolly Deal (The Deck / Card Container):
 *    - 0%..44% (0ms..431.2ms): cubic-bezier(0.4, 0, 0.34, 1) -> translateZ: 0 -> -190px (scale 1.0 -> 900/1090 ≈ 0.8257), rotateY: 0 -> -15deg
 *    - 44%..68% (431.2ms..666.4ms): Dwell phase (scale = 0.8257, rotateY = -15deg)
 *    - 68%..100% (666.4ms..980ms): cubic-bezier(0.32, 0, 0.2, 1) -> translateZ: -190px -> 0 (scale 0.8257 -> 1.0), rotateY: -15deg -> 0
 * 4. Deal In (Incoming Front Card):
 *    - 0%..16% (0ms..156.8ms): Held off-frame at translateX: -124%, translateY: -76%, rotateZ: -10deg
 *    - 16%..64% (156.8ms..627.2ms): cubic-bezier(0.24, 0.64, 0.22, 1) -> flies into place (translateX: -124% -> 0, translateY: -76% -> 0, rotateZ: -10deg -> 0)
 *    - 64%..100% (627.2ms..980ms): Flush with deck while deck returns to resting position
 * 5. "No-Dip" Rule:
 *    - Both faces stay 100% opaque throughout the deal. Outgoing card sits still underneath as the pile.
 * 6. Shadows:
 *    - 0 10px 30px rgba(0, 0, 0, 0.38)
 *    - Cast by both faces during deal, cast only by front face at rest.
 * 7. Rapid-Skip Guard:
 *    - Snaps current transition to end on rapid skip, immediately starting new transition from newly seated card.
 */

// Timeline constants (ms)
const val SHUFFLE_DURATION_MS = 980

// Phase split fractions
private const val DOLLY_OUT_END = 0.44f       // 431.2ms
private const val DOLLY_DWELL_END = 0.68f     // 666.4ms
private const val DEAL_HOLD_END = 0.16f       // 156.8ms
private const val DEAL_FLIGHT_END = 0.64f     // 627.2ms

// Exact Cubic Bezier curves
private val EaseDollyOut = CubicBezierEasing(0.40f, 0.0f, 0.34f, 1.0f)
private val EaseDollyReturn = CubicBezierEasing(0.32f, 0.0f, 0.20f, 1.0f)
private val EaseDealIn = CubicBezierEasing(0.24f, 0.64f, 0.22f, 1.0f)

// 3D Geometry Constants
// perspective: 900px on ~200px card -> scale factor at z = -190px is 900 / (900 - (-190)) = 900 / 1090
private const val MIN_DOLLY_SCALE = 900f / (900f + 190f) // ≈ 0.8256881f
private const val MAX_ROTATE_Y = -15.0f                  // -15 degrees
private const val MAX_DEAL_ROTATE_Z = -10.0f            // -10 degrees
private const val DEAL_OFFSET_X_FACTOR = -1.24f         // -124%
private const val DEAL_OFFSET_Y_FACTOR = -0.76f         // -76%

private val CardShape = RoundedCornerShape(20.dp)
private val CardShadowColor = Color.Black.copy(alpha = 0.38f)

@Composable
fun ShuffleArtworkTransition(
    currentSong: SongItem,
    context: Context,
    modifier: Modifier = Modifier,
    showAnimatedArt: Boolean = true,
    allowCanvas: Boolean = true,
    activeCanvasArtwork: CanvasArtwork? = null,
    onSwipeNext: (() -> Unit)? = null,
    onSwipePrevious: (() -> Unit)? = null,
) {
    // Two persistent faces: face 0 and face 1.
    // Neither face is ever unmounted; they ping-pong between being the resting pile and the incoming deal.
    var frontFaceIndex by remember { mutableStateOf(0) }
    var incomingFaceIndex by remember { mutableStateOf(0) }
    var songFace0 by remember { mutableStateOf<SongItem?>(currentSong) }
    var songFace1 by remember { mutableStateOf<SongItem?>(null) }
    var isTransitionActive by remember { mutableStateOf(false) }

    val animProgress = remember { Animatable(1f) }
    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    // Track changes & rapid-skip guard
    LaunchedEffect(currentSong.id, currentSong.thumbnailUrl) {
        val currentFrontSong = if (frontFaceIndex == 0) songFace0 else songFace1
        
        // If it's the exact same song, update metadata silently without triggering transition
        if (currentFrontSong?.id == currentSong.id) {
            if (frontFaceIndex == 0) {
                songFace0 = currentSong
            } else {
                songFace1 = currentSong
            }
            return@LaunchedEffect
        }

        // Rapid-skip guard: if mid-transition, snap immediately to final state
        if (isTransitionActive) {
            animProgress.snapTo(1f)
            frontFaceIndex = incomingFaceIndex
            isTransitionActive = false
        }

        val nextIncomingIndex = 1 - frontFaceIndex
        incomingFaceIndex = nextIncomingIndex

        // Assign the new song to the incoming face slot.
        // The outgoing face slot remains untouched with its already-painted artwork!
        if (nextIncomingIndex == 0) {
            songFace0 = currentSong
        } else {
            songFace1 = currentSong
        }

        isTransitionActive = true

        // Run 980ms linear progress driver (phases evaluated strictly in GPU draw-phase inside graphicsLayer)
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SHUFFLE_DURATION_MS,
                easing = LinearEasing
            )
        )

        // Deal completed: the incoming face becomes the resting front face
        frontFaceIndex = nextIncomingIndex
        isTransitionActive = false
    }

    // Touch gesture support: Horizontal swipe to skip tracks
    val swipeModifier = if (onSwipeNext != null || onSwipePrevious != null) {
        Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { totalDragAmount = 0f },
                onDragEnd = {
                    val thresholdPx = 48.dp.toPx()
                    if (totalDragAmount < -thresholdPx) {
                        onSwipeNext?.invoke()
                    } else if (totalDragAmount > thresholdPx) {
                        onSwipePrevious?.invoke()
                    }
                    totalDragAmount = 0f
                },
                onDragCancel = { totalDragAmount = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    totalDragAmount += dragAmount
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(swipeModifier),
        contentAlignment = Alignment.Center
    ) {
        // ── Card Container / Deck (Undergoes Dolly Deal Animation) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = animProgress.value
                    if (isTransitionActive && t < 1.0f) {
                        applyDollyTransform(t, this)
                        cameraDistance = 16f * density
                    } else {
                        scaleX = 1f
                        scaleY = 1f
                        rotationY = 0f
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Face 0: Stably keyed, never unmounted when transitioning to face 1
            key(0) {
                val song0 = songFace0
                if (song0 != null) {
                    val isIncoming = isTransitionActive && incomingFaceIndex == 0
                    val isFront = (!isTransitionActive && frontFaceIndex == 0) || isIncoming

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isIncoming) 2f else if (isFront) 1.5f else 1f)
                            .graphicsLayer {
                                val t = animProgress.value
                                if (isIncoming && t < 1.0f) {
                                    applyDealTransform(t, this)
                                } else {
                                    translationX = 0f
                                    translationY = 0f
                                    rotationZ = 0f
                                }
                                shape = CardShape
                                shadowElevation = if (isFront) 10.dp.toPx() else 0f
                            }
                    ) {
                        ArtworkPageItem(
                            song = song0,
                            isCurrentPage = isFront && !isTransitionActive,
                            showAnimatedArt = showAnimatedArt,
                            allowCanvas = allowCanvas,
                            activeCanvasArtwork = if (isFront && !isTransitionActive) activeCanvasArtwork else null,
                            context = context
                        )
                    }
                }
            }

            // Face 1: Stably keyed, never unmounted when transitioning to face 0
            key(1) {
                val song1 = songFace1
                if (song1 != null) {
                    val isIncoming = isTransitionActive && incomingFaceIndex == 1
                    val isFront = (!isTransitionActive && frontFaceIndex == 1) || isIncoming

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isIncoming) 2f else if (isFront) 1.5f else 1f)
                            .graphicsLayer {
                                val t = animProgress.value
                                if (isIncoming && t < 1.0f) {
                                    applyDealTransform(t, this)
                                } else {
                                    translationX = 0f
                                    translationY = 0f
                                    rotationZ = 0f
                                }
                                shape = CardShape
                                shadowElevation = if (isFront) 10.dp.toPx() else 0f
                            }
                    ) {
                        ArtworkPageItem(
                            song = song1,
                            isCurrentPage = isFront && !isTransitionActive,
                            showAnimatedArt = showAnimatedArt,
                            allowCanvas = allowCanvas,
                            activeCanvasArtwork = if (isFront && !isTransitionActive) activeCanvasArtwork else null,
                            context = context
                        )
                    }
                }
            }
        }
    }
}

/**
 * Computes the 3D Dolly Deal transform (Deck scaling and Y-axis rotation)
 * directly into the graphics layer scope with zero object allocations.
 */
private fun applyDollyTransform(t: Float, layer: androidx.compose.ui.graphics.GraphicsLayerScope) {
    val scale: Float
    val rotY: Float
    when {
        t <= 0f -> {
            scale = 1.0f
            rotY = 0f
        }
        t < DOLLY_OUT_END -> {
            val phaseT = (t / DOLLY_OUT_END).coerceIn(0f, 1f)
            val eased = EaseDollyOut.transform(phaseT)
            scale = lerp(1.0f, MIN_DOLLY_SCALE, eased)
            rotY = MAX_ROTATE_Y * eased
        }
        t <= DOLLY_DWELL_END -> {
            scale = MIN_DOLLY_SCALE
            rotY = MAX_ROTATE_Y
        }
        t < 1.0f -> {
            val phaseT = ((t - DOLLY_DWELL_END) / (1.0f - DOLLY_DWELL_END)).coerceIn(0f, 1f)
            val eased = EaseDollyReturn.transform(phaseT)
            scale = lerp(MIN_DOLLY_SCALE, 1.0f, eased)
            rotY = MAX_ROTATE_Y * (1.0f - eased)
        }
        else -> {
            scale = 1.0f
            rotY = 0f
        }
    }
    layer.scaleX = scale
    layer.scaleY = scale
    layer.rotationY = rotY
}

/**
 * Computes the Deal In transform (Incoming card offset and Z-axis rotation)
 * directly into the graphics layer scope with zero object allocations.
 */
private fun applyDealTransform(t: Float, layer: androidx.compose.ui.graphics.GraphicsLayerScope) {
    val normX: Float
    val normY: Float
    val rotZ: Float
    when {
        t <= DEAL_HOLD_END -> {
            normX = DEAL_OFFSET_X_FACTOR
            normY = DEAL_OFFSET_Y_FACTOR
            rotZ = MAX_DEAL_ROTATE_Z
        }
        t < DEAL_FLIGHT_END -> {
            val phaseT = ((t - DEAL_HOLD_END) / (DEAL_FLIGHT_END - DEAL_HOLD_END)).coerceIn(0f, 1f)
            val eased = EaseDealIn.transform(phaseT)
            val factor = 1.0f - eased
            normX = DEAL_OFFSET_X_FACTOR * factor
            normY = DEAL_OFFSET_Y_FACTOR * factor
            rotZ = MAX_DEAL_ROTATE_Z * factor
        }
        else -> {
            normX = 0f
            normY = 0f
            rotZ = 0f
        }
    }
    layer.translationX = normX * layer.size.width
    layer.translationY = normY * layer.size.height
    layer.rotationZ = rotZ
}
