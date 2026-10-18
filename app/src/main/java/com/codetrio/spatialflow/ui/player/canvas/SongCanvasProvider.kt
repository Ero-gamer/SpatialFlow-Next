package com.codetrio.spatialflow.ui.player.canvas

import android.content.Context
import com.codetrio.spatialflow.SpatialFlowApplication
import com.codetrio.spatialflow.data.spotify.SpotifyClient
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates canvas motion artwork across Spotify and Apple Music providers.
 *
 * Fallback order is configured by user preference (`canvas_provider_order` in AppSettings),
 * defaulting to:
 * 1. If Spotify is authenticated: Spotify Canvaz -> Apple Music Motion Artwork
 * 2. If Spotify is unauthenticated: Apple Music Motion Artwork -> Spotify Canvaz
 *
 * Thread-safe in-memory TTL cache (60 s) avoids duplicate network calls for the same track.
 */
internal object SongCanvasProvider {

    private data class CacheEntry(val value: CanvasArtwork?, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val TTL_MS = 60_000L

    fun clearCache() {
        cache.clear()
    }

    private fun cacheKey(prefix: String, vararg parts: String): String {
        val normalized = parts.map { it.trim().lowercase(Locale.ROOT) }.joinToString("|")
        return "$prefix|$normalized"
    }

    private suspend fun getPreferredCanvasOrder(): List<String> {
        val context = runCatching { SpatialFlowApplication.instance }.getOrNull()
        if (context != null) {
            val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val orderRaw = prefs.getString("canvas_provider_order", null)
            if (!orderRaw.isNullOrBlank()) {
                val list = orderRaw.split(",").map { it.trim() }.filter { it in listOf("spotify", "apple_music") }
                if (list.isNotEmpty()) {
                    return (list + listOf("spotify", "apple_music")).distinct()
                }
            }
        }
        val isSpotifyAuthenticated = !SpotifyClient.getEffectiveToken().isNullOrBlank()
        return if (isSpotifyAuthenticated) {
            listOf("spotify", "apple_music")
        } else {
            listOf("apple_music", "spotify")
        }
    }

    private fun CanvasArtwork?.hasValidVideo(): Boolean =
        this != null && (!animated.isNullOrBlank() || !videoUrl.isNullOrBlank() ||
            !animatedVertical.isNullOrBlank() || !videoUrlVertical.isNullOrBlank())

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        videoId: String? = null,
        storefront: String = "us",
    ): CanvasArtwork? {
        val order = getPreferredCanvasOrder()
        val key = cacheKey("sa", song, artist, storefront, videoId ?: "", order.joinToString(","))
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        var value: CanvasArtwork? = null
        for (provider in order) {
            val result = when (provider) {
                "spotify" -> SpotifyCanvasProvider.getBySongArtist(song, artist, videoId)
                "apple_music" -> AppleMusicCanvasProvider.getBySongArtist(song, artist, null, storefront)
                else -> null
            }
            if (result.hasValidVideo()) {
                value = result
                break
            } else if (result != null && value == null) {
                // Keep static artwork as last-resort fallback if no video is found
                value = result
            }
        }

        cache[key] = CacheEntry(value = value, expiresAtMs = System.currentTimeMillis() + TTL_MS)
        return value
    }

    suspend fun getByAlbumId(albumId: String, storefront: String = "us"): CanvasArtwork? {
        val key = cacheKey("id", albumId, storefront)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val value = AppleMusicCanvasProvider.getByAlbumId(albumId, storefront)
        cache[key] = CacheEntry(value = value, expiresAtMs = System.currentTimeMillis() + TTL_MS)
        return value
    }

    suspend fun getByAlbumUrl(url: String): CanvasArtwork? {
        val key = cacheKey("url", url)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val value = parseAppleMusicAlbumUrl(url)?.let { (albumId, storefront) ->
            AppleMusicCanvasProvider.getByAlbumId(albumId, storefront)
        }

        cache[key] = CacheEntry(value = value, expiresAtMs = System.currentTimeMillis() + TTL_MS)
        return value
    }

    private fun parseAppleMusicAlbumUrl(url: String): Pair<String, String>? {
        if (!url.contains("music.apple.com")) return null
        val albumPart = url.substringAfter("/album/", "").substringBefore("?")
        val albumId = albumPart.substringAfterLast("/", "")
        if (albumId.isBlank() || !albumId.all { it.isDigit() }) return null
        val storefront = url.substringAfter("music.apple.com/").substringBefore("/")
        if (storefront.isBlank()) return null
        return albumId to storefront
    }
}
