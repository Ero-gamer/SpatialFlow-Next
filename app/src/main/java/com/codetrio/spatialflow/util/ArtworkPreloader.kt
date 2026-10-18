package com.codetrio.spatialflow.util

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.PlayerColorExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * 3-Tier Lookahead Artwork & Palette Preloader.
 * Pre-fetches high-res album art for queue tracks (N-1, N+1, N+2, N+3) into Glide's LRU cache,
 * and extracts palette colors off-thread on Dispatchers.Default for 0ms player transitions.
 */
class ArtworkPreloader(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ArtworkPreloader"
    }

    private var preloadJob: Job? = null

    fun preloadQueueArtwork(
        songs: List<SongItem>,
        currentIndex: Int,
        lookaheadDepth: Int = 3
    ) {
        cancel()
        if (songs.isEmpty() || currentIndex !in songs.indices || lookaheadDepth <= 0) return

        preloadJob = scope.launch(Dispatchers.IO) {
            val targets = mutableListOf<SongItem>()

            // N+1 ... N+depth
            for (i in 1..lookaheadDepth) {
                val nextIdx = currentIndex + i
                if (nextIdx in songs.indices) {
                    targets.add(songs[nextIdx])
                }
            }

            // N-1
            val prevIdx = currentIndex - 1
            if (prevIdx in songs.indices) {
                targets.add(songs[prevIdx])
            }

            targets.distinctBy { it.id }.forEach { song ->
                ensureActive()
                preloadSingleArtwork(song)
            }
        }
    }

    private fun preloadSingleArtwork(song: SongItem) {
        val rawUri = song.getAlbumArtUri()
        val videoId = song.videoId
        val artworkUrl = when {
            rawUri != null && rawUri.toString().isNotEmpty() ->
                SongItem.enhanceThumbnailUrl(rawUri.toString())
            !videoId.isNullOrEmpty() ->
                "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            else -> null
        } ?: return

        val cacheKey = song.id.toString()

        // 1. Check if palette is already cached
        if (PlayerColorExtractor.getCached(cacheKey) != null) {
            // Palette already cached, just warm Glide cache
            withContextGlidePreload(artworkUrl)
            return
        }

        // 2. Fetch bitmap off-thread to extract palette
        try {
            val bitmap = if (rawUri != null && AudioArtworkFetcher.isAudioUri(rawUri)) {
                AudioArtworkFetcher.extractArtworkBitmap(context, rawUri, targetWidth = 160, targetHeight = 160)
            } else {
                Glide.with(context.applicationContext)
                    .asBitmap()
                    .load(artworkUrl)
                    .submit(160, 160) // Downscaled thumbnail size for rapid load
                    .get()
            }

            if (bitmap != null) {
                val colors = PlayerColorExtractor.extractFromBitmap(cacheKey, bitmap)
                PlayerColorExtractor.putInCache(cacheKey, colors)
                Log.d(TAG, "⚡ Off-thread palette extracted and cached for track: ${song.title}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Artwork preloading non-critical issue for ${song.title}: ${e.message}")
        }
    }

    private fun withContextGlidePreload(url: String) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                Glide.with(context.applicationContext)
                    .downloadOnly()
                    .load(url)
                    .submit()
            }
        } catch (_: Exception) {}
    }

    fun cancel() {
        preloadJob?.cancel()
        preloadJob = null
    }
}
