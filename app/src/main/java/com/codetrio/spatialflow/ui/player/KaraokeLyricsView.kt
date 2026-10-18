package com.codetrio.spatialflow.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricWord
import com.codetrio.spatialflow.data.lyrics.LyricsBreakUtils
import com.codetrio.spatialflow.data.lyrics.LyricsRomanizer
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexLyricsActive
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexLyricsBackground
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexLyricsInactive
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexLyricsSub
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexNonRounded
import com.codetrio.spatialflow.ui.theme.SpatialShapes
import com.codetrio.spatialflow.ui.theme.observeKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L
private const val MANUAL_SCROLL_DEBOUNCE_MS = 50L
private const val MIN_SWEEP_MS = 180L
private const val ACTIVE_WORD_LIFT_DP = 1.2f

// ── Long-Word Vocal Swell & Sweep Scaling Tokens (e.g., "to NIGHT" sustain) ──
private const val LONG_WORD_THRESHOLD_MS = 500L
private const val MAX_WORD_SCALE_BOOST = 0.065f

/**
 * Apple Music-styled High-precision Karaoke Canvas View.
 * Features 35% focus anchoring, first-focus smooth fade-in, manual drag auto-resume,
 * sub-frame playback clock, physics-driven word sweep, and ruby romanization word placements.
 */
