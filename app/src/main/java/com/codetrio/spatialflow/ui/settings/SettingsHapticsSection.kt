@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.util.PlayerHapticManager
import kotlin.math.abs

@Composable
fun HapticsScreen(
    navController: NavController,
    hasHaptics: Boolean,
    hapticsEnabled: Boolean,
    onHapticsEnabledChange: (Boolean) -> Unit,
    vibrationStrength: Float,
    onVibrationStrengthChange: (Float) -> Unit,
    hapticAudioMode: String,
    onHapticAudioModeChange: (String) -> Unit,
    hapticPunchBoost: Boolean,
    onHapticPunchBoostChange: (Boolean) -> Unit,
    hapticPlayPause: Boolean,
    onHapticPlayPauseChange: (Boolean) -> Unit,
    hapticSliderTicks: Boolean,
    onHapticSliderTicksChange: (Boolean) -> Unit,
    hapticQueue: Boolean,
    onHapticQueueChange: (Boolean) -> Unit,
    hapticFavorite: Boolean,
    onHapticFavoriteChange: (Boolean) -> Unit,
    hapticPauseInBackground: Boolean,
    onHapticPauseInBackgroundChange: (Boolean) -> Unit,
    hapticBatterySaver: Boolean,
    onHapticBatterySaverChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val capabilities = remember(context) { PlayerHapticManager.getDeviceCapabilities(context) }

    SettingsScaffold(
        title = "Haptics",
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
            // ── DIVISION 1: DEVICE HARDWARE CAPABILITIES & DIAGNOSTICS ───────
            SettingsHeader("Device Capabilities")
            SettingsGroupCard(buildList {
                if (!hasHaptics) {
                    add { HapticsNotSupported() }
                } else {
                    add {
                        CapabilityInfoRow(
                            title = "Haptic Actuator",
                            description = if (capabilities.isLraActuator) {
                                "Linear Resonant Actuator (LRA) — High-definition crisp tactile response"
                            } else {
                                "Eccentric Rotating Mass (ERM) — Standard rotational motor"
                            },
                            badgeText = if (capabilities.isLraActuator) "LRA Motor" else "ERM Motor",
                            isSuccess = capabilities.isLraActuator,
                            icon = Icons.Rounded.Vibration
                        )
                    }
                    add {
                        CapabilityInfoRow(
                            title = "Amplitude Modulation",
                            description = if (capabilities.hasAmplitudeControl) {
                                "256-Level Dynamic Control — Fine-grained sub-bass curves and velocity"
                            } else {
                                "Binary Pulse Control — Fixed vibration intensity"
                            },
                            badgeText = if (capabilities.hasAmplitudeControl) "256-Level" else "Fixed Pulse",
                            isSuccess = capabilities.hasAmplitudeControl,
                            icon = Icons.Rounded.Speed
                        )
                    }
                    add {
                        CapabilityInfoRow(
                            title = "Composition Primitives",
                            description = if (capabilities.supportsCompositionPrimitives) {
                                "Hardware supported: " + capabilities.supportedPrimitivesList.joinToString(", ")
                            } else if (capabilities.supportsWaveforms) {
                                "Dynamic Waveform Envelopes supported (Android 8+)"
                            } else {
                                "Basic Legacy Timed Pulses"
                            },
                            badgeText = if (capabilities.supportsCompositionPrimitives) "Rich Primitives" else "Waveforms",
                            isSuccess = capabilities.supportsCompositionPrimitives,
                            icon = Icons.Rounded.Sensors
                        )
                    }
                    add {
                        HapticTestBenchRow()
                    }
                }
            })

            // ── DIVISION 2: MUSIC HAPTICS ENGINE ─────────────────────────────
            SettingsHeader("Music Haptics Engine")
            SettingsGroupCard(buildList {
                add {
                    HapticToggleRow(
                        title = "Music Haptics",
                        subtitle = "Synchronize tactile vibrations with playing music in real-time",
                        checked = hapticsEnabled,
                        onToggle = onHapticsEnabledChange,
                        enabled = hasHaptics,
                        icon = Icons.Rounded.MusicNote
                    )
                }
                add {
                    VibrationStrengthRow(
                        value = vibrationStrength,
                        onValueChange = onVibrationStrengthChange,
                        enabled = hasHaptics && hapticsEnabled
                    )
                }
                add {
                    FrequencyFocusRow(
                        selectedMode = hapticAudioMode,
                        onModeSelected = onHapticAudioModeChange,
                        enabled = hasHaptics && hapticsEnabled
                    )
                }
                add {
                    HapticToggleRow(
                        title = "Kick Punch Boost",
                        subtitle = "Pushes kick drum attacks to 100% motor capacity for deep chest thumps",
                        checked = hapticPunchBoost,
                        onToggle = onHapticPunchBoostChange,
                        enabled = hasHaptics && hapticsEnabled,
                        icon = Icons.Rounded.Bolt
                    )
                }
            })

            // ── DIVISION 3: TACTILE CONTROLS & GESTURES ──────────────────────
            SettingsHeader("Tactile UI Interactions")
            SettingsGroupCard(buildList {
                add {
                    HapticToggleRow(
                        title = "Play / Pause Haptic",
                        subtitle = "Crisp mechanical latch click when audio playback toggles",
                        checked = hapticPlayPause,
                        onToggle = onHapticPlayPauseChange,
                        enabled = hasHaptics,
                        icon = Icons.Rounded.PlayArrow
                    )
                }
                add {
                    HapticToggleRow(
                        title = "Slider & Seeking Detents",
                        subtitle = "Subtle mechanical micro-ticks when dragging seekbars and sliders",
                        checked = hapticSliderTicks,
                        onToggle = onHapticSliderTicksChange,
                        enabled = hasHaptics,
                        icon = Icons.Rounded.Tune
                    )
                }
                add {
                    HapticToggleRow(
                        title = "Heart / Favorite Haptic",
                        subtitle = "Apple Music heartbeat double-pulse when liking songs",
                        checked = hapticFavorite,
                        onToggle = onHapticFavoriteChange,
                        enabled = hasHaptics,
                        icon = Icons.Rounded.Favorite
                    )
                }
                add {
                    HapticToggleRow(
                        title = "Queue Reordering Haptic",
                        subtitle = "Tactile bump when reordering tracks in the playback queue",
                        checked = hapticQueue,
                        onToggle = onHapticQueueChange,
                        enabled = hasHaptics,
                        icon = Icons.Rounded.QueueMusic
                    )
                }
            })

            // ── DIVISION 4: POWER & BATTERY SAFEGUARDS ───────────────────────
            SettingsHeader("Power & Battery Safeguards")
            SettingsGroupCard(buildList {
                add {
                    HapticToggleRow(
                        title = "Disable Haptics in Background",
                        subtitle = "Pause music haptics when app is minimized or screen is turned off",
                        checked = hapticPauseInBackground,
                        onToggle = onHapticPauseInBackgroundChange,
                        enabled = hasHaptics,
                        icon = Icons.Rounded.PhoneAndroid
                    )
                }
                add {
                    HapticToggleRow(
                        title = "Battery Saver Integration",
                        subtitle = "Automatically pause music haptics when Android Battery Saver is active",
                        checked = hapticBatterySaver,
                        onToggle = onHapticBatterySaverChange,
                        enabled = hasHaptics,
                        icon = Icons.Rounded.DataSaverOn
                    )
                }
            })
        }
    }
}

