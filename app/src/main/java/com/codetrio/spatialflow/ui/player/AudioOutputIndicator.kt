package com.codetrio.spatialflow.ui.player

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.ui.settings.DacCapabilitiesBottomSheet
import com.codetrio.spatialflow.util.QualityModeSelector

/**
 * Pure floating audio output indication below player controls.
 * Features:
 *  - Expressive entry and exit animation when external audio connects or disconnects.
 *  - Automatically hidden when playing from phone speaker (only shown for external audio).
 *  - Displays strictly (Icon + Device Name) with no extra prefix words.
 *  - Zero container / no box / no border (pure unboxed typography).
 *  - Interactive tap to open Bluetooth / audio settings / live DAC inspector.
 */
@Composable
fun AudioOutputIndicator(
    contentColor: Color,
    modifier: Modifier = Modifier,
    outputInfo: AudioOutputInfo? = rememberAudioOutputDevice()
) {
    var lastOutputInfo by remember { mutableStateOf<AudioOutputInfo?>(null) }
    if (outputInfo != null) {
        lastOutputInfo = outputInfo
    }

    AnimatedVisibility(
        visible = outputInfo != null && outputInfo.type != AudioOutputType.SPEAKER,
        modifier = Modifier.fillMaxWidth(),
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + scaleIn(
            initialScale = 0.90f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + expandVertically(
            expandFrom = Alignment.CenterVertically,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + scaleOut(
            targetScale = 0.90f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + shrinkVertically(
            shrinkTowards = Alignment.CenterVertically,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    ) {
        val currentOutput = outputInfo ?: lastOutputInfo ?: return@AnimatedVisibility

        val context = LocalContext.current
        val interactionSource = remember { MutableInteractionSource() }
        var showDacSheet by remember { mutableStateOf(false) }

        if (showDacSheet) {
            DacCapabilitiesBottomSheet(onDismiss = { showDacSheet = false })
        }

        val dacLiveState by com.codetrio.spatialflow.service.dac.UsbDacManager.liveState.collectAsStateWithLifecycle()
        val runtimeState by com.codetrio.spatialflow.service.dac.UsbDacManager.runtimeState.collectAsStateWithLifecycle()
        // Bit-Perfect output is strictly exclusive to physical USB DAC devices. Wireless (Bluetooth) devices must never show or use Bit-Perfect.
        val isUsbOutput = currentOutput.type == AudioOutputType.USB_DAC
        val isBitPerfectVerified = isUsbOutput && runtimeState.verifiedBitPerfect
        val isBitPerfectMode = isUsbOutput && (dacLiveState.playbackMode == com.codetrio.spatialflow.util.PlaybackMode.BIT_PERFECT)
        val isStudioSound = !isBitPerfectMode && QualityModeSelector.isStudioSoundModeEnabled(context)

        val iconVector = when (currentOutput.type) {
            AudioOutputType.SPEAKER -> Icons.Rounded.PhoneAndroid
            AudioOutputType.BLUETOOTH -> Icons.Rounded.Bluetooth
            AudioOutputType.HEADPHONES -> Icons.Rounded.Headphones
            AudioOutputType.USB_DAC -> Icons.Rounded.Usb
        }

        val isHighlighted = isBitPerfectVerified || isStudioSound

        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = contentColor.copy(alpha = 0.20f))
                ) {
                    showDacSheet = true
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = currentOutput.deviceName,
                tint = if (isHighlighted) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.70f),
                modifier = Modifier.size(15.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            val labelText = when {
                isUsbOutput && runtimeState.verifiedBitPerfect -> "${currentOutput.deviceName} • ${runtimeState.qualityTierBadge}"
                isUsbOutput && runtimeState.resamplerActive -> "${currentOutput.deviceName} • Resampled"
                isBitPerfectMode -> "${currentOutput.deviceName} • Standby"
                isStudioSound -> "${currentOutput.deviceName} • SpatialFX"
                else -> currentOutput.deviceName
            }

            Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.1.sp
                ),
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
}
}
