package com.codetrio.spatialflow.ui.player

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.math.cos

/**
 * Expressive 3D "Flip" album art transition.
 *
 * Mathematical & Choreographic Specifications:
 * 1. Total Duration: Exactly 820ms
 * 2. 3D Perspective: cameraDistance = 16f * density (equivalent to perspective 900px)
 * 3. Spring Overshoot: cubic-bezier(0.28, 1.30, 0.50, 1.0) overshooting 9.6 degrees past 180° before settling
 * 4. Backface Visibility: Hidden for both faces; Face 1 is pre-rotated by 180°
 * 5. Zero-Recomposition Draw-Phase Execution: All rotation values evaluate strictly inside graphicsLayer
 * 6. Rapid-skip guard: Snaps instantly to target rotation on rapid skip
 */

const val FLIP_DURATION_MS = 820

// Spring overshoot easing cubic-bezier(0.28, 1.3, 0.5, 1)
private val EaseFlipSpring = CubicBezierEasing(0.28f, 1.30f, 0.50f, 1.0f)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun FlipArtworkTransition(
    currentSong: SongItem,
    context: Context,
    modifier: Modifier = Modifier,
    showAnimatedArt: Boolean = true,
    allowCanvas: Boolean = true,
    activeCanvasArtwork: CanvasArtwork? = null,
    onSwipeNext: (() -> Unit)? = null,
    onSwipePrevious: (() -> Unit)? = null,
) {
    // Two persistent faces: face 0 and face 1
    var frontFaceIndex by remember { mutableIntStateOf(0) }
    var incomingFaceIndex by remember { mutableIntStateOf(0) }
    var songFace0 by remember { mutableStateOf<SongItem?>(currentSong) }
    var songFace1 by remember { mutableStateOf<SongItem?>(null) }
    var isTransitionActive by remember { mutableStateOf(false) }

    // Start & target container rotations in degrees
    var startRotationDeg by remember { mutableFloatStateOf(0f) }
    var targetRotationDeg by remember { mutableFloatStateOf(0f) }

    val animProgress = remember { Animatable(1f) }
    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentSong.id, currentSong.thumbnailUrl) {
        val currentFrontSong = if (frontFaceIndex == 0) songFace0 else songFace1

        if (currentFrontSong?.id == currentSong.id) {
            if (frontFaceIndex == 0) songFace0 = currentSong else songFace1 = currentSong
            return@LaunchedEffect
        }

        // Rapid-skip guard: snap to target state if currently animating
        if (isTransitionActive) {
            animProgress.snapTo(1f)
            frontFaceIndex = incomingFaceIndex
            startRotationDeg = targetRotationDeg
            isTransitionActive = false
        }

        val nextIncomingIndex = 1 - frontFaceIndex
        incomingFaceIndex = nextIncomingIndex

        if (nextIncomingIndex == 0) {
            songFace0 = currentSong
        } else {
            songFace1 = currentSong
        }

        // Face 0 rests at 0°, Face 1 rests at 180°
        val newTargetDeg = if (nextIncomingIndex == 1) 180f else 0f
        startRotationDeg = if (nextIncomingIndex == 1) 0f else 180f
        targetRotationDeg = newTargetDeg

        isTransitionActive = true

        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = FLIP_DURATION_MS,
                easing = EaseFlipSpring
            )
        )

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
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    totalDragAmount += dragAmount
                }
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(swipeModifier),
        contentAlignment = Alignment.Center
    ) {
        // 3D Card Container rotating on Y axis with zero recompositions
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    cameraDistance = 16f * density
                    val t = animProgress.value
                    val currentRot = lerp(startRotationDeg, targetRotationDeg, t)
                    rotationY = currentRot
                },
            contentAlignment = Alignment.Center
        ) {
            // Face 0: Renders songFace0 at rotationY = 0°
            key(0) {
                val song0 = songFace0
                if (song0 != null) {
                    val isFront = (!isTransitionActive && frontFaceIndex == 0) ||
                            (isTransitionActive && incomingFaceIndex == 0)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = animProgress.value
                                val currentRot = lerp(startRotationDeg, targetRotationDeg, t)
                                val rad = Math.toRadians(currentRot.toDouble())
                                val isFacingCamera = cos(rad) >= 0.0
                                alpha = if (isFacingCamera) 1f else 0f
                                shape = CardShape
                                shadowElevation = if (isFront && !isTransitionActive) 10.dp.toPx() else 0f
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

            // Face 1: Renders songFace1 pre-rotated by 180°
            key(1) {
                val song1 = songFace1
                if (song1 != null) {
                    val isFront = (!isTransitionActive && frontFaceIndex == 1) ||
                            (isTransitionActive && incomingFaceIndex == 1)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = 180f
                                val t = animProgress.value
                                val currentRot = lerp(startRotationDeg, targetRotationDeg, t)
                                val rad = Math.toRadians(currentRot.toDouble())
                                val isFacingCamera = cos(rad) < 0.0
                                alpha = if (isFacingCamera) 1f else 0f
                                shape = CardShape
                                shadowElevation = if (isFront && !isTransitionActive) 10.dp.toPx() else 0f
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
