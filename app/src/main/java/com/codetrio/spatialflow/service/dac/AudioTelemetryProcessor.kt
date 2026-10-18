package com.codetrio.spatialflow.service.dac

import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp

/**
 * Audio processor that copies buffer to output, capturing live decoded PCM formats
 * directly from ExoPlayer's audio pipeline for telemetry and extracting
 * sample-rate-adaptive PCM data for real-time music haptics when in DSP mode.
 */
@OptIn(UnstableApi::class)
class AudioTelemetryProcessor(
    val observeOnly: Boolean = false
) : BaseAudioProcessor() {

    private val _pipelineStats = MutableStateFlow(PipelineAudioStats())
    val pipelineStats: StateFlow<PipelineAudioStats> = _pipelineStats.asStateFlow()

    @Volatile
    var isDspBypassed: Boolean = false
        set(value) {
            field = value
            updateState()
        }

    @Volatile
    var activeDspList: List<String> = emptyList()
        set(value) {
            field = value
            updateState()
        }

    private var currentSampleRate: Int = 44100
    private var currentChannels: Int = 2
    private var currentEncoding: Int = C.ENCODING_PCM_16BIT
    private var currentEncodingName: String = "16-bit PCM"

    // Dynamic Crossover Filter Coefficients (sample-rate adaptive)
    private var alphaSubBass: Float = 0.06f
    private var alphaBass: Float = 0.22f
    private var alphaMid: Float = 0.95f

    // Running IIR filter states
    private var subBassFilterState = 0f
    private var bassFilterState = 0f
    private var midFilterState = 0f

    private val step = 4 // Downsample step to process at ~11-48kHz effective rate, ensuring Nyquist stability

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        currentSampleRate = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 44100
        currentChannels = inputAudioFormat.channelCount
        currentEncoding = inputAudioFormat.encoding
        val bitDepth = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT -> 32
            C.ENCODING_PCM_FLOAT -> 32
            C.ENCODING_PCM_16BIT -> 16
            else -> 16
        }
        currentEncodingName = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> "32-bit Float PCM"
            C.ENCODING_PCM_24BIT -> "24-bit Packed PCM"
            C.ENCODING_PCM_32BIT -> "32-bit Integer PCM"
            C.ENCODING_PCM_16BIT -> "16-bit Integer PCM"
            else -> "PCM (Format ${inputAudioFormat.encoding})"
        }

        // Dynamically compute crossover filter coefficients based on current sample rate:
        val effectiveFs = (currentSampleRate.toFloat() / step).coerceAtLeast(1000f)
        val twoPi = 2.0 * Math.PI
        // Target cutoffs: Sub-bass: 60Hz, Bass: 220Hz, Mid: 2600Hz
        alphaSubBass = (1.0 - exp(-twoPi * 60.0 / effectiveFs)).toFloat().coerceIn(0.001f, 0.999f)
        alphaBass = (1.0 - exp(-twoPi * 220.0 / effectiveFs)).toFloat().coerceIn(0.001f, 0.999f)
        alphaMid = (1.0 - exp(-twoPi * 2600.0 / effectiveFs)).toFloat().coerceIn(0.001f, 0.999f)

        // Reset filter states on format reconfiguration
        subBassFilterState = 0f
        bassFilterState = 0f
        midFilterState = 0f

        updateState()
        UsbDacManager.onDecoderFormatChanged(
            sampleRateHz = inputAudioFormat.sampleRate,
            bitDepth = bitDepth,
            encoding = inputAudioFormat.encoding,
            channelCount = inputAudioFormat.channelCount
        )
        return inputAudioFormat
    }

    private fun updateState() {
        _pipelineStats.value = PipelineAudioStats(
            pcmEncoding = currentEncodingName,
            sampleRateHz = currentSampleRate,
            channelCount = currentChannels,
            isDspBypassed = isDspBypassed,
            activeProcessors = if (isDspBypassed) emptyList() else activeDspList
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Copies buffer to output for AudioTrack / downstream sink
        val buffer = replaceOutputBuffer(remaining)
        buffer.put(inputBuffer)
        buffer.flip()

        // Read-only PCM analysis for Apple Music-style music haptics (bypassed in bit-perfect or observe-only mode)
        if (!isDspBypassed && !observeOnly) {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            try {
                analyzePcmForHaptics(buffer, remaining)
            } catch (_: Throwable) {
                // Ignore in headless / test environments
            }
        }
    }

    private fun analyzePcmForHaptics(buffer: ByteBuffer, length: Int) {
        val service = com.codetrio.spatialflow.service.AudioPlaybackService.currentInstance
        val haptic = service?.hapticManager ?: service?.viewModel?.hapticManager
        if (haptic == null || !haptic.isHapticsEnabled) return

        if (service?.hapticPauseInBackground == true) {
            val pm = service.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            if (pm?.isInteractive == false) return
        }

        val prefs = service?.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        if (prefs?.getBoolean("haptic_battery_saver", true) == true) {
            val pm = service.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            if (pm?.isPowerSaveMode == true) return
        }

        val inEncoding = currentEncoding
        val channels = currentChannels.coerceAtLeast(1)
        val isStereo = channels >= 2
        val bytesPerSample = when (inEncoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        val bytesPerFrame = bytesPerSample * channels
        val frameCount = length / bytesPerFrame
        if (frameCount <= 0) return

        var sumSubBass = 0f
        var sumBass = 0f
        var sumMid = 0f
        var sumHigh = 0f
        var count = 0

        val startPos = buffer.position()

        for (i in 0 until frameCount step step) {
            val bytePos = startPos + i * bytesPerFrame
            if (bytePos + bytesPerFrame > buffer.limit()) break

            val sampleVal: Float = when (inEncoding) {
                C.ENCODING_PCM_16BIT -> {
                    val left = buffer.getShort(bytePos).toFloat()
                    val right = if (isStereo) buffer.getShort(bytePos + 2).toFloat() else left
                    (left + right) * (0.5f / 32768f)
                }
                C.ENCODING_PCM_FLOAT -> {
                    val left = buffer.getFloat(bytePos)
                    val right = if (isStereo) buffer.getFloat(bytePos + 4) else left
                    ((left + right) * 0.5f).coerceIn(-1.5f, 1.5f)
                }
                C.ENCODING_PCM_24BIT -> {
                    val inv24 = 1.0f / 8388608.0f
                    val b0 = buffer.get(bytePos).toInt() and 0xFF
                    val b1 = buffer.get(bytePos + 1).toInt() and 0xFF
                    val b2 = buffer.get(bytePos + 2).toInt() and 0xFF
                    val raw24 = (b2 shl 16) or (b1 shl 8) or b0
                    val signed24 = if ((raw24 and 0x800000) != 0) raw24 or -0x1000000 else raw24
                    val left = signed24 * inv24
                    val right = if (isStereo) {
                        val r0 = buffer.get(bytePos + 3).toInt() and 0xFF
                        val r1 = buffer.get(bytePos + 4).toInt() and 0xFF
                        val r2 = buffer.get(bytePos + 5).toInt() and 0xFF
                        val rawR = (r2 shl 16) or (r1 shl 8) or r0
                        val signedR = if ((rawR and 0x800000) != 0) rawR or -0x1000000 else rawR
                        signedR * inv24
                    } else left
                    (left + right) * 0.5f
                }
                C.ENCODING_PCM_32BIT -> {
                    val inv32 = 1.0f / 2147483648.0f
                    val left = buffer.getInt(bytePos) * inv32
                    val right = if (isStereo) buffer.getInt(bytePos + 4) * inv32 else left
                    (left + right) * 0.5f
                }
                else -> {
                    val left = buffer.getShort(bytePos).toFloat()
                    val right = if (isStereo) buffer.getShort(bytePos + 2).toFloat() else left
                    (left + right) * (0.5f / 32768f)
                }
            }

            if (sampleVal.isNaN() || sampleVal.isInfinite()) continue

            // Dynamic Sample-Rate Adaptive Crossover Filters (1st order IIR)
            subBassFilterState = alphaSubBass * sampleVal + (1f - alphaSubBass) * subBassFilterState
            bassFilterState = alphaBass * sampleVal + (1f - alphaBass) * bassFilterState
            midFilterState = alphaMid * sampleVal + (1f - alphaMid) * midFilterState

            val subBassSample = subBassFilterState
            val bassSample = bassFilterState - subBassFilterState
            val midSample = midFilterState - bassFilterState
            val highSample = sampleVal - midFilterState

            sumSubBass += abs(subBassSample)
            sumBass += abs(bassSample)
            sumMid += abs(midSample)
            sumHigh += abs(highSample)
            count++
        }

        if (count > 0) {
            val boost = if (inEncoding == C.ENCODING_PCM_24BIT || inEncoding == C.ENCODING_PCM_FLOAT || inEncoding == C.ENCODING_PCM_32BIT) 6.0f else 4.5f
            val subBassEnergy = (sumSubBass / count) * boost
            val bassEnergy = (sumBass / count) * boost
            val midEnergy = (sumMid / count) * boost
            val highEnergy = (sumHigh / count) * boost

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                haptic.processPcmHaptics(
                    subBassEnergy.coerceIn(0f, 1f),
                    bassEnergy.coerceIn(0f, 1f),
                    midEnergy.coerceIn(0f, 1f),
                    highEnergy.coerceIn(0f, 1f)
                )
            }
        }
    }
}
