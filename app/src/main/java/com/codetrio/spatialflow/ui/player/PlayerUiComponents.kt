package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.theme.observeKey
import kotlinx.coroutines.flow.StateFlow

/**
 * Custom Compose extension to render a marquee with smooth horizontal alpha-faded edges.
 * Uses drawWithCache to avoid allocating Brush and List objects on every frame of the drawing phase.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.basicMarqueeWithFadedEdges(
    edgeWidth: Dp = 12.dp,
    initialDelayMillis: Int = 1200
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val edgeWidthPx = edgeWidth.toPx()
        // Cache the brushes so they aren't recreated every frame
        val leftBrush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startX = 0f,
            endX = edgeWidthPx
        )
        val rightBrush = Brush.horizontalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startX = size.width - edgeWidthPx,
            endX = size.width
        )
        
        onDrawWithContent {
            drawContent()
            drawRect(brush = leftBrush, blendMode = BlendMode.DstIn)
            drawRect(brush = rightBrush, blendMode = BlendMode.DstIn)
        }
    }
    .basicMarquee(initialDelayMillis = initialDelayMillis)
    .padding(horizontal = edgeWidth)

/**
 * Expressive shimmer sweep applied directly to text glyphs (using offscreen compositing),
 * keeping the pill container background completely static.
 */
@Composable
fun Modifier.textShimmer(
    enabled: Boolean,
    baseColor: Color,
    highlightColor: Color = Color.White,
    shimmerWidthDp: Dp = 48.dp,
    durationMillis: Int = 4500
): Modifier {
    if (!enabled) return this

    val infiniteTransition = rememberInfiniteTransition(label = "TextShimmerTransition")
    val sweepFraction by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TextShimmerSweep"
    )

    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithCache {
            val sweepWidthPx = shimmerWidthDp.toPx()
            val totalSpan = size.width + sweepWidthPx * 2
            val currentX = sweepFraction * totalSpan - sweepWidthPx

            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    baseColor,
                    highlightColor,
                    baseColor
                ),
                start = Offset(currentX, 0f),
                end = Offset(currentX + sweepWidthPx, size.height)
            )

            onDrawWithContent {
                drawContent()
                drawRect(
                    brush = shimmerBrush,
                    blendMode = BlendMode.SrcIn
                )
            }
        }
}

/**
 * Sweeps a subtle highlight across the container shape (e.g. for banners).
 */
@Composable
fun Modifier.formatPillShimmer(
    enabled: Boolean,
    shimmerColor: Color,
    shimmerWidthDp: Dp = 48.dp
): Modifier {
    if (!enabled) return this

    val infiniteTransition = rememberInfiniteTransition(label = "PillShimmerTransition")
    val sweepFraction by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PillShimmerSweep"
    )

    return this.drawWithCache {
        val sweepWidthPx = shimmerWidthDp.toPx()
        val totalSpan = size.width + sweepWidthPx * 2
        val currentX = sweepFraction * totalSpan - sweepWidthPx

        val shimmerBrush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                shimmerColor.copy(alpha = 0.32f),
                Color.Transparent
            ),
            start = Offset(currentX, 0f),
            end = Offset(currentX + sweepWidthPx, size.height)
        )

        onDrawWithContent {
            drawContent()
            drawRoundRect(
                brush = shimmerBrush,
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                blendMode = BlendMode.SrcAtop
            )
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val sTen = seconds / 10
    val sOne = seconds % 10

    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMin = minutes % 60
        val mTen = remMin / 10
        val mOne = remMin % 10
        "$hours:$mTen$mOne:$sTen$sOne"
    } else {
        val mTen = minutes / 10
        val mOne = minutes % 10
        "$mTen$mOne:$sTen$sOne"
    }
}

