package com.codetrio.spatialflow.service.dac

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Audiophile Bit-Perfect AudioProcessorChain for Media3 DefaultAudioSink.
 *
 * In Bit-Perfect mode:
 * - Completely bypasses ALL audio processors (returns empty array), ensuring unmodified PCM
 *   flows directly from decoder to sink output.
 * - Bypasses Sonic speed and pitch alteration (forces PlaybackParameters.DEFAULT).
 * - Bypasses silence skipping.
 *
 * In Standard DSP mode:
 * - Sequentially executes user-configured DSP processors (telemetry, 8D spatial, reverb, balance, etc.).
 */
@OptIn(UnstableApi::class)
class BitPerfectAudioProcessorChain(
    private val telemetryProcessor: AudioTelemetryProcessor,
    private val dspProcessors: Array<AudioProcessor>,
    private val isBitPerfectProvider: () -> Boolean
) : DefaultAudioSink.AudioProcessorChain {

    override fun getAudioProcessors(): Array<AudioProcessor> {
        return if (isBitPerfectProvider()) {
            emptyArray()
        } else {
            arrayOf(telemetryProcessor, *dspProcessors)
        }
    }

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        // In Bit-Perfect mode, never apply speed or pitch manipulation to prevent Sonic resampling
        return if (isBitPerfectProvider()) {
            PlaybackParameters.DEFAULT
        } else {
            playbackParameters
        }
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
        // In Bit-Perfect mode, never skip silence to prevent buffer slicing
        return if (isBitPerfectProvider()) false else skipSilenceEnabled
    }

    override fun getMediaDuration(playoutDuration: Long): Long = playoutDuration

    override fun getSkippedOutputFrameCount(): Long = 0L
}
