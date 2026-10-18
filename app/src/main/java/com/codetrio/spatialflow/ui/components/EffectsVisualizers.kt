package com.codetrio.spatialflow.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin


// ─── Reverb: Dampened Waveform with Ambient Glow & Early Reflections ────────

@Composable
fun ReverbRoomVisualizer(
    presetIndex: Int, // 0 = None, 1 = Room, 2 = Hall, 3 = Stadium, 4 = Plate, 5 = Spring
    enabled: Boolean,
    speed: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    var rotationPhase by remember { mutableStateOf(0f) }
    LaunchedEffect(enabled, speed) {
        if (enabled) {
            val duration = (6000f / speed).toInt().coerceIn(1500, 15000)
            val startTime = System.currentTimeMillis()
            while (true) {
                withFrameNanos {
                    val elapsed = (System.currentTimeMillis() - startTime) % duration
                    rotationPhase = (elapsed.toFloat() / duration.toFloat()) * (2f * PI.toFloat())
                }
            }
        } else {
            rotationPhase = 0f
        }
    }

    // Morphing spring weights for each preset
    val roomWeight by animateFloatAsState(
        targetValue = if (enabled && presetIndex == 1) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "reverb_room_weight"
    )
    val hallWeight by animateFloatAsState(
        targetValue = if (enabled && presetIndex == 2) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "reverb_hall_weight"
    )
    val stadiumWeight by animateFloatAsState(
        targetValue = if (enabled && presetIndex == 3) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "reverb_stadium_weight"
    )
    val plateWeight by animateFloatAsState(
        targetValue = if (enabled && presetIndex == 4) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "reverb_plate_weight"
    )
    val springWeight by animateFloatAsState(
        targetValue = if (enabled && presetIndex == 5) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "reverb_spring_weight"
    )

    val density = LocalDensity.current
    val strokeWidth = with(density) { 1.5.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        val baseColor = if (enabled) primaryColor else onSurfaceVariant.copy(alpha = 0.38f)
        val phase = if (enabled) rotationPhase else 0f

        // Ambient radial background glow
        val totalActiveWeight = (roomWeight + hallWeight + stadiumWeight + plateWeight + springWeight).coerceIn(0f, 1f)
        if (totalActiveWeight > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.15f * totalActiveWeight), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = h * 0.5f
                ),
                radius = h * 0.5f,
                center = Offset(cx, cy)
            )
        }

        // ─── 1. SMALL ROOM: Isometric 3D Studio Chamber with Corner Absorption Nodes ─────
        if (roomWeight > 0.001f) {
            val scale = (0.7f + 0.3f * roomWeight) * (h * 0.35f)
            val boxAlpha = 0.85f * roomWeight

            // Corner 3D perspective points
            val corners = arrayOf(
                Offset(cx - scale * 1.1f, cy - scale * 0.6f),
                Offset(cx + scale * 1.1f, cy - scale * 0.6f),
                Offset(cx + scale * 1.1f, cy + scale * 0.6f),
                Offset(cx - scale * 1.1f, cy + scale * 0.6f),
                // Inner 3D depth wall points
                Offset(cx - scale * 0.6f, cy - scale * 0.35f),
                Offset(cx + scale * 0.6f, cy - scale * 0.35f),
                Offset(cx + scale * 0.6f, cy + scale * 0.35f),
                Offset(cx - scale * 0.6f, cy + scale * 0.35f)
            )

            // Outer front frame
            for (i in 0..3) {
                drawLine(
                    color = baseColor.copy(alpha = boxAlpha),
                    start = corners[i],
                    end = corners[(i + 1) % 4],
                    strokeWidth = strokeWidth * 1.2f
                )
                // Depth lines connecting front to back wall
                drawLine(
                    color = baseColor.copy(alpha = boxAlpha * 0.5f),
                    start = corners[i],
                    end = corners[i + 4],
                    strokeWidth = strokeWidth * 0.8f
                )
                // Inner back wall frame
                drawLine(
                    color = baseColor.copy(alpha = boxAlpha * 0.4f),
                    start = corners[i + 4],
                    end = corners[((i + 1) % 4) + 4],
                    strokeWidth = strokeWidth * 0.8f
                )
                // Acoustic dampening nodes at corners
                drawCircle(
                    color = primaryColor.copy(alpha = boxAlpha),
                    radius = 3.dp.toPx() * roomWeight,
                    center = corners[i]
                )
            }
        }

        // ─── 2. CONCERT HALL: 3D Parabolic Acoustic Cathedral Dome & Stage Ribs ──────────
        if (hallWeight > 0.001f) {
            val domeAlpha = 0.85f * hallWeight
            val ribCount = 8
            val maxRx = w * 0.35f * hallWeight
            val maxRy = h * 0.42f * hallWeight

            for (i in 0 until ribCount) {
                val progress = i.toFloat() / (ribCount - 1)
                val rx = maxRx * (0.3f + 0.7f * progress)
                val ry = maxRy * (0.3f + 0.7f * progress)
                val pulse = sin(phase * 2f + i * 0.5f) * 4f

                val domePath = Path().apply {
                    for (step in 0..60) {
                        val a = (step.toFloat() / 60f) * PI.toFloat() // Top half-arch dome
                        val x = cx + (rx + pulse) * cos(a)
                        val y = (cy + maxRy * 0.3f) - (ry + pulse) * sin(a)
                        if (step == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(
                    path = domePath,
                    color = secondaryColor.copy(alpha = domeAlpha * (0.3f + 0.7f * progress)),
                    style = Stroke(width = strokeWidth * (1f + progress * 0.5f))
                )
            }
            // Stage floor reflection baseline
            drawLine(
                color = secondaryColor.copy(alpha = domeAlpha * 0.6f),
                start = Offset(cx - maxRx, cy + maxRy * 0.35f),
                end = Offset(cx + maxRx, cy + maxRy * 0.35f),
                strokeWidth = strokeWidth * 1.5f
            )
        }

        // ─── 3. STADIUM: Open Arena Oval System with Expanding Wavefront Rings ───────────
        if (stadiumWeight > 0.001f) {
            val stadiumAlpha = 0.85f * stadiumWeight
            val ringCount = 5
            val rxBase = w * 0.38f * stadiumWeight
            val ryBase = h * 0.35f * stadiumWeight

            for (r in 0 until ringCount) {
                val wavePhase = (phase * 1.5f + r * 0.8f) % (2f * PI.toFloat())
                val expansion = 0.3f + 0.7f * (r.toFloat() / (ringCount - 1))
                val rx = rxBase * expansion
                val ry = ryBase * expansion
                val ringAlpha = stadiumAlpha * (0.2f + 0.8f * abs(sin(wavePhase)))

                val ringPath = Path().apply {
                    for (step in 0..80) {
                        val angle = (step.toFloat() / 80f) * (2 * PI.toFloat())
                        val x = cx + rx * cos(angle)
                        val y = cy + ry * sin(angle)
                        if (step == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(
                    path = ringPath,
                    color = tertiaryColor.copy(alpha = ringAlpha),
                    style = Stroke(width = strokeWidth * (1f + (1f - expansion)))
                )
            }
            // Radiating acoustic light beacons around arena perimeter
            for (b in 0 until 12) {
                val angle = (b.toFloat() / 12f) * (2 * PI).toFloat() + phase * 0.5f
                val bx = cx + rxBase * cos(angle)
                val by = cy + ryBase * sin(angle)
                drawCircle(
                    color = tertiaryColor.copy(alpha = stadiumAlpha * 0.7f),
                    radius = 2.5.dp.toPx() * stadiumWeight,
                    center = Offset(bx, by)
                )
            }
        }

        // ─── 4. PLATE: Floating 3D Metallic Matrix Lattice with Vibrating Tension Nodes ──
        if (plateWeight > 0.001f) {
            val plateAlpha = 0.85f * plateWeight
            val cols = 7
            val rows = 5
            val spacingX = (w * 0.55f * plateWeight) / (cols - 1)
            val spacingY = (h * 0.5f * plateWeight) / (rows - 1)
            val startX = cx - (cols - 1) * spacingX / 2f
            val startY = cy - (rows - 1) * spacingY / 2f

            val points = Array(rows) { r ->
                Array(cols) { c ->
                    val wave = sin(phase * 3f + (r + c) * 0.7f) * (4.dp.toPx() * plateWeight)
                    Offset(startX + c * spacingX, startY + r * spacingY + wave)
                }
            }

            // Draw lattice grid lines
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (c < cols - 1) {
                        drawLine(
                            color = primaryColor.copy(alpha = plateAlpha * 0.6f),
                            start = points[r][c],
                            end = points[r][c + 1],
                            strokeWidth = strokeWidth
                        )
                    }
                    if (r < rows - 1) {
                        drawLine(
                            color = primaryColor.copy(alpha = plateAlpha * 0.6f),
                            start = points[r][c],
                            end = points[r + 1][c],
                            strokeWidth = strokeWidth
                        )
                    }
                    drawCircle(
                        color = secondaryColor.copy(alpha = plateAlpha),
                        radius = 2.dp.toPx() * plateWeight,
                        center = points[r][c]
                    )
                }
            }
        }

        // ─── 5. SPRING: Vertical Dual Interlocking Springs (Accordion Bounce Effect) ─────
        if (springWeight > 0.001f) {
            val springAlpha = 0.85f * springWeight
            val totalCoils = 10
            val maxSpringHeight = h * 0.72f * springWeight
            val rx = 16.dp.toPx() * springWeight
            val ry = 7.dp.toPx() * springWeight
            val springSpacing = 42.dp.toPx() * springWeight

            val springCentersX = listOf(cx - springSpacing / 2f, cx + springSpacing / 2f)
            val totalSteps = 160

            springCentersX.forEachIndexed { sIdx, springCx ->
                val phaseDirection = if (sIdx == 0) 1f else -1f
                // Opposite phase accordion bounce animation
                val bouncePhase = phase * 4f * phaseDirection
                val dynamicHeight = maxSpringHeight * (0.82f + 0.18f * sin(bouncePhase))
                val startY = cy - dynamicHeight / 2f

                var prevPt: Offset? = null
                var prevAlpha = 0f
                var prevStroke = strokeWidth

                for (step in 0..totalSteps) {
                    val progress = step.toFloat() / totalSteps.toFloat()
                    
                    // Vertical compression / extension wave propagation along the spring
                    val waveCompression = sin(bouncePhase + progress * 8f) * 0.08f
                    val adjustedProgress = (progress + waveCompression).coerceIn(0f, 1f)
                    
                    val py = startY + adjustedProgress * dynamicHeight
                    val angle = progress * totalCoils * (2 * PI).toFloat() + phase * 3f * phaseDirection
                    
                    val px = springCx + rx * cos(angle)
                    val zDepth = sin(angle) // -1.0 (back) to +1.0 (front)

                    val segmentAlpha = springAlpha * (0.35f + 0.65f * ((zDepth + 1f) / 2f))
                    val segmentStroke = strokeWidth * (0.75f + 0.85f * ((zDepth + 1f) / 2f))
                    val currPt = Offset(px, py)

                    if (prevPt != null) {
                        val lineColor = if (sIdx == 0) primaryColor else secondaryColor
                        drawLine(
                            color = lineColor.copy(alpha = (segmentAlpha + prevAlpha) / 2f),
                            start = prevPt,
                            end = currPt,
                            strokeWidth = (segmentStroke + prevStroke) / 2f
                        )
                    }

                    // Acoustic spark nodes on front coil peaks
                    if (zDepth > 0.92f && step % 12 == 0) {
                        drawCircle(
                            color = if (sIdx == 0) primaryColor else tertiaryColor,
                            radius = 2.5.dp.toPx() * springWeight,
                            center = currPt
                        )
                    }

                    prevPt = currPt
                    prevAlpha = segmentAlpha
                    prevStroke = segmentStroke
                }

                // Connecting top and bottom mounting plates for realism
                drawLine(
                    color = baseColor.copy(alpha = springAlpha * 0.7f),
                    start = Offset(springCx - rx, startY),
                    end = Offset(springCx + rx, startY),
                    strokeWidth = strokeWidth * 1.5f
                )
                drawLine(
                    color = baseColor.copy(alpha = springAlpha * 0.7f),
                    start = Offset(springCx - rx, startY + dynamicHeight),
                    end = Offset(springCx + rx, startY + dynamicHeight),
                    strokeWidth = strokeWidth * 1.5f
                )
            }
        }

        // ─── 0. RESTING / OFF: Subtle Ambient Pulse Wave ──────────────────────────────
        if (totalActiveWeight < 0.1f) {
            val restingAlpha = (1f - totalActiveWeight * 10f).coerceIn(0f, 1f) * 0.35f
            val pulseRadius = (w * 0.2f) + sin(phase * 2f) * 5.dp.toPx()
            drawCircle(
                color = baseColor.copy(alpha = restingAlpha),
                radius = pulseRadius,
                center = Offset(cx, cy),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

// ─── Loudness Enhancer: Arc Gauge with Pulse + dB Readout ───────────────────

@Composable
fun LoudnessRingIndicator(
    gain: Float, // 0f..12f
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val coolTeal = MaterialTheme.colorScheme.primary
    val warmAmber = MaterialTheme.colorScheme.tertiary
    val dangerRed = MaterialTheme.colorScheme.error
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val scrimColor = MaterialTheme.colorScheme.scrim
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    val animatedGain by animateFloatAsState(
        targetValue = gain,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "loudness_gain"
    )

    val fraction = (animatedGain / 12f).coerceIn(0f, 1f)

    var jitterPhase by remember { mutableStateOf(0f) }
    LaunchedEffect(enabled, gain) {
        if (enabled && gain > 8f) {
            val duration = 150
            val startTime = System.currentTimeMillis()
            while (true) {
                withFrameNanos {
                    val elapsed = (System.currentTimeMillis() - startTime) % duration
                    jitterPhase = (elapsed.toFloat() / duration.toFloat()) * (2f * PI.toFloat())
                }
            }
        } else {
            jitterPhase = 0f
        }
    }

    // Needle jitter scales up past 8 dB
    val jitter = if (enabled && gain > 8f) {
        val intensity = ((gain - 8f) / 4f).coerceIn(0f, 1f)
        sin(jitterPhase * 6f) * 2.2f * intensity
    } else 0f

    // dB text readout
    val textMeasurer = rememberTextMeasurer()
    val dbText = if (gain > 0f) "+${gain.toInt()} dB" else "0 dB"
    val textStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    val textColor = if (enabled) lerp(coolTeal, dangerRed, fraction) else onSurfaceVariant.copy(alpha = 0.4f)

    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 6.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height * 0.65f
        val radius = size.height * 0.56f
        val arcRect = Size(radius * 2, radius * 2)
        val topLeft = Offset(cx - radius, cy - radius)

        // Pivot center for needle (matching arc center)
        val pivotX = cx
        val pivotY = cy

        // 1. Draw Backplate Glow (Warm vacuum tube ambiance)
        if (enabled) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lerp(coolTeal, dangerRed, fraction).copy(alpha = 0.08f + fraction * 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = radius * 1.2f
                ),
                radius = radius * 1.2f,
                center = Offset(cx, cy)
            )
        }

        // 2. Draw Backplate Arc Track (200° sweep)
        drawArc(
            color = onSurfaceVariant.copy(alpha = 0.1f),
            startAngle = 170f,
            sweepAngle = 200f,
            useCenter = false,
            topLeft = topLeft,
            size = arcRect,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )

        // 3. Draw Radial Scale Ticks
        val tickCount = 13
        for (i in 0 until tickCount) {
            val tickAngle = 170f + i * (200f / (tickCount - 1))
            val angleRad = Math.toRadians(tickAngle.toDouble())
            
            val isWarningTick = i >= 10
            val tickColor = when {
                !enabled -> onSurfaceVariant.copy(alpha = 0.2f)
                isWarningTick -> dangerRed.copy(alpha = 0.8f)
                else -> coolTeal.copy(alpha = 0.7f)
            }

            // Radial line coordinates
            val innerR = radius - 4.dp.toPx()
            val outerR = radius + (if (i % 3 == 0) 4.dp.toPx() else 1.5.dp.toPx())
            
            val tickStartX = cx + innerR * cos(angleRad).toFloat()
            val tickStartY = cy + innerR * sin(angleRad).toFloat()
            val tickEndX = cx + outerR * cos(angleRad).toFloat()
            val tickEndY = cy + outerR * sin(angleRad).toFloat()

            drawLine(
                color = tickColor,
                start = Offset(tickStartX, tickStartY),
                end = Offset(tickEndX, tickEndY),
                strokeWidth = (if (i % 3 == 0) 2.5.dp.toPx() else 1.2.dp.toPx())
            )
        }

        // 4. Draw Active Arc Fill
        if (enabled && fraction > 0.01f) {
            val sweepAngle = 200f * fraction
            drawArc(
                color = lerp(coolTeal, dangerRed, fraction),
                startAngle = 170f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcRect,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }

        // 5. Draw Mechanical Needle Pointer
        val needleAngle = 170f + 200f * fraction + jitter
        val needleAngleRad = Math.toRadians(needleAngle.toDouble())
        val needleLength = radius * 0.95f
        
        val needleEndX = pivotX + needleLength * cos(needleAngleRad).toFloat()
        val needleEndY = pivotY + needleLength * sin(needleAngleRad).toFloat()

        // Shadow for the needle to give depth
        if (enabled) {
            drawLine(
                color = scrimColor.copy(alpha = 0.15f),
                start = Offset(pivotX + 2.5.dp.toPx(), pivotY + 2.5.dp.toPx()),
                end = Offset(needleEndX + 2.5.dp.toPx(), needleEndY + 2.5.dp.toPx()),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Main needle pointer line
        drawLine(
            color = if (enabled) lerp(coolTeal, dangerRed, fraction) else onSurfaceVariant.copy(alpha = 0.3f),
            start = Offset(pivotX, pivotY),
            end = Offset(needleEndX, needleEndY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Pivot brass cap
        drawCircle(
            color = if (enabled) lerp(coolTeal, dangerRed, fraction).copy(alpha = 0.2f) else Color.Transparent,
            radius = 10.dp.toPx(),
            center = Offset(pivotX, pivotY)
        )
        drawCircle(
            color = if (enabled) primaryColor else onSurfaceVariant.copy(alpha = 0.5f),
            radius = 6.dp.toPx(),
            center = Offset(pivotX, pivotY)
        )
        drawCircle(
            color = onPrimaryColor,
            radius = 2.dp.toPx(),
            center = Offset(pivotX, pivotY)
        )

        // 6. Center LED Peak Bar segments (Bottom horizontal indicator)
        val segWidth = 8.dp.toPx()
        val segHeight = 4.dp.toPx()
        val gap = 4.dp.toPx()
        val segCount = 7
        val totalWidth = segCount * segWidth + (segCount - 1) * gap
        val startX = cx - totalWidth / 2f
        val segY = size.height - 8.dp.toPx()

        for (j in 0 until segCount) {
            val isActive = enabled && (fraction >= (j.toFloat() / (segCount - 1)))
            val segColor = when {
                !isActive -> onSurfaceVariant.copy(alpha = 0.08f)
                j >= 5 -> dangerRed // Peak Red
                j >= 3 -> warmAmber // Mid Orange
                else -> coolTeal     // Normal Teal
            }
            drawRoundRect(
                color = segColor,
                topLeft = Offset(startX + j * (segWidth + gap), segY),
                size = Size(segWidth, segHeight),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }

        // 7. Center dB Readout
        val measuredText = textMeasurer.measure(dbText, textStyle)
        drawText(
            textLayoutResult = measuredText,
            color = textColor,
            topLeft = Offset(
                cx - measuredText.size.width / 2f,
                cy + radius * 0.28f - measuredText.size.height / 2f
            )
        )
    }
}

// ─── Stereo Balance: Speaker Icons with Constant-Power Panning ──────────────

@Composable
fun BalanceChannelMeter(
    balancePosition: Float, // -50..50
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val animatedBalance by animateFloatAsState(
        targetValue = balancePosition,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "balance_pos"
    )

    val normalizedBalance = (animatedBalance / 50f).coerceIn(-1f, 1f)

    // Constant-power panning law
    val panAngle = (PI.toFloat() / 4f) * (normalizedBalance + 1f)
    val leftIntensity = if (enabled) cos(panAngle) else 0.3f
    val rightIntensity = if (enabled) sin(panAngle) else 0.3f

    var waveOffsetFraction by remember { mutableStateOf(0f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            val duration = 1200
            val startTime = System.currentTimeMillis()
            while (true) {
                withFrameNanos {
                    val elapsed = (System.currentTimeMillis() - startTime) % duration
                    waveOffsetFraction = elapsed.toFloat() / duration.toFloat()
                }
            }
        } else {
            waveOffsetFraction = 0f
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 8.dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // 1. Draw Soundstage grid background (subtle crosshairs and ticks)
        val gridColor = onSurfaceVariant.copy(alpha = 0.05f)
        // Horizontal baseline
        drawLine(
            color = gridColor,
            start = Offset(24.dp.toPx(), cy),
            end = Offset(w - 24.dp.toPx(), cy),
            strokeWidth = 1.dp.toPx()
        )
        // Vertical midline
        drawLine(
            color = gridColor,
            start = Offset(cx, 8.dp.toPx()),
            end = Offset(cx, h - 8.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        // 2. Draw Listener sweet spot (head/ears representation in the center)
        val listenerColor = if (enabled) primaryColor.copy(alpha = 0.15f) else onSurfaceVariant.copy(alpha = 0.1f)
        // Sweet spot zone ring
        drawCircle(
            color = listenerColor,
            radius = 16.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
        )
        // Inner core
        drawCircle(
            color = if (enabled) primaryColor.copy(alpha = 0.7f) else onSurfaceVariant.copy(alpha = 0.4f),
            radius = 5.dp.toPx(),
            center = Offset(cx, cy)
        )

        // 3. Draw Left & Right Speaker Sources (at the far edges)
        val leftSpeakerX = 16.dp.toPx()
        val rightSpeakerX = w - 16.dp.toPx()

        // Left Speaker Source Indicator
        drawRoundRect(
            color = if (enabled) primaryColor.copy(alpha = 0.1f + leftIntensity * 0.2f) else onSurfaceVariant.copy(alpha = 0.08f),
            topLeft = Offset(leftSpeakerX - 6.dp.toPx(), cy - 12.dp.toPx()),
            size = Size(12.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = if (enabled) primaryColor.copy(alpha = leftIntensity) else onSurfaceVariant.copy(alpha = 0.3f),
            topLeft = Offset(leftSpeakerX - 6.dp.toPx(), cy - 12.dp.toPx()),
            size = Size(12.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Right Speaker Source Indicator
        drawRoundRect(
            color = if (enabled) primaryColor.copy(alpha = 0.1f + rightIntensity * 0.2f) else onSurfaceVariant.copy(alpha = 0.08f),
            topLeft = Offset(rightSpeakerX - 6.dp.toPx(), cy - 12.dp.toPx()),
            size = Size(12.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = if (enabled) primaryColor.copy(alpha = rightIntensity) else onSurfaceVariant.copy(alpha = 0.3f),
            topLeft = Offset(rightSpeakerX - 6.dp.toPx(), cy - 12.dp.toPx()),
            size = Size(12.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 4. Draw Acoustic Soundwave Propagation
        if (enabled) {
            val maxWaveRadius = cx - leftSpeakerX - 8.dp.toPx()
            val waveCount = 3
            for (i in 0 until waveCount) {
                // Staggered propagation progress
                val progress = (waveOffsetFraction + i.toFloat() / waveCount) % 1f
                val r = progress * maxWaveRadius
                val waveAlpha = (1f - progress) * 0.45f

                // Left speaker soundwave arc (propagating to the right)
                if (leftIntensity > 0.05f) {
                    drawArc(
                        color = primaryColor.copy(alpha = waveAlpha * leftIntensity),
                        startAngle = -45f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(leftSpeakerX - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Right speaker soundwave arc (propagating to the left)
                if (rightIntensity > 0.05f) {
                    drawArc(
                        color = primaryColor.copy(alpha = waveAlpha * rightIntensity),
                        startAngle = 135f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(rightSpeakerX - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        // 5. Draw Dynamic Glowing Sound Source Node
        val nodeX = cx + (cx - leftSpeakerX - 24.dp.toPx()) * normalizedBalance
        val nodeY = cy

        if (enabled) {
            // Radial outer glow centered on the source node
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(nodeX, nodeY),
                    radius = 24.dp.toPx()
                ),
                radius = 24.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )

            // Inner core orb
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
            // Accent dot inside orb
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
        } else {
            // Disabled state center indicator
            drawCircle(
                color = onSurfaceVariant.copy(alpha = 0.3f),
                radius = 5.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
        }
    }
}



// ─── Playback Speed: Vinyl Turntable & Scrolling Time-Stretch Waveform ──────

@Composable
fun SpeedDialVisualizer(
    speed: Float, // 0.5..2.0
    enabled: Boolean,
    isPitchMatched: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Continuous, frame-aligned delta accumulation loop to handle dynamic speed changes smoothly without jumping
    var phase by remember { mutableStateOf(0f) }
    var wobblePhase by remember { mutableStateOf(0f) }
    LaunchedEffect(enabled, speed) {
        if (enabled) {
            var lastTime = withFrameNanos { it }
            val wobbleDuration = 600
            val startTime = System.currentTimeMillis()
            while (true) {
                withFrameNanos { time ->
                    val deltaSeconds = (time - lastTime) / 1_000_000_000f
                    lastTime = time
                    // 1.0x speed = 2 * PI radians in 1.8 seconds -> speed coefficient scales this rate
                    val rate = (2f * PI.toFloat() / 1.8f) * speed
                    phase = (phase + rate * deltaSeconds) % (2f * PI.toFloat())

                    val wobbleElapsed = (System.currentTimeMillis() - startTime) % wobbleDuration
                    wobblePhase = (wobbleElapsed.toFloat() / wobbleDuration.toFloat()) * (2f * PI.toFloat())
                }
            }
        } else {
            wobblePhase = 0f
        }
    }

    val waveColor = when {
        !enabled -> onSurfaceVariant.copy(alpha = 0.2f)
        !isPitchMatched -> tertiaryColor // Vinyl mode: warm tint
        else -> primaryColor
    }

    val vinylDiscColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val vinylGrooveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val vinylSpindleColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // 1. Vinyl Turntable centered parameters
        val recordRadius = h * 0.46f
        val recordCenterX = w / 2f
        val recordCenterY = midY

        val platterColor = onSurfaceVariant.copy(alpha = 0.15f)
        val vinylColor = vinylDiscColor

        // Turntable Platter Ambient Glow
        if (enabled) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(waveColor.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(recordCenterX, recordCenterY),
                    radius = recordRadius * 1.3f
                ),
                radius = recordRadius * 1.3f,
                center = Offset(recordCenterX, recordCenterY)
            )
        }

        // Platter outer rim
        drawCircle(
            color = platterColor,
            radius = recordRadius,
            center = Offset(recordCenterX, recordCenterY),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Vinyl Disc Body
        drawCircle(
            color = vinylColor,
            radius = recordRadius - 2.dp.toPx(),
            center = Offset(recordCenterX, recordCenterY)
        )

        // Concentric Vinyl Grooves
        if (enabled) {
            val grooveColor = vinylGrooveColor
            drawCircle(color = grooveColor, radius = recordRadius * 0.8f, center = Offset(recordCenterX, recordCenterY), style = Stroke(width = 1f))
            drawCircle(color = grooveColor, radius = recordRadius * 0.65f, center = Offset(recordCenterX, recordCenterY), style = Stroke(width = 1f))
            drawCircle(color = grooveColor, radius = recordRadius * 0.5f, center = Offset(recordCenterX, recordCenterY), style = Stroke(width = 1f))
        }

        // Center label (Dynamic color scheme matching pitch mode)
        drawCircle(
            color = waveColor.copy(alpha = if (enabled) 0.85f else 0.35f),
            radius = recordRadius * 0.32f,
            center = Offset(recordCenterX, recordCenterY)
        )

        // Center spindle hole
        drawCircle(
            color = vinylSpindleColor,
            radius = recordRadius * 0.08f,
            center = Offset(recordCenterX, recordCenterY)
        )

        // Spinning indicator mark
        if (enabled) {
            val indicatorRadius = recordRadius * 0.62f
            val markerPhase = phase // Spun by wave scroll phase
            val indX = recordCenterX + indicatorRadius * cos(markerPhase)
            val indY = recordCenterY + indicatorRadius * sin(markerPhase)
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 2.dp.toPx(),
                center = Offset(indX, indY)
            )
        }

        // 2. Tonearm / Needle (Pivots on the right side of the centered disc)
        val pivotX = recordCenterX + recordRadius + 8.dp.toPx()
        val pivotY = recordCenterY - recordRadius + 4.dp.toPx()

        // Rest or park position
        val targetX = if (enabled) recordCenterX + recordRadius * 0.6f * cos(0.2f) else pivotX - 3.dp.toPx()
        val targetY = if (enabled) recordCenterY + recordRadius * 0.6f * sin(0.2f) else pivotY + 22.dp.toPx()

        // Micro wobble jitter feedback on the needle
        val jitter = if (enabled && !isPitchMatched) {
            sin(wobblePhase * 4f) * 0.6.dp.toPx()
        } else 0f

        // Draw metal arm
        drawLine(
            color = if (enabled) onSurfaceVariant else onSurfaceVariant.copy(alpha = 0.4f),
            start = Offset(pivotX, pivotY),
            end = Offset(targetX + jitter, targetY + jitter),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Stylus / cartridge head
        drawCircle(
            color = waveColor,
            radius = 2.5.dp.toPx(),
            center = Offset(targetX + jitter, targetY + jitter)
        )

        // Pivot base circle
        drawCircle(
            color = onSurfaceVariant.copy(alpha = 0.8f),
            radius = 3.5.dp.toPx(),
            center = Offset(pivotX, pivotY)
        )
    }
}

// ─── 8D Audio: Headphone Listener with Orbiting Glowing Sound Node ──────────

@Composable
fun Spatial8DVisualizer(
    enabled: Boolean,
    speedHz: Float = 0.15f,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val durationMs = ((1.0f / speedHz.coerceIn(0.03f, 0.40f)) * 1000f).toInt().coerceIn(1500, 20000)
    val transition = rememberInfiniteTransition(label = "8DInfiniteTransition")

    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (enabled) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "8DRotation"
    )

    val waveOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (enabled) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "8DWave"
    )

    val pulseScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (enabled) 1.04f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "8DPulse"
    )

    val density = LocalDensity.current
    val headRadius = with(density) { 14.dp.toPx() }
    val orbitRadius = with(density) { 38.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(116.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        val activeColor = primaryColor
        val inactiveColor = onSurfaceVariant.copy(alpha = 0.3f)
        val currentColor = if (enabled) activeColor else inactiveColor

        // 1. Draw Radar/Compass concentric rings (Grid)
        val radarColor = onSurfaceVariant.copy(alpha = 0.05f)
        drawCircle(
            color = radarColor,
            radius = orbitRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
        )
        drawCircle(
            color = radarColor,
            radius = orbitRadius * 0.6f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
        )
        // Subtle crosshair lines
        drawLine(
            color = radarColor,
            start = Offset(cx - orbitRadius - 10.dp.toPx(), cy),
            end = Offset(cx + orbitRadius + 10.dp.toPx(), cy),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = radarColor,
            start = Offset(cx, cy - orbitRadius - 10.dp.toPx()),
            end = Offset(cx, cy + orbitRadius + 10.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        // 2. Draw Listener Head and Headphones (Center)
        val currentPulse = if (enabled) pulseScale else 1f
        val currentHeadRadius = headRadius * currentPulse

        // Listener Head
        drawCircle(
            color = currentColor.copy(alpha = if (enabled) 0.1f else 0.05f),
            radius = currentHeadRadius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = currentColor.copy(alpha = if (enabled) 0.6f else 0.3f),
            radius = currentHeadRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Headphones earcups (Left & Right of Head)
        val earcupW = 4.dp.toPx() * currentPulse
        val earcupH = 12.dp.toPx() * currentPulse
        val earcupRadius = 1.5.dp.toPx()

        // Left earcup
        drawRoundRect(
            color = currentColor,
            topLeft = Offset(cx - currentHeadRadius - earcupW, cy - earcupH / 2f),
            size = Size(earcupW, earcupH),
            cornerRadius = CornerRadius(earcupRadius)
        )
        // Right earcup
        drawRoundRect(
            color = currentColor,
            topLeft = Offset(cx + currentHeadRadius, cy - earcupH / 2f),
            size = Size(earcupW, earcupH),
            cornerRadius = CornerRadius(earcupRadius)
        )

        // Headphone headband (arc connecting earcups over the top of the head)
        val bandRect = Size((currentHeadRadius + earcupW / 2f) * 2f, (currentHeadRadius + earcupW / 2f) * 2f)
        drawArc(
            color = currentColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - currentHeadRadius - earcupW / 2f, cy - currentHeadRadius - earcupW / 2f),
            size = bandRect,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 3. Draw Orbiting Sound Source & Particle Trail
        val angleRad = if (enabled) Math.toRadians(rotationAngle.toDouble()) else Math.toRadians(-90.0)
        val nodeX = cx + orbitRadius * cos(angleRad).toFloat()
        val nodeY = cy + orbitRadius * sin(angleRad).toFloat()

        if (enabled) {
            // Draw particle trails behind the orbiting node
            val trailCount = 5
            for (i in 1..trailCount) {
                val trailAngleRad = Math.toRadians((rotationAngle - i * 8f).toDouble())
                val tx = cx + orbitRadius * cos(trailAngleRad).toFloat()
                val ty = cy + orbitRadius * sin(trailAngleRad).toFloat()
                val trailAlpha = 0.5f * (1f - i.toFloat() / trailCount)
                val trailSize = 4.dp.toPx() * (1f - 0.15f * i)

                drawCircle(
                    color = secondaryColor.copy(alpha = trailAlpha),
                    radius = trailSize,
                    center = Offset(tx, ty)
                )
            }

            // Draw soundwave rings expanding from the active sound source
            val waveCount = 2
            for (wIndex in 0 until waveCount) {
                val wProgress = (waveOffset + wIndex.toFloat() / waveCount) % 1f
                val maxWaveRadius = 24.dp.toPx()
                val wr = wProgress * maxWaveRadius
                val wAlpha = (1f - wProgress) * 0.4f

                drawCircle(
                    color = primaryColor.copy(alpha = wAlpha),
                    radius = wr,
                    center = Offset(nodeX, nodeY),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            // Radial Glow centered on the main sound node
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(nodeX, nodeY),
                    radius = 16.dp.toPx()
                ),
                radius = 16.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )

            // Inner solid core node
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
        } else {
            // Static node in disabled state
            drawCircle(
                color = inactiveColor,
                radius = 4.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
        }
    }
}

