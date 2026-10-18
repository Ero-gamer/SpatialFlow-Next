package com.codetrio.spatialflow.ui.player.canvas

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.Result

private const val TAG = "CanvasArtworkResolver"

/**
 * Coordinates cache lookups and network resolution for canvas motion artwork.
 *
 * Resolution order:
 * 1. [CanvasArtworkPlaybackCache] (local disk cache)
 * 2. [SongCanvasProvider] by song title + artist (with normalized variants)
 * 3. [SongCanvasProvider] by album ID or album title fallback
 */
internal suspend fun resolveCanvasArtworkForPlayback(
    mediaId: String,
    songTitleRaw: String,
    artistNameRaw: String,
    albumId: String? = null,
    albumTitleRaw: String? = null,
    storefront: String = "us",
    requireVertical: Boolean = false,
    allowNetwork: Boolean = true,
): Result<CanvasArtwork, DataError> {
    // 1. Cache hit
    val cached = withContext(Dispatchers.IO) {
        CanvasArtworkPlaybackCache.get(
            mediaId = mediaId,
            preferCachedOnly = !allowNetwork,
        )
    }?.takeIf { it.hasRequiredCanvasVariant(requireVertical) }

    if (cached != null) {
        return Result.Success(cached)
    }

    if (!allowNetwork || mediaId.isBlank()) {
        Log.d(TAG, "Skipping canvas network lookup for mediaId=$mediaId")
        return Result.Error(DataError.Local.NOT_FOUND)
    }

    return withContext(Dispatchers.IO) {
        try {
            val fetched = fetchCanvasArtworkForPlayback(
                songTitleRaw = songTitleRaw,
                artistNameRaw = artistNameRaw,
                storefront = storefront,
                requireVertical = requireVertical,
                mediaId = mediaId,
            ) ?: fetchCanvasArtworkByAlbumFallback(
                albumId = albumId,
                albumTitleRaw = albumTitleRaw,
                artistNameRaw = artistNameRaw,
                storefront = storefront,
                requireVertical = requireVertical,
            )

            if (fetched == null) {
                Log.d(TAG, "No playable canvas resolved for mediaId=$mediaId")
                Result.Error(DataError.Network.NOT_FOUND)
            } else {
                CanvasArtworkPlaybackCache.put(mediaId, fetched)
                Result.Success(fetched)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Canvas network error for mediaId=$mediaId: ${e.message}")
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}

internal suspend fun fetchCanvasArtworkForPlayback(
    songTitleRaw: String,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
    mediaId: String? = null,
): CanvasArtwork? {
    val songTitle = normalizeCanvasSongTitle(songTitleRaw)
    val artistName = normalizeCanvasArtistName(artistNameRaw)

    val candidates = linkedSetOf(
        songTitle to artistName,
        songTitleRaw to artistName,
        songTitle to artistNameRaw,
        songTitleRaw to artistNameRaw,
    ).filter { (song, artist) -> song.isNotBlank() && artist.isNotBlank() }

    return candidates.firstNotNullOfOrNull { (song, artist) ->
        val artwork = SongCanvasProvider
            .getBySongArtist(song = song, artist = artist, storefront = storefront, videoId = mediaId)
            ?.takeIf { it.hasRequiredCanvasVariant(requireVertical) }

        if (artwork != null && !artwork.name.isNullOrBlank() && !artwork.artist.isNullOrBlank()) {
            val normArtworkTitle = com.codetrio.spatialflow.data.sourcepool.TrackMatching.normalizeTitle(artwork.name)
            val normSongTitle = com.codetrio.spatialflow.data.sourcepool.TrackMatching.normalizeTitle(songTitle)
            val normArtworkArtist = com.codetrio.spatialflow.data.sourcepool.TrackMatching.normalize(artwork.artist)
            val normArtistName = com.codetrio.spatialflow.data.sourcepool.TrackMatching.normalize(artistName)

            val titleMatches = normArtworkTitle.contains(normSongTitle) || normSongTitle.contains(normArtworkTitle)
            val artistMatches = normArtworkArtist.contains(normArtistName) || normArtistName.contains(normArtworkArtist)
            if (!titleMatches || !artistMatches) {
                Log.w(TAG, "Rejected mismatched canvas artwork '${artwork.name}' by '${artwork.artist}' for target '$songTitleRaw' by '$artistNameRaw'")
                return@firstNotNullOfOrNull null
            }
        }
        artwork
    }
}

private suspend fun fetchCanvasArtworkByAlbumFallback(
    albumId: String?,
    albumTitleRaw: String?,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
): CanvasArtwork? {
    albumId?.trim()?.takeIf { it.isNotBlank() }?.let { nonBlankAlbumId ->
        SongCanvasProvider
            .getByAlbumId(nonBlankAlbumId)
            ?.takeIf { it.hasRequiredCanvasVariant(requireVertical) }
            ?.let { return it }
    }

    val albumTitle = albumTitleRaw?.trim().orEmpty()
    val artistName = artistNameRaw.trim()
    if (albumTitle.isBlank() || artistName.isBlank()) return null

    // Fallback directly to Apple Music ALBUM motion artwork, NEVER query a song named after the album
    return AppleMusicCanvasProvider
        .getByAlbumArtist(album = albumTitle, artist = artistName, storefront = storefront)
        ?.takeIf { it.hasRequiredCanvasVariant(requireVertical) }
}

internal fun CanvasArtwork.hasRequiredCanvasVariant(requireVertical: Boolean): Boolean =
    if (requireVertical) !preferredVerticalAnimationUrl.isNullOrBlank()
    else (!preferredAnimationUrl.isNullOrBlank() || !preferredVerticalAnimationUrl.isNullOrBlank())

// ───────────── Query normalization ─────────────

internal fun normalizeCanvasSongTitle(raw: String): String {
    val stripped = raw
        .replace(Regex("\\s*\\[[^]]*]"), "")
        .replace(Regex("\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)", RegexOption.IGNORE_CASE), "")
        .replace(
            Regex(
                "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        .replace(
            Regex(
                "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        .replace(Regex("\\s+"), " ")
        .trim()

    return stripped
        .trim('-')
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun normalizeCanvasArtistName(raw: String): String {
    val first = raw.split(
        Regex(
            "(?:\\s*,\\s*|\\s*&\\s*|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
            RegexOption.IGNORE_CASE,
        ),
        limit = 2,
    ).firstOrNull().orEmpty()

    return first.replace(Regex("\\s+"), " ").trim()
}
