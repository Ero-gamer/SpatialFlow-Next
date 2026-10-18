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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork

/**
 * Expressive "Crossfade" (Scale-Settle) album art transition.
 *
 * Mathematical & Choreographic Specifications:
 * 1. Total Duration: Exactly 620ms
 * 2. Easing: cubic-bezier(0.22, 1.0, 0.36, 1.0)
 * 3. Incoming Card: Scale 1.06 -> 1.0, Alpha 0 -> 1
 * 4. Outgoing Card: Scale 1.0 -> 0.97, Alpha stays 100% (Strict No-Dip Rule: prevents mid-transition dimming)
 * 5. Zero-Recomposition Draw-Phase Execution: All scale and alpha values evaluate strictly inside graphicsLayer
 */

const val CROSSFADE_DURATION_MS = 620

private val EaseSettle = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun CrossfadeArtworkTransition(
    currentSong: SongItem,
    context: Context,
    modifier: Modifier = Modifier,
    showAnimatedArt: Boolean = true,
    allowCanvas: Boolean = true,
    activeCanvasArtwork: CanvasArtwork? = null,
    onSwipeNext: (() -> Unit)? = null,
    onSwipePrevious: (() -> Unit)? = null,
) {
    var frontFaceIndex by remember { mutableIntStateOf(0) }
    var incomingFaceIndex by remember { mutableIntStateOf(0) }
    var songFace0 by remember { mutableStateOf<SongItem?>(currentSong) }
    var songFace1 by remember { mutableStateOf<SongItem?>(null) }
    var isTransitionActive by remember { mutableStateOf(false) }

    val animProgress = remember { Animatable(1f) }
    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentSong.id, currentSong.thumbnailUrl) {
        val currentFrontSong = if (frontFaceIndex == 0) songFace0 else songFace1

        if (currentFrontSong?.id == currentSong.id) {
            if (frontFaceIndex == 0) songFace0 = currentSong else songFace1 = currentSong
            return@LaunchedEffect
        }

        // Rapid-skip guard: immediately snap to completion
        if (isTransitionActive) {
            animProgress.snapTo(1f)
            frontFaceIndex = incomingFaceIndex
            isTransitionActive = false
        }

        val nextIncomingIndex = 1 - frontFaceIndex
        incomingFaceIndex = nextIncomingIndex

        if (nextIncomingIndex == 0) {
            songFace0 = currentSong
        } else {
            songFace1 = currentSong
        }

        isTransitionActive = true

        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = CROSSFADE_DURATION_MS,
                easing = EaseSettle
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
            .clip(CardShape)
            .then(swipeModifier),
        contentAlignment = Alignment.Center
    ) {
        // Face 0
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
                            if (isTransitionActive) {
                                if (isIncoming) {
                                    val scale = lerp(1.06f, 1.0f, t)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = t
                                } else {
                                    val scale = lerp(1.0f, 0.97f, t)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 1.0f // Strict No-Dip Rule
                                }
                            } else {
                                scaleX = 1.0f
                                scaleY = 1.0f
                                alpha = if (isFront) 1.0f else 0f
                            }
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

        // Face 1
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
                            if (isTransitionActive) {
                                if (isIncoming) {
                                    val scale = lerp(1.06f, 1.0f, t)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = t
                                } else {
                                    val scale = lerp(1.0f, 0.97f, t)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 1.0f // Strict No-Dip Rule
                                }
                            } else {
                                scaleX = 1.0f
                                scaleY = 1.0f
                                alpha = if (isFront) 1.0f else 0f
                            }
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
