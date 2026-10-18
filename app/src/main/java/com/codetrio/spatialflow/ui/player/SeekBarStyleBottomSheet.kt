package com.codetrio.spatialflow.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeekBarStyleBottomSheet(
    onDismissRequest: () -> Unit,
    currentStyle: String,
    onStyleSelect: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "Seek Bar Style",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Select your preferred scrubber physics and visual waveform",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Option 1: Apple Music Style
            val isAppleSelected = currentStyle == "apple" || currentStyle == "apple_music"
            SeekBarStyleOptionCard(
                title = "Apple Music",
                tag = "Capsule Scrub",
                description = "Minimalist rounded capsule that smoothly expands with tactile spring physics when touched.",
                isSelected = isAppleSelected,
                onSelect = { onStyleSelect("apple") }
            ) {
                var previewVal by remember { mutableFloatStateOf(0.48f) }
                AppleMusicSlider(
                    value = previewVal,
                    onValueChange = { previewVal = it },
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    thumbColor = MaterialTheme.colorScheme.primary,
                    restingHeight = 8.dp,
                    pressedHeight = 11.dp,
                    expandedHeight = 14.dp,
                    thumbWidth = 4.5.dp,
                    thumbHeight = 18.dp
                )
            }

            // Option 2: Wavy Expressive Style
            val isWavySelected = currentStyle == "wavy"
            SeekBarStyleOptionCard(
                title = "Wavy",
                tag = "Animated Sine",
                description = "Animated sine wave that flattens into a precise linear track during active scrubbing.",
                isSelected = isWavySelected,
                onSelect = { onStyleSelect("wavy") }
            ) {
                var previewVal by remember { mutableFloatStateOf(0.55f) }
                WavyMusicSlider(
                    value = previewVal,
                    onValueChange = { previewVal = it },
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    thumbColor = MaterialTheme.colorScheme.primary,
                    isPlaying = true,
                    isWaveEligible = true,
                    trackHeight = 4.dp,
                    thumbRadius = 6.dp,
                    waveAmplitudeWhenPlaying = 2.5.dp
                )
            }

            // Option 3: Classic Line
            val isClassicSelected = currentStyle == "classic" || currentStyle == "line"
            SeekBarStyleOptionCard(
                title = "Classic Line",
                tag = "Minimalist",
                description = "Standard sleek line progress bar with a classic circular thumb indicator.",
                isSelected = isClassicSelected,
                onSelect = { onStyleSelect("classic") }
            ) {
                var previewVal by remember { mutableFloatStateOf(0.42f) }
                WavyMusicSlider(
                    value = previewVal,
                    onValueChange = { previewVal = it },
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    thumbColor = MaterialTheme.colorScheme.primary,
                    isPlaying = false,
                    isWaveEligible = false,
                    trackHeight = 4.dp,
                    thumbRadius = 6.dp,
                    waveAmplitudeWhenPlaying = 0.dp
                )
            }

            // Option 4: Expressive Waves (Fluid Fill)
            val isLiquidSelected = currentStyle == "liquid" || currentStyle == "spatialwave"
            SeekBarStyleOptionCard(
                title = "Expressive Waves",
                tag = "Fluid Harmonic",
                description = "Multi-layer volumetric fluid waves with dynamic harmonic flow and tactile seek ripple.",
                isSelected = isLiquidSelected,
                onSelect = { onStyleSelect("liquid") }
            ) {
                var previewVal by remember { mutableFloatStateOf(0.62f) }
                SpatialWaveMusicSlider(
                    value = previewVal,
                    onValueChange = { previewVal = it },
                    onValueChangeFinished = null,
                    isPlaying = true,
                    activeColor = MaterialTheme.colorScheme.primary,
                    inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                )
            }
        }
    }
}

@Composable
private fun SeekBarStyleOptionCard(
    title: String,
    tag: String,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    previewContent: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = tag,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(14.dp))

            // Visual Preview Container
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        previewContent()
                    }
                }
            }
        }
    }
}
