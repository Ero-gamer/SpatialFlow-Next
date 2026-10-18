package com.codetrio.spatialflow.ui.settings

import android.graphics.Matrix
import android.graphics.SweepGradient
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.ui.theme.concentricInner
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.codetrio.spatialflow.service.AudioPlaybackService
import com.codetrio.spatialflow.service.dac.UsbDacManager
import com.codetrio.spatialflow.util.QualityModeSelector
import kotlinx.coroutines.launch
import java.util.Locale

private const val SHOW_CLOCK_DRIFT_GAUGE = false

/**
 * Material 3 Expressive DAC Capabilities & Audio Routing Bottom Sheet.
 *
 * Features:
 * - Unified AudioSignalFlowCard with continuous Shimmer Effect
 * - Displays live Decoded Input on Left, Processor Icon in Centre, and Hardware Output on Right
 * - Distinct, functional control for Direct USB, Bit-Perfect, Native Sample Rate, and Line Out
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun DacCapabilitiesBottomSheet(
    onDismiss: () -> Unit
) {
    val liveState by UsbDacManager.liveState.collectAsState()
    val runtimeState by UsbDacManager.runtimeState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val nativeDspState by QualityModeSelector.isNativeDspEnabledFlow.collectAsState()

    var usbExclusiveState by remember {
        mutableStateOf(QualityModeSelector.isUsbExclusiveModeEnabled(context))
    }
    var bitPerfectState by remember {
        mutableStateOf(QualityModeSelector.isDacBitPerfectEnabled(context))
    }
    var nativeSampleRateState by remember {
        mutableStateOf(QualityModeSelector.isNativeSampleRateEnabled(context))
    }
    var fixedVolumeState by remember {
        mutableStateOf(QualityModeSelector.isDacFixedVolumeEnabled(context))
    }
    var showFixedVolumeConfirmDialog by remember {
        mutableStateOf(false)
    }

    val isUsbDac = liveState.hardware.isUsbDac
    val isVerified = runtimeState.verifiedBitPerfect
    val sourceStats = liveState.source

    // Sync live telemetry when the sheet opens so all values are current
    LaunchedEffect(Unit) {
        AudioPlaybackService.currentInstance?.syncLiveTelemetryToDacManager()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                )
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Device Header ────────────────────────────────────────────
            Text(
                text = liveState.hardware.deviceName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            AnimatedVisibility(
                visible = isUsbDac,
                enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            liveState.hardware.manufacturer?.let { append("$it  •  ") }
                            append(liveState.hardware.vendorIdHex ?: "")
                            liveState.hardware.productIdHex?.let { append(" / $it") }
                        },
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 1. Hero: Shimmer Signal Flow Card (Input → Processor → Output) ─
            AudioSignalFlowCard(
                runtimeState = runtimeState,
                sourceStats = sourceStats,
                deviceName = liveState.hardware.deviceName,
                isUsbDac = isUsbDac,
                isVerified = isVerified,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── 2. Live Status Badge & Stream Indicator ──────────────────
            AnimatedVisibility(
                visible = isUsbDac,
                enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val statusBg by animateColorAsState(
                        targetValue = when {
                            isVerified -> MaterialTheme.colorScheme.primaryContainer
                            runtimeState.resamplerActive -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "StatusBg"
                    )
                    val statusFg by animateColorAsState(
                        targetValue = when {
                            isVerified -> MaterialTheme.colorScheme.onPrimaryContainer
                            runtimeState.resamplerActive -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "StatusFg"
                    )

                    Surface(
                        shape = CircleShape,
                        color = statusBg,
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isVerified) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = statusFg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            AnimatedContent(
                                targetState = if (isVerified) runtimeState.qualityTierBadge else runtimeState.liveStatusDisplay,
                                transitionSpec = {
                                    (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn())
                                        .togetherWith(slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeOut())
                                },
                                label = "StatusText"
                            ) { status ->
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = statusFg,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (SHOW_CLOCK_DRIFT_GAUGE) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // ── Stream Health: Real-time Clock Drift with 2.5D Animation ──
                        ClockDriftIndicator(
                            isVerified = isVerified,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Organic M3 Expressive stream indicator
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(6.dp),
                        color = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // ── 3. SpatialFX Mode (DSP) Hero Card ────────────────────
            val heroInteractionSource = remember { MutableInteractionSource() }
            val isHeroPressed by heroInteractionSource.collectIsPressedAsState()

            val heroCornerRadius by animateDpAsState(
                targetValue = if (isHeroPressed) 14.dp else if (nativeDspState) 24.dp else 18.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "HeroCornerMorph"
            )

            val dspContainerColor by animateColorAsState(
                targetValue = if (nativeDspState) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "DspColorMorph"
            )

            val dspContentColor by animateColorAsState(
                targetValue = if (nativeDspState) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "DspTextColorMorph"
            )

            Surface(
                shape = RoundedCornerShape(heroCornerRadius),
                color = dspContainerColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(heroCornerRadius))
                    .clickable(
                        interactionSource = heroInteractionSource,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val newDsp = !nativeDspState
                        QualityModeSelector.setNativeDspMode(context, newDsp)
                        bitPerfectState = !newDsp
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (nativeDspState) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                            contentDescription = if (nativeDspState) "SpatialFX active" else "SpatialFX off",
                            tint = dspContentColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            AnimatedContent(
                                targetState = nativeDspState,
                                transitionSpec = {
                                    (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn())
                                        .togetherWith(slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it } + fadeOut())
                                },
                                label = "HeroTitleTransition"
                            ) { active ->
                                Text(
                                    text = if (active) "SpatialFX" else "SpatialFX Off",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = dspContentColor
                                )
                            }
                            Text(
                                text = if (nativeDspState) "EQ • Spatial • Reverb (Mixer active)" else "Bit-identical bypass (Byte-exact to DAC)",
                                style = MaterialTheme.typography.bodySmall,
                                color = dspContentColor.copy(alpha = 0.75f)
                            )
                        }
                    }

                    Switch(
                        checked = nativeDspState,
                        onCheckedChange = { isChecked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            QualityModeSelector.setNativeDspMode(context, isChecked)
                            bitPerfectState = !isChecked
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 4. Hardware Output Controls (USB DAC only) ───────────────
            AnimatedVisibility(
                visible = isUsbDac,
                enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                ) {
                    Text(
                        text = "Hardware Routing & Interface",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val totalOptions = if (bitPerfectState) 4 else 3
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                        verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        // 1. Direct USB Playback
                        SegmentedConfigRow(
                            shape = verticalSegmentedShape(index = 0, count = totalOptions),
                            icon = Icons.Rounded.Usb,
                            title = "Direct USB",
                            description = if (usbExclusiveState) "Locked exclusively to USB DAC hardware" else "Using default system output routing",
                            checked = usbExclusiveState,
                            onCheckedChange = { isChecked ->
                                usbExclusiveState = isChecked
                                QualityModeSelector.setUsbExclusiveMode(context, isChecked)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )

                        // 2. Bit-Perfect Output (Master)
                        SegmentedConfigRow(
                            shape = verticalSegmentedShape(index = 1, count = totalOptions),
                            icon = Icons.Rounded.Speed,
                            title = "Bit-Perfect",
                            description = if (bitPerfectState) "Bypasses DSP, locks HAL direct bit-exact" else "Allows Studio DSP, EQ, and system mixer",
                            checked = bitPerfectState,
                            onCheckedChange = { isChecked ->
                                bitPerfectState = isChecked
                                QualityModeSelector.setDacBitPerfect(context, isChecked)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )

                        // 3. Native Sample Rate (Nested under Bit-Perfect)
                        AnimatedVisibility(
                            visible = bitPerfectState,
                            enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                            exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
                        ) {
                            SegmentedConfigRow(
                                shape = verticalSegmentedShape(index = 2, count = totalOptions),
                                icon = Icons.Rounded.MusicNote,
                                title = "Native Rate",
                                description = if (nativeSampleRateState) "DAC clock locked to track (${formatRate(runtimeState.sourceSampleRate)})" else "Direct fixed 48 kHz (System resampled)",
                                checked = nativeSampleRateState,
                                onCheckedChange = { isChecked ->
                                    nativeSampleRateState = isChecked
                                    QualityModeSelector.setNativeSampleRate(context, isChecked)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }

                        // 4. Fixed Line-Out Volume
                        if (showFixedVolumeConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showFixedVolumeConfirmDialog = false },
                                title = {
                                    Text(
                                        text = "Fixed Line-Out",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Fixed Line-Out sets system media volume to maximum while a USB DAC is connected. Connect this only to a headphone amplifier or powered speakers with volume control. Continue?",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                dismissButton = {
                                    TextButton(onClick = { showFixedVolumeConfirmDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showFixedVolumeConfirmDialog = false
                                            fixedVolumeState = true
                                            QualityModeSelector.setDacFixedVolume(context, true)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    ) {
                                        Text("Enable")
                                    }
                                }
                            )
                        }

                        SegmentedConfigRow(
                            shape = verticalSegmentedShape(index = if (bitPerfectState) 3 else 2, count = totalOptions),
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            title = "Line-Out",
                            description = if (fixedVolumeState) "Fixed 100% max line-level voltage for external amps" else "Variable digital volume control",
                            checked = fixedVolumeState,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    showFixedVolumeConfirmDialog = true
                                } else {
                                    fixedVolumeState = false
                                    QualityModeSelector.setDacFixedVolume(context, false)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // ── 5. Supported Sample Rates ────────────────────────────────
            AnimatedVisibility(
                visible = liveState.hardware.supportedSampleRates.isNotEmpty(),
                enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                ) {
                    Text(
                        text = "Supported Rates",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        liveState.hardware.supportedSampleRates.forEach { rate ->
                            val isCurrent = rate == runtimeState.outputSampleRate
                            val chipInteractionSource = remember { MutableInteractionSource() }
                            val isPressed by chipInteractionSource.collectIsPressedAsState()
                            val scale by animateFloatAsState(
                                targetValue = if (isPressed) 0.94f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "ChipScale"
                            )

                            FilterChip(
                                selected = isCurrent,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                label = {
                                    Text(
                                        text = formatRate(rate),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                interactionSource = chipInteractionSource,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 6. Diagnostics Harness (Debug / Verification) ────────────
            var showDiagnosticsDialog by remember { mutableStateOf(false) }
            var diagnosticsOutput by remember { mutableStateOf("") }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    diagnosticsOutput = AudioPlaybackService.dumpBitPerfectDiagnostics()
                    showDiagnosticsDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Run Bit-Perfect Diagnostics",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (showDiagnosticsDialog) {
                AlertDialog(
                    onDismissRequest = { showDiagnosticsDialog = false },
                    title = {
                        Text(
                            text = "Bit-Perfect Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = diagnosticsOutput,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Bit-Perfect Diagnostics", diagnosticsOutput)
                                clipboard?.setPrimaryClip(clip)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showDiagnosticsDialog = false
                            }
                        ) {
                            Text("Copy Output")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiagnosticsDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Audio Signal Flow Card with Shimmering Route Lines & Arrows
// ────────────────────────────────────────────────────────────────────────────────

/**
 * Shimmer-infused Audiophile Signal Flow Card.
 * Visualizes the complete live pipeline:
 * [INPUT: Decoded Stream] ──(Shimmer line + arrow)──► [PROCESSOR: Memory Icon] ──(Shimmer line + arrow)──► [REAL OUTPUT: Hardware DAC]
 * Displays dynamic data pulses traveling continuously across the route connectors.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AudioSignalFlowCard(
    runtimeState: com.codetrio.spatialflow.service.dac.BitPerfectRuntimeState,
    sourceStats: com.codetrio.spatialflow.service.dac.SourceAudioStats,
    deviceName: String,
    isUsbDac: Boolean,
    isVerified: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "FlowShimmerTransition")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FlowShimmerTranslate"
    )
    val glowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GlowRotation"
    )

    // Pure Material 3 Expressive rotating shimmer palette derived from theme colorScheme
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer

    val sweepColors = remember(primary, secondary, tertiary, primaryContainer, tertiaryContainer) {
        val p = primary.toArgb()
        val s = secondary.toArgb()
        val t = tertiary.toArgb()
        val tc = tertiaryContainer.toArgb()
        val pc = primaryContainer.toArgb()
        val trans = android.graphics.Color.TRANSPARENT
        intArrayOf(
            p, s, t, tc, pc, p,
            trans, trans, trans, trans, trans,
            p
        )
    }

    val cardCornerRadius = 20.dp

    Surface(
        shape = RoundedCornerShape(cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
            )
            .drawWithCache {
                val strokeWidth = 1.5.dp.toPx()
                val cornerRadiusPx = cardCornerRadius.toPx()
                val haloStrokeWidth = 7.dp.toPx()

                val sweepShader = SweepGradient(
                    size.width / 2f,
                    size.height / 2f,
                    sweepColors,
                    null
                )
                val matrix = Matrix()
                val sweepBrush = ShaderBrush(sweepShader)

                onDrawWithContent {
                    // Rotate the sweep gradient along the perimeter
                    matrix.setRotate(glowRotation, size.width / 2f, size.height / 2f)
                    sweepShader.setLocalMatrix(matrix)

                    // 1. Soft Ambient Halo Glow (radiates seamlessly behind the card with matching rounded corners)
                    drawRoundRect(
                        brush = sweepBrush,
                        topLeft = Offset(0f, 0f),
                        size = size,
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        style = Stroke(width = haloStrokeWidth),
                        alpha = 0.28f
                    )

                    // 2. Draw Card Surface & Content
                    drawContent()

                    // 3. Crisp Rotating Border Stroke (mathematically locked to the card's 20.dp rounded perimeter)
                    drawRoundRect(
                        brush = sweepBrush,
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        cornerRadius = CornerRadius((cornerRadiusPx - strokeWidth / 2f).coerceAtLeast(0f)),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Signal chain label row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE AUDIO SIGNAL CHAIN",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(
                    visible = isVerified,
                    enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "HAL LOCKED",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── Active Track Header ──────────────────────────────────────
            val trackTitle = runtimeState.trackTitle ?: sourceStats.title
            val trackArtist = runtimeState.trackArtist ?: sourceStats.artist
            if (!trackTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Playing / Paused dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (runtimeState.isPlaying) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trackTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!trackArtist.isNullOrBlank()) {
                            Text(
                                text = trackArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Codec badge chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = sourceStats.displayCodec,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Stage Headers: INPUT and OUTPUT markers ──────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "INPUT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "OUTPUT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Central Signal Pipeline: Input ──► Processor ──► Output ──
            // Guaranteed perfect vertical centerline alignment across all nodes
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: INPUT Stream Rates
                Column(
                    modifier = Modifier.weight(1.05f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "${runtimeState.sourceBitDepth}-bit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatRate(runtimeState.sourceSampleRate),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = sourceStats.displayChannels,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Shimmer Connector 1: INPUT ──► PROCESSOR
                SignalRouteConnector(
                    modifier = Modifier.weight(0.9f),
                    isVerified = isVerified,
                    shimmerProgress = shimmerTranslate,
                    phaseOffset = 0f
                )

                // Center: PROCESSOR Node (Memory Icon from Library)
                Surface(
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = if (isVerified) MaterialTheme.colorScheme.primaryContainer
                    else if (isUsbDac) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = "Audio Processor",
                            tint = if (isVerified) MaterialTheme.colorScheme.onPrimaryContainer
                            else if (isUsbDac) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Shimmer Connector 2: PROCESSOR ──► OUTPUT
                SignalRouteConnector(
                    modifier = Modifier.weight(0.9f),
                    isVerified = isVerified,
                    shimmerProgress = shimmerTranslate,
                    phaseOffset = 0.35f
                )

                // Right: OUTPUT Node Rates
                Column(
                    modifier = Modifier.weight(1.05f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "${runtimeState.outputBitDepth}-bit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = formatRate(runtimeState.outputSampleRate),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Stream Subtitles & Center DSP Mode Badge ─────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Stream Subtitle — real codec + bitrate
                Text(
                    text = buildString {
                        append(sourceStats.displayCodec)
                        val br = sourceStats.displayBitrate
                        if (br.isNotBlank() && br != "Variable VBR") append(" • $br")
                    }.ifEmpty {
                        runtimeState.sourceEncoding.replace(" PCM", "").takeIf { it.isNotBlank() && !it.contains("-bit") } ?: "Decoded Audio"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Center: DSP / Routing Pill Badge
                Surface(
                    shape = CircleShape,
                    color = if (isVerified) MaterialTheme.colorScheme.primary
                    else if (runtimeState.resamplerActive) MaterialTheme.colorScheme.tertiaryContainer
                    else if (runtimeState.dspActive) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = when {
                            isVerified -> if (runtimeState.oboeExclusiveActive) "OBOE EXCLUSIVE" else "BIT-PERFECT"
                            runtimeState.resamplerActive && runtimeState.dspActive -> "DSP + RESAMPLE"
                            runtimeState.resamplerActive -> buildString {
                                append("RESAMPLER")
                                if (runtimeState.sourceSampleRate > 0 && runtimeState.outputSampleRate > 0 &&
                                    runtimeState.sourceSampleRate != runtimeState.outputSampleRate) {
                                    append(" (${formatRate(runtimeState.sourceSampleRate)} → ${formatRate(runtimeState.outputSampleRate)})")
                                }
                            }
                            runtimeState.dspActive -> "SPATIALFX"
                            else -> "DIRECT HAL"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isVerified) MaterialTheme.colorScheme.onPrimary
                        else if (runtimeState.resamplerActive) MaterialTheme.colorScheme.onTertiaryContainer
                        else if (runtimeState.dspActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Right Output Subtitle
                Text(
                    text = when {
                        !isUsbDac -> "Android Mixer"
                        isVerified -> if (runtimeState.oboeExclusiveActive) "Oboe MMAP (Bit-Exact)" else "Direct Bit-Exact"
                        runtimeState.resamplerActive -> "Mixer (Resampled)"
                        runtimeState.usbExclusiveActive -> "USB Exclusive"
                        else -> "Android Mixer"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Real Metrics Summary Bar ──────────────────────────────────
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricCell(label = "Codec", value = sourceStats.displayCodec)
                    MetricCell(label = "Rate", value = sourceStats.displaySampleRate)
                    MetricCell(label = "Depth", value = sourceStats.displayBitDepth)
                    MetricCell(label = "Ch", value = sourceStats.displayChannels.substringBefore(" ("))
                    MetricCell(label = "Bitrate", value = sourceStats.displayBitrate)
                }
            }
        }
    }
}

/**
 * Compact metric cell for the summary bar — label above, value below.
 */