internal fun deriveArtworkSurfaceColor(
    sourceColor: Color,
    isDark: Boolean,
    darkLightness: Float,
    lightLightness: Float,
    darkSaturationRange: ClosedFloatingPointRange<Float>,
    lightSaturationRange: ClosedFloatingPointRange<Float>,
    monochromeSaturationThreshold: Float = 0.06f
): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(sourceColor.toArgb(), hsl)
    val isMonochrome = hsl[1] < monochromeSaturationThreshold
    hsl[2] = if (isDark) darkLightness else lightLightness
    hsl[1] = if (isMonochrome) {
        0f
    } else if (isDark) {
        hsl[1].coerceIn(darkSaturationRange.start, darkSaturationRange.endInclusive)
    } else {
        hsl[1].coerceIn(lightSaturationRange.start, lightSaturationRange.endInclusive)
    }
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SplitLikeDislikeChip(
    isLiked: Boolean,
    isDisliked: Boolean,
    likesCount: String,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    isDark: Boolean
) {
    val displayLikesText = remember(likesCount) {
        likesCount.ifBlank { "Like" }
    }

    Row(
        modifier = Modifier.height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val likeInteraction = remember { MutableInteractionSource() }
        val likePressed by likeInteraction.collectIsPressedAsState()

        // Like Button (Connected Leading)
        ToggleButton(
            checked = isLiked || likePressed,
            onCheckedChange = { onLikeClick() },
            interactionSource = likeInteraction,
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f),
                contentColor = contentColor.copy(alpha = 0.8f),
                checkedContainerColor = accentColor.copy(alpha = if (isDark) 0.25f else 0.18f),
                checkedContentColor = accentColor
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.fillMaxHeight()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                    contentDescription = "Like",
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = displayLikesText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }

        val dislikeInteraction = remember { MutableInteractionSource() }
        val dislikePressed by dislikeInteraction.collectIsPressedAsState()

        // Dislike Button (Connected Trailing)
        ToggleButton(
            checked = isDisliked || dislikePressed,
            onCheckedChange = { onDislikeClick() },
            interactionSource = dislikeInteraction,
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f),
                contentColor = contentColor.copy(alpha = 0.8f),
                checkedContainerColor = accentColor.copy(alpha = if (isDark) 0.25f else 0.18f),
                checkedContentColor = accentColor
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.fillMaxHeight()
        ) {
            Icon(
                imageVector = if (isDisliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
                contentDescription = "Dislike",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun PillChip(
    icon: Any,
    label: String,
    onClick: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    isDark: Boolean,
    isSelected: Boolean = false,
    progress: Float? = null,
    chipHeight: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        accentColor.copy(alpha = if (isDark) 0.25f else 0.18f)
    } else {
        contentColor.copy(alpha = if (isDark) 0.08f else 0.06f)
    }
    
    val tintColor = if (isSelected) accentColor else contentColor.copy(alpha = 0.8f)
    val progressColor = accentColor.copy(alpha = if (isDark) 0.35f else 0.25f)
    
    val animatedFill by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 150f),
        label = "DownloadChipProgress"
    )

    Box(
        modifier = modifier
            .height(chipHeight)
            .clip(CircleShape)
            .background(backgroundColor)
            .drawBehind {
                if (progress != null && progress > 0f) {
                    drawRect(
                        color = progressColor,
                        size = size.copy(width = size.width * animatedFill)
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (icon) {
                is ImageVector -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                is Painter -> {
                    Icon(
                        painter = icon,
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = tintColor
            )
        }
    }
}

@Composable
internal fun WavySliderWithLabels(
    currentPositionProvider: () -> Int,
    duration: Int,
    isPlaying: Boolean,
    onSeekTo: (Int) -> Unit,
    dynamicAccentColor: Color,
    contentColor: Color,
    contentSecondary: Color,
    isDark: Boolean,
    playbackFormat: String,
    modifier: Modifier = Modifier,
    currentPositionFlow: StateFlow<Int>? = null,
    allowRealtimeUpdates: Boolean = true
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var sliderScrubPos by remember { mutableFloatStateOf(0f) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }
    var lastSeekPos by remember { mutableFloatStateOf(0f) }

    val currentPosition = if (currentPositionFlow != null && allowRealtimeUpdates) {
        val positionState by currentPositionFlow.collectAsStateWithLifecycle()
        positionState
    } else {
        currentPositionProvider()
    }
    val safeDur = if (duration > 0) duration.toFloat() else 1f
    val progressRatio = (currentPosition.toFloat() / safeDur).coerceIn(0f, 1f)

    val isWaitingForPlayer = remember(progressRatio, lastSeekTime, lastSeekPos) {
        if (lastSeekTime == 0L) {
            false
        } else {
            val elapsed = System.currentTimeMillis() - lastSeekTime
            val diff = kotlin.math.abs(progressRatio - lastSeekPos)
            elapsed < 1000 && diff > 0.02f
        }
    }

    val displayValue = when {
        isScrubbing -> sliderScrubPos
        isWaitingForPlayer -> lastSeekPos
        else -> progressRatio
    }

    val displayPos = when {
        isScrubbing -> (sliderScrubPos * safeDur).toInt()
        isWaitingForPlayer -> (lastSeekPos * safeDur).toInt()
        else -> currentPosition
    }

    val codecLabel = remember(playbackFormat) {
        if (playbackFormat.contains("•")) {
            playbackFormat.split("•").first().trim()
        } else {
            playbackFormat.trim()
        }
    }

    val liveDacState by com.codetrio.spatialflow.service.dac.UsbDacManager.liveState.collectAsStateWithLifecycle()
    val isUsbDac = liveDacState.hardware.isUsbDac
    val isBitPerfect = isUsbDac && liveDacState.pipeline.isBitPerfectVerified
    val isAtmosActive by com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.isAtmosActive.collectAsStateWithLifecycle()
    val isMixing by com.codetrio.spatialflow.service.automix.AutoMixUiState.mixing.collectAsStateWithLifecycle()
    val transitionWindow by com.codetrio.spatialflow.service.automix.AutoMixUiState.transitionWindow.collectAsStateWithLifecycle()

    val liveSourceBitrateKbps = remember(liveDacState.source.bitrateBps) {
        if (liveDacState.source.bitrateBps > 0) liveDacState.source.bitrateBps / 1000 else 0
    }

    val isLossless = remember(codecLabel, playbackFormat, liveSourceBitrateKbps) {
        val upper = codecLabel.uppercase()
        val isCodecMatch = upper == "FLAC" || upper == "WAV" || upper == "LOSSLESS" || playbackFormat.contains("Lossless", ignoreCase = true)
        isCodecMatch && (liveSourceBitrateKbps == 0 || liveSourceBitrateKbps >= 350)
    }

    val formatIconVector = remember(codecLabel, isLossless) {
        when (codecLabel.uppercase()) {
            "OPUS" -> Icons.Rounded.HighQuality
            "AAC"  -> Icons.Rounded.MusicNote
            "MP3"  -> Icons.Rounded.AudioFile
            else   -> if (!isLossless) Icons.Rounded.MusicNote else null
        }
    }

    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            kotlinx.coroutines.delay(3000)
            isExpanded = false
        }
    }

    val bitrateText = remember(playbackFormat, codecLabel, liveSourceBitrateKbps) {
        when {
            playbackFormat.contains("kbps") -> {
                val isLosslessTag = playbackFormat.contains("Lossless", true) || playbackFormat.contains("FLAC", true) || playbackFormat.contains("Hi-Res", true)
                if (isLosslessTag && liveSourceBitrateKbps in 1..349) {
                    playbackFormat.replace(Regex("""•\s*\d+[\s,]*kbps"""), "").trim()
                } else {
                    playbackFormat
                }
            }
            liveSourceBitrateKbps > 0 -> {
                val formattedKbps = java.text.NumberFormat.getIntegerInstance().format(liveSourceBitrateKbps)
                if (playbackFormat.contains("•")) {
                    "$playbackFormat • $formattedKbps kbps"
                } else {
                    "$codecLabel • $formattedKbps kbps"
                }
            }
            playbackFormat.contains("•") || playbackFormat.contains("Lossless") -> playbackFormat
            else -> codecLabel
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE) }
    val seekBarStyle by prefs.observeKey("seekbar_style", "apple")

    val durationText = remember(duration) { formatDuration(duration.toLong()) }

    val pillScaleAnim = remember { Animatable(1f) }
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val rippleAnim = remember { Animatable(0f) }

    val pillInteractionSource = remember { MutableInteractionSource() }
    val isPillPressed by pillInteractionSource.collectIsPressedAsState()

    val pillPressScale by animateFloatAsState(
        targetValue = if (isPillPressed) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PillPressScale"
    )

    val pillBgAlpha by animateFloatAsState(
        targetValue = if (isPillPressed) 0.22f else 0.14f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PillBgAlpha"
    )

    LaunchedEffect(isPillPressed) {
        if (isPillPressed) {
            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(Unit) {
        com.codetrio.spatialflow.service.dac.UsbDacManager.connectionEvents.collect {
            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            launch {
                pillScaleAnim.animateTo(
                    1.15f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
                pillScaleAnim.animateTo(
                    1.0f,
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
            repeat(3) {
                rippleAnim.snapTo(0f)
                rippleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1600, easing = LinearEasing)
                )
            }
            rippleAnim.snapTo(0f)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            when (seekBarStyle.lowercase()) {
                "apple", "apple_music" -> {
                    AppleMusicSlider(
                        value = displayValue,
                        onValueChange = {
                            isScrubbing = true
                            sliderScrubPos = it
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                            lastSeekTime = System.currentTimeMillis()
                            lastSeekPos = sliderScrubPos
                            onSeekTo((sliderScrubPos * safeDur).toInt())
                        },
                        activeTrackColor = if (isDark) Color.White.copy(alpha = 0.88f) else dynamicAccentColor.copy(alpha = 0.90f),
                        inactiveTrackColor = (if (isDark) Color.White else contentColor).copy(alpha = 0.20f),
                        thumbColor = if (isDark) Color.White.copy(alpha = 0.95f) else contentColor,
                        restingHeight = 8.dp,
                        pressedHeight = 11.dp,
                        expandedHeight = 14.dp,
                        thumbWidth = 4.5.dp,
                        thumbHeight = 18.dp,
                        transitionWindow = transitionWindow,
                        mixing = isMixing
                    )
                }
                "classic", "line" -> {
                    WavyMusicSlider(
                        value = displayValue,
                        onValueChange = {
                            isScrubbing = true
                            sliderScrubPos = it
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                            lastSeekTime = System.currentTimeMillis()
                            lastSeekPos = sliderScrubPos
                            onSeekTo((sliderScrubPos * safeDur).toInt())
                        },
                        activeTrackColor = dynamicAccentColor,
                        inactiveTrackColor = contentColor.copy(alpha = if (isDark) 0.16f else 0.12f),
                        thumbColor = contentColor,
                        isPlaying = false,
                        isWaveEligible = false,
                        trackHeight = 4.dp,
                        thumbRadius = 6.dp,
                        waveAmplitudeWhenPlaying = 0.dp,
                        transitionWindow = transitionWindow,
                        mixing = isMixing
                    )
                }
                "liquid", "spatialwave" -> {
                    SpatialWaveMusicSlider(
                        value = displayValue,
                        onValueChange = {
                            isScrubbing = true
                            sliderScrubPos = it
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                            lastSeekTime = System.currentTimeMillis()
                            lastSeekPos = sliderScrubPos
                            onSeekTo((sliderScrubPos * safeDur).toInt())
                        },
                        isPlaying = isPlaying,
                        activeColor = dynamicAccentColor,
                        inactiveColor = contentColor.copy(alpha = if (isDark) 0.16f else 0.12f),
                        transitionWindow = transitionWindow
                    )
                }
                else -> {
                    WavyMusicSlider(
                        value = displayValue,
                        onValueChange = {
                            isScrubbing = true
                            sliderScrubPos = it
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                            lastSeekTime = System.currentTimeMillis()
                            lastSeekPos = sliderScrubPos
                            onSeekTo((sliderScrubPos * safeDur).toInt())
                        },
                        activeTrackColor = dynamicAccentColor,
                        inactiveTrackColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f),
                        thumbColor = contentColor,
                        isPlaying = isPlaying,
                        trackHeight = 4.dp,
                        thumbRadius = 6.dp,
                        waveAmplitudeWhenPlaying = 6.dp,
                        waveLength = 48.dp,
                        transitionWindow = transitionWindow,
                        mixing = isMixing
                    )
                }
            }

            var showRemainingTime by remember { mutableStateOf(true) }
            val remainingMs = (safeDur - displayPos).toLong().coerceAtLeast(0L)
            val rightTimeText = if (showRemainingTime) "-${formatDuration(remainingMs)}" else durationText

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatDuration(displayPos.toLong()),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = contentColor,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                val pillColor = Color.White

                AnimatedContent(
                    targetState = isMixing,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400, easing = LinearEasing)) togetherWith
                            fadeOut(animationSpec = tween(400, easing = LinearEasing))
                    },
                    modifier = Modifier.align(Alignment.Center),
                    label = "MixingVsFormatPill"
                ) { mixing ->
                    if (mixing) {
                        val infiniteTransition = rememberInfiniteTransition(label = "MixingTextShimmer")
                        val sweepFraction by infiniteTransition.animateFloat(
                            initialValue = -0.4f,
                            targetValue = 1.4f,
                            animationSpec = infiniteRepeatable(
                                animation = keyframes {
                                    durationMillis = 2000
                                    -0.4f at 0 using FastOutSlowInEasing
                                    1.4f at 1350
                                    1.4f at 2000
                                },
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "MixingTextSweep"
                        )

                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val textSpanPx = with(density) { 85.dp.toPx() }
                        val bandWidthPx = with(density) { 55.dp.toPx() }
                        val currentX = sweepFraction * textSpanPx

                        val mixingBrush = Brush.linearGradient(
                            0.00f to Color.White.copy(alpha = 0.70f),
                            0.30f to Color.White.copy(alpha = 0.85f),
                            0.50f to Color.White,
                            0.70f to Color.White.copy(alpha = 0.85f),
                            1.00f to Color.White.copy(alpha = 0.70f),
                            start = Offset(currentX, 0f),
                            end = Offset(currentX + bandWidthPx, 0f)
                        )

                        Text(
                            text = "Mixing",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 13.5.sp,
                                letterSpacing = 0.35.sp,
                                brush = mixingBrush
                            ),
                            fontWeight = FontWeight.Bold,
                            color = Color.Unspecified
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = pillScaleAnim.value * pillPressScale
                                    scaleY = pillScaleAnim.value * pillPressScale
                                }
                                .clip(RoundedCornerShape(7.5.dp))
                                .background(Color.White.copy(alpha = pillBgAlpha))
                                .animateContentSize(
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioLowBouncy
                                    )
                                )
                                .clickable(
                                    interactionSource = pillInteractionSource,
                                    indication = null
                                ) {
                                    isExpanded = !isExpanded
                                }
                        ) {
                            if (rippleAnim.value > 0f) {
                                val p = rippleAnim.value
                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier.matchParentSize()
                                ) {
                                    val originX = 16.dp.toPx().coerceAtMost(size.width * 0.25f)
                                    val originY = size.height / 2f
                                    val maxRadius = size.width - originX + 32.dp.toPx()
                                    val radius = p * maxRadius
                                    val alpha = ((1f - p) * 0.75f).coerceIn(0f, 1f)

                                    // Soft inner ambient wash
                                    drawCircle(
                                        color = Color.White.copy(alpha = alpha * 0.12f),
                                        radius = radius,
                                        center = Offset(originX, originY)
                                    )

                                    // Wide feathered ripple band with smooth Gaussian faded edges (~20dp total width)
                                    drawCircle(
                                        color = Color.White.copy(alpha = (alpha * 0.16f).coerceIn(0f, 1f)),
                                        radius = radius,
                                        center = Offset(originX, originY),
                                        style = Stroke(width = 20.dp.toPx())
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = (alpha * 0.28f).coerceIn(0f, 1f)),
                                        radius = radius,
                                        center = Offset(originX, originY),
                                        style = Stroke(width = 14.dp.toPx())
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = (alpha * 0.45f).coerceIn(0f, 1f)),
                                        radius = radius,
                                        center = Offset(originX, originY),
                                        style = Stroke(width = 8.dp.toPx())
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
                                        radius = radius,
                                        center = Offset(originX, originY),
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                val isHiRes = isLossless && (liveSourceBitrateKbps == 0 || liveSourceBitrateKbps >= 600) && ((liveDacState.hardware.activeSampleRate?.let {
                                    it.contains("88") || it.contains("96") || it.contains("176") || it.contains("192") || it.contains("352") || it.contains("384")
                                } == true) || liveDacState.source.sampleRateHz > 48000 || liveDacState.source.bitDepth > 16)

                                val showDolby = isAtmosActive && !isHiRes
                                val baseTextAlpha = if (isDark) 0.74f else 0.78f
                                val iconTint = pillColor.copy(alpha = baseTextAlpha)

                                if (showDolby) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_dolby),
                                        contentDescription = "Dolby Atmos",
                                        modifier = Modifier.size(18.dp),
                                        tint = iconTint
                                    )
                                } else if (isLossless) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_lossless),
                                        contentDescription = "Lossless Audio",
                                        modifier = Modifier.size(18.dp),
                                        tint = iconTint
                                    )
                                } else {
                                    Icon(
                                        imageVector = formatIconVector ?: Icons.Rounded.MusicNote,
                                        contentDescription = "Audio Format Quality Badge",
                                        modifier = Modifier.size(18.dp),
                                        tint = iconTint
                                    )
                                }

                                val dacLabel = when {
                                    isLossless && isHiRes -> "HI-RES LOSSLESS"
                                    isLossless -> "LOSSLESS"
                                    isUsbDac -> if (codecLabel.isNotBlank()) "$codecLabel • DAC" else "DAC"
                                    else -> codecLabel.ifBlank { "AAC" }
                                }

                                val pillText = when {
                                    showDolby -> {
                                        val baseCodec = if (isLossless) "Lossless" else codecLabel.ifBlank { "AAC" }
                                        if (isExpanded) {
                                            val formattedKbps = if (liveSourceBitrateKbps > 0) {
                                                "${java.text.NumberFormat.getIntegerInstance().format(liveSourceBitrateKbps)} kbps"
                                            } else if (playbackFormat.contains("kbps")) {
                                                playbackFormat.substringAfterLast("•").trim()
                                            } else if (bitrateText.contains("kbps")) {
                                                bitrateText
                                            } else null

                                            if (formattedKbps != null) {
                                                "• $baseCodec • $formattedKbps"
                                            } else {
                                                "• $baseCodec"
                                            }
                                        } else {
                                            "• $baseCodec"
                                        }
                                    }
                                    isUsbDac -> {
                                        if (isExpanded) {
                                            if (liveSourceBitrateKbps > 0) {
                                                val formatted = java.text.NumberFormat.getIntegerInstance().format(liveSourceBitrateKbps)
                                                "$dacLabel • $formatted kbps"
                                            } else if (!liveDacState.hardware.activeSampleRate.isNullOrBlank()) {
                                                "$dacLabel • ${liveDacState.hardware.activeSampleRate}"
                                            } else {
                                                bitrateText
                                            }
                                        } else {
                                            dacLabel
                                        }
                                    }
                                    isLossless -> {
                                        val label = if (isHiRes) "HI-RES LOSSLESS" else "Lossless"
                                        if (isExpanded) {
                                            if (liveSourceBitrateKbps >= 350) {
                                                val formatted = java.text.NumberFormat.getIntegerInstance().format(liveSourceBitrateKbps)
                                                "$label • $formatted kbps"
                                            } else {
                                                bitrateText
                                            }
                                        } else {
                                            label
                                        }
                                    }
                                    else -> {
                                        if (isExpanded) bitrateText else codecLabel
                                    }
                                }

                                val shouldShimmer = isLossless || playbackFormat.contains("Lossless", ignoreCase = true) || playbackFormat.contains("FLAC", ignoreCase = true)
                                val textHighlightColor = Color.White

                                val infiniteTransition = rememberInfiniteTransition(label = "FormatTextShimmer")
                                val sweepFraction by infiniteTransition.animateFloat(
                                    initialValue = -0.6f,
                                    targetValue = 1.6f,
                                    animationSpec = infiniteRepeatable(
                                        animation = keyframes {
                                            durationMillis = 7500
                                            -0.6f at 0 using LinearEasing
                                            1.6f at 4500
                                            1.6f at 7500 // 3.0s serene resting interval
                                        },
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "TextSweep"
                                )

                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val shimmerSpanPx = with(density) { 220.dp.toPx() }
                                val bandWidthPx = with(density) { 100.dp.toPx() }
                                val tiltYPx = with(density) { 20.dp.toPx() }
                                val currentX = sweepFraction * shimmerSpanPx

                                val textBrush = if (shouldShimmer) {
                                    Brush.linearGradient(
                                        0.00f to pillColor.copy(alpha = baseTextAlpha),
                                        0.18f to pillColor.copy(alpha = baseTextAlpha),
                                        0.34f to pillColor.copy(alpha = 0.95f),
                                        0.42f to Color.White,
                                        0.50f to Color.White,
                                        0.58f to Color.White,
                                        0.66f to pillColor.copy(alpha = 0.95f),
                                        0.82f to pillColor.copy(alpha = baseTextAlpha),
                                        1.00f to pillColor.copy(alpha = baseTextAlpha),
                                        start = Offset(currentX, -tiltYPx),
                                        end = Offset(currentX + bandWidthPx, tiltYPx)
                                    )
                                } else null

                                Text(
                                    text = pillText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.5.sp,
                                        letterSpacing = 0.25.sp,
                                        brush = textBrush
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = if (shouldShimmer) Color.Unspecified else pillColor.copy(alpha = baseTextAlpha)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = rightTimeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = contentColor,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showRemainingTime = !showRemainingTime
                        }
                )
            }
        }
    }
}

