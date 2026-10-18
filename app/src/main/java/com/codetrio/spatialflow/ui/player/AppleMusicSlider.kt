package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.codetrio.spatialflow.util.PlayerHapticManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Apple Music-style seekbar with custom rendering and Android-native gesture/accessibility handling.
 *
 * Micro-interaction features:
 * - 3-stage state machine:
 *     • At rest: 4 dp sleek rounded capsule with zero thumb.
 *     • Pressed: Subtle expansion (6 dp) indicating touch focus.
 *     • Dragging: Full expansion (8 dp) revealing a vertically-oriented pill grab indicator.
 * - Active track cleanly clipped inside the single capsule path.
 * - Snappy, decisive iOS-like spring motion (Spring.StiffnessMediumHigh, DampingRatioNoBouncy).
 * - Adaptive haptics: 10 discrete quantum steps per full seek to avoid feedback noise.
 * - Full Compose accessibility semantics with underlying Slider backing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = Color.White,
    restingHeight: Dp = 8.dp,
    pressedHeight: Dp = 11.dp,
    expandedHeight: Dp = 14.dp,
    thumbWidth: Dp = 4.5.dp,
    thumbHeight: Dp = 18.dp,
    semanticsLabel: String? = "Playback Seek Bar",
    transitionWindow: com.codetrio.spatialflow.service.automix.AutoMixTransitionWindow? = null,
    mixing: Boolean = false
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    // 1. Two-stage dynamic height expansion: rest (4dp) -> press (6dp) -> drag (8dp)
    val targetTrackHeight = when {
        isDragged -> expandedHeight
        isPressed -> pressedHeight
        else -> restingHeight
    }

    val currentTrackHeight by animateDpAsState(
        targetValue = targetTrackHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 3000f // Snappy, decisive iOS expansion
        ),
        label = "AppleMusicTrackHeight"
    )

    // Fill factor for smooth morph down to 0 during automix/crossfade
    val fillFactor by animateFloatAsState(
        targetValue = if (mixing) 0f else 1f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "AppleMusicSeekFillFactor"
    )

    // 2. Thumb appears only during active dragging/scrubbing and when not mixing
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragged && !mixing) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 3000f
        ),
        label = "AppleMusicThumbScale"
    )

    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val rangeSpan = valueRange.endInclusive - valueRange.start
    val normalizedProgress = if (rangeSpan > 0f) {
        ((clampedValue - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
    } else 0f
    val lastHapticStep = remember { mutableIntStateOf(-1) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp), // Ergonomic touch container
        contentAlignment = Alignment.Center
    ) {
        // Invisible Slider engine managing gesture arbitration and accessibility
        Slider(
            value = clampedValue,
            onValueChange = { newValue ->
                val newNorm = if (valueRange.endInclusive > valueRange.start) {
                    ((newValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                } else 0f

                // Adaptive Apple Music-style tactile micro-ticks: 14 discrete steps per seek
                val currentStep = (newNorm * 14f).roundToInt()
                if (currentStep != lastHapticStep.intValue) {
                    PlayerHapticManager.triggerSliderTick(context)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticStep.intValue = currentStep
                }
                onValueChange(newValue)
            },
            onValueChangeFinished = {
                if (lastHapticStep.intValue >= 0) {
                    PlayerHapticManager.triggerInteractionHaptic(context, "haptic_play_pause")
                }
                lastHapticStep.intValue = -1
                onValueChangeFinished?.invoke()
            },
            enabled = enabled,
            valueRange = valueRange,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clearAndSetSemantics {
                    if (!semanticsLabel.isNullOrBlank()) {
                        contentDescription = semanticsLabel
                    }
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = clampedValue,
                        range = valueRange.start..valueRange.endInclusive,
                        steps = 0
                    )
                    if (enabled) {
                        setProgress { requested ->
                            val coerced = requested.coerceIn(valueRange.start, valueRange.endInclusive)
                            onValueChange(coerced)
                            onValueChangeFinished?.invoke()
                            true
                        }
                    }
                },
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent
            )
        )

        // Visual rendering layer with clipped capsule track & signature vertical pill grab indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .drawWithCache {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val trackHeightPx = currentTrackHeight.toPx()
                    val thumbWidthPx = thumbWidth.toPx()
                    val thumbHeightPx = thumbHeight.toPx()
                    val topOffset = (canvasHeight - trackHeightPx) / 2f
                    val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
                    val shadowOffsetX = 0.75.dp.toPx()
                    val shadowOffsetY = 1.dp.toPx()
                    val shadowExtraWidth = 1.5.dp.toPx()

                    val capsulePath = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = 0f,
                                top = topOffset,
                                right = canvasWidth,
                                bottom = topOffset + trackHeightPx,
                                cornerRadius = cornerRadius
                            )
                        )
                    }

                    onDrawWithContent {
                        val progressPx = (canvasWidth * normalizedProgress).coerceIn(0f, canvasWidth)

                        // 1. Inactive Track (Full Rounded Capsule)
                        drawRoundRect(
                            color = inactiveTrackColor,
                            topLeft = Offset(0f, topOffset),
                            size = Size(canvasWidth, trackHeightPx),
                            cornerRadius = cornerRadius
                        )

                        // 1b. AutoMix Transition Window Marker
                        if (transitionWindow != null) {
                            val winStartPx = (canvasWidth * transitionWindow.startFraction).coerceIn(0f, canvasWidth)
                            val winEndPx = (canvasWidth * transitionWindow.endFraction).coerceIn(winStartPx, canvasWidth)
                            val winWidthPx = winEndPx - winStartPx
                            if (winWidthPx > 0f) {
                                clipPath(capsulePath) {
                                    drawRoundRect(
                                        color = activeTrackColor.copy(alpha = 0.38f),
                                        topLeft = Offset(winStartPx, topOffset),
                                        size = Size(winWidthPx, trackHeightPx),
                                        cornerRadius = cornerRadius
                                    )
                                }
                            }
                        }

                        // 2. Active Track (Clipped inside capsule path with smooth rounded tip)
                        val filledProgressPx = (progressPx * fillFactor).coerceIn(0f, canvasWidth)
                        if (filledProgressPx > 0f) {
                            clipPath(capsulePath) {
                                drawRoundRect(
                                    color = activeTrackColor,
                                    topLeft = Offset(0f, topOffset),
                                    size = Size(filledProgressPx, trackHeightPx),
                                    cornerRadius = cornerRadius
                                )
                            }
                        }

                        // 3. Apple-style Vertical Pill Grab Indicator on Drag
                        if (thumbScale > 0.01f) {
                            val scaledThumbWidth = thumbWidthPx * thumbScale
                            val scaledThumbHeight = thumbHeightPx * thumbScale
                            val thumbTop = (canvasHeight - scaledThumbHeight) / 2f
                            val thumbLeft = (progressPx - scaledThumbWidth / 2f).coerceIn(
                                trackHeightPx / 4f,
                                canvasWidth - trackHeightPx / 4f - scaledThumbWidth
                            )
                            val pillCorner = CornerRadius(scaledThumbWidth / 2f, scaledThumbWidth / 2f)

                            // Soft ambient drop shadow
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.28f * thumbScale),
                                topLeft = Offset(thumbLeft - shadowOffsetX, thumbTop + shadowOffsetY),
                                size = Size(scaledThumbWidth + shadowExtraWidth, scaledThumbHeight),
                                cornerRadius = pillCorner
                            )

                            // Main tactile vertical pill handle
                            drawRoundRect(
                                color = thumbColor,
                                topLeft = Offset(thumbLeft, thumbTop),
                                size = Size(scaledThumbWidth, scaledThumbHeight),
                                cornerRadius = pillCorner
                            )
                        }
                    }
                }
        )

        AnimatedVisibility(
            visible = mixing,
            enter = fadeIn(tween(durationMillis = 450, easing = FastOutSlowInEasing)) +
                scaleIn(
                    animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ),
            exit = fadeOut(tween(durationMillis = 450, easing = FastOutSlowInEasing)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        ) {
            MixSheen(
                trackHeight = currentTrackHeight,
                color = activeTrackColor
            )
        }
    }
}

@Composable
private fun MixSheen(trackHeight: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "appleMusicMixSheen")
    val phase by transition.animateFloat(
        initialValue = -0.34f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "appleMusicMixSheenPhase",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        val band = size.width * 0.34f
        val x = phase * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to color,
                    1f to Color.Transparent,
                ),
                startX = x,
                endX = x + band,
            ),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}
