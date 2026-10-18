package com.codetrio.spatialflow.data.sourcepool

import androidx.annotation.DrawableRes
import com.codetrio.spatialflow.R

/**
 * Supported lossless & high-fidelity audio stream providers in SpatialFlow.
 */
enum class LosslessProvider(
    val id: String,
    val displayName: String,
    val description: String,
    val qualityTag: String,
    @DrawableRes val logoDrawableRes: Int
) {
    KOUZU(
        id = "kouzu",
        displayName = "Kouzu (MLS)",
        description = "Direct YouTube ID match via mls.kouzu.in",
        qualityTag = "16/24-bit Studio FLAC",
        logoDrawableRes = R.drawable.ic_lossless
    ),
    DEEZER(
        id = "deezer",
        displayName = "Deezer",
        description = "Lossless CD FLAC via Deezer catalog",
        qualityTag = "16-bit 44.1kHz FLAC",
        logoDrawableRes = R.drawable.ic_deezer
    ),
    TIDAL(
        id = "tidal",
        displayName = "Tidal",
        description = "Hi-Fi & Lossless FLAC via Tidal source pool",
        qualityTag = "Hi-Fi / Master FLAC",
        logoDrawableRes = R.drawable.ic_tidal
    ),
    QOBUZ(
        id = "qobuz",
        displayName = "Qobuz",
        description = "Studio FLAC via Source Pool catalog search",
        qualityTag = "Up to 24-bit 192kHz FLAC",
        logoDrawableRes = R.drawable.ic_qobuz
    ),
    SAAVN(
        id = "saavn",
        displayName = "JioSaavn",
        description = "Direct 320 kbps high-fidelity stream via JioSaavn CDN",
        qualityTag = "320 kbps AAC Hi-Fi",
        logoDrawableRes = R.drawable.ic_lossless
    );

    companion object {
        // Alias MLS to KOUZU for seamless backwards compatibility
        val MLS: LosslessProvider get() = KOUZU

        fun fromId(id: String?): LosslessProvider {
            return when (id?.trim()?.lowercase()) {
                "kouzu", "mls" -> KOUZU
                "deezer" -> DEEZER
                "tidal" -> TIDAL
                "qobuz" -> QOBUZ
                "saavn", "jiosaavn" -> SAAVN
                else -> KOUZU
            }
        }

        val DEFAULT_PRIORITY_ORDER: List<LosslessProvider> = listOf(QOBUZ, SAAVN, TIDAL, DEEZER, KOUZU)
    }
}
