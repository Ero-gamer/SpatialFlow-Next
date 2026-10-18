package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.codetrio.spatialflow.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.codetrio.spatialflow.service.dac.DacConnectionEvent
import com.codetrio.spatialflow.service.dac.UsbDacManager
import com.codetrio.spatialflow.ui.settings.DacCapabilitiesBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top-level floating "DAC Connected" Banner.
 *
 * Automatically pops down with an expressive spring bounce whenever a physical USB DAC
 * or Bit-Perfect audio interface is detected. Displays real-time hardware telemetry
 * and offers a 1-tap gateway to open the DAC Capabilities Inspector.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DacConnectedBannerHost(
    modifier: Modifier = Modifier
) {
    var activeEvent by remember { mutableStateOf<DacConnectionEvent?>(null) }
    var showInspector by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var autoDismissJob by remember { mutableStateOf<Job?>(null) }
    val haptic = LocalHapticFeedback.current

    val bannerScale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        UsbDacManager.connectionEvents.collect { event ->
            autoDismissJob?.cancel()
            activeEvent = event

            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            // Tactile spring bounce on entry
            scope.launch {
                bannerScale.snapTo(0.92f)
                bannerScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }

            autoDismissJob = scope.launch {
                delay(3800)
                activeEvent = null
            }
        }
    }

    if (showInspector) {
        DacCapabilitiesBottomSheet(onDismiss = { showInspector = false })
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(9999f)
            .padding(top = topInset + 8.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = activeEvent != null,
            enter = slideInVertically(
                initialOffsetY = { -it - 40 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = slideOutVertically(
                targetOffsetY = { -it - 40 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
        ) {
            activeEvent?.let { event ->
                DacConnectedBannerContent(
                    event = event,
                    scale = bannerScale.value,
                    onClick = {
                        autoDismissJob?.cancel()
                        activeEvent = null
                        showInspector = true
                    },
                    onDismiss = {
                        autoDismissJob?.cancel()
                        activeEvent = null
                    }
                )
            }
        }
    }
}

@Composable
private fun DacConnectedBannerContent(
    event: DacConnectionEvent,
    scale: Float,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isBitPerfect = event.isBitPerfect
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .widthIn(max = 412.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -15f) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    }
                }
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = primaryColor.copy(alpha = 0.12f))
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // High-fidelity soundwave badge (replaces USB trident)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isBitPerfect) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = "Audio Processor",
                    tint = if (isBitPerfect) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = event.deviceName.ifBlank { "External Audio Device" },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val rateStr = if (event.sampleRateHz > 0) {
                    "${(event.sampleRateHz / 1000.0).toString().replace(".0", "")} kHz"
                } else "Direct Route"

                val modeLabel = if (isBitPerfect) "Hi-Res Direct" else "Direct USB"

                Text(
                    text = "$modeLabel • $rateStr",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Accessible dismiss button conforming to DESIGN_SKILL.md (>= 48 dp touch target)
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
