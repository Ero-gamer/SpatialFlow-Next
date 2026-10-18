package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Tonal palette helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun Color.shiftTonal(lightnessDelta: Float, saturationScale: Float = 1f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red   * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue  * 255).toInt().coerceIn(0, 255),
        hsv,
    )
    hsv[1] = (hsv[1] * saturationScale).coerceIn(0.15f, 1f)
    hsv[2] = (hsv[2] + lightnessDelta).coerceIn(0.15f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// ─────────────────────────────────────────────────────────────────────────────
// Pure Math Functions (Hoisted to prevent per-frame allocation)
// ─────────────────────────────────────────────────────────────────────────────

private fun smootherstep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * c * (c * (c * 6f - 15f) + 10f)
}

/**
 * Populates volumetric wave path with a true semicircle round left cap.
 * Guarantees the left corner is ALWAYS smooth and 100% rounded without allocating objects.
 */
private fun populateFluidWave(
    filled: Path,
    arcRect: Rect,
    wavelength: Float,
    amplitude: Float,
    phase: Float,
    trackStart: Float,
    thumbX: Float,
    topY: Float,
    bottomY: Float,
    transitionLengthPx: Float,
    waveSampleStepPx: Float
) {
    filled.reset()
    // 1. Start at bottom of left round cap
    filled.moveTo(trackStart, bottomY)
    // 2. True semicircle arc for the left rounded cap: 90° (bottom) to 270° (top)
    filled.arcTo(
        rect = arcRect,
        startAngleDegrees = 90f,
        sweepAngleDegrees = 180f,
        forceMoveTo       = false
    )

    // 3. Sample the fluid wave from trackStart to thumbX
    var x = trackStart
    val waveSpan = thumbX - trackStart
    val effectiveTransition = minOf(transitionLengthPx, waveSpan * 0.48f)
    val invTransition       = if (effectiveTransition > 0f) 1f / effectiveTransition else 0f
    val invWavelength2Pi    = (2 * PI / wavelength).toFloat()

    while (x < thumbX) {
        val relX       = x - trackStart
        val startEnv   = if (invTransition > 0f) smootherstep(relX * invTransition) else 1f
        val endEnv     = if (invTransition > 0f) smootherstep((thumbX - x) * invTransition) else 1f
        val envelope   = startEnv * endEnv

        val angle      = relX * invWavelength2Pi - phase
        val harmonic   = sin(angle * 1.65f + phase * 0.55f) * 0.16f
        val waveFactor = (0.5f + 0.38f * sin(angle) + harmonic).coerceIn(0f, 1f)
        val waveHeight = waveFactor * amplitude * envelope
        val y          = topY - waveHeight

        filled.lineTo(x, y)
        x += waveSampleStepPx
    }

    // 4. Close path cleanly down to bottomY and back to trackStart
    filled.lineTo(thumbX, topY)
    filled.lineTo(thumbX, bottomY)
    filled.lineTo(trackStart, bottomY)
    filled.close()
}

private class SpatialWaveDrawCache {
    var lastThumbX: Float = -1000f
    var cachedActiveGradient: Brush? = null
    var cachedLayer1Brush: Brush? = null
    var cachedLayer2Brush: Brush? = null
    var cachedLayer3Brush: Brush? = null
    var cachedOuterGlowBrush: Brush? = null
    var cachedInnerGlowBrush: Brush? = null
    var cachedArcRect: Rect? = null
}

// ─────────────────────────────────────────────────────────────────────────────
// Expressive Volumetric Spatial Wave Seekbar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * SpatialFlow Material Expressive Volumetric Fluid Wave Seekbar:
 *
 *  • Continuous monotonic wave animation without resets or jumps.
 *  • Left corner is physically formed by a true semicircle arc — always 100% rounded.
 *  • Tactile seek shockwave ripple expanding from the exact seek position.
 *  • Wave collapses smoothly to 0 on pause with spring physics into a sleek M3 progress bar.
 *  • Zero wave computation when paused.
 *  • 100% volumetric filled fluid waves without stroke outlines.
 *  • Zero-allocation draw phase: reused paths, hoisted math, memoized color lists.
 *
 * @param value                  current playback position in [0, 1]
 * @param onValueChange          called during drag with the new [0, 1] position
 * @param onValueChangeFinished  called when the user lifts their finger or taps
 * @param isPlaying              drives the fluid wave phase animation and amplitude
 * @param activeColor            primary accent color (artwork-derived or theme)
 * @param inactiveColor          inactive track color
 */