@Composable
fun CapabilityInfoRow(
    title: String,
    description: String,
    badgeText: String,
    isSuccess: Boolean,
    icon: ImageVector? = null
) {
    ListItem(
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun HapticTestBenchRow() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Live Haptic Test Bench",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Touch any preview to feel this device's tactile responses",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { PlayerHapticManager.triggerTestKick(context) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Kick Beat", style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(
                onClick = { PlayerHapticManager.triggerHeartbeat(context) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Heartbeat", style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(
                onClick = { PlayerHapticManager.triggerSliderTick(context) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Detent", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun FrequencyFocusRow(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    enabled: Boolean
) {
    val modes = listOf(
        "full" to "Full Mix",
        "beats" to "Beats Only",
        "subbass" to "Sub-Bass"
    )
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = SmoothSpring,
        label = "frequency_focus_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Audio Frequency Focus",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(
            text = when (selectedMode) {
                "beats" -> "Beats Only: Kicks, snares & percussion only (no humming)"
                "subbass" -> "Sub-Bass: Continuous 808s and low-end basslines"
                else -> "Full Mix: Transient drum punch & continuous body resonance"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 40.dp)
                .padding(bottom = 12.dp)
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            modes.forEachIndexed { index, (modeKey, label) ->
                SegmentedButton(
                    selected = selectedMode == modeKey,
                    onClick = { if (enabled) onModeSelected(modeKey) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun HapticToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean,
    icon: ImageVector? = null
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = SmoothSpring,
        label = "haptic_row_alpha"
    )

    ListItem(
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { this.alpha = alpha }
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.graphicsLayer { this.alpha = alpha }
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { this.alpha = alpha }
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = enabled
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun HapticsNotSupported() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_haptics_not_supported),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun VibrationStrengthRow(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean
) {
    val context = LocalContext.current
    var lastTickValue by remember { mutableFloatStateOf(value) }
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = SmoothSpring,
        label = "haptic_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Vibration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.setting_vibration_strength),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = "${value.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = value.coerceIn(0f, 100f),
            onValueChange = { newVal ->
                onValueChange(newVal)
                if (abs(newVal - lastTickValue) >= 10f) {
                    lastTickValue = newVal
                    PlayerHapticManager.triggerSliderTick(context)
                }
            },
            enabled = enabled,
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
