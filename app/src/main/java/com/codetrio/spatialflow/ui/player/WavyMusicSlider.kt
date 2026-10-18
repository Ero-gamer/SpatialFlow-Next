package com.codetrio.spatialflow.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlin.math.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import kotlin.math.roundToInt

/**
 * A highly optimized, custom Bezier curve wavy slider.
 * Flattens out automatically during user scrub operations to aid tracking visual precision.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun WavyMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 10.dp,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    waveAmplitudeWhenPlaying: Dp = 2.5.dp,
    waveLength: Dp = 48.dp,
    waveAnimationDuration: Int = 2200,
    hideInactiveTrackPortion: Boolean = true,
    isPlaying: Boolean = true,
    thumbLineHeightWhenInteracting: Dp = 20.dp,
    isWaveEligible: Boolean = true,
    semanticsLabel: String? = null,
    semanticsProgressStep: Float = 0.01f,
    transitionWindow: com.codetrio.spatialflow.service.automix.AutoMixTransitionWindow? = null,
    mixing: Boolean = false
) {
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val fillFactor by animateFloatAsState(
        targetValue = if (mixing) 0f else 1f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "WavyFillFactor"
    )

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting && !mixing) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )

    val shouldShowWave = isWaveEligible && isPlaying && !isInteracting

    val animatedWaveAmplitude by animateDpAsState(
        targetValue = if (shouldShowWave) waveAmplitudeWhenPlaying else 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "WaveAmplitudeAnim"
    )

    // Infinitely shifting phase when active
    val phaseShiftAnim = remember { Animatable(0f) }



    LaunchedEffect(shouldShowWave, waveAnimationDuration) {
        if (shouldShowWave && waveAnimationDuration > 0) {
            val fullRotation = (2 * PI).toFloat()
            while (shouldShowWave) {
                val start = (phaseShiftAnim.value % fullRotation).let { if (it < 0f) it + fullRotation else it }
                phaseShiftAnim.snapTo(start)
                phaseShiftAnim.animateTo(
                    targetValue = start + fullRotation,
                    animationSpec = tween(durationMillis = waveAnimationDuration, easing = LinearEasing)
                )
            }
        }
    }

    val trackHeightPx = with(LocalDensity.current) { trackHeight.toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
    val waveAmplitudePxInternal = with(LocalDensity.current) { animatedWaveAmplitude.toPx() }
    val waveLengthPx = with(LocalDensity.current) { waveLength.toPx() }
    val waveFrequency = if (waveLengthPx > 0f) ((2 * PI) / waveLengthPx).toFloat() else 0f
    
    val thumbLineHeightPxInternal = with(LocalDensity.current) { thumbLineHeightWhenInteracting.toPx() }
    val thumbGapPx = with(LocalDensity.current) { 4.dp.toPx() }

    val wavePath = remember { Path() }

    val sliderVisualHeight = remember(trackHeight, thumbRadius, thumbLineHeightWhenInteracting) {
        max(trackHeight * 2, max(thumbRadius * 2, thumbLineHeightWhenInteracting) + 8.dp)
    }
    val activeGradientColors = remember(activeTrackColor) {
        listOf(activeTrackColor.copy(alpha = 0.35f), activeTrackColor)
    }

    val hapticFeedback = LocalHapticFeedback.current
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val normalizedValue = if (valueRange.endInclusive == valueRange.start) 0f
        else ((clampedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    // Smoothly animate seeking/playback progress using M3 Expressive Motion physics
    val animatedNormalizedValue by animateFloatAsState(
        targetValue = normalizedValue,
        animationSpec = if (normalizedValue == 0f) {
            // Material 3 Slow Spatial spring (Stiffness: 200f, Damping: 0.75f) for hero song transitions
            spring(
                dampingRatio = 0.75f,
                stiffness = 200f
            )
        } else {
            // Material 3 Default Effects spring (Stiffness: 380f, Damping: 1.0f) for linear playback interpolation
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 380f
            )
        },
        label = "ProgressValueAnim"
    )
    val renderNormalizedValue = if (isInteracting) normalizedValue else animatedNormalizedValue

    val safeSemanticsStep = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticNormalizedValue = remember(normalizedValue, safeSemanticsStep) {
        ((normalizedValue / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
    }
    val semanticSliderValue = remember(semanticNormalizedValue, valueRange) {
        valueRange.start + semanticNormalizedValue * (valueRange.endInclusive - valueRange.start)
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val lastHapticStep = remember { mutableIntStateOf(-1) }

        Slider(
            value = clampedValue,
            onValueChange = { newValue ->
                val normalizedNew = if (valueRange.endInclusive == valueRange.start) 0f
                    else ((newValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                
                // Quantum trigger for drag vibration ticks
                val currentStep = (normalizedNew * 50f).roundToInt()
                if (currentStep != lastHapticStep.intValue) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticStep.intValue = currentStep
                }
                onValueChange(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
                .clearAndSetSemantics {
                    if (!semanticsLabel.isNullOrBlank()) {
                        contentDescription = semanticsLabel
                    }
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = semanticSliderValue,
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
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
                .drawWithCache {
                    val canvasWidth = size.width
                    val localCenterY = size.height / 2f
                    val localTrackStart = thumbRadiusPx
                    val localTrackEnd = canvasWidth - thumbRadiusPx
                    val localTrackWidth = (localTrackEnd - localTrackStart).coerceAtLeast(0f)
                    val taperDistancePx = 64.dp.toPx()

                    onDrawWithContent {
                        val effectiveNormProgress = (renderNormalizedValue * fillFactor).coerceIn(0f, 1f)
                        val currentProgressPxEndVisual = localTrackStart + localTrackWidth * effectiveNormProgress
                        
                        // 1. Draw Inactive Track
                        if (hideInactiveTrackPortion) {
                            if (currentProgressPxEndVisual < localTrackEnd) {
                                drawLine(
                                    color = inactiveTrackColor,
                                    start = Offset(currentProgressPxEndVisual, localCenterY),
                                    end = Offset(localTrackEnd, localCenterY),
                                    strokeWidth = trackHeightPx,
                                    cap = StrokeCap.Round
                                )
                            }
                        } else {
                            drawLine(
                                color = inactiveTrackColor,
                                start = Offset(localTrackStart, localCenterY),
                                end = Offset(localTrackEnd, localCenterY),
                                strokeWidth = trackHeightPx,
                                cap = StrokeCap.Round
                            )
                        }

                        // 1b. AutoMix Transition Window Marker
                        if (transitionWindow != null) {
                            val winStartPx = (localTrackStart + localTrackWidth * transitionWindow.startFraction).coerceIn(localTrackStart, localTrackEnd)
                            val winEndPx = (localTrackStart + localTrackWidth * transitionWindow.endFraction).coerceIn(winStartPx, localTrackEnd)
                            if (winEndPx > winStartPx) {
                                drawLine(
                                    color = activeTrackColor.copy(alpha = 0.40f),
                                    start = Offset(winStartPx, localCenterY),
                                    end = Offset(winEndPx, localCenterY),
                                    strokeWidth = trackHeightPx * 1.5f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        // 2. Draw Active Track (Sinusoidal Wave / Line) with Spatial Gradient
                        if (effectiveNormProgress > 0.001f) {
                            val activeTrackVisualEnd = currentProgressPxEndVisual - (thumbGapPx * thumbInteractionFraction)

                            val activeGradient = Brush.linearGradient(
                                colors = activeGradientColors,
                                start = Offset(localTrackStart, localCenterY),
                                end = Offset(activeTrackVisualEnd.coerceAtLeast(localTrackStart), localCenterY)
                            )

                            if (waveAmplitudePxInternal > 0.01f && waveFrequency > 0f) {
                                wavePath.reset()
                                val waveStartDrawX = localTrackStart
                                val waveEndDrawX = activeTrackVisualEnd.coerceAtLeast(waveStartDrawX)
                                if (waveEndDrawX > waveStartDrawX) {
                                    val phaseShift = phaseShiftAnim.value
                                    val periodPx = ((2 * PI) / waveFrequency).toFloat()
                                    val samplesPerCycle = 10f
                                    val waveStep = (periodPx / samplesPerCycle)
                                        .coerceAtLeast(4.0f)

                                    fun yAt(x: Float): Float {
                                        val s = sin(waveFrequency * x + phaseShift)
                                        val taper = ((x - waveStartDrawX) / taperDistancePx).coerceIn(0f, 1f)
                                        val finalAmplitude = waveAmplitudePxInternal * taper
                                        return localCenterY + finalAmplitude * s
                                    }

                                    wavePath.moveTo(waveStartDrawX, yAt(waveStartDrawX))

                                    var x = waveStartDrawX + waveStep
                                    while (x < waveEndDrawX) {
                                        wavePath.lineTo(x, yAt(x))
                                        x += waveStep
                                    }
                                    wavePath.lineTo(waveEndDrawX, yAt(waveEndDrawX))

                                    drawPath(
                                        path = wavePath,
                                        brush = activeGradient,
                                        style = Stroke(
                                            width = trackHeightPx,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round,
                                            miter = 1f
                                        )
                                    )
                                }
                            } else {
                                if (activeTrackVisualEnd > localTrackStart) {
                                    drawLine(
                                        brush = activeGradient,
                                        start = Offset(localTrackStart, localCenterY),
                                        end = Offset(activeTrackVisualEnd, localCenterY),
                                        strokeWidth = trackHeightPx,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        // 3. Draw Sleek Hollow Halo Ring / Capsule-morphing Thumb
                        if (fillFactor > 0.01f) {
                            val currentThumbCenterX = localTrackStart + localTrackWidth * renderNormalizedValue
                            
                            fun fastLerp(start: Float, stop: Float, fraction: Float): Float {
                                return start + (stop - start) * fraction
                            }

                            val thumbCurrentWidthPx = fastLerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, thumbInteractionFraction) * fillFactor
                            val thumbCurrentHeightPx = fastLerp(thumbRadiusPx * 2f, thumbLineHeightPxInternal, thumbInteractionFraction) * fillFactor

                            drawRoundRect(
                                color = thumbColor,
                                topLeft = Offset(
                                    currentThumbCenterX - thumbCurrentWidthPx / 2f,
                                    localCenterY - thumbCurrentHeightPx / 2f
                                ),
                                size = Size(thumbCurrentWidthPx, thumbCurrentHeightPx),
                                cornerRadius = CornerRadius(thumbCurrentWidthPx / 2f)
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
            WavyMixSheen(
                trackHeight = trackHeight,
                color = activeTrackColor
            )
        }
    }
}

@Composable
private fun WavyMixSheen(trackHeight: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "wavyMixSheen")
    val phase by transition.animateFloat(
        initialValue = -0.34f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wavyMixSheenPhase",
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
