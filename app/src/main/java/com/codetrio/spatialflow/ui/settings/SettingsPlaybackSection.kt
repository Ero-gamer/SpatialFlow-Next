@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.ui.SettingsRoute
import com.codetrio.spatialflow.ui.player.SleepTimerBottomSheet
import com.codetrio.spatialflow.util.QualityModeSelector
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import androidx.compose.ui.platform.LocalContext

@Composable
fun PlaybackScreen(
    navController: NavController,
    crossfadeEnabled: Boolean,
    onCrossfadeToggle: (Boolean) -> Unit,
    crossfadeDuration: Float,
    onCrossfadeDurationChange: (Float) -> Unit,
    crossfadeMode: String,
    onCrossfadeModeChange: (String) -> Unit,
    autoMixEnabled: Boolean = false,
    onAutoMixToggle: (Boolean) -> Unit = {},
    autoMixPerformanceMode: String = "BALANCED",
    onAutoMixPerformanceModeChange: (String) -> Unit = {},
    audioFocus: Boolean,
    onAudioFocusToggle: (Boolean) -> Unit,
    disconnectAction: String,
    onDisconnectActionChange: (String) -> Unit,
    resumeOnConnect: Boolean,
    onResumeOnConnectChange: (Boolean) -> Unit,
    autoplayEnabled: Boolean,
    onAutoplayToggle: (Boolean) -> Unit,
    sleepTimerEndTime: Long,
    sleepTimerMode: PlayerSharedViewModel.SleepTimerMode,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetEndOfSong: (Boolean) -> Unit,
    audioQuality: String,
    onAudioQualityChange: (String) -> Unit,
    forceOpus: Boolean,
    onForceOpusChange: (Boolean) -> Unit,
    losslessStreamPool: Boolean = true,
    onLosslessStreamPoolChange: (Boolean) -> Unit = {},
    losslessPriorityOrder: List<com.codetrio.spatialflow.data.sourcepool.LosslessProvider> = com.codetrio.spatialflow.data.sourcepool.LosslessProvider.DEFAULT_PRIORITY_ORDER,
    disabledLosslessProviders: Set<String> = emptySet(),
    onLosslessPriorityOrderChange: (List<com.codetrio.spatialflow.data.sourcepool.LosslessProvider>) -> Unit = {},
    onToggleLosslessProvider: (com.codetrio.spatialflow.data.sourcepool.LosslessProvider, Boolean) -> Unit = { _, _ -> },
    onResetLosslessPriorityOrder: () -> Unit = {},
    dacDirectMode: Boolean = true,
    onDacDirectModeChange: (Boolean) -> Unit = {},
    dacBitPerfect: Boolean = true,
    onDacBitPerfectChange: (Boolean) -> Unit = {},
    nativeSampleRate: Boolean = true,
    onNativeSampleRateChange: (Boolean) -> Unit = {},
    dacFixedVolume: Boolean = false,
    onDacFixedVolumeChange: (Boolean) -> Unit = {},
    dacBannerEnabled: Boolean = true,
    onDacBannerChange: (Boolean) -> Unit = {},
    volumeNormalizationEnabled: Boolean,
    onVolumeNormalizationChange: (Boolean) -> Unit,
    targetLufs: Float,
    onTargetLufsChange: (Float) -> Unit
) {
    var showDacInspector by remember { mutableStateOf(false) }
    var showPrioritySheet by remember { mutableStateOf(false) }
    val isDacConnected by com.codetrio.spatialflow.service.dac.UsbDacManager.isDacConnected.collectAsStateWithLifecycle()
    val isBitPerfect = (dacBitPerfect && dacDirectMode && isDacConnected) || QualityModeSelector.isBitPerfectActive(LocalContext.current)

    if (showDacInspector) {
        DacCapabilitiesBottomSheet(onDismiss = { showDacInspector = false })
    }

    if (showPrioritySheet) {
        LosslessPriorityBottomSheet(
            priorityOrder = losslessPriorityOrder,
            disabledProviders = disabledLosslessProviders,
            onOrderChange = onLosslessPriorityOrderChange,
            onToggleProvider = onToggleLosslessProvider,
            onResetDefaults = onResetLosslessPriorityOrder,
            onDismiss = { showPrioritySheet = false }
        )
    }

    SettingsScaffold(
        title = "Playback",
        onBack = { navController.popBackStack() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsHeader("Streaming Quality")
            SettingsGroupCard(buildList {
                add { LosslessStreamRow(losslessStreamPool, onLosslessStreamPoolChange) }
                if (losslessStreamPool) {
                    add {
                        LosslessPriorityRow(
                            priorityOrder = losslessPriorityOrder,
                            disabledProviders = disabledLosslessProviders,
                            onClick = { showPrioritySheet = true }
                        )
                    }
                    add { DeezerAccountRow(onClick = { navController.navigate(SettingsRoute.DeezerSettings.route) }) }
                    add { TidalAccountRow(onClick = { navController.navigate(SettingsRoute.TidalSettings.route) }) }
                }
                add { ForceOpusRow(forceOpus, onForceOpusChange) }
                add { AudioQualityRow(audioQuality, onAudioQualityChange) }
                add { SourcePoolStatusRow(onClick = { navController.navigate(SettingsRoute.SourcePool.route) }) }
            })

            SettingsHeader("USB DAC & Bit-Perfect Audio")
            SettingsGroupCard(buildList {
                add { DacStatusRow(onClick = { showDacInspector = true }) }
                add { DacDirectModeRow(dacDirectMode, onDacDirectModeChange) }
                if (dacDirectMode) {
                    add { DacBitPerfectRow(dacBitPerfect, onDacBitPerfectChange) }
                    if (dacBitPerfect) {
                        add { DacNativeSampleRateRow(nativeSampleRate, onNativeSampleRateChange) }
                    }
                    add { DacFixedVolumeRow(dacFixedVolume, onDacFixedVolumeChange) }
                }
                add { DacBannerRow(dacBannerEnabled, onDacBannerChange) }
            })

            SettingsHeader("Dolby Atmos & Spatial Audio")
            SettingsGroupCard(buildList {
                add { DolbyAtmosStatusRow() }
                add { DolbyAtmosToggleRow() }
            })

            SettingsHeader("Crossfade")
            SettingsGroupCard(listOf(
                { CrossfadeRow(crossfadeEnabled, onCrossfadeToggle, crossfadeDuration, onCrossfadeDurationChange, crossfadeMode, onCrossfadeModeChange, isBitPerfect = isBitPerfect, autoMixEnabled = autoMixEnabled) }
            ))

            SettingsHeader("AutoMix (DJ Transitions)")
            SettingsGroupCard(listOf(
                { AutoMixRow(autoMixEnabled, onAutoMixToggle, autoMixPerformanceMode, onAutoMixPerformanceModeChange, isBitPerfect = isBitPerfect) }
            ))
            
            SettingsHeader("Audio Focus")
            SettingsGroupCard(listOf(
                { AudioFocusRow(audioFocus, onAudioFocusToggle) }
            ))

            SettingsHeader("Headphones & Bluetooth")
            SettingsGroupCard(buildList {
                add { DisconnectActionRow(disconnectAction, onDisconnectActionChange) }
                add { ResumeOnConnectRow(resumeOnConnect, onResumeOnConnectChange) }
            })

            SettingsHeader("Queue & Autoplay")
            SettingsGroupCard(listOf(
                { AutoplayRow(autoplayEnabled, onAutoplayToggle) }
            ))

            SettingsHeader("Sleep Timer")
            SettingsGroupCard(listOf(
                { SleepTimerSection(sleepTimerEndTime, sleepTimerMode, onStartSleepTimer, onCancelSleepTimer, onSetEndOfSong) }
            ))

            SettingsHeader("Volume Normalization")
            SettingsGroupCard(listOf(
                { VolumeNormalizationRow(volumeNormalizationEnabled, onVolumeNormalizationChange, targetLufs, onTargetLufsChange) }
            ))

            SettingsHeader("Lyrics Romanization")
            SettingsGroupCard(buildList {
                add { RomanizeJapaneseRow() }
                add { RomanizeKoreanRow() }
                add { RomanizeChineseRow() }
                add { RomanizeHindiRow() }
                add { RomanizeOtherLanguagesRow() }
            })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioQualityRow(
    currentQuality: String,
    onQualityChange: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    data class QualityOption(
        val id: String,
        val title: String,
        val bitrate: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )

    val options = remember {
        listOf(
            QualityOption("High", "High", "256 kbps", Icons.Rounded.HighQuality),
            QualityOption("Normal", "Normal", "160 kbps", Icons.Rounded.GraphicEq),
            QualityOption("Data Saver", "Data Saver", "64 kbps", Icons.Rounded.DataSaverOn)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Streaming Quality",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Select preferred streaming bitrate and audio fidelity",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            expandedRatio = 0.25f,
            overflowIndicator = {}
        ) {
            val scope = this
            options.forEachIndexed { index, option ->
                val checked = option.id.equals(currentQuality, ignoreCase = true)
                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val cornerRadius by animateDpAsState(
                            targetValue = if (isPressed) 6.dp else if (checked) 20.dp else 10.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "QualityCorner"
                        )

                        Button(
                            onClick = {
                                if (!checked) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onQualityChange(option.id)
                                }
                            },
                            modifier = with(scope) {
                                Modifier
                                    .animateWidth(interactionSource)
                                    .weight(1f)
                                    .height(62.dp)
                            },
                            interactionSource = interactionSource,
                            shape = RoundedCornerShape(cornerRadius),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = option.bitrate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (checked) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    menuContent = {}
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VolumeNormalizationRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    targetLufs: Float,
    onTargetLufsChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val options = listOf(
        Triple("Quiet", -19f, "–19 LUFS"),
        Triple("Normal", -14f, "–14 LUFS"),
        Triple("Loud", -11f, "–11 LUFS")
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    text = "Volume Normalization",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            supportingContent = {
                Text(
                    text = "Automatically adjust playback volume so all songs sound equally loud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        AnimatedVisibility(visible = enabled) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Target Loudness",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                ButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    expandedRatio = 0.25f,
                    overflowIndicator = {}
                ) {
                    val scope = this
                    options.forEachIndexed { index, (label, lufs, subtitle) ->
                        val checked = lufs == targetLufs
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 6.dp else if (checked) 20.dp else 10.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "LoudnessCorner"
                                )

                                Button(
                                    onClick = {
                                        if (!checked) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onTargetLufsChange(lufs)
                                        }
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(54.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (checked) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            }
                                        )
                                    }
                                }
                            },
                            menuContent = {}
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CrossfadeRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    duration: Float,
    onDurationChange: (Float) -> Unit,
    mode: String,
    onModeChange: (String) -> Unit,
    isBitPerfect: Boolean = QualityModeSelector.isBitPerfectActive(LocalContext.current),
    autoMixEnabled: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val effectiveEnabled = enabled && !autoMixEnabled
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.setting_crossfade),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            supportingContent = {
                val subtitle = if (isBitPerfect) {
                    "Unavailable in Bit-Perfect mode"
                } else if (autoMixEnabled) {
                    "Disabled while AutoMix is active"
                } else if (!enabled) {
                    "Off"
                } else if (mode == "auto") {
                    "Intelligent (Automatic)"
                } else {
                    "${duration.toInt()}s"
                }
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = {
                        fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                            fadeOut(spring(stiffness = Spring.StiffnessMedium))
                    },
                    label = "crossfade_label"
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isBitPerfect) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = {
                Switch(
                    checked = effectiveEnabled && !isBitPerfect,
                    enabled = !isBitPerfect,
                    onCheckedChange = onToggle
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        AnimatedVisibility(visible = effectiveEnabled && !isBitPerfect) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                ButtonGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    expandedRatio = 0.25f,
                    overflowIndicator = {}
                ) {
                    val scope = this
                    val modes = listOf("auto" to "Intelligent", "custom" to "Custom")
                    modes.forEachIndexed { index, (key, label) ->
                        val checked = mode == key
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 6.dp else if (checked) 20.dp else 10.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "CrossfadeCorner"
                                )

                                Button(
                                    onClick = {
                                        if (!checked) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onModeChange(key)
                                        }
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(48.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            },
                            menuContent = {}
                        )
                    }
                }

                AnimatedVisibility(visible = mode == "custom") {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Slider(
                            value = duration,
                            onValueChange = onDurationChange,
                            enabled = enabled,
                            valueRange = 1f..12f,
                            steps = 10,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AutoMixRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    performanceMode: String,
    onPerformanceModeChange: (String) -> Unit,
    isBitPerfect: Boolean = QualityModeSelector.isBitPerfectActive(LocalContext.current)
) {
    val haptic = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setting_automix),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    BetaPill(text = "Experimental")
                }
            },
            supportingContent = {
                val subtitle = if (isBitPerfect) {
                    "Unavailable in Bit-Perfect mode"
                } else if (!enabled) {
                    "Off"
                } else {
                    when (performanceMode.uppercase()) {
                        "EFFICIENT" -> "Efficient (Fast analysis)"
                        "PERFORMANCE" -> "Precision (Deep analysis)"
                        else -> "Balanced (Smart transitions)"
                    }
                }
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = {
                        fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                            fadeOut(spring(stiffness = Spring.StiffnessMedium))
                    },
                    label = "automix_label"
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isBitPerfect) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = {
                Switch(
                    checked = enabled && !isBitPerfect,
                    enabled = !isBitPerfect,
                    onCheckedChange = onToggle
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        AnimatedVisibility(visible = enabled && !isBitPerfect) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_automix_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ButtonGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    expandedRatio = 0.25f,
                    overflowIndicator = {}
                ) {
                    val scope = this
                    val modes = listOf(
                        "EFFICIENT" to "Efficient",
                        "BALANCED" to "Balanced",
                        "PERFORMANCE" to "Precision"
                    )
                    modes.forEachIndexed { _, (key, label) ->
                        val checked = performanceMode.equals(key, ignoreCase = true)
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 6.dp else if (checked) 20.dp else 10.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "AutoMixCorner"
                                )

                                Button(
                                    onClick = {
                                        if (!checked) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onPerformanceModeChange(key)
                                        }
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(48.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            },
                            menuContent = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioFocusRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!enabled) },
        content = {
            Column {
                Text(
                    text = stringResource(R.string.setting_audio_focus),
                    style = MaterialTheme.typography.bodyLarge
                )
                val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedContent(
                    targetState = enabled,
                    transitionSpec = {
                        fadeIn(animationSpec = effectsSpec) togetherWith fadeOut(animationSpec = effectsSpec)
                    },
                    label = "audioFocusDesc"
                ) { isEnabled ->
                    Text(
                        text = if (isEnabled) {
                            "Stop or pause playback when another app plays audio"
                        } else {
                            "Do not stop or pause playback when another app plays audio"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_audio_focus),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = { Switch(checked = enabled, onCheckedChange = onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun AutoplayRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!enabled) },
        content = {
            Column {
                Text(
                    text = "Autoplay",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Similar songs will play next",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_queue_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = { Switch(checked = enabled, onCheckedChange = onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SleepTimerSection(
    endTime: Long, 
    mode: PlayerSharedViewModel.SleepTimerMode,
    onStart: (Int) -> Unit, 
    onCancel: () -> Unit,
    onSetEndOfSong: (Boolean) -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    val remaining by remember(endTime) {
        derivedStateOf { endTime - System.currentTimeMillis() }
    }

    Column {
        ListItem(
            onClick = { showBottomSheet = true },
            content = {
                Text(
                    text = stringResource(R.string.setting_sleep_timer),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            supportingContent = {
                val supportingText = when (mode) {
                    PlayerSharedViewModel.SleepTimerMode.OFF -> stringResource(R.string.setting_sleep_timer_off)
                    PlayerSharedViewModel.SleepTimerMode.END_OF_SONG -> "Stop at end of current song"
                    PlayerSharedViewModel.SleepTimerMode.END_OF_QUEUE -> "Stop at end of queue"
                    PlayerSharedViewModel.SleepTimerMode.CUSTOM -> {
                        if (remaining > 0) "${remaining / 60000} min remaining"
                        else stringResource(R.string.setting_sleep_timer_off)
                    }
                }
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_timer),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        if (showBottomSheet) {
            SleepTimerBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sleepTimerEndTime = endTime,
                sleepTimerMode = mode,
                onStartTimer = { mins ->
                    onStart(mins)
                },
                onCancelTimer = {
                    onCancel()
                },
                onSetEndOfSong = { enable ->
                    onSetEndOfSong(enable)
                }
            )
        }
    }
}



@Composable
fun ForceOpusRow(
    forceOpus: Boolean,
    onForceOpusChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Force OPUS Stream",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BetaPill(
                    text = "Beta",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        },
        supportingContent = {
            Text(
                text = "Prioritize Opus for higher fidelity streaming",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.HighQuality,
                contentDescription = null,
                tint = if (forceOpus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = forceOpus,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onForceOpusChange(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun LosslessStreamRow(
    losslessStreamPool: Boolean,
    onLosslessStreamPoolChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Lossless Audio Streaming",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BetaPill(
                    text = "FLAC",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        supportingContent = {
            Text(
                text = "Stream studio-quality Lossless FLAC via Source Pool with automatic YouTube fallback",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = if (losslessStreamPool) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = losslessStreamPool,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onLosslessStreamPoolChange(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun LosslessPriorityRow(
    priorityOrder: List<com.codetrio.spatialflow.data.sourcepool.LosslessProvider>,
    disabledProviders: Set<String>,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val activeList = priorityOrder.filter { !disabledProviders.contains(it.id) }
    val summaryText = if (activeList.isNotEmpty()) {
        activeList.joinToString(" ➔ ") { it.displayName }
    } else {
        "All disabled (fallback to YouTube native)"
    }

    ListItem(
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        headlineContent = {
            Text(
                text = "Lossless Provider Priority",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Configure priority order",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SourcePoolStatusRow(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        headlineContent = {
            Text(
                text = "Source Pool",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = "Live sources, Qobuz, Tidal, Apple Music & Gateway",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(id = R.drawable.ic_archivetune),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DeezerAccountRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isLoggedIn = QualityModeSelector.isDeezerLoggedIn(context)
    val accountName = QualityModeSelector.getDeezerAccountName(context)

    ListItem(
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        headlineContent = {
            Text(
                text = "Deezer FLAC",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            val displayName = accountName?.ifBlank { "User" } ?: "User"
            Text(
                text = if (isLoggedIn) "Connected as $displayName • HiFi FLAC" else "Sign in or enter ARL for lossless streaming",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = Color(0xFFA238FF).copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_deezer),
                        contentDescription = "Deezer",
                        tint = Color(0xFFA238FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun TidalAccountRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isLoggedIn = QualityModeSelector.isTidalLoggedIn(context)
    val isPremium = QualityModeSelector.isTidalPremium(context)

    ListItem(
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        headlineContent = {
            Text(
                text = "Tidal Hi-Fi",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = if (isLoggedIn) "Connected • ${if (isPremium) "Hi-Res / HiFi" else "Standard"}" else "Sign in for direct user account streaming",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tidal),
                        contentDescription = "Tidal",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DacStatusRow(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val dacStats by com.codetrio.spatialflow.service.dac.UsbDacManager.liveDacStats.collectAsStateWithLifecycle()
    val runtimeState by com.codetrio.spatialflow.service.dac.UsbDacManager.runtimeState.collectAsStateWithLifecycle()

    ListItem(
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = dacStats.deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (runtimeState.verifiedBitPerfect) {
                    BetaPill(
                        text = "BIT-PERFECT",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else if (dacStats.isUsbDac) {
                    BetaPill(
                        text = "USB DAC",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text = if (dacStats.isUsbDac) {
                    runtimeState.liveStatusDisplay
                } else {
                    "No USB DAC connected. Tap to inspect audio routing."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (runtimeState.verifiedBitPerfect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                imageVector = if (dacStats.isUsbDac) Icons.Rounded.Usb else Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = if (dacStats.isUsbDac) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DacDirectModeRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        headlineContent = {
            Text(
                text = "Direct USB DAC Routing",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = "Explicitly binds audio playback to connected USB DAC hardware",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DacBitPerfectRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Bit-Perfect Output",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BetaPill(
                    text = "USB DAC Only",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = {
            Text(
                text = "Bypasses Android AudioFlinger mixer, resampler, and software DSP for USB DACs. Automatically disabled on wireless (Bluetooth) devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DacNativeSampleRateRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        modifier = Modifier.padding(start = 16.dp),
        headlineContent = {
            Text(
                text = "Native Sample Rate",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = "Matches DAC hardware clock directly to track resolution (44.1k, 48k, 96k, 192k) with zero framework resampling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DacFixedVolumeRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Fixed Line-Out",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Fixed Line-Out sets system media volume to maximum while a USB DAC is connected. Connect this only to a headphone amplifier or powered speakers with volume control. Continue?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onToggle(true)
                    }
                ) {
                    Text("Enable")
                }
            }
        )
    }

    ListItem(
        headlineContent = {
            Text(
                text = "Hardware Volume Lock (0 dBFS)",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = "Locks digital stream at 100% to preserve full 24-bit dynamic range without software attenuation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = { isChecked ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isChecked) {
                        showConfirmDialog = true
                    } else {
                        onToggle(false)
                    }
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DacBannerRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        headlineContent = {
            Text(
                text = "DAC Connection Banner",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = "Displays an expressive floating spring-animated status pill when an external DAC is connected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun RomanizeJapaneseRow() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("lyrics_romanize_japanese", true)) }
    val packState by com.codetrio.spatialflow.data.lyrics.JapaneseLanguagePackManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = {
            Text(
                text = "Romanize Japanese",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Show Romaji pronunciation subtitles for Japanese Kana and Kanji",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when (val st = packState) {
                    is com.codetrio.spatialflow.data.lyrics.JapaneseLanguagePackState.Downloading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.LinearWavyProgressIndicator(
                                progress = { st.progressPercent / 100f },
                                modifier = Modifier.weight(1f).height(6.dp)
                            )
                            Text(
                                text = "${st.progressPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is com.codetrio.spatialflow.data.lyrics.JapaneseLanguagePackState.Failed -> {
                        Text(
                            text = "Dictionary error: ${st.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> Unit
                }
            }
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = { isChecked ->
                    enabled = isChecked
                    prefs.edit().putBoolean("lyrics_romanize_japanese", isChecked).apply()
                    if (isChecked && packState is com.codetrio.spatialflow.data.lyrics.JapaneseLanguagePackState.NotInstalled) {
                        scope.launch {
                            com.codetrio.spatialflow.data.lyrics.JapaneseLanguagePackManager.install()
                        }
                    }
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun RomanizeKoreanRow() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("lyrics_romanize_korean", true)) }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = {
            Text(
                text = "Romanize Korean",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = "Show Revised Romanization (Romaja) subtitles for Korean Hangul",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    prefs.edit().putBoolean("lyrics_romanize_korean", it).apply()
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun RomanizeChineseRow() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("lyrics_romanize_chinese", true)) }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = {
            Text(
                text = "Romanize Chinese",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = "Show Pinyin phonetic subtitles for Chinese Hanzi characters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    prefs.edit().putBoolean("lyrics_romanize_chinese", it).apply()
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun RomanizeHindiRow() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("lyrics_romanize_hindi", true)) }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = {
            Text(
                text = "Romanize Hindi",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = "Show Latin phonetic subtitles for Hindi Devanagari text",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    prefs.edit().putBoolean("lyrics_romanize_hindi", it).apply()
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun RomanizeOtherLanguagesRow() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("lyrics_romanize_other", true)) }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = {
            Text(
                text = "Romanize Other Languages",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = "Show Latin subtitles for Cyrillic, Greek, Arabic, Thai, Hebrew, and other scripts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    prefs.edit().putBoolean("lyrics_romanize_other", it).apply()
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DolbyAtmosToggleRow() {
    val haptic = LocalHapticFeedback.current
    val isEnabled by com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.isAtmosEnabledByUser.collectAsStateWithLifecycle()
    val diag by com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.liveDiagnostics.collectAsStateWithLifecycle()

    ListItem(
        headlineContent = {
            Text(
                text = "Dolby Atmos Playback",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = if (isEnabled) {
                    if (diag.capability == com.codetrio.spatialflow.audio.dolby.DolbyAtmosCapability.FULLY_SUPPORTED) {
                        "Active"
                    } else {
                        "Enabled"
                    }
                } else {
                    "Disabled"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(id = R.drawable.ic_dolby),
                contentDescription = "Dolby Atmos",
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.setAtmosEnabled(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DolbyAtmosStatusRow() {
    val diag by com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.liveDiagnostics.collectAsStateWithLifecycle()
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isExpanded = !isExpanded
            }
            .padding(vertical = 4.dp)
    ) {
        ListItem(
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Spatializer & Decoders",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    when (diag.capability) {
                        com.codetrio.spatialflow.audio.dolby.DolbyAtmosCapability.FULLY_SUPPORTED -> {
                            BetaPill(
                                text = "READY",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        com.codetrio.spatialflow.audio.dolby.DolbyAtmosCapability.DECODER_SUPPORTED -> {
                            BetaPill(
                                text = "DECODER ONLY",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        com.codetrio.spatialflow.audio.dolby.DolbyAtmosCapability.SPATIALIZER_SUPPORTED -> {
                            BetaPill(
                                text = "ROUTE LIMITED",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        com.codetrio.spatialflow.audio.dolby.DolbyAtmosCapability.DISABLED -> {
                            BetaPill(
                                text = "DISABLED",
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        com.codetrio.spatialflow.audio.dolby.DolbyAtmosCapability.UNSUPPORTED -> {
                            BetaPill(
                                text = "UNSUPPORTED",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            },
            supportingContent = {
                Text(
                    text = "Route: ${diag.displayRouteSummary}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RealtimeTelemetryItem("Output Device", diag.activeOutputDeviceName)
                RealtimeTelemetryItem("Route Sink Type", diag.activeOutputDeviceType)
                RealtimeTelemetryItem("E-AC-3 JOC Decoder", if (diag.hasEac3JocDecoder) "Supported (Hardware/OEM)" else "Unavailable")
                RealtimeTelemetryItem("AC-4 Decoder", if (diag.hasAc4Decoder) "Supported" else "Unavailable")
                RealtimeTelemetryItem("Android Spatializer API", if (diag.isSpatializerAvailable) "Available (Android 12L+)" else "Unavailable")
                RealtimeTelemetryItem("Spatializer Enabled", if (diag.isSpatializerEnabled) "Yes" else "No")
                RealtimeTelemetryItem("Route Spatialization", if (diag.canCurrentRouteSpatialize) "Supported & Verified" else "Incompatible / Stereo Only")
            }
        }
    }
}

@Composable
private fun RealtimeTelemetryItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}



