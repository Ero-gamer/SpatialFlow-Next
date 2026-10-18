package com.codetrio.spatialflow.ui.player.canvas

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified resolver for fetching live motion/canvas video loops for
 * Albums, Artists, and Playlists across Apple Music and Spotify providers.
 */
object DetailCanvasResolver {

    private const val TAG = "DetailCanvasResolver"
    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 12 // 12 Hours

    private data class CacheEntry(val value: CanvasArtwork?, val expiresAtMs: Long)
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()

    private fun cacheKey(type: String, vararg parts: String): String =
        "$type|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }

    /**
     * Resolves live motion artwork for an Album.
     */
    suspend fun resolveAlbumCanvas(
        albumTitle: String,
        artistName: String,
        albumId: String? = null,
        storefront: String = "us",
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        val title = albumTitle.trim()
        val artist = artistName.trim()
        if (title.isBlank()) return@withContext null

        val key = cacheKey("album", title, artist, albumId ?: "", storefront)
        memoryCache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it.value }

        // 1. Apple Music (Primary for Albums)
        var result = if (!albumId.isNullOrBlank() && albumId.all { it.isDigit() }) {
            AppleMusicCanvasProvider.getByAlbumId(albumId, storefront)
        } else null

        if (result == null) {
            result = AppleMusicCanvasProvider.getByAlbumArtist(title, artist, storefront)
        }

        // 2. Apple Music Mirror Fallback (Boidu)
        if (result == null) {
            result = SongCanvasProvider.getBySongArtist(song = title, artist = artist, storefront = storefront)
        }

        // 3. Spotify Fallback
        if (result == null && artist.isNotBlank()) {
            result = SpotifyCanvasProvider.getBySongArtist(title, artist)
        }

        if (result != null) {
            Log.d(TAG, "Resolved Album motion for '$title' ($artist)")
            memoryCache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        result
    }

    /**
     * Resolves live motion hero loop for an Artist.
     */
    suspend fun resolveArtistCanvas(
        artistName: String,
        artistId: String? = null,
        storefront: String = "us",
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        val artist = artistName.trim()
        if (artist.isBlank()) return@withContext null

        val key = cacheKey("artist", artist, artistId ?: "", storefront)
        memoryCache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it.value }

        // 1. Apple Music Hero / Editorial Video
        var result = AppleMusicCanvasProvider.getByArtist(artist, storefront)

        // 2. Spotify Artist Visual / Top Track Canvas
        if (result == null) {
            result = SpotifyCanvasProvider.getArtistCanvas(artist, artistId)
        }

        if (result != null) {
            Log.d(TAG, "Resolved Artist motion for '$artist'")
            memoryCache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        result
    }

    /**
     * Resolves live motion cover loop for a Playlist.
     */
    suspend fun resolvePlaylistCanvas(
        playlistTitle: String,
        curatorName: String? = null,
        playlistId: String? = null,
        storefront: String = "us",
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        val title = playlistTitle.trim()
        if (title.isBlank()) return@withContext null

        val key = cacheKey("playlist", title, curatorName ?: "", playlistId ?: "", storefront)
        memoryCache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it.value }

        // 1. Apple Music Editorial Playlist Motion
        var result = AppleMusicCanvasProvider.getByPlaylist(title, curatorName, storefront)

        // 2. Spotify Playlist Canvas Fallback
        if (result == null) {
            result = SpotifyCanvasProvider.getPlaylistCanvas(title, playlistId)
        }

        if (result != null) {
            Log.d(TAG, "Resolved Playlist motion for '$title'")
            memoryCache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        result
    }
}
