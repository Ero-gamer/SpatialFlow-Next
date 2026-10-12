package com.codetrio.spatialflow.data.lastfm

import android.util.Log
import android.util.LruCache
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * High-performance, zero-leak artwork resolution engine for Last.fm tracks.
 * Resolves missing album covers via iTunes Search API with 600x600 HD artwork,
 * backed by an in-memory LRU cache to avoid redundant network calls.
 */
object LastFmArtworkResolver {

    private const val TAG = "LastFmArtworkResolver"
    private const val ITUNES_ENDPOINT = "https://itunes.apple.com/search"

    // 500-item memory cache keyed by "artist:title" in lowercase
    private val coverCache = LruCache<String, String>(500)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(com.codetrio.spatialflow.data.network.FastFallbackDns)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val resolverDispatcher = Dispatchers.IO.limitedParallelism(4)

    private fun cacheKey(artist: String, title: String): String {
        return "${artist.trim().lowercase()}:${title.trim().lowercase()}"
    }

    /**
     * Resolves high-resolution album artwork for a single artist and track title.
     */
    suspend fun resolveCover(artist: String, title: String): String? = withContext(resolverDispatcher) {
        if (artist.isBlank() || title.isBlank()) return@withContext null

        val key = cacheKey(artist, title)
        coverCache.get(key)?.let { return@withContext it }

        try {
            val query = "$artist $title"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$ITUNES_ENDPOINT?term=$encodedQuery&entity=song&limit=1"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", LastFmAuth.USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body.string()
                val json = JsonParser.parseString(body).asJsonObject
                val results = json.getAsJsonArray("results")
                if (results != null && results.size() > 0) {
                    val first = results.get(0).asJsonObject
                    val rawArt = first.get("artworkUrl100")?.asString
                    if (!rawArt.isNullOrBlank()) {
                        // Upgrade 100x100 to 600x600 high definition cover
                        val hdArt = rawArt.replace("/100x100bb.", "/600x600bb.")
                            .replace("100x100", "600x600")
                        coverCache.put(key, hdArt)
                        return@withContext hdArt
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to resolve cover for $artist - $title: ${e.message}")
        }
        null
    }

    /**
     * Resolves missing artwork for a list of recent tracks in parallel.
     */
    suspend fun resolveRecentTracks(tracks: List<LastFmRecentTrack>): List<LastFmRecentTrack> = coroutineScope {
        if (tracks.isEmpty()) return@coroutineScope tracks

        tracks.map { track ->
            async(resolverDispatcher) {
                if (track.getImageUrl() != null) {
                    track
                } else {
                    val artistName = track.artist?.displayName ?: ""
                    val trackName = track.name
                    val resolved = resolveCover(artistName, trackName)
                    if (resolved != null) {
                        track.copy(resolvedCoverUrl = resolved)
                    } else {
                        track
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Resolves missing artwork for a list of top tracks in parallel.
     */
    suspend fun resolveTopTracks(tracks: List<LastFmTopTrack>): List<LastFmTopTrack> = coroutineScope {
        if (tracks.isEmpty()) return@coroutineScope tracks

        tracks.map { track ->
            async(resolverDispatcher) {
                if (track.getImageUrl() != null) {
                    track
                } else {
                    val artistName = track.artist?.displayName ?: ""
                    val trackName = track.name
                    val resolved = resolveCover(artistName, trackName)
                    if (resolved != null) {
                        track.copy(resolvedCoverUrl = resolved)
                    } else {
                        track
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Resolves missing artwork for a list of loved tracks in parallel.
     */
    suspend fun resolveLovedTracks(tracks: List<LastFmLovedTrack>): List<LastFmLovedTrack> = coroutineScope {
        if (tracks.isEmpty()) return@coroutineScope tracks

        tracks.map { track ->
            async(resolverDispatcher) {
                if (track.getImageUrl() != null) {
                    track
                } else {
                    val artistName = track.artist?.displayName ?: ""
                    val trackName = track.name
                    val resolved = resolveCover(artistName, trackName)
                    if (resolved != null) {
                        track.copy(resolvedCoverUrl = resolved)
                    } else {
                        track
                    }
                }
            }
        }.awaitAll()
    }
}