@Composable
fun KaraokeLyricsView(
    lyrics: List<LyricLine>,
    currentPositionProvider: () -> Int,
    isPlayingProvider: () -> Boolean,
    playbackSpeedProvider: () -> Float,
    songDurationMs: Long = 0L,
    onSeekTo: (Int) -> Unit,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onLineLongClick: ((LyricLine) -> Unit)? = null,
    footerContent: @Composable () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
    }
    val isRomanizationEnabled by prefs.observeKey("lyrics_romanization_enabled", true)
    val hasRomanizableScript = remember(lyrics) {
        LyricsRomanizer.hasRomanizableScript(lyrics)
    }

    // ── Sub-frame clock state ──
    var interpolatedPositionMs by remember { mutableFloatStateOf(currentPositionProvider().toFloat()) }
    val currentPositionMsProvider = remember { { interpolatedPositionMs.toLong() } }

    // ── User scroll interaction state ──
    var isUserInteracting by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isUserInteracting, lastManualScrollTime) {
        if (isUserInteracting) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isUserInteracting = false
        }
    }

    val nestedScrollConnection = remember {
        var lastUserScrollEventMs = 0L
        object : NestedScrollConnection {
            private fun markManualScroll() {
                val now = System.currentTimeMillis()
                if (now - lastUserScrollEventMs >= MANUAL_SCROLL_DEBOUNCE_MS) {
                    isUserInteracting = true
                    lastManualScrollTime = now
                    lastUserScrollEventMs = now
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 1f) {
                    markManualScroll()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isUserInteracting = true
                lastManualScrollTime = System.currentTimeMillis()
                return super.onPostFling(consumed, available)
            }
        }
    }

    // ── High-precision sub-frame clock tick with frame deltas ──
    LaunchedEffect(Unit) {
        var lastSystemNanos = System.nanoTime()

        while (isActive) {
            withFrameNanos { frameNanos ->
                val deltaNanos = frameNanos - lastSystemNanos
                lastSystemNanos = frameNanos

                val isPlaying = isPlayingProvider()
                val speed = playbackSpeedProvider()
                val actualMs = currentPositionProvider().toFloat()

                if (isPlaying) {
                    val deltaMs = (deltaNanos / 1_000_000f) * speed
                    val projected = interpolatedPositionMs + deltaMs
                    val drift = actualMs - projected

                    if (abs(drift) > 1000f) {
                        // User seeked or track changed — hard snap to actual position
                        interpolatedPositionMs = actualMs
                    } else {
                        // Smooth monotonic forward step with subtle 5% drift correction
                        interpolatedPositionMs = (projected + drift * 0.05f).coerceAtLeast(interpolatedPositionMs)
                    }
                } else {
                    // Paused: Lock clock immediately to actual media player position
                    interpolatedPositionMs = actualMs
                }
            }
        }
    }

    // ── Inject instrumental break interludes (Intro, Gap >= 5s, Outro) ──
    val displayLyrics = remember(lyrics, songDurationMs) {
        val merged = mergeBackgroundVocalLines(lyrics)
        LyricsBreakUtils.insertInstrumentalBreaks(merged, songDurationMs)
    }

    // ── Active line index computation: holds focus until word & vocal sweeps complete ──
    val activeIndex by remember(displayLyrics) {
        derivedStateOf {
            val currentPos = interpolatedPositionMs.toLong()
            if (displayLyrics.isEmpty()) -1
            else {
                var candidate = -1
                for (i in displayLyrics.indices) {
                    val line = displayLyrics[i]
                    if (line.isInterlude) continue
                    if (line.startTimeMs <= currentPos) {
                        candidate = i
                    } else break
                }

                // If candidate is a background vocal line, fall back to preceding main line
                while (candidate > 0 && displayLyrics[candidate].isBackground) {
                    candidate--
                }

                // If candidate > 0, ensure preceding line has finished its active word sweep before scrolling
                if (candidate > 0) {
                    var prevMainIdx = candidate - 1
                    while (prevMainIdx > 0 && (displayLyrics[prevMainIdx].isInterlude || displayLyrics[prevMainIdx].isBackground)) {
                        prevMainIdx--
                    }
                    if (prevMainIdx >= 0) {
                        val prevLine = displayLyrics[prevMainIdx]
                        val nextStart = displayLyrics[candidate].startTimeMs
                        val prevSweepEnd = getLineSweepEndTimeMs(prevLine, nextStart)
                        // If the previous line's words or background vocals are STILL sweeping, hold active focus!
                        if (currentPos < prevSweepEnd) {
                            candidate = prevMainIdx
                        }
                    }
                }

                candidate
            }
        }
    }

    var awaitingFirstFocus by remember(displayLyrics) { mutableStateOf(displayLyrics.isNotEmpty()) }
    LaunchedEffect(awaitingFirstFocus) {
        if (!awaitingFirstFocus) return@LaunchedEffect
        delay(400L)
        awaitingFirstFocus = false
    }
    val firstFocusAlpha by animateFloatAsState(
        targetValue = if (awaitingFirstFocus) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (awaitingFirstFocus) 0 else 200,
            easing = LinearEasing,
        ),
        label = "firstFocusAlpha",
    )

    // ── Outer Container with Lyrics and Floating Controls ──
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        // ── Edge-faded lyrics scroll container with initial first-focus fade ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = firstFocusAlpha
                }
                .drawWithContent {
                    drawContent()
                    val h = size.height
                    if (h > 0f) {
                        val topFadePx = 54.dp.toPx().coerceAtMost(h * 0.15f)
                        val bottomFadePx = 76.dp.toPx().coerceAtMost(h * 0.22f)

                        val topRatio = (topFadePx / h).coerceIn(0f, 0.35f)
                        val bottomRatio = (1f - (bottomFadePx / h)).coerceIn(topRatio, 1f)

                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                topRatio to Color.Black,
                                bottomRatio to Color.Black,
                                1.0f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
        var previousActiveIndex by remember { mutableIntStateOf(-1) }

        // ── Native Spring Focal Scroll Engine (Pull Effect & Seek Bounce) ──
        LaunchedEffect(activeIndex, isUserInteracting, displayLyrics) {
            if (isUserInteracting || displayLyrics.isEmpty() || activeIndex !in displayLyrics.indices) return@LaunchedEffect

            val isSeek = previousActiveIndex == -1 || activeIndex != previousActiveIndex + 1
            previousActiveIndex = activeIndex

            try {
                val targetIndexInList = activeIndex + 1 // +1 for head_spacer
                val visibleInfo = listState.layoutInfo
                val viewportHeight = visibleInfo.viewportSize.height
                if (viewportHeight <= 0) return@LaunchedEffect
                val targetOffset = (viewportHeight * 0.28f).toInt()

                val itemInfo = visibleInfo.visibleItemsInfo.firstOrNull { it.index == targetIndexInList }
                if (itemInfo != null) {
                    val scrollDistance = (itemInfo.offset - targetOffset).toFloat()
                    if (abs(scrollDistance) > 3f) {
                        listState.animateScrollBy(
                            value = scrollDistance,
                            animationSpec = if (isSeek) {
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                )
                            } else {
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 85f,
                                    visibilityThreshold = 0.05f
                                )
                            }
                        )
                    }
                } else {
                    val firstVisible = listState.firstVisibleItemIndex
                    val distance = abs(targetIndexInList - firstVisible)
                    if (distance > 15) {
                        listState.scrollToItem((targetIndexInList - 2).coerceAtLeast(0), 0)
                    }
                    listState.animateScrollToItem(
                        index = targetIndexInList,
                        scrollOffset = -targetOffset
                    )
                }
            } catch (_: Exception) {
                // Ignore scroll interruptions during fast seeks
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "head_spacer") {
                Spacer(modifier = Modifier.height(110.dp))
            }

            itemsIndexed(
                items = displayLyrics,
                key = { index, item -> item.startTimeMs * 31L + index },
                contentType = { _, item -> if (item.isInterlude) "interlude" else "lyric_line" }
            ) { index, line ->
                val isActive = index == activeIndex
                val distanceFromActive = if (activeIndex >= 0) {
                    abs(index - activeIndex)
                } else {
                    Int.MAX_VALUE
                }
                val lineEndMs = if (index + 1 < displayLyrics.size) {
                    displayLyrics[index + 1].startTimeMs
                } else {
                    line.startTimeMs + 5000
                }
                val isInterludeVisibleState = remember(line, lineEndMs) {
                    derivedStateOf {
                        line.isInterlude && currentPositionMsProvider() >= line.startTimeMs && currentPositionMsProvider() < lineEndMs
                    }
                }
                val isInterludeVisible = isInterludeVisibleState.value

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false }
                ) {
                    if (line.isInterlude) {
                        AnimatedVisibility(
                            visible = isInterludeVisible,
                            enter = slideInVertically(
                                initialOffsetY = { -it },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(
                                animationSpec = tween(280)
                            ) + expandVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { -it },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeOut(
                                animationSpec = tween(200)
                            ) + shrinkVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        ) {
                            InterludeItem(
                                isActive = isInterludeVisible,
                                currentPositionMsProvider = currentPositionMsProvider,
                                line = line,
                                nextLineStartMs = lineEndMs,
                                accentColor = accentColor,
                                contentColor = contentColor,
                                onClick = { onSeekTo(line.startTimeMs.toInt()) }
                            )
                        }
                    } else {
                        KaraokeLineItem(
                            line = line,
                            isActive = isActive,
                            distanceFromActive = distanceFromActive,
                            isUserInteracting = isUserInteracting,
                            isRomanizationEnabled = isRomanizationEnabled,
                            currentPositionMsProvider = currentPositionMsProvider,
                            lineStartMs = line.startTimeMs,
                            lineEndMs = lineEndMs,
                            accentColor = accentColor,
                            contentColor = contentColor,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSeekTo(line.startTimeMs.toInt())
                            },
                            onLongClick = onLineLongClick?.let { callback -> { callback(line) } }
                        )
                    }
                }

                // ── Native Accordion Spacer ──
                BounceSpacer(index = index, activeIndex = activeIndex, listState = listState)
            }

            item(key = "lyrics_footer") {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    footerContent()
                }
            }

            item(key = "footer_spacer") {
                Spacer(modifier = Modifier.height(300.dp))
            }
        }
    }

    // ── Floating Romanization Toggle Button ──
    // Placed outside the edge-fade container so it has NO gradient or brush affecting it and remains 100% visible
    AnimatedVisibility(
        visible = hasRomanizableScript,
        enter = fadeIn(animationSpec = tween(200)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(
            targetScale = 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 14.dp, bottom = 12.dp)
    ) {
        val squircleShape = SpatialShapes.squircle(8.dp)
        val haptic = LocalHapticFeedback.current

        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                prefs.edit().putBoolean("lyrics_romanization_enabled", !isRomanizationEnabled).apply()
            },
            shape = squircleShape,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isRomanizationEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (isRomanizationEnabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = "Toggle Romanization",
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // ── Floating Resume Button for Manual Scrolling ──
    AnimatedVisibility(
        visible = isUserInteracting && activeIndex in displayLyrics.indices,
        enter = fadeIn(animationSpec = tween(180)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(
            targetScale = 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    ) {
        FilledTonalButton(
            onClick = {
                isUserInteracting = false
            },
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Resume",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
}


private data class VisualWord(
    val text: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val isTimed: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimatedWord(
    word: VisualWord,
    wordIndex: Int = 0,
    isActive: Boolean,
    currentPositionMsProvider: () -> Long,
    textColor: Color,
    dimColor: Color,
    textStyle: TextStyle,
) {
    if (!word.isTimed) {
        Text(
            text = word.text,
            style = textStyle,
            color = if (isActive) dimColor else dimColor.copy(alpha = 0.5f)
        )
        return
    }

    val wordStartMs = word.startTimeMs
    val wordEndMs = wordStartMs + word.durationMs
    val wordDuration = word.durationMs.coerceAtLeast(1L)
    val chainOffsetMs = (wordIndex * 40L).coerceAtMost(280L)
    val effectiveLiftStartMs = wordStartMs + chainOffsetMs

    val isWordActive by remember(wordStartMs, wordEndMs) {
        derivedStateOf {
            val pos = currentPositionMsProvider()
            pos in wordStartMs until wordEndMs
        }
    }
    val isWordComplete by remember(wordEndMs) {
        derivedStateOf {
            currentPositionMsProvider() >= wordEndMs
        }
    }

    val wordTextStyle = textStyle

    val glowPadding = 10.dp
    val density = LocalDensity.current
    val softEdgePx = with(density) { 24.dp.toPx() }
    val maxLiftPx = with(density) { ACTIVE_WORD_LIFT_DP.dp.toPx() }

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val glowPaddingPx = glowPadding.roundToPx()
                val looseConstraints = constraints.copy(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity
                )
                val placeable = measurable.measure(looseConstraints)
                val coreWidth = (placeable.width - glowPaddingPx * 2).coerceAtLeast(0)
                val coreHeight = (placeable.height - glowPaddingPx * 2).coerceAtLeast(0)
                layout(coreWidth, coreHeight) {
                    placeable.place(-glowPaddingPx, -glowPaddingPx)
                }
            }
            .graphicsLayer {
                clip = false
                val posMs = currentPositionMsProvider()
                val isLongWord = wordDuration >= LONG_WORD_THRESHOLD_MS
                val durationWeight = ((wordDuration - LONG_WORD_THRESHOLD_MS).toFloat() / 1500f).coerceIn(0f, 1f)

                val rawProgress = when {
                    posMs >= wordEndMs -> 1f
                    posMs <= wordStartMs -> 0f
                    else -> ((posMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
                }

                // Dynamic scale expansion for sustained notes (like "to NIGHT" by The Weeknd):
                // Held vowels swell smoothly by up to +6.5% during their vocal duration via a sine curve
                val targetScaleBoost = if (isLongWord) {
                    MAX_WORD_SCALE_BOOST * durationWeight
                } else {
                    0.015f
                }
                val sinSwell = if (posMs in wordStartMs until wordEndMs) sin(rawProgress * PI).toFloat() else 0f
                val wordScale = 1.0f + (targetScaleBoost * sinSwell)

                scaleX = wordScale
                scaleY = wordScale
                transformOrigin = TransformOrigin(0.5f, 0.5f)

                val liftProgress = when {
                    !isActive && !isWordActive -> 0f
                    posMs >= wordEndMs -> 1f
                    posMs <= effectiveLiftStartMs -> 0f
                    else -> ((posMs - effectiveLiftStartMs).toFloat() / (wordEndMs - effectiveLiftStartMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                }
                val easedLift = liftProgress * liftProgress * (3f - 2f * liftProgress)
                translationY = if (isActive || isWordActive) -maxLiftPx * easedLift else 0f
            }
    ) {
        // Layer 1: Dim base text
        Text(
            text = word.text,
            style = textStyle,
            color = dimColor,
            modifier = Modifier.padding(glowPadding)
        )

        // Layer 2: Lit/Glowing active text overlay with soft gradient sweep mask
        if ((isWordComplete && isActive) || isWordActive) {
            Text(
                text = word.text,
                style = wordTextStyle,
                color = textColor,
                modifier = Modifier
                    .padding(glowPadding)
                    .then(
                        if (isWordActive && !isWordComplete) {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    if (size.width > 0f) {
                                        val posMs = currentPositionMsProvider()
                                        val isLongWord = wordDuration >= LONG_WORD_THRESHOLD_MS
                                        val durationWeight = ((wordDuration - LONG_WORD_THRESHOLD_MS).toFloat() / 1500f).coerceIn(0f, 1f)
                                        val rawProgress = when {
                                            posMs >= wordEndMs -> 1f
                                            posMs <= wordStartMs -> 0f
                                            else -> ((posMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
                                        }
                                        val sweepProgress = rawProgress * rawProgress * (3f - 2f * rawProgress)
                                        val dynamicSoftEdgePx = if (isLongWord) {
                                            softEdgePx * (1f + 0.6f * durationWeight)
                                        } else {
                                            softEdgePx
                                        }
                                        val headX = (size.width + dynamicSoftEdgePx * 2f) * sweepProgress - dynamicSoftEdgePx

                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colorStops = arrayOf(
                                                    0.0f to Color.Black,
                                                    ((headX - dynamicSoftEdgePx).coerceAtLeast(0f) / size.width).coerceIn(0f, 1f) to Color.Black,
                                                    (headX.coerceAtLeast(0f) / size.width).coerceIn(0f, 1f) to Color.Black.copy(alpha = 0.5f),
                                                    ((headX + dynamicSoftEdgePx).coerceAtLeast(0f) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                                                    1.0f to Color.Transparent
                                                )
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                }
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KaraokeLineItem(
    line: LyricLine,
    isActive: Boolean,
    distanceFromActive: Int,
    isUserInteracting: Boolean = false,
    isRomanizationEnabled: Boolean = true,
    currentPositionMsProvider: () -> Long,
    lineStartMs: Long,
    lineEndMs: Long,
    accentColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val isKaraoke = line.isWordByWord && line.words.isNotEmpty()

    val lineSweepEndMs = remember(line, lineStartMs, lineEndMs) {
        getLineSweepEndTimeMs(line, lineEndMs)
    }

    val isStillSweepingState = remember(lineSweepEndMs, lineStartMs) {
        derivedStateOf {
            val pos = currentPositionMsProvider()
            lineSweepEndMs > lineStartMs && pos in lineStartMs until lineSweepEndMs
        }
    }
    val isStillSweeping = isStillSweepingState.value
    val isVisuallyActive = isActive || isStillSweeping

    val alpha by animateFloatAsState(
        targetValue = when {
            isVisuallyActive -> 1.0f
            isUserInteracting -> {
                when {
                    distanceFromActive == 1 -> 0.88f
                    distanceFromActive == 2 -> 0.72f
                    distanceFromActive == 3 -> 0.55f
                    else -> 0.40f
                }
            }
            distanceFromActive == 1 -> 0.48f
            distanceFromActive == 2 -> 0.28f
            else -> 0.18f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "KaraokeAlpha"
    )

    val scale = 1.0f

    val blurRadius by animateDpAsState(
        targetValue = when {
            isVisuallyActive || isUserInteracting -> 0.dp
            distanceFromActive == 1 -> 0.8.dp
            distanceFromActive == 2 -> 2.0.dp
            else -> 3.5.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "KaraokeBlur"
    )

    // Use TTML-sourced background vocals if available, fall back to bracket regex for plain lyrics
    val bracketMatch = remember(line.content, line.backgroundContent, line.isBackground) {
        if (line.backgroundContent != null || line.isBackground) null
        else Regex("""^(.+?)\s*((\([^\)]+\)\s*|\[[^\]]+\]\s*|\{[^\}]+\}\s*)+)$""").find(line.content.trim())
    }

    val hasValidMainContent = remember(bracketMatch, line.isBackground) {
        if (line.isBackground) false
        else if (bracketMatch != null) {
            val mainText = bracketMatch.groupValues[1].trim()
            val bracketText = bracketMatch.groupValues[2].trim()
            mainText.isNotBlank() && bracketText.isNotBlank() && !mainText.startsWith("(") && !mainText.startsWith("[") && !mainText.startsWith("{")
        } else false
    }

    val mainContent = remember(line.content, line.backgroundContent, bracketMatch, hasValidMainContent, line.isBackground) {
        if (line.isBackground) {
            ""
        } else if (hasValidMainContent) {
            bracketMatch!!.groupValues[1].trimEnd()
        } else {
            line.content.trimEnd()
        }
    }

    val bracketContent = remember(line.content, line.backgroundContent, bracketMatch, hasValidMainContent, line.isBackground) {
        if (line.isBackground) {
            line.content.trimEnd()
        } else if (line.backgroundContent != null) {
            line.backgroundContent.trimEnd()
        } else if (hasValidMainContent) {
            bracketMatch!!.groupValues[2].trim()
        } else {
            null
        }
    }

    val bgWords = remember(line.words, line.backgroundWords, line.backgroundContent, line.isBackground) {
        if (line.backgroundWords.isNotEmpty()) {
            line.backgroundWords.map { it.copy(isBackground = true) }
        } else if (line.isBackground) {
            line.words.map { it.copy(isBackground = true) }
        } else {
            line.words.filter { it.isBackground }
        }
    }
    val hasBgKaraoke = bgWords.isNotEmpty()
    val hasBracketLayer = !bracketContent.isNullOrBlank()
    val bracketTimingWords = if (hasBgKaraoke) {
        bgWords
    } else if (hasBracketLayer && isKaraoke) {
        val bgWordCandidates = line.words.filter { it.charRange.first >= mainContent.length || it.isBackground }
        if (bgWordCandidates.isNotEmpty()) {
            bgWordCandidates
        } else {
            val bracketTokens = bracketContent.split(" ").filter { it.isNotEmpty() }
            if (bracketTokens.isNotEmpty() && line.words.size > bracketTokens.size) {
                line.words.takeLast(bracketTokens.size)
            } else {
                emptyList()
            }
        }
    } else {
        emptyList()
    }

    val mainTimingWords = remember(line.words, mainContent, hasValidMainContent) {
        if (line.words.any { it.isBackground }) {
            line.words.filter { !it.isBackground }
        } else if (hasValidMainContent) {
            line.words.filter { it.charRange.last <= mainContent.length && !it.isBackground }
        } else {
            line.words
        }
    }

    val bgStartMs = remember(bracketTimingWords, bgWords, line, lineStartMs, lineEndMs, mainTimingWords) {
        val wordStart = bracketTimingWords.map { it.absoluteStartTimeMs }.filter { it > 0L }.minOrNull()
            ?: bgWords.map { it.absoluteStartTimeMs }.filter { it > 0L }.minOrNull()
        if (wordStart != null && wordStart > 0L) {
            wordStart
        } else {
            val mainWordsEnd = mainTimingWords.map { it.absoluteStartTimeMs + it.durationMs }.filter { it > 0L }.maxOrNull()
            if (mainWordsEnd != null && mainWordsEnd > lineStartMs) {
                mainWordsEnd
            } else {
                val dur = (lineEndMs - lineStartMs).coerceAtLeast(1000L)
                lineStartMs + (dur * 0.5f).toLong()
            }
        }
    }

    val bgEndMs = remember(bracketTimingWords, bgWords, line, lineEndMs, bgStartMs) {
        val wordEnd = bracketTimingWords.map { it.absoluteStartTimeMs + it.durationMs }.filter { it > 0L }.maxOrNull()
            ?: bgWords.map { it.absoluteStartTimeMs + it.durationMs }.filter { it > 0L }.maxOrNull()
        if (wordEnd != null && wordEnd > bgStartMs) {
            wordEnd
        } else {
            lineEndMs.coerceAtLeast(bgStartMs + 1500L)
        }
    }

    val isStandaloneBackground = mainContent.isBlank()
    val isBracketVisibleState = remember(bgStartMs, bgEndMs, isStandaloneBackground) {
        derivedStateOf {
            if (isStandaloneBackground) {
                true
            } else {
                val pos = currentPositionMsProvider()
                val hasStarted = pos >= (bgStartMs - 350L)
                val hasEnded = pos >= (bgEndMs + 350L)
                hasStarted && !hasEnded
            }
        }
    }
    val isBracketVisible = if (isUserInteracting) true else isBracketVisibleState.value

    val isBracketActiveState = remember(bgStartMs, bgEndMs) {
        derivedStateOf {
            val pos = currentPositionMsProvider()
            pos in (bgStartMs - 150L)..(bgEndMs + 200L)
        }
    }
    val isBracketActive = isBracketActiveState.value

    val density = LocalDensity.current
    val dimColor = contentColor.copy(alpha = 0.45f)
    val litColor = contentColor
    val bgLitColor = contentColor.copy(alpha = 0.75f)
    val bgDimColor = contentColor.copy(alpha = 0.30f)

    val mainTextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontFamily = GoogleSansFlexLyricsActive,
        fontSize = 42.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 52.sp,
        letterSpacing = (-0.2).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val visualWords = remember(mainTimingWords, mainContent, lineStartMs, lineEndMs) {
        val validTimingWords = mainTimingWords.filter { it.absoluteStartTimeMs >= 0L && it.durationMs > 0L }
        if (validTimingWords.isNotEmpty()) {
            val list = mutableListOf<VisualWord>()
            var lastIdx = 0
            validTimingWords.forEach { word ->
                val wStart = word.charRange.first.coerceIn(0, mainContent.length)
                val wEnd = (word.charRange.last + 1).coerceIn(0, mainContent.length)
                if (wStart > lastIdx) {
                    val gapText = mainContent.substring(lastIdx, wStart)
                    if (gapText.isNotEmpty()) {
                        val cleanGap = if (gapText.all { it.isWhitespace() }) " " else gapText.replace(Regex("[ \\t]+"), " ")
                        list.add(VisualWord(text = cleanGap, startTimeMs = 0L, durationMs = 0L, isTimed = false))
                    }
                }
                val wordText = if (wEnd > wStart) mainContent.substring(wStart, wEnd) else word.text
                list.add(VisualWord(text = wordText, startTimeMs = word.absoluteStartTimeMs, durationMs = word.durationMs, isTimed = true))
                lastIdx = wEnd
            }
            if (lastIdx < mainContent.length) {
                val trailingText = mainContent.substring(lastIdx)
                val cleanTrailing = trailingText.replace(Regex("[ \\t]+"), " ").trimEnd()
                if (cleanTrailing.isNotEmpty()) {
                    list.add(VisualWord(text = cleanTrailing, startTimeMs = 0L, durationMs = 0L, isTimed = false))
                }
            }
            list
        } else {
            val rawTokens = mainContent.split(" ")
            val nonSpaceTokens = rawTokens.filter { it.isNotEmpty() }
            if (nonSpaceTokens.isEmpty()) {
                listOf(VisualWord(text = mainContent, startTimeMs = lineStartMs, durationMs = (lineEndMs - lineStartMs).coerceAtLeast(500L), isTimed = true))
            } else {
                val lineDuration = (lineEndMs - lineStartMs).coerceAtLeast(600L)
                val wordDur = (lineDuration / nonSpaceTokens.size).coerceAtLeast(150L)
                val list = mutableListOf<VisualWord>()
                var currentStart = lineStartMs
                rawTokens.forEachIndexed { idx, token ->
                    if (token.isNotEmpty()) {
                        list.add(VisualWord(text = token, startTimeMs = currentStart, durationMs = wordDur, isTimed = true))
                        currentStart += wordDur
                        if (idx < rawTokens.lastIndex) {
                            list.add(VisualWord(text = " ", startTimeMs = 0L, durationMs = 0L, isTimed = false))
                        }
                    }
                }
                list
            }
        }
    }

    val bracketVisualWords = remember(bracketTimingWords, bracketContent, bgStartMs, bgEndMs) {
        val content = bracketContent ?: return@remember emptyList<VisualWord>()
        val rawTokens = content.split(" ")
        val nonSpaceTokens = rawTokens.filter { it.isNotEmpty() }
        if (nonSpaceTokens.isEmpty()) {
            return@remember listOf(VisualWord(text = content, startTimeMs = bgStartMs, durationMs = (bgEndMs - bgStartMs).coerceAtLeast(400L), isTimed = true))
        }

        val validTimingWords = bracketTimingWords.filter { it.absoluteStartTimeMs >= 0L && it.durationMs > 0L }

        // If we have distinct syllable/word timestamps matching our backing vocal tokens
        if (validTimingWords.size >= nonSpaceTokens.size && nonSpaceTokens.size > 1) {
            val list = mutableListOf<VisualWord>()
            var timingIdx = 0
            rawTokens.forEachIndexed { idx, token ->
                if (token.isNotEmpty()) {
                    val timing = validTimingWords.getOrNull(timingIdx)
                    val wStart = timing?.absoluteStartTimeMs ?: bgStartMs
                    val wDur = timing?.durationMs ?: ((bgEndMs - bgStartMs) / nonSpaceTokens.size).coerceAtLeast(150L)
                    list.add(VisualWord(text = token, startTimeMs = wStart, durationMs = wDur, isTimed = true))
                    timingIdx++
                    if (idx < rawTokens.lastIndex) {
                        list.add(VisualWord(text = " ", startTimeMs = 0L, durationMs = 0L, isTimed = false))
                    }
                }
            }
            list
        } else {
            // Proportional timing across non-whitespace words based on token length
            val bgActiveDuration = (bgEndMs - bgStartMs).coerceAtLeast(300L)
            val totalChars = nonSpaceTokens.sumOf { it.length }.coerceAtLeast(1)
            val list = mutableListOf<VisualWord>()
            var currentStart = bgStartMs
            rawTokens.forEachIndexed { idx, token ->
                if (token.isNotEmpty()) {
                    val tokenFraction = token.length.toFloat() / totalChars
                    val tokenDur = (tokenFraction * bgActiveDuration).toLong().coerceAtLeast(150L)
                    list.add(VisualWord(text = token, startTimeMs = currentStart, durationMs = tokenDur, isTimed = true))
                    currentStart += tokenDur
                    if (idx < rawTokens.lastIndex) {
                        list.add(VisualWord(text = " ", startTimeMs = 0L, durationMs = 0L, isTimed = false))
                    }
                }
            }
            list
        }
    }

    val bracketTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = GoogleSansFlexLyricsBackground,
        fontSize = 26.sp,
        fontWeight = FontWeight.Medium,
        fontStyle = FontStyle.Italic,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val romanizedTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = GoogleSansFlexLyricsSub,
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val isRtl = remember(line.content) { isRtlText(line.content) }
    val isAlignEnd = line.isRightAligned || isRtl

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(start = 28.dp, end = 28.dp, top = 10.dp, bottom = 10.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                clip = false
            }
            .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        horizontalAlignment = if (isAlignEnd) Alignment.End else Alignment.Start
    ) {
        // ── 1. Main Text Box ──
        if (isKaraoke) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isAlignEnd) Arrangement.End else Arrangement.Start
            ) {
                visualWords.forEachIndexed { wordIndex, word ->
                    AnimatedWord(
                        word = word,
                        wordIndex = wordIndex,
                        isActive = isVisuallyActive,
                        currentPositionMsProvider = currentPositionMsProvider,
                        textColor = litColor,
                        dimColor = dimColor,
                        textStyle = mainTextStyle,
                    )
                }
            }
        } else {
            Text(
                text = mainContent,
                style = mainTextStyle,
                color = if (isVisuallyActive) litColor else dimColor,
                textAlign = if (isAlignEnd) TextAlign.End else TextAlign.Start,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── 2. Subtitle / Translation / Romanized Text (Placed BELOW Main Line) ──
        if (!line.romanizedContent.isNullOrBlank()) {
            AnimatedVisibility(
                visible = isRomanizationEnabled,
                enter = fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                        expandVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) +
                        scaleIn(
                            initialScale = 0.88f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                exit = fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                       shrinkVertically(
                           animationSpec = spring(
                               dampingRatio = Spring.DampingRatioMediumBouncy,
                               stiffness = Spring.StiffnessMediumLow
                           )
                       ) +
                       slideOutVertically(
                           targetOffsetY = { it / 2 },
                           animationSpec = spring(
                               dampingRatio = Spring.DampingRatioMediumBouncy,
                               stiffness = Spring.StiffnessMediumLow
                           )
                       ) +
                       scaleOut(
                           targetScale = 0.88f,
                           animationSpec = spring(
                               dampingRatio = Spring.DampingRatioMediumBouncy,
                               stiffness = Spring.StiffnessMediumLow
                           )
                       )
            ) {
                Column(
                    horizontalAlignment = if (isAlignEnd) Alignment.End else Alignment.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val romanizedText = line.romanizedContent
                    val hasWordTimings = isKaraoke && mainTimingWords.any { it.absoluteStartTimeMs >= 0L && it.durationMs > 0L }
                    if (hasWordTimings && isActive) {
                        val romanizedWords = remember(romanizedText, line.romanizedWords) {
                            if (line.romanizedWords.isNotEmpty()) {
                                line.romanizedWords.filterNotNull().filter { it.isNotBlank() }
                            } else {
                                romanizedText.split(" ").filter { it.isNotBlank() }
                            }
                        }
                        val validTimings = remember(mainTimingWords) {
                            mainTimingWords.filter { it.absoluteStartTimeMs >= 0L && it.durationMs > 0L }
                        }
                        if (romanizedWords.isNotEmpty() && validTimings.isNotEmpty()) {
                            val currentPos = currentPositionMsProvider()
                            val wordCount = romanizedWords.size
                            val timingsCount = validTimings.size

                            val annotatedRomanized = remember(romanizedWords, validTimings, currentPos, litColor, dimColor) {
                                buildAnnotatedString {
                                    romanizedWords.forEachIndexed { index, wordText ->
                                        val timingIndex = (index.toFloat() / wordCount * timingsCount).toInt().coerceIn(0, timingsCount - 1)
                                        val timing = validTimings[timingIndex]
                                        val wStartMs = timing.absoluteStartTimeMs
                                        val wDurMs = timing.durationMs.coerceAtLeast(1L)
                                        val wEndMs = wStartMs + wDurMs

                                        val isWordActive = currentPos in wStartMs until wEndMs
                                        val hasWordPassed = currentPos >= wEndMs

                                        val rawProgress = if (isWordActive && wDurMs > 0) {
                                            ((currentPos - wStartMs).toFloat() / wDurMs).coerceIn(0f, 1f)
                                        } else if (hasWordPassed) {
                                            1f
                                        } else {
                                            0f
                                        }
                                        val smoothProgress = rawProgress * rawProgress * (3f - 2f * rawProgress)

                                        val romAlpha = when {
                                            hasWordPassed -> 0.85f
                                            isWordActive -> 0.35f + (0.50f * smoothProgress)
                                            else -> 0.35f
                                        }

                                        withStyle(
                                            style = SpanStyle(
                                                color = litColor.copy(alpha = romAlpha)
                                            )
                                        ) {
                                            append(wordText)
                                        }
                                        if (index < romanizedWords.lastIndex) {
                                            append(" ")
                                        }
                                    }
                                }
                            }

                            Text(
                                text = annotatedRomanized,
                                style = romanizedTextStyle,
                                textAlign = if (isAlignEnd) TextAlign.End else TextAlign.Start,
                                softWrap = true,
                                maxLines = Int.MAX_VALUE,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = romanizedText,
                                style = romanizedTextStyle,
                                color = litColor.copy(alpha = 0.60f),
                                textAlign = if (isAlignEnd) TextAlign.End else TextAlign.Start,
                                softWrap = true,
                                maxLines = Int.MAX_VALUE,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = romanizedText,
                            style = romanizedTextStyle,
                            color = if (isVisuallyActive) litColor.copy(alpha = 0.60f) else dimColor.copy(alpha = 0.35f),
                            textAlign = if (isAlignEnd) TextAlign.End else TextAlign.Start,
                            softWrap = true,
                            maxLines = Int.MAX_VALUE,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ── 3. Bracket Text Box (Secondary / Backing Vocals with Animated Appearance) ──
        if (bracketContent != null) {
            AnimatedVisibility(
                visible = isBracketVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)) +
                        expandVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            expandFrom = Alignment.Top
                        ) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) +
                        scaleIn(
                            initialScale = 0.90f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                exit = fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
                       shrinkVertically(
                           animationSpec = spring(
                               dampingRatio = Spring.DampingRatioNoBouncy,
                               stiffness = Spring.StiffnessMedium
                           ),
                           shrinkTowards = Alignment.Top
                       ) +
                       slideOutVertically(
                           targetOffsetY = { it / 2 },
                           animationSpec = spring(
                               dampingRatio = Spring.DampingRatioNoBouncy,
                               stiffness = Spring.StiffnessMedium
                           )
                       ) +
                       scaleOut(
                           targetScale = 0.90f,
                           animationSpec = spring(
                               dampingRatio = Spring.DampingRatioNoBouncy,
                               stiffness = Spring.StiffnessMedium
                           )
                       )
            ) {
                Column(
                    horizontalAlignment = if (isAlignEnd) Alignment.End else Alignment.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isAlignEnd) Arrangement.End else Arrangement.Start
                    ) {
                        bracketVisualWords.forEachIndexed { wordIndex, word ->
                            AnimatedWord(
                                word = word,
                                wordIndex = wordIndex + visualWords.size,
                                isActive = isBracketActive,
                                currentPositionMsProvider = currentPositionMsProvider,
                                textColor = bgLitColor,
                                dimColor = bgDimColor,
                                textStyle = bracketTextStyle,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Background Vocal & Sweep Completion Helpers ──

private fun getLineSweepEndTimeMs(line: LyricLine, fallbackNextStartMs: Long): Long {
    val mainWordEnd = if (line.words.isNotEmpty()) {
        line.words.filter { !it.isBackground }
            .maxOfOrNull { it.absoluteStartTimeMs + it.durationMs } ?: 0L
    } else 0L

    val bgWordEnd = if (line.backgroundWords.isNotEmpty()) {
        line.backgroundWords.maxOfOrNull { it.absoluteStartTimeMs + it.durationMs } ?: 0L
    } else if (line.words.any { it.isBackground }) {
        line.words.filter { it.isBackground }
            .maxOfOrNull { it.absoluteStartTimeMs + it.durationMs } ?: 0L
    } else if (line.words.isNotEmpty()) {
        line.words.maxOfOrNull { it.absoluteStartTimeMs + it.durationMs } ?: 0L
    } else if (line.backgroundContent != null) {
        line.startTimeMs + 3500L
    } else 0L

    val maxWordEnd = maxOf(mainWordEnd, bgWordEnd)
    return if (maxWordEnd > line.startTimeMs) {
        maxWordEnd
    } else {
        fallbackNextStartMs
    }
}

private fun mergeBackgroundVocalLines(lines: List<LyricLine>): List<LyricLine> {
    if (lines.isEmpty()) return lines
    val result = mutableListOf<LyricLine>()
    for (line in lines) {
        val prev = result.lastOrNull()
        val isBracketOnly = line.content.trim().let { it.startsWith("(") && it.endsWith(")") }
        val isBg = line.isBackground || isBracketOnly

        if (isBg && prev != null && !prev.isInterlude && prev.backgroundContent == null) {
            val prevEnd = getLineSweepEndTimeMs(prev, prev.startTimeMs + 4000L)
            // If this background vocal starts within reasonable proximity or overlaps the preceding main vocal
            if (line.startTimeMs <= prevEnd + 2500L && line.startTimeMs >= prev.startTimeMs - 500L) {
                val bgWords = if (line.words.isNotEmpty()) {
                    line.words.map { it.copy(isBackground = true) }
                } else {
                    val bgDur = (prevEnd - line.startTimeMs).coerceAtLeast(2000L)
                    listOf(LyricWord(text = line.content, absoluteStartTimeMs = line.startTimeMs, durationMs = bgDur, isBackground = true))
                }
                result[result.lastIndex] = prev.copy(
                    backgroundContent = line.content,
                    backgroundWords = bgWords
                )
                continue
            }
        }
        result.add(line)
    }
    return result
}



private fun calculateLineFillProgress(
    words: List<LyricWord>,
    posMs: Long
): Float {
    if (words.isEmpty()) return 0f

    val firstWordStartMs = words.first().absoluteStartTimeMs
    val lastWordEndMs = words.last().absoluteStartTimeMs + words.last().durationMs
    val lineDuration = (lastWordEndMs - firstWordStartMs).coerceAtLeast(1L)
    val timeElapsed = (posMs - firstWordStartMs).coerceIn(0L, lineDuration)
    return (timeElapsed.toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
}

private fun spatialFlowSweepBrush(
    text: String,
    fillProgress: Float,
    contentColor: Color,
    tailAlpha: Float
): Brush {
    val clampedProgress = fillProgress.coerceIn(0f, 1f)
    val isRtl = isRtlText(text)
    val glowColor = Color.White

    return if (isRtl) {
        val activePos = 1f - clampedProgress
        Brush.horizontalGradient(
            0.0f to contentColor.copy(alpha = tailAlpha),
            (activePos - 0.015f).coerceIn(0f, 1f) to contentColor.copy(alpha = tailAlpha),
            activePos.coerceIn(0f, 1f) to glowColor,
            (activePos + 0.15f).coerceIn(0f, 1f) to contentColor,
            1.0f to contentColor
        )
    } else {
        Brush.horizontalGradient(
            0.0f to contentColor,
            (clampedProgress - 0.15f).coerceIn(0f, 1f) to contentColor,
            clampedProgress.coerceIn(0f, 1f) to glowColor,
            (clampedProgress + 0.015f).coerceIn(0f, 1f) to contentColor.copy(alpha = tailAlpha),
            1.0f to contentColor.copy(alpha = tailAlpha)
        )
    }
}

internal fun isRtlText(text: String): Boolean {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                -> return true

            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                -> return false
        }
    }
    return false
}

private fun rtlAwareHorizontalGradient(
    isRtl: Boolean,
    vararg colorStops: Pair<Float, Color>
): Brush {
    val stops = if (isRtl) {
        colorStops
            .map { (fraction, color) -> (1f - fraction).coerceIn(0f, 1f) to color }
            .sortedBy { it.first }
    } else {
        colorStops.toList()
    }
    return Brush.horizontalGradient(*stops.toTypedArray())
}

@Composable
private fun InterludeItem(
    isActive: Boolean,
    currentPositionMsProvider: () -> Long,
    line: LyricLine,
    nextLineStartMs: Long,
    accentColor: Color,
    contentColor: Color,
    onClick: () -> Unit = {}
) {
    val duration = (nextLineStartMs - line.startTimeMs).coerceAtLeast(1)
    val rawProgress = if (isActive) {
        ((currentPositionMsProvider() - line.startTimeMs).toFloat() / duration).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "InterludeProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "InterludeBreathing")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathScale"
    )

    val density = LocalDensity.current
    val interludeStroke = remember(density) {
        Stroke(
            width = with(density) { 3.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 28.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_music_note),
            contentDescription = "Interlude",
            tint = Color.White.copy(alpha = 0.95f),
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = breatheScale
                    scaleY = breatheScale
                }
        )

        LinearWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = Color.White.copy(alpha = 0.95f),
            trackColor = Color.White.copy(alpha = 0.20f),
            stroke = interludeStroke,
            trackStroke = interludeStroke,
            wavelength = 22.dp,
            amplitude = { 0.8f }
        )
    }
}

@Composable
private fun BounceSpacer(
    index: Int,
    activeIndex: Int,
    listState: LazyListState
) {
    val targetHeightState = remember(index, activeIndex, listState) {
        derivedStateOf {
            val targetIndexInList = activeIndex + 1
            val visibleInfo = listState.layoutInfo
            val viewportHeight = visibleInfo.viewportSize.height
            val anchor = viewportHeight * 0.28f
            val activeItem = visibleInfo.visibleItemsInfo.firstOrNull { it.index == targetIndexInList }
            val scrollDistance = (activeItem?.offset?.toFloat() ?: anchor) - anchor
            val visibleCount = visibleInfo.visibleItemsInfo.size.coerceAtLeast(1)
            val firstVisible = listState.firstVisibleItemIndex
            val itemIndexInList = index + 1
            val inWindow = itemIndexInList >= targetIndexInList - 1
            val weight = if (inWindow) {
                (1f - ((itemIndexInList - firstVisible).toFloat() / visibleCount.toFloat())).coerceIn(0f, 1f)
            } else {
                0f
            }
            val thisScroll = (scrollDistance / visibleCount).coerceIn(0f, 16f)
            if (inWindow) (thisScroll * weight).coerceAtLeast(0f) else 0f
        }
    }

    val spacerHeight by animateDpAsState(
        targetValue = targetHeightState.value.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 100f,
            visibilityThreshold = 0.01.dp
        ),
        label = "v2BounceSpacer"
    )
    Spacer(modifier = Modifier.height(spacerHeight))
}

private fun calculateWordGlowAlpha(
    words: List<LyricWord>,
    posMs: Long
): Float {
    if (words.isEmpty()) return 0f
    val activeWord = words.firstOrNull { word ->
        posMs in word.absoluteStartTimeMs until (word.absoluteStartTimeMs + word.durationMs)
    } ?: return 0f
    val wordDuration = activeWord.durationMs.coerceAtLeast(80L).toFloat()
    val rawProgress = ((posMs - activeWord.absoluteStartTimeMs).toFloat() / wordDuration).coerceIn(0f, 1f)
    return (rawProgress * 2f).coerceAtMost(1f) * 0.45f
}

private fun drawBackgroundDimEffects(
    layout: TextLayoutResult,
    words: List<LyricWord>,
    wordBounds: List<Pair<Float, Float>>,
    posMs: Long,
    wordOffset: Int,
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    drawContent: () -> Unit
) {
    if (words.isEmpty() || layout.layoutInput.text.isEmpty()) {
        drawContent()
        return
    }

    val textLength = layout.layoutInput.text.length
    val width = drawScope.size.width
    val height = drawScope.size.height
    if (width <= 0f || height <= 0f) {
        drawContent()
        return
    }

    val lineWords = if (wordOffset > 0) {
        words.filter { it.charRange.first >= wordOffset }
    } else {
        words
    }

    if (lineWords.isEmpty()) {
        drawContent()
        return
    }

    val firstWord = lineWords.first()
    val lastWord = lineWords.last()

    val lineStartMs = firstWord.absoluteStartTimeMs
    val lineEndMs = lastWord.absoluteStartTimeMs + lastWord.durationMs

    if (posMs < lineStartMs || posMs >= lineEndMs) {
        drawContent()
        return
    }

    val activeWord = lineWords.find { posMs >= it.absoluteStartTimeMs && posMs < (it.absoluteStartTimeMs + it.durationMs) }
    if (activeWord == null) {
        drawContent()
        return
    }

    val activeWordStartChar = (activeWord.charRange.first - wordOffset).coerceIn(0, textLength - 1)
    val activeLineIndex = layout.getLineForOffset(activeWordStartChar)

    for (lineIdx in 0 until layout.lineCount) {
        val lineTop = layout.getLineTop(lineIdx)
        val lineBottom = layout.getLineBottom(lineIdx)

        if (lineIdx == activeLineIndex) {
            val activeWordIdx = lineWords.indexOf(activeWord)
            val bounds = wordBounds.getOrNull(activeWordIdx)
            val wLeft = bounds?.first ?: layout.getLineLeft(lineIdx)
            val wRight = bounds?.second ?: layout.getLineLeft(lineIdx)

            // Clip out the active word bounds using ClipOp.Difference
            drawScope.clipRect(
                left = wLeft,
                top = lineTop - 10f,
                right = wRight,
                bottom = lineBottom + 10f,
                clipOp = androidx.compose.ui.graphics.ClipOp.Difference
            ) {
                drawContent()
            }
        } else {
            drawScope.clipRect(
                left = 0f,
                top = lineTop - 10f,
                right = width,
                bottom = lineBottom + 10f
            ) {
                drawContent()
            }
        }
    }
}


