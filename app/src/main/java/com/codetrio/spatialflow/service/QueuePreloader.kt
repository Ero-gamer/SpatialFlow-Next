package com.codetrio.spatialflow.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.util.SongDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Lookahead Audio Stream Preloader.
 * Resolves YouTube audio stream URLs and pre-caches the initial 512KB audio buffers
 * for upcoming tracks (N+1, N+2, N-1) to ensure zero playback latency.
 */
@OptIn(UnstableApi::class)
class QueuePreloader(
    private val context: Context,
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "QueuePreloader"
        private const val DEFAULT_PRELOAD_DEPTH = 2
        private const val CACHE_EXPIRY_MS = 4 * 60 * 60 * 1000L
    }

    private var preloaderJob: Job? = null
    // Cache URI with expiry timestamp (TTL: 4 hours max to stay safely under 6-hour YouTube token expiry)
    private val resolvedUrlCache = ConcurrentHashMap<Long, Pair<Uri, Long>>()

    fun getResolvedUri(songId: Long): Uri? {
        val entry = resolvedUrlCache[songId] ?: return null
        val now = System.currentTimeMillis()
        if (now > entry.second) {
            resolvedUrlCache.remove(songId)
            Log.d(TAG, "Evicted expired pre-resolved stream URL for songId: $songId")
            return null
        }
        val uri = entry.first
        // Safety check for GoogleVideo URL expire query parameter
        val expireParam = uri.getQueryParameter("expire")?.toLongOrNull()
        if (expireParam != null && (expireParam * 1000L) <= now + 30000L) {
            resolvedUrlCache.remove(songId)
            Log.d(TAG, "Evicted expiring GoogleVideo token for songId: $songId")
            return null
        }
        return uri
    }

    fun putResolvedUri(songId: Long, uri: Uri) {
        val expiryTime = System.currentTimeMillis() + CACHE_EXPIRY_MS
        resolvedUrlCache[songId] = uri to expiryTime
    }

    fun invalidate(songId: Long) {
        resolvedUrlCache.remove(songId)
        Log.d(TAG, "Invalidated pre-resolved cache for songId: $songId")
    }

    fun invalidateAll() {
        resolvedUrlCache.clear()
        Log.d(TAG, "Invalidated all pre-resolved cache")
    }

    fun updatePreloadQueue(
        songs: List<SongItem>,
        currentIndex: Int,
        depth: Int = DEFAULT_PRELOAD_DEPTH,
        explicitNextSong: SongItem? = null
    ) {
        cancel()

        if (songs.isEmpty() || depth <= 0) return

        preloaderJob = scope.launch(Dispatchers.IO) {
            val targets = mutableListOf<SongItem>()

            // 1. If an explicit next song is provided (e.g. from stable shuffle prediction), prioritize it first
            if (explicitNextSong != null && songs.any { it.id == explicitNextSong.id }) {
                targets.add(explicitNextSong)
            } else if (currentIndex in songs.indices) {
                for (i in 1..depth) {
                    val nextIdx = currentIndex + i
                    if (nextIdx in songs.indices) {
                        targets.add(songs[nextIdx])
                    }
                }
            }

            // Also look back N-1 for fast previous skip
            if (currentIndex in songs.indices) {
                val prevIdx = currentIndex - 1
                if (prevIdx in songs.indices) {
                    targets.add(songs[prevIdx])
                }
            }

            val upcomingVideoIds = targets.mapNotNull { it.videoId?.takeIf { id -> id.isNotBlank() } }
            if (upcomingVideoIds.isNotEmpty()) {
                com.codetrio.spatialflow.data.sourcepool.LosslessStreamResolver.queueMlsTracks(upcomingVideoIds)
            }

            targets.distinctBy { it.id }.forEach { song ->
                ensureActive()
                preloadTrack(song)
            }
        }
    }

    private suspend fun preloadTrack(song: SongItem) {
        val songId = song.id
        if (getResolvedUri(songId) != null) return

        // 1. Check local download
        val localUri = SongDownloader.getDownloadedSongUri(context, song)
        if (localUri != null) {
            putResolvedUri(songId, localUri)
            Log.d(TAG, "Preloader linked offline file for '${song.title}': $localUri")
            return
        }

        // 2. Resolve stream URL if YouTube track
        val videoId = song.videoId
        if (!videoId.isNullOrEmpty()) {
            val resolvedUrl = YouTubeMusic.getStreamUrl(
                videoId = videoId,
                title = song.title,
                artist = song.artist,
                durationMs = song.duration.takeIf { it > 0 }
            ).getOrNull()
            if (!resolvedUrl.isNullOrEmpty()) {
                val uri = resolvedUrl.toUri()
                putResolvedUri(songId, uri)
                Log.d(TAG, "Pre-resolved stream URL for '${song.title}'")

                // 3. Pre-cache initial 512KB audio chunk into SimpleCache on unmetered connections
                val isLowData = com.codetrio.spatialflow.util.QualityModeSelector.getActiveQualityMode(context) == com.codetrio.spatialflow.util.QualityMode.LOW
                if (!isLowData) {
                    preCacheChunk(resolvedUrl)
                }
            }
        }
    }

    private suspend fun preCacheChunk(streamUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                ensureActive()
                val dataSpec = DataSpec.Builder()
                    .setUri(streamUrl)
                    .setLength(512 * 1024) // 512KB first chunk
                    .build()

                val cacheWriter = CacheWriter(
                    cacheDataSourceFactory.createDataSource(),
                    dataSpec,
                    null
                ) { _, _, _ ->
                    if (!coroutineContext.isActive) {
                        throw CancellationException("Precache cancelled")
                    }
                }
                cacheWriter.cache()
                Log.d(TAG, "⚡ First-chunk pre-cached (512KB) for stream: $streamUrl")
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.w(TAG, "First-chunk pre-cache non-critical error: ${e.message}")
                }
            }
        }
    }

    fun cancel() {
        preloaderJob?.cancel()
        preloaderJob = null
    }

    fun clearCache() {
        cancel()
        resolvedUrlCache.clear()
    }
}