@Composable
private fun MetricCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Visual route connector displaying a continuous signal line with a directional arrow
 * and a traveling shimmer wave indicating live audio stream flow.
 */
@Composable
private fun SignalRouteConnector(
    modifier: Modifier = Modifier,
    isVerified: Boolean,
    shimmerProgress: Float,
    phaseOffset: Float = 0f
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier
            .height(28.dp)
            .padding(horizontal = 4.dp)
    ) {
        val strokeWidth = 2.dp.toPx()
        val centerY = size.height / 2f
        val arrowHeadWidth = 7.dp.toPx()
        val arrowHalfHeight = 4.5.dp.toPx()
        val lineEndX = (size.width - arrowHeadWidth).coerceAtLeast(0f)

        // Calculate cyclic shimmer wave position
        val progress = (shimmerProgress + phaseOffset) % 1f
        val shimmerSpan = size.width * 0.85f
        val startX = (size.width + shimmerSpan) * progress - shimmerSpan
        val endX = startX + shimmerSpan

        val lineBrush = Brush.linearGradient(
            colors = listOf(
                trackColor,
                trackColor,
                activeColor.copy(alpha = 0.55f),
                activeColor,
                activeColor.copy(alpha = 0.55f),
                trackColor,
                trackColor
            ),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY)
        )

        // Draw horizontal connector line
        drawLine(
            brush = lineBrush,
            start = Offset(0f, centerY),
            end = Offset(lineEndX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Draw directional arrow pointing to next stage
        val arrowPath = Path().apply {
            moveTo(size.width, centerY)
            lineTo(size.width - arrowHeadWidth, centerY - arrowHalfHeight)
            lineTo(size.width - arrowHeadWidth * 0.6f, centerY)
            lineTo(size.width - arrowHeadWidth, centerY + arrowHalfHeight)
            close()
        }

        // Arrow intensity pulse when wave arrives
        val arrowPulse = if (progress in 0.65f..1.0f) 1f else 0.55f
        drawPath(
            path = arrowPath,
            color = activeColor.copy(alpha = arrowPulse)
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Supporting Expressive Components
// ────────────────────────────────────────────────────────────────────────────────

/**
 * Creates properly shaped corners for a vertical segmented card group.
 * Top item has rounded top corners, middle items have minimal corners,
 * and bottom item has rounded bottom corners.
 */
private fun verticalSegmentedShape(
    index: Int,
    count: Int,
    outerCorner: Dp = 20.dp,
    innerCorner: Dp = 4.dp
): Shape {
    return when {
        count <= 1 -> RoundedCornerShape(outerCorner)
        index == 0 -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = innerCorner,
            bottomEnd = innerCorner
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner
        )
        else -> RoundedCornerShape(innerCorner)
    }
}

/**
 * Segmented Configuration Row with spring switch feedback and zero borders.
 */
@Composable
private fun SegmentedConfigRow(
    shape: Shape,
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Concentric corner calculation: Outer Card (20.dp) - Vertical Padding (14.dp) = 6.dp
            val iconCornerRadius = 20.dp.concentricInner(14.dp)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(iconCornerRadius))
                    .background(
                        if (checked) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}

/**
 * Formats sample rate for audiophile display: 44.1 kHz, 96 kHz, 192 kHz, etc.
 */
private fun formatRate(rate: Int): String {
    return if (rate > 0) "${rate / 1000.0} kHz".replace(".0 ", " ") else "—"
}

/**
 * Concentric Corner Radius rule (Material Design 3 & Geometry):
 * Inner Radius = Outer Radius - Padding
 * When two nested shapes have concentric corners, their corner arcs share the
 * exact same center of curvature, eliminating awkward visual pinching.
 */
private fun concentricCorner(outerRadius: Dp, padding: Dp): Dp {
    return (outerRadius - padding).coerceAtLeast(0.dp)
}

/**
 * Highly optimized, 2.5D perspective animated real-time Clock Drift indicator.
 *
 * Performance Safeguards:
 * - Recomposition Isolation: ClockDriftValue25D directly collects UsbDacManager.clockDriftPpm
 *   as a leaf node, preventing the parent sheet or sibling elements from recomposing.
 * - Draw-Phase Transformations: cameraDistance, rotationX, and scale bounces operate inside
 *   graphicsLayer lambdas, avoiding layout and measure passes during animation frames.
 * - Tabular Numerals: Uses fontFeatureSettings = "tnum" so digit changes have zero horizontal jitter.
 * - Zero GC Allocation: Formats using fast string formatting without object churn.
 */
@Composable
private fun ClockDriftIndicator(
    isVerified: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Stream health",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Clock drift",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ClockDriftValue25D(
                isVerified = isVerified
            )
        }
    }
}

