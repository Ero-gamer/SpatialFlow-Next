package com.codetrio.spatialflow.service.dac

import android.media.AudioDeviceInfo
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer

/**
 * Audiophile delegating AudioSink for Media3 ExoPlayer.
 *
 * When a USB DAC is connected:
 * - Completely bypasses Java AudioTrack and AudioFlinger.
 * - Routes raw decoded PCM directly into the native C++ Oboe AAudio MMAP stream.
 * - Strict 1:1 matching of native source sample rate and bit depth (no forced upsampling).
 * - Zero effects / Zero sound boosting.
 *
 * When no USB DAC is connected (or fallback needed):
 * - Seamlessly delegates to Media3's DefaultAudioSink.
 */
@OptIn(UnstableApi::class)
class OboeAudioSink(
    private val fallbackSink: DefaultAudioSink
) : AudioSink {

    companion object {
        private const val TAG = "OboeAudioSink"
    }

    private var listener: AudioSink.Listener? = null

    @Volatile
    private var isOboeActive = false
    private var currentSampleRate = 0
    private var currentBitDepth = 16
    private var currentChannels = 2
    private var currentEncoding = C.ENCODING_PCM_16BIT

    @Volatile
    private var isFirstBufferAfterFlush = true
    @Volatile
    private var startMediaTimeUs: Long = C.TIME_UNSET
    @Volatile
    private var startFramePosition: Long = 0L

    private var lastFormat: Format? = null

    init {
        NativeOboeEngine.onStreamDisconnected = { errorCode ->
            Log.w(TAG, "Native Oboe stream disconnected (error: $errorCode). Requesting fallback...")
            isOboeActive = false
            lastFormat?.let { fmt ->
                listener?.onAudioSinkError(AudioSink.WriteException(errorCode, fmt, false))
            }
        }
    }

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
        fallbackSink.setListener(listener)
    }

    override fun supportsFormat(format: Format): Boolean {
        // Oboe supports raw PCM at all standard rates and depths
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType) {
            return true
        }
        return fallbackSink.supportsFormat(format)
    }

    override fun getFormatSupport(format: Format): Int {
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType) {
            return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        }
        return fallbackSink.getFormatSupport(format)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (isOboeActive && NativeOboeEngine.isStreamOpen) {
            if (isFirstBufferAfterFlush || startMediaTimeUs == C.TIME_UNSET) {
                return AudioSink.CURRENT_POSITION_NOT_SET
            }
            val framesPlayed = (NativeOboeEngine.totalFramesRendered - startFramePosition).coerceAtLeast(0L)
            val playedUs = if (currentSampleRate > 0) {
                (framesPlayed * 1_000_000L) / currentSampleRate
            } else 0L
            return startMediaTimeUs + playedUs
        }
        return fallbackSink.getCurrentPositionUs(sourceEnded)
    }

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?
    ) {
        this.lastFormat = inputFormat
        val isDac = UsbDacManager.isDacConnected.value
        val dacDevice = UsbDacManager.activeDacDevice

        if (isDac && dacDevice != null && NativeOboeEngine.isAvailable) {
            val sampleRate = if (inputFormat.sampleRate > 0) inputFormat.sampleRate else 44100
            val channels = if (inputFormat.channelCount > 0) inputFormat.channelCount else 2
            val bitDepth = when (inputFormat.pcmEncoding) {
                C.ENCODING_PCM_24BIT -> 24
                C.ENCODING_PCM_32BIT -> 32
                C.ENCODING_PCM_FLOAT -> -32
                else -> 16
            }

            // If an Oboe stream is already running with identical format, retain it for 100% gapless playback!
            if (isOboeActive && NativeOboeEngine.isStreamOpen &&
                currentSampleRate == sampleRate && currentBitDepth == bitDepth && currentChannels == channels
            ) {
                Log.d(TAG, "Retaining open Oboe stream for gapless playback ($sampleRate Hz, $bitDepth-bit)")
                isFirstBufferAfterFlush = true
                startMediaTimeUs = C.TIME_UNSET
                startFramePosition = 0L
                return
            }

            // Format change: open a matching native stream on the DAC
            val opened = NativeOboeEngine.openStream(
                deviceId = dacDevice.id,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth
            )

            if (opened) {
                isOboeActive = true
                currentSampleRate = sampleRate
                currentChannels = channels
                currentBitDepth = bitDepth
                currentEncoding = inputFormat.pcmEncoding
                isFirstBufferAfterFlush = true
                startMediaTimeUs = C.TIME_UNSET
                startFramePosition = 0L

                val effectiveDepth = if (bitDepth == -32) 32 else bitDepth
                UsbDacManager.onOboeExclusiveActive(sampleRate, effectiveDepth)
                Log.i(TAG, "Oboe MMAP Stream established: $sampleRate Hz, $effectiveDepth-bit on DAC: ${dacDevice.productName}")
                return
            } else {
                Log.w(TAG, "Failed to open native Oboe stream on DAC. Falling back to DefaultAudioSink.")
                isOboeActive = false
            }
        } else {
            isOboeActive = false
        }

        fallbackSink.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun play() {
        if (isOboeActive) {
            NativeOboeEngine.start()
        } else {
            fallbackSink.play()
        }
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        if (isOboeActive) {
            val remaining = buffer.remaining()
            if (remaining == 0) return true

            if (isFirstBufferAfterFlush) {
                startMediaTimeUs = presentationTimeUs
                startFramePosition = NativeOboeEngine.totalFramesRendered
                isFirstBufferAfterFlush = false
                Log.d(TAG, "Syncing media presentation timebase: startMediaTimeUs=$startMediaTimeUs, startFramePosition=$startFramePosition")
            }

            val written = if (buffer.isDirect) {
                // Zero-copy direct memory write into native lock-free ring buffer
                NativeOboeEngine.writeDirectBuffer(buffer, buffer.position(), remaining)
            } else {
                // Array-backed buffer fallback
                val bytes = ByteArray(remaining)
                val pos = buffer.position()
                buffer.get(bytes)
                buffer.position(pos)
                NativeOboeEngine.writeByteArray(bytes, 0, remaining)
            }

            if (written > 0) {
                buffer.position(buffer.position() + written)
            }

            return written == remaining
        }

        return fallbackSink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun playToEndOfStream() {
        if (isOboeActive) {
            // Native stream drain progress is monitored via hasDrained() / isEnded()
        } else {
            fallbackSink.playToEndOfStream()
        }
    }

    override fun isEnded(): Boolean {
        if (isOboeActive) {
            return NativeOboeEngine.hasDrained()
        }
        return fallbackSink.isEnded
    }

    override fun hasPendingData(): Boolean {
        if (isOboeActive) {
            return !NativeOboeEngine.hasDrained()
        }
        return fallbackSink.hasPendingData()
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        // In Bit-Perfect Oboe mode, always lock to DEFAULT (1.0f speed & pitch) to prevent resampling
        if (isOboeActive) {
            fallbackSink.playbackParameters = PlaybackParameters.DEFAULT
        } else {
            fallbackSink.playbackParameters = playbackParameters
        }
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        if (isOboeActive) {
            return PlaybackParameters.DEFAULT
        }
        return fallbackSink.playbackParameters
    }

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        if (!isOboeActive) {
            fallbackSink.setSkipSilenceEnabled(skipSilenceEnabled)
        }
    }

    override fun getSkipSilenceEnabled(): Boolean {
        return if (isOboeActive) false else fallbackSink.getSkipSilenceEnabled()
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        fallbackSink.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes? {
        return fallbackSink.getAudioAttributes()
    }

    override fun getAudioTrackBufferSizeUs(): Long {
        return fallbackSink.getAudioTrackBufferSizeUs()
    }

    override fun setAudioSessionId(audioSessionId: Int) {
        fallbackSink.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        if (!isOboeActive) {
            fallbackSink.setAuxEffectInfo(auxEffectInfo)
        }
    }

    override fun enableTunnelingV21() {
        fallbackSink.enableTunnelingV21()
    }

    override fun disableTunneling() {
        fallbackSink.disableTunneling()
    }

    override fun setVolume(volume: Float) {
        // When Oboe is active, volume is locked to 1.0f unity gain for bit-perfection
        if (isOboeActive) {
            fallbackSink.setVolume(1.0f)
        } else {
            fallbackSink.setVolume(volume)
        }
    }

    override fun pause() {
        if (isOboeActive) {
            NativeOboeEngine.pause()
        } else {
            fallbackSink.pause()
        }
    }

    override fun flush() {
        if (isOboeActive) {
            NativeOboeEngine.flush()
        }
        isFirstBufferAfterFlush = true
        startMediaTimeUs = C.TIME_UNSET
        startFramePosition = 0L
        fallbackSink.flush()
    }

    override fun reset() {
        if (isOboeActive) {
            NativeOboeEngine.stop()
            NativeOboeEngine.close()
            isOboeActive = false
        }
        isFirstBufferAfterFlush = true
        startMediaTimeUs = C.TIME_UNSET
        startFramePosition = 0L
        fallbackSink.reset()
    }

    override fun release() {
        if (isOboeActive) {
            NativeOboeEngine.stop()
            NativeOboeEngine.close()
            isOboeActive = false
        }
        isFirstBufferAfterFlush = true
        startMediaTimeUs = C.TIME_UNSET
        startFramePosition = 0L
        fallbackSink.release()
    }

    override fun handleDiscontinuity() {
        fallbackSink.handleDiscontinuity()
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        fallbackSink.setPreferredDevice(audioDeviceInfo)
    }
}
