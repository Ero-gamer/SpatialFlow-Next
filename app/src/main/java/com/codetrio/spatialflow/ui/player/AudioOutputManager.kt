package com.codetrio.spatialflow.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class AudioOutputType {
    SPEAKER,
    BLUETOOTH,
    HEADPHONES,
    USB_DAC
}

data class AudioOutputInfo(
    val type: AudioOutputType,
    val deviceName: String,
    val isBitPerfect: Boolean = false
) {
    val isWireless: Boolean get() = type == AudioOutputType.BLUETOOTH
    val isUsb: Boolean get() = type == AudioOutputType.USB_DAC
}

/**
 * Returns the actively connected external audio output route (USB DAC, Bluetooth, or Wired Headphones)
 * along with its friendly product name and real-time bit-perfect status.
 * Returns null when playing from the phone's internal speaker.
 */
fun getActiveAudioOutput(context: Context): AudioOutputInfo? {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return null

    val devices = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    } catch (e: Exception) {
        emptyArray()
    }

    // 1. USB DAC / USB Audio Device (External DAC/Amps, Type-C dongles)
    val usbDevice = devices.firstOrNull { dev ->
        dev.type == AudioDeviceInfo.TYPE_USB_DEVICE || dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }
    if (usbDevice != null) {
        val dacStats = com.codetrio.spatialflow.service.dac.UsbDacManager.liveDacStats.value
        val rawName = if (dacStats.isUsbDac && dacStats.deviceName.isNotBlank()) {
            dacStats.deviceName
        } else {
            val raw = usbDevice.productName?.toString()?.trim().orEmpty()
            if (raw.isNotBlank() && !raw.equals("default", ignoreCase = true)) raw else "USB DAC"
        }
        // Strip redundant "USB-Audio - " prefix (common Android HAL naming pattern)
        val name = rawName
            .removePrefix("USB-Audio - ")
            .removePrefix("USB-audio - ")
            .trim()
            .ifEmpty { "USB DAC" }
        return AudioOutputInfo(
            type = AudioOutputType.USB_DAC,
            deviceName = name,
            isBitPerfect = dacStats.isBitPerfectVerified
        )
    }

    // 2. Bluetooth Audio (A2DP, SCO, BLE Headset, BLE Speaker)
    val btDevice = devices.firstOrNull { dev ->
        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
            dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
            dev.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
        ))
    }
    if (btDevice != null) {
        val rawName = btDevice.productName?.toString()?.trim().orEmpty()
        val cleanName = if (rawName.isNotBlank() && !rawName.equals("default", ignoreCase = true)) {
            rawName
        } else {
            "Audio"
        }
        return AudioOutputInfo(
            type = AudioOutputType.BLUETOOTH,
            deviceName = cleanName
        )
    }

    // 3. Wired Analog Headphones / Headset (3.5mm Jack)
    val wiredDevice = devices.firstOrNull { dev ->
        dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
    }
    if (wiredDevice != null) {
        val rawName = wiredDevice.productName?.toString()?.trim().orEmpty()
        val cleanName = if (rawName.isNotBlank() && !rawName.equals("default", ignoreCase = true)) {
            rawName
        } else {
            "Headphones"
        }
        return AudioOutputInfo(
            type = AudioOutputType.HEADPHONES,
            deviceName = cleanName
        )
    }

    // 4. Phone Built-in Speaker
    return AudioOutputInfo(
        type = AudioOutputType.SPEAKER,
        deviceName = "Phone Speaker"
    )
}

/**
 * Composable helper that observes audio output changes (Bluetooth connect/disconnect,
 * wired plug/unplug) in real time. Returns null when playing through phone speaker.
 */
@Composable
fun rememberAudioOutputDevice(): AudioOutputInfo? {
    val context = LocalContext.current
    var currentOutput by remember { mutableStateOf(getActiveAudioOutput(context)) }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val audioDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                currentOutput = getActiveAudioOutput(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                currentOutput = getActiveAudioOutput(context)
            }
        }

        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)

        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                currentOutput = getActiveAudioOutput(context)
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
        }

        try {
            context.registerReceiver(broadcastReceiver, filter)
        } catch (e: Exception) {
            // Ignored on restricted configurations
        }

        onDispose {
            try {
                audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            } catch (e: Exception) {
                // Ignore
            }
            try {
                context.unregisterReceiver(broadcastReceiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    return currentOutput
}