@Composable
fun SpatialWaveMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    isPlaying: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    semanticsLabel: String = "Playback progress",
    transitionWindow: com.codetrio.spatialflow.service.automix.AutoMixTransitionWindow? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()

    var dragValue by remember { mutableFloatStateOf(value) }
    val displayValue = if (dragging) dragValue else value

    // Track exact position where user touched/seeked for ripple origin
    val lastInteractedFraction = remember { mutableFloatStateOf(value) }

    // ── Tonal palette derived from activeColor (3 fluid roles) ───────────────
    val layer1Light = remember(activeColor) { activeColor.shiftTonal(+0.22f, 0.70f) }
    val layer1Dark  = remember(activeColor) { activeColor.shiftTonal(-0.18f, 1.10f) }
    val layer2Dark  = remember(activeColor) { activeColor.shiftTonal(-0.16f, 1.30f) }
    val layer2Light = remember(activeColor) { activeColor.shiftTonal(+0.18f, 0.85f) }
    val layer3Light = remember(activeColor) { activeColor.shiftTonal(+0.20f, 0.90f) }
    val layer3Dark  = remember(activeColor) { activeColor.shiftTonal(-0.14f, 1.35f) }

    // Memoized color lists to eliminate allocations in onDraw
    val layer1Colors = remember(layer1Light, layer1Dark) {
        listOf(layer1Light.copy(alpha = 0.38f), layer1Dark.copy(alpha = 0.26f))
    }
    val layer2Colors = remember(layer2Dark, layer2Light) {
        listOf(layer2Dark.copy(alpha = 0.52f), layer2Light.copy(alpha = 0.42f))
    }
    val layer3Colors = remember(layer3Light, layer3Dark) {
        listOf(layer3Light.copy(alpha = 0.88f), layer3Dark.copy(alpha = 0.80f))
    }
    val activeGradientColors = remember(layer3Light, layer3Dark) {
        listOf(layer3Light, layer3Dark)
    }

    val outerGlowDraggingColors = remember(activeColor) {
        listOf(activeColor.copy(alpha = 0.65f), activeColor.copy(alpha = 0.35f), activeColor.copy(alpha = 0.12f), Color.Transparent)
    }
    val outerGlowRestingColors = remember(activeColor) {
        listOf(activeColor.copy(alpha = 0.45f), activeColor.copy(alpha = 0.22f), activeColor.copy(alpha = 0.06f), Color.Transparent)
    }
    val innerGlowDraggingColors = remember(activeColor) {
        listOf(activeColor.copy(alpha = 0.85f), activeColor.copy(alpha = 0.45f), Color.Transparent)
    }
    val innerGlowRestingColors = remember(activeColor) {
        listOf(activeColor.copy(alpha = 0.70f), activeColor.copy(alpha = 0.30f), Color.Transparent)
    }

    // ── Wave amplitude smoothly animates to 0 on pause/scrub ────────────────
    val waveAmplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying && !dragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "WaveAmplitudeFactor"
    )

    // ── Monotonic continuous phase clock (NO resets, NO jumps) ───────────────
    var phaseOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos) * 1e-9f
                    // Continuously increment phase fast and smoothly; wrap gently over large period
                    phaseOffset = (phaseOffset + dt * 4.2f)
                    if (phaseOffset > 20000f) {
                        phaseOffset -= 20000f
                    }
                }
                lastNanos = frameNanos
            }
        }
    }

    // ── Expressive Progress Animation with Hero Reset Recoil ────────────────
    val animatedProgress by animateFloatAsState(
        targetValue = displayValue,
        animationSpec = if (displayValue <= 0.005f) {
            // Material 3 Slow Spatial recoil spring (damping: 0.75f, stiffness: 200f) for resets
            spring(
                dampingRatio = 0.75f,
                stiffness = 200f
            )
        } else {
            // Responsive smooth playback progress tracking
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 450f
            )
        },
        label = "ExpressiveProgressAnim"
    )
    val renderProgress = if (dragging) displayValue else animatedProgress

    // ── Tactile Seek Shockwave Ripple Effect ─────────────────────────────────
    val rippleAnim = remember { Animatable(0f) }
    val rippleOriginFraction = remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val triggerSeekRipple: (Float) -> Unit = { fraction ->
        rippleOriginFraction.floatValue = fraction
        coroutineScope.launch {
            rippleAnim.snapTo(0f)
            rippleAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
            )
        }
    }

    // ── Reset ripple trigger when track restarts or resets to 0 ──────────────
    var previousValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) {
        if (value <= 0.015f && previousValue > 0.08f) {
            // Track reset detected: clean phase restart & tactile ripple at origin
            phaseOffset = 0f
            triggerSeekRipple(0f)
        }
        previousValue = value
    }

    // ── Thumb spring expansion (M3 Expressive feel) ─────────────────────────
    val thumbRadiusNormal = 7.5.dp
    val thumbRadiusDrag   = 9.5.dp
    val thumbRadius by animateDpAsState(
        targetValue = if (dragging) thumbRadiusDrag else thumbRadiusNormal,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "ThumbRadiusSpring",
    )

    // ── Material Expressive geometry constants ──────────────────────────────
    val density = LocalDensity.current
    val baseAmp1Px           = with(density) { 13.0.dp.toPx() }
    val baseAmp2Px           = with(density) { 10.0.dp.toPx() }
    val baseAmp3Px           = with(density) {  7.5.dp.toPx() }
    val waveLength1Px        = with(density) { 150.dp.toPx() }
    val waveLength2Px        = with(density) { 115.dp.toPx() }
    val waveLength3Px        = with(density) {  85.dp.toPx() }
    val baseTrackThicknessPx = with(density) {   6.0.dp.toPx() }   // M3 Expressive pill thickness
    val transitionLengthPx   = with(density) {  42.dp.toPx() }
    val waveSampleStepPx     = with(density) {   1.5.dp.toPx() }

    // Reusable Path caches
    val pathFilled1 = remember { Path() }
    val pathFilled2 = remember { Path() }
    val pathFilled3 = remember { Path() }

    val outerGlowRadiusPx = remember(density, dragging) { with(density) { (if (dragging) 28.dp else 22.dp).toPx() } }
    val innerGlowRadiusPx = remember(density, dragging) { with(density) { (if (dragging) 15.dp else 12.dp).toPx() } }

    val drawCache = remember { SpatialWaveDrawCache() }

    val semanticProgress = ((value / 0.01f).toInt() * 0.01f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w         = size.width
            val h         = size.height
            val centerY   = h / 2f
            val halfThick = baseTrackThicknessPx / 2f
            val bottomY   = centerY + halfThick
            val topY      = centerY - halfThick

            // Usable track range: from halfThick to w - halfThick
            val trackStart = halfThick
            val trackEnd   = w - halfThick
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val thumbX     = trackStart + trackWidth * renderProgress.coerceIn(0f, 1f)
            val thumbCenter = Offset(thumbX, centerY)

            val currentThumbRadiusPx = with(density) { thumbRadius.toPx() }

            // ── 1. Inactive Material Expressive rounded capsule track ───────
            drawLine(
                color       = inactiveColor,
                start       = Offset(trackStart, centerY),
                end         = Offset(trackEnd, centerY),
                strokeWidth = baseTrackThicknessPx,
                cap         = StrokeCap.Round,
            )

            // ── 1b. AutoMix Transition Window Marker ─────────────────────────
            transitionWindow?.let { window ->
                val winStartX = trackStart + trackWidth * window.startFraction.coerceIn(0f, 1f)
                val winEndX = trackStart + trackWidth * window.endFraction.coerceIn(0f, 1f)
                if (winEndX > winStartX) {
                    val markerColor = activeColor.copy(alpha = 0.50f)
                    drawLine(
                        color = markerColor,
                        start = Offset(winStartX, centerY),
                        end = Offset(winEndX, centerY),
                        strokeWidth = baseTrackThicknessPx * 1.25f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // ── 2. Active region: Waves when playing; flat line when paused ─
            if (thumbX > trackStart) {
                val ampFactor = waveAmplitudeFactor
                
                // Cache brushes across frames when thumb movement is sub-pixel (< 0.5px)
                val thumbMoved = kotlin.math.abs(thumbX - drawCache.lastThumbX) >= 0.5f
                val activeGradient = if (thumbMoved || drawCache.cachedActiveGradient == null) {
                    drawCache.lastThumbX = thumbX
                    val grad = Brush.horizontalGradient(
                        colors = activeGradientColors,
                        startX = trackStart,
                        endX   = thumbX.coerceAtLeast(trackStart + 1f),
                    )
                    drawCache.cachedActiveGradient = grad
                    drawCache.cachedLayer1Brush = Brush.horizontalGradient(
                        colors = layer1Colors,
                        startX = trackStart, endX = thumbX.coerceAtLeast(trackStart + 1f),
                    )
                    drawCache.cachedLayer2Brush = Brush.horizontalGradient(
                        colors = layer2Colors,
                        startX = trackStart, endX = thumbX.coerceAtLeast(trackStart + 1f),
                    )
                    drawCache.cachedLayer3Brush = Brush.horizontalGradient(
                        colors = layer3Colors,
                        startX = trackStart, endX = thumbX.coerceAtLeast(trackStart + 1f),
                    )
                    drawCache.cachedOuterGlowBrush = Brush.radialGradient(
                        colors = if (dragging) outerGlowDraggingColors else outerGlowRestingColors,
                        center = thumbCenter,
                        radius = outerGlowRadiusPx
                    )
                    drawCache.cachedInnerGlowBrush = Brush.radialGradient(
                        colors = if (dragging) innerGlowDraggingColors else innerGlowRestingColors,
                        center = thumbCenter,
                        radius = innerGlowRadiusPx
                    )
                    grad
                } else {
                    drawCache.cachedActiveGradient ?: Brush.horizontalGradient(
                        colors = activeGradientColors,
                        startX = trackStart,
                        endX   = thumbX.coerceAtLeast(trackStart + 1f),
                    )
                }

                if (ampFactor > 0.005f) {
                    val currentPhase = phaseOffset
                    val phase1 = currentPhase
                    val phase2 = currentPhase * 1.35f + 1.2f
                    val phase3 = currentPhase * 1.75f + 2.4f

                    val amp1 = baseAmp1Px * ampFactor
                    val amp2 = baseAmp2Px * ampFactor
                    val amp3 = baseAmp3Px * ampFactor

                    val currentArcRect = drawCache.cachedArcRect
                    val arcRect = if (currentArcRect == null || currentArcRect.bottom != bottomY) {
                        val r = Rect(
                            left   = 0f,
                            top    = topY,
                            right  = baseTrackThicknessPx,
                            bottom = bottomY
                        )
                        drawCache.cachedArcRect = r
                        r
                    } else {
                        currentArcRect
                    }

                    populateFluidWave(pathFilled1, arcRect, waveLength1Px, amp1, phase1, trackStart, thumbX, topY, bottomY, transitionLengthPx, waveSampleStepPx)
                    populateFluidWave(pathFilled2, arcRect, waveLength2Px, amp2, phase2, trackStart, thumbX, topY, bottomY, transitionLengthPx, waveSampleStepPx)
                    populateFluidWave(pathFilled3, arcRect, waveLength3Px, amp3, phase3, trackStart, thumbX, topY, bottomY, transitionLengthPx, waveSampleStepPx)

                    // Layer 1: Ambient deep liquid fill (tallest wave)
                    drawPath(
                        path  = pathFilled1,
                        brush = drawCache.cachedLayer1Brush ?: activeGradient,
                    )

                    // Layer 2: Mid-depth harmonic fluid fill (counter-gradient)
                    drawPath(
                        path  = pathFilled2,
                        brush = drawCache.cachedLayer2Brush ?: activeGradient,
                    )

                    // Layer 3: Foreground vibrant liquid wave (volumetric solid fill)
                    drawPath(
                        path  = pathFilled3,
                        brush = drawCache.cachedLayer3Brush ?: activeGradient,
                    )

                    // Baseline Material Expressive rounded solid track (guarantees perfect rounded cap)
                    drawLine(
                        brush       = activeGradient,
                        start       = Offset(trackStart, centerY),
                        end         = thumbCenter,
                        strokeWidth = baseTrackThicknessPx,
                        cap         = StrokeCap.Round,
                    )
                } else {
                    // PAUSED / FLAT: Direct Material Expressive rounded line (0 wave math)
                    drawLine(
                        brush       = activeGradient,
                        start       = Offset(trackStart, centerY),
                        end         = thumbCenter,
                        strokeWidth = baseTrackThicknessPx,
                        cap         = StrokeCap.Round,
                    )
                }
            }

            // ── 3. Tactile Seek Ripple Effect ───────────────────────────────
            if (rippleAnim.value > 0.001f) {
                val rippleFraction  = rippleAnim.value
                val rippleOriginX   = trackStart + trackWidth * rippleOriginFraction.floatValue.coerceIn(0f, 1f)
                val maxRippleRadius = with(density) { 40.dp.toPx() }
                val currentRadius   = currentThumbRadiusPx + rippleFraction * (maxRippleRadius - currentThumbRadiusPx)
                val rippleAlpha     = (1f - rippleFraction).coerceIn(0f, 1f) * 0.65f

                // Outer expanding shockwave ring
                drawCircle(
                    color  = activeColor.copy(alpha = rippleAlpha),
                    radius = currentRadius,
                    center = Offset(rippleOriginX, centerY),
                    style  = Stroke(width = with(density) { (3.0.dp * (1f - rippleFraction * 0.5f)).toPx() })
                )

                // Secondary harmonic trailing ring
                if (rippleFraction > 0.10f) {
                    val secFraction = (rippleFraction - 0.10f) / 0.90f
                    val secRadius   = currentThumbRadiusPx + secFraction * (maxRippleRadius * 0.72f - currentThumbRadiusPx)
                    val secAlpha    = (1f - secFraction).coerceIn(0f, 1f) * 0.35f
                    drawCircle(
                        color  = activeColor.copy(alpha = secAlpha),
                        radius = secRadius,
                        center = Offset(rippleOriginX, centerY),
                        style  = Stroke(width = with(density) { (1.8.dp * (1f - secFraction)).toPx() })
                    )
                }

                // Inner soft ambient splash
                drawCircle(
                    color  = activeColor.copy(alpha = rippleAlpha * 0.20f),
                    radius = currentRadius * 0.6f,
                    center = Offset(rippleOriginX, centerY),
                )
            }

            // ── 4. Expressive Thumb with Spread Blurred Glow & White Stroke ──
            drawCache.cachedOuterGlowBrush?.let { outerGlow ->
                drawCircle(
                    brush  = outerGlow,
                    radius = outerGlowRadiusPx,
                    center = thumbCenter,
                )
            }

            drawCache.cachedInnerGlowBrush?.let { innerGlow ->
                drawCircle(
                    brush  = innerGlow,
                    radius = innerGlowRadiusPx,
                    center = thumbCenter,
                )
            }

            // 3. Solid expressive accent thumb core (Primary color)
            drawCircle(
                color  = activeColor,
                radius = currentThumbRadiusPx,
                center = thumbCenter,
            )

            // 4. Delicate crisp white stroke rim
            drawCircle(
                color  = Color.White.copy(alpha = 0.95f),
                radius = currentThumbRadiusPx,
                center = thumbCenter,
                style  = Stroke(width = with(density) { 1.5.dp.toPx() }),
            )
        }

        // Invisible Slider — handles touch, drag, and accessibility
        Slider(
            value    = displayValue,
            onValueChange = { new ->
                dragValue = new
                lastInteractedFraction.floatValue = new
                onValueChange(new)
            },
            onValueChangeFinished = {
                triggerSeekRipple(lastInteractedFraction.floatValue)
                onValueChangeFinished?.invoke()
            },
            valueRange = 0f..1f,
            interactionSource = interactionSource,
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .clearAndSetSemantics {
                    contentDescription = semanticsLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = semanticProgress,
                        range   = 0f..1f,
                        steps   = 0,
                    )
                    setProgress { req ->
                        val coerced = req.coerceIn(0f, 1f)
                        lastInteractedFraction.floatValue = coerced
                        onValueChange(coerced)
                        triggerSeekRipple(coerced)
                        onValueChangeFinished?.invoke()
                        true
                    }
                },
            colors = SliderDefaults.colors(
                thumbColor         = Color.Transparent,
                activeTrackColor   = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

