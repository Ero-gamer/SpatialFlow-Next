package com.codetrio.spatialflow.ui.spotify

import com.codetrio.spatialflow.data.innertube.SearchFilter
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.data.spotify.SpotifyTrack
import com.codetrio.spatialflow.data.spotify.buildSearchQuery
import com.codetrio.spatialflow.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val spotifyResolutionCache = ConcurrentHashMap<String, SongItem>()

/**
 * Resolves a SpotifyTrack to a playable YouTube Music SongItem with in-memory caching.
 */
internal suspend fun SpotifyTrack.resolveForPlayback(): SongItem? {
    val key = id.ifEmpty { buildSearchQuery() }
    spotifyResolutionCache[key]?.let { return it }

    val query = buildSearchQuery()
    val searchResult = YouTubeMusic.search(query, SearchFilter.SONGS).getOrNull()
        ?: YouTubeMusic.search(query).getOrNull()
        ?: return null

    val match = searchResult.items.filterIsInstance<SearchItem.Song>().firstOrNull()?.song
        ?: searchResult.items.filterIsInstance<SearchItem.TopResult>().firstOrNull()?.song
        ?: return null

    val song = SongItem.createOnlineSong(
        videoId = match.videoId,
        title = match.title,
        artist = match.artist,
        streamUrl = "",
        durationMs = match.durationMs,
        thumbnailUrl = match.thumbnailUrl,
        artistId = match.artistId,
        isExplicit = match.isExplicit,
    )
    spotifyResolutionCache[key] = song
    return song
}

/**
 * Resolves multiple SpotifyTracks in parallel with coroutines for maximum throughput.
 */
internal suspend fun List<SpotifyTrack>.resolveBatchForPlayback(limit: Int = 10): List<SongItem> = withContext(Dispatchers.IO) {
    val targetTracks = take(limit)
    coroutineScope {
        targetTracks.map { track ->
            async { track.resolveForPlayback() }
        }.awaitAll().filterNotNull()
    }
}