/**
 * Footer showing song metadata at the bottom of lyrics.
 * Displays song name, artist, album, and lyrics provider — only when values are present.
 * Styled to look "always inactive" with small text and low opacity.
 */
@Composable
internal fun LyricsMetadataFooter(
    currentSong: SongItem?,
    selectedProvider: String?,
    providerResults: Map<String, LyricsResult>,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    if (currentSong == null) return

    // Resolve active result using the prioritized matching logic
    val activeResult = providerResults[selectedProvider] ?: providerResults.values
        .filter { it.confidence >= 0f && it.hasLyrics() }
        .maxWithOrNull(
            compareBy<LyricsResult> { it.isWordByWord }
                .thenBy { it.isSynced }
                .thenBy { it.confidence }
        )

    val albumName = activeResult?.matchedAlbum
    val providerName = activeResult?.providerName ?: selectedProvider

    val mutedColor = contentColor.copy(alpha = 0.35f)
    val metaStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp,
        color = mutedColor
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Song title
        if (currentSong.title.isNotBlank()) {
            Text(text = currentSong.title, style = metaStyle, maxLines = 1)
        }
        // Artist
        if (currentSong.artist.isNotBlank() &&
            !currentSong.artist.equals("Unknown Artist", ignoreCase = true)
        ) {
            Text(text = currentSong.artist, style = metaStyle, maxLines = 1)
        }
        // Album (from lyrics provider match)
        if (!albumName.isNullOrBlank()) {
            Text(text = albumName, style = metaStyle, maxLines = 1)
        }
        // Lyrics provider
        if (!providerName.isNullOrBlank()) {
            Text(
                text = "Lyrics by $providerName",
                style = metaStyle,
                maxLines = 1
            )
        }
    }
}