@Composable
private fun ClockDriftValue25D(
    isVerified: Boolean,
    modifier: Modifier = Modifier
) {
    val clockDrift by UsbDacManager.clockDriftPpm.collectAsState()

    val driftText = remember(clockDrift) {
        when (val drift = clockDrift) {
            null -> "-- PPM"
            else -> {
                val sign = if (drift >= 0f) "+" else ""
                "$sign${String.format(Locale.US, "%.1f", drift)} PPM"
            }
        }
    }

    // 2.5D spatial animation states applied strictly to the text
    val rotationX = remember { Animatable(0f) }
    val rotationZ = remember { Animatable(0f) }
    val stretchY = remember { Animatable(1f) }
    val squashX = remember { Animatable(1f) }

    LaunchedEffect(driftText) {
        if (driftText != "-- PPM") {
            // 1. 2.5D Perspective Tilt on the text (rotationX + subtle isometric rotationZ)
            launch {
                rotationX.snapTo(-35f)
                rotationX.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                rotationZ.snapTo(-4f)
                rotationZ.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            // 2. Volume-Conserving 2.5D Squash & Stretch on the text numbers
            launch {
                stretchY.snapTo(1.18f)
                stretchY.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                squashX.snapTo(0.86f)
                squashX.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    val density = LocalDensity.current

    // Outer pill container remains completely static and steady
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isVerified) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
        border = BorderStroke(
            1.dp,
            if (isVerified) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            AnimatedContent(
                targetState = driftText,
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { height -> height / 2 } + fadeIn(animationSpec = tween(150)))
                        .togetherWith(
                            slideOutVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) { height -> -height / 2 } + fadeOut(animationSpec = tween(120))
                        )
                },
                label = "M3_2_5D_TextRoll",
                modifier = Modifier.graphicsLayer {
                    // 2.5D perspective effect applied strictly to the text
                    cameraDistance = 16f * density.density
                    this.rotationX = rotationX.value
                    this.rotationZ = rotationZ.value
                    this.scaleX = squashX.value
                    this.scaleY = stretchY.value
                    this.transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    fontWeight = FontWeight.Bold,
                    color = if (isVerified) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
