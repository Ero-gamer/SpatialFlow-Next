package com.codetrio.spatialflow.service.dac

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Platform-independent representation of audio mixer attributes for testing and HAL communication.
 */
data class MixerAttribute(
    val mixerBehavior: Int,
    val sampleRate: Int,
    val encoding: Int,
    val channelMask: Int = AudioFormat.CHANNEL_OUT_STEREO,
    val raw: AudioMixerAttributes? = null
) {
    companion object {
        const val BEHAVIOR_DEFAULT = 0
        const val BEHAVIOR_BIT_PERFECT = 1
    }
}

/**
 * Abstraction over Android 14+ AudioManager mixer attributes APIs to enable unit testing without a physical device.
 */
interface MixerAttributesProvider {
    fun getSupported(device: AudioDeviceInfo?): List<MixerAttribute>
    fun getPreferred(attributes: AudioAttributes?, device: AudioDeviceInfo?): MixerAttribute?
    fun set(attributes: AudioAttributes?, device: AudioDeviceInfo?, mixerAttribute: MixerAttribute): Boolean
    fun clear(attributes: AudioAttributes?, device: AudioDeviceInfo?): Boolean
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AndroidMixerAttributesProvider(
    private val audioManager: AudioManager
) : MixerAttributesProvider {

    override fun getSupported(device: AudioDeviceInfo?): List<MixerAttribute> {
        if (device == null) return emptyList()
        return try {
            audioManager.getSupportedMixerAttributes(device).map { attr ->
                MixerAttribute(
                    mixerBehavior = attr.mixerBehavior,
                    sampleRate = attr.format.sampleRate,
                    encoding = attr.format.encoding,
                    channelMask = attr.format.channelMask,
                    raw = attr
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun getPreferred(attributes: AudioAttributes?, device: AudioDeviceInfo?): MixerAttribute? {
        if (attributes == null || device == null) return null
        return try {
            val pref = audioManager.getPreferredMixerAttributes(attributes, device) ?: return null
            MixerAttribute(
                mixerBehavior = pref.mixerBehavior,
                sampleRate = pref.format.sampleRate,
                encoding = pref.format.encoding,
                channelMask = pref.format.channelMask,
                raw = pref
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun set(attributes: AudioAttributes?, device: AudioDeviceInfo?, mixerAttribute: MixerAttribute): Boolean {
        if (attributes == null || device == null) return false
        val raw = mixerAttribute.raw ?: return false
        return try {
            audioManager.setPreferredMixerAttributes(attributes, device, raw)
        } catch (_: Exception) {
            false
        }
    }

    override fun clear(attributes: AudioAttributes?, device: AudioDeviceInfo?): Boolean {
        if (attributes == null || device == null) return false
        return try {
            audioManager.clearPreferredMixerAttributes(attributes, device)
        } catch (_: Exception) {
            false
        }
    }
}
