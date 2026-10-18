package com.codetrio.spatialflow.service.dac

sealed interface BitPerfectResult {
    data class Verified(val sampleRateHz: Int, val bitDepth: Int) : BitPerfectResult
    data class Fallback(val reason: String) : BitPerfectResult
    data object Unavailable : BitPerfectResult
}
