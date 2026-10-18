package com.codetrio.spatialflow.ui.player

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork

/**
 * Expressive "Push" album art transition.
 *
 * Mathematical & Choreographic Specifications:
 * 1. Total Duration: Exactly 620ms
 * 2. Easing: cubic-bezier(0.4, 0.02, 0.2, 1)
 * 3. Incoming Card: Slides from translateX: 104% -> 0%
 * 4. Outgoing Card: Parallax retreat from translateX: 0% -> -34% (maintains 100% opacity, No-Dip Rule)
 * 5. Dynamic Shadow: -18px 0 34px rgba(0,0,0,0.62) along left edge of incoming card, fading to 0% at 90%..100%
 * 6. Zero-Recomposition Draw-Phase Execution: All translations evaluate strictly inside graphicsLayer
 */

const val PUSH_DURATION_MS = 620

private val EasePush = CubicBezierEasing(0.40f, 0.02f, 0.20f, 1.0f)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun PushArtworkTransition(
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
                durationMillis = PUSH_DURATION_MS,
                easing = EasePush
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
                                    // Incoming card: 104% -> 0%
                                    translationX = size.width * (1.04f * (1.0f - t))
                                } else {
                                    // Outgoing card parallax: 0% -> -34%
                                    translationX = size.width * (-0.34f * t)
                                }
                            } else {
                                translationX = 0f
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

                    // Dynamic left drop shadow on incoming card
                    if (isIncoming) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val t = animProgress.value
                            val shadowAlpha = if (t <= 0.90f) 0.62f else lerp(0.62f, 0f, (t - 0.90f) / 0.10f)
                            if (shadowAlpha > 0f) {
                                val shadowWidth = 24.dp.toPx()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = shadowAlpha),
                                            Color.Transparent
                                        ),
                                        startX = 0f,
                                        endX = shadowWidth
                                    ),
                                    topLeft = Offset.Zero,
                                    size = Size(shadowWidth, size.height)
                                )
                            }
                        }
                    }
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
                                    // Incoming card: 104% -> 0%
                                    translationX = size.width * (1.04f * (1.0f - t))
                                } else {
                                    // Outgoing card parallax: 0% -> -34%
                                    translationX = size.width * (-0.34f * t)
                                }
                            } else {
                                translationX = 0f
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

                    // Dynamic left drop shadow on incoming card
                    if (isIncoming) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val t = animProgress.value
                            val shadowAlpha = if (t <= 0.90f) 0.62f else lerp(0.62f, 0f, (t - 0.90f) / 0.10f)
                            if (shadowAlpha > 0f) {
                                val shadowWidth = 24.dp.toPx()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = shadowAlpha),
                                            Color.Transparent
                                        ),
                                        startX = 0f,
                                        endX = shadowWidth
                                    ),
                                    topLeft = Offset.Zero,
                                    size = Size(shadowWidth, size.height)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
