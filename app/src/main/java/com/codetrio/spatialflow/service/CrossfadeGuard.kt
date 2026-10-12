package com.codetrio.spatialflow.service

/**
 * Encapsulates calculation and execution guards for crossfade playback,
 * ensuring crossfade is strictly disabled and bypassed in Bit-Perfect mode.
 */
object CrossfadeGuard {

    /**
     * Computes the effective crossfade duration in milliseconds.
     * Always returns 0 when Bit-Perfect mode is active.
     */
    fun computeCrossfadeDurationMs(
        crossfadeEnabled: Boolean,
        crossfadeMode: String,
        configuredDurationSec: Float,
        isBitPerfect: Boolean
    ): Int {
        if (isBitPerfect || !crossfadeEnabled) return 0
        return if (crossfadeMode == "auto") {
            4000 // 4 seconds intelligent default
        } else {
            (configuredDurationSec * 1000).toInt()
        }
    }

    /**
     * Determines whether crossfade should be executed.
     * When Bit-Perfect is active, immediately returns false and invokes [onBypassed].
     */
    fun shouldExecuteCrossfade(
        crossfadeDurationMs: Int,
        isBitPerfectProvider: () -> Boolean,
        onBypassed: (() -> Unit)? = null
    ): Boolean {
        if (isBitPerfectProvider()) {
            onBypassed?.invoke()
            return false
        }
        return crossfadeDurationMs > 0
    }
}
