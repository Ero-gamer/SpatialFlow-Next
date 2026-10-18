package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.ui.player.metrics.ScreenMetrics
import com.codetrio.spatialflow.ui.theme.SpatialShapes
import kotlinx.coroutines.flow.StateFlow

private enum class PlayPauseVisualState {
    PROCESSING,
    PLAYING,
    PAUSED
}

/**
 * Bottom transport controls: seek bar + M3 Expressive [ButtonGroup] with animated
 * Prev / Play-Pause / Next buttons.
 *
 * Each transport button morphs its corner radius from pill (50 dp) to rounded-rect (16 dp)
 * while pressed, driven by a spring animation on [MutableInteractionSource].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerTransportControls(
    currentPositionProvider: () -> Int,
    duration: Int,
    isPlaying: Boolean,
    playbackFormat: String,
    contentColor: Color,
    contentSecondary: Color,
    dynamicAccentColor: Color,
    isDark: Boolean,
    metrics: ScreenMetrics,
    onSeekTo: (Int) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    currentPositionFlow: StateFlow<Int>? = null,
    allowRealtimeUpdates: Boolean = true
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Spacer(modifier = Modifier.height(if (metrics.isLandscape) 4.dp else metrics.chipGap))

        // ── Wavy seek bar ───────────────────────────────────────────────────────────
        WavySliderWithLabels(
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            isPlaying = isPlaying,
            onSeekTo = onSeekTo,
            dynamicAccentColor = dynamicAccentColor,
            contentColor = contentColor,
            contentSecondary = contentSecondary,
            isDark = isDark,
            playbackFormat = playbackFormat,
            currentPositionFlow = currentPositionFlow,
            allowRealtimeUpdates = allowRealtimeUpdates
        )

    Spacer(modifier = Modifier.height(if (metrics.isLandscape) 6.dp else metrics.seekbarGap))

    val haptic = LocalHapticFeedback.current

    // ── Play-transport ButtonGroup ───────────────────────────────────────────────
    ButtonGroup(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.marginHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        expandedRatio = 0.3f,
        overflowIndicator = {}
    ) {
        val scope = this

        // Previous
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed) 16.dp else 50.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "PrevCorner"
                )
                androidx.compose.material3.Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPreviousClick()
                    },
                    modifier = with(scope) {
                        Modifier
                            .animateWidth(interactionSource)
                            .weight(1f)
                            .height(metrics.buttonHeight)
                    },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(cornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f),
                        contentColor = contentColor
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_skip_previous),
                            contentDescription = "Previous Song",
                            tint = contentColor,
                            modifier = Modifier.size(metrics.skipIconSize)
                        )
                    }
                }
            },
            menuContent = {}
        )

        // Play / Pause
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed) 14.dp else if (isPlaying || isProcessing) 50.dp else 18.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "PlayCorner"
                )
                val playPauseVisualState = when {
                    isProcessing -> PlayPauseVisualState.PROCESSING
                    isPlaying -> PlayPauseVisualState.PLAYING
                    else -> PlayPauseVisualState.PAUSED
                }
                // WCAG AAA Dynamic Contrast:
                // Calculate perceived luminance of the button container.
                // If container is bright (e.g. yellow, cyan, lime, white), icon is crisp black.
                // If container is dark (e.g. purple, navy, dark red, dark gray), icon is pure white.
                val accentLuminance = remember(dynamicAccentColor) {
                    androidx.core.graphics.ColorUtils.calculateLuminance(dynamicAccentColor.toArgb())
                }
                val playBtnContentColor = if (accentLuminance > 0.40f) Color(0xFF141218) else Color.White
                val density = LocalDensity.current
                val wavyStroke = remember(density) {
                    Stroke(
                        width = with(density) { 4.5.dp.toPx() },
                        cap = StrokeCap.Round
                    )
                }

                androidx.compose.material3.Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPauseClick()
                    },
                    modifier = with(scope) {
                        Modifier
                            .animateWidth(interactionSource)
                            .weight(1.15f)
                            .height(metrics.buttonHeight)
                    },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(cornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dynamicAccentColor,
                        contentColor = playBtnContentColor
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = playPauseVisualState,
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.65f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(tween(180)))
                                    .togetherWith(
                                        scaleOut(
                                            targetScale = 0.65f,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeOut(tween(120))
                                    )
                            },
                            label = "PlayPauseIconAnimation"
                        ) { visualState ->
                            when (visualState) {
                                PlayPauseVisualState.PROCESSING -> {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(metrics.playIconSize.coerceAtLeast(32.dp)),
                                        color = playBtnContentColor,
                                        trackColor = playBtnContentColor.copy(alpha = 0.30f),
                                        stroke = wavyStroke,
                                        trackStroke = wavyStroke
                                    )
                                }
                                PlayPauseVisualState.PLAYING -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pause),
                                        contentDescription = "Pause",
                                        tint = playBtnContentColor,
                                        modifier = Modifier.size(metrics.playIconSize)
                                    )
                                }
                                PlayPauseVisualState.PAUSED -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_play),
                                        contentDescription = "Play",
                                        tint = playBtnContentColor,
                                        modifier = Modifier.size(metrics.playIconSize)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            menuContent = {}
        )

        // Next
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed) 16.dp else 50.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "NextCorner"
                )
                androidx.compose.material3.Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNextClick()
                    },
                    modifier = with(scope) {
                        Modifier
                            .animateWidth(interactionSource)
                            .weight(1f)
                            .height(metrics.buttonHeight)
                    },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(cornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f),
                        contentColor = contentColor
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_skip_next),
                            contentDescription = "Next Song",
                            tint = contentColor,
                            modifier = Modifier.size(metrics.skipIconSize)
                        )
                    }
                }
            },
            menuContent = {}
        )
    }

        Spacer(modifier = Modifier.height(if (metrics.isLandscape) 4.dp else 8.dp))
    }
}
