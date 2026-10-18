package com.codetrio.spatialflow.service.dac

/**
 * 100% real-time telemetry model for the decoded audio source.
 */
data class SourceAudioStats(
    val title: String? = null,
    val artist: String? = null,
    val codec: String? = null,
    val formatTag: String? = null,
    val mimeType: String = "Unknown",
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val channelCount: Int = 0,
    val bitrateBps: Int = 0,
    val isPlaying: Boolean = false
) {
    val displayBitrate: String
        get() = if (bitrateBps > 0) "${bitrateBps / 1000} kbps" else "Variable VBR"

    val displaySampleRate: String
        get() = if (sampleRateHz > 0) "${sampleRateHz / 1000.0} kHz".replace(".0", "") else "Unknown"

    val displayBitDepth: String
        get() = if (bitDepth > 0) "$bitDepth-bit" else "16-bit"

    val displayChannels: String
        get() = when (channelCount) {
            1 -> "Mono (1.0)"
            2 -> "Stereo (2.0)"
            6 -> "Surround (5.1)"
            8 -> "Surround (7.1)"
            else -> if (channelCount > 0) "$channelCount ch" else "Stereo"
        }

    val displayCodec: String
        get() = codec ?: when {
            mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("opus", ignoreCase = true) -> "OPUS"
            mimeType.contains("aac", ignoreCase = true) || mimeType.contains("mp4a", ignoreCase = true) -> "AAC"
            mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "MP3"
            mimeType.contains("wav", ignoreCase = true) -> "WAV"
            else -> "PCM"
        }
}

/**
 * Live telemetry model capturing ExoPlayer's internal PCM processing stage.
 */
data class PipelineAudioStats(
    val pcmEncoding: String = "16-bit Integer PCM",
    val sampleRateHz: Int = 0,
    val channelCount: Int = 2,
    val isDspBypassed: Boolean = false,
    val activeProcessors: List<String> = emptyList()
) {
    val actualPcmEncoding: String get() = pcmEncoding
    val isBitPerfectVerified: Boolean get() = isDspBypassed
}

enum class BitPerfectVerificationStatus {
    VERIFIED,
    FALLBACK_SYSTEM_MIXER,
    UNAVAILABLE_NO_DAC,
    UNSUPPORTED_SAMPLE_RATE,
    UNSUPPORTED_BIT_DEPTH,
    DSP_ACTIVE
}

/**
 * Live telemetry model introspecting the connected hardware audio sink (USB DAC, Bluetooth, etc.).
 */
data class HardwareDacStats(
    val deviceName: String = "Internal Speaker",
    val manufacturer: String? = null,
    val vendorIdHex: String? = null,
    val productIdHex: String? = null,
    val isUsbDac: Boolean = false,
    val supportedSampleRates: List<Int> = emptyList(),
    val supportedEncodings: List<String> = emptyList(),
    val isBitPerfectVerified: Boolean = false,
    val verificationStatus: BitPerfectVerificationStatus = BitPerfectVerificationStatus.UNAVAILABLE_NO_DAC,
    val verificationDetails: String = "Internal Android audio routing",
    val activeHardwareSampleRateHz: Int = 0,
    val activeHardwareEncoding: String = "16-bit PCM",
    val mixerModeSummary: String = "Default System Mixer (AudioFlinger)"
) {
    val vendorId: String? get() = vendorIdHex
    val productId: String? get() = productIdHex
    val activeSampleRate: String?
        get() = if (activeHardwareSampleRateHz > 0) "${activeHardwareSampleRateHz / 1000.0} kHz".replace(".0", "") else null
}

/**
 * Aggregated live audio pipeline state representing the 3-stage audiophile signal path.
 */
data class AudioPipelineLiveState(
    val source: SourceAudioStats = SourceAudioStats(),
    val pipeline: PipelineAudioStats = PipelineAudioStats(),
    val hardware: HardwareDacStats = HardwareDacStats(),
    val playbackMode: com.codetrio.spatialflow.util.PlaybackMode = com.codetrio.spatialflow.util.PlaybackMode.DSP,
    val isDirectDacActive: Boolean = false,
    val isBitPerfectActive: Boolean = false
) {
    val verificationStatus: BitPerfectVerificationStatus get() = hardware.verificationStatus
}

/**
 * Event fired when a physical DAC is plugged in or identified.
 */
data class DacConnectionEvent(
    val deviceName: String,
    val isBitPerfect: Boolean,
    val sampleRateHz: Int,
    val vendorIdHex: String?,
    val productIdHex: String?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Authoritative single source of truth for the active Bit-Perfect / Native Output audio pipeline.
 */
data class BitPerfectRuntimeState(
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val codec: String? = null,
    val formatTag: String? = null,
    val bitrateBps: Int = 0,
    val isPlaying: Boolean = false,
    val sourceEncoding: String = "16-bit PCM",
    val sourceBitDepth: Int = 16,
    val sourceSampleRate: Int = 44100,
    val outputEncoding: String = "16-bit PCM",
    val outputBitDepth: Int = 16,
    val outputSampleRate: Int = 44100,
    val channels: Int = 2,
    val directPlaybackSupported: Boolean = false,
    val nativeRateMatched: Boolean = false,
    val resamplerActive: Boolean = false,
    val dspActive: Boolean = false,
    val softwareVolumeActive: Boolean = false,
    val usbExclusiveActive: Boolean = false,
    val mixerBitPerfectActive: Boolean = false,
    val verifiedBitPerfect: Boolean = false,
    val oboeExclusiveActive: Boolean = false,
    val failureReason: String? = null,
    val bitPerfectResult: BitPerfectResult = BitPerfectResult.Unavailable
) {
    /**
     * Standard clean audiophile tier badge: "Hi-Res Lossless" or "Lossless" (zero emojis).
     */
    val qualityTierBadge: String
        get() {
            val isLossless = codec?.let {
                it.contains("flac", ignoreCase = true) ||
                it.contains("alac", ignoreCase = true) ||
                it.contains("wav", ignoreCase = true) ||
                it.contains("aiff", ignoreCase = true) ||
                it.contains("dsd", ignoreCase = true)
            } ?: (sourceBitDepth >= 16)

            return when {
                !isLossless -> "High-Quality"
                sourceSampleRate > 48000 || sourceBitDepth > 16 -> "Hi-Res Lossless"
                else -> "Lossless"
            }
        }
    /**
     * Read-only live status representing the ACTUAL active hardware output.
     * Formats strictly:
     * - "Bit-Perfect • 24-bit • 96 kHz"
     * - "Native Rate • 24-bit • 44.1 kHz"
     * - "Resampling • 24-bit/96 kHz → 48 kHz"
     * - "Bit-Perfect unavailable"
     */
    val liveStatusDisplay: String
        get() = when {
            verifiedBitPerfect -> "Bit-Perfect • $outputBitDepth-bit • ${formatRate(outputSampleRate)}"
            resamplerActive -> "Resampling • $sourceBitDepth-bit/${formatRate(sourceSampleRate)} → ${formatRate(outputSampleRate)}"
            nativeRateMatched && !dspActive -> "Native Rate • $outputBitDepth-bit • ${formatRate(outputSampleRate)}"
            failureReason?.contains("unavailable", ignoreCase = true) == true || bitPerfectResult is BitPerfectResult.Unavailable -> "Bit-Perfect unavailable"
            dspActive -> "SpatialFX (Mixer Active)"
            else -> "Bit-Perfect unavailable"
        }

    private fun formatRate(rate: Int): String {
        return if (rate > 0) "${rate / 1000.0} kHz".replace(".0", "") else "Unknown"
    }
}

