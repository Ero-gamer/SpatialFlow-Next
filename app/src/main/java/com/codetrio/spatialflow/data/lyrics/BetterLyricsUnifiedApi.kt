package com.codetrio.spatialflow.data.lyrics

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.security.BetterLyricsTokenManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Client for Unified SSE Streaming Lyrics API (v2/lyrics).
 *
 * Simultaneously streams and delivers:
 * - Portato (QQ Music QRC word-by-word karaoke)
 * - Musixmatch (Word-by-word richsync and line-synced LRC)
 * - TTML syllable-synced
 * - Kugou LRC
 *
 * Implements in-flight request deduplication so multiple provider requests for the
 * same song share a single streaming network call.
 */
object BetterLyricsUnifiedApi {
    private const val TAG = "BetterLyricsUnified"
    private const val UNIFIED_API_URL = "https://lyrics.api.dacubeking.com/v2/lyrics"

    data class UnifiedLyricsBundle(
        var portatoLyrics: LyricsResult? = null,
        var musixmatchRichSync: LyricsResult? = null,
        var musixmatchSynced: LyricsResult? = null,
        var bLyricsRichSync: LyricsResult? = null,
        var legatoSynced: LyricsResult? = null
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Keyed by "${track.cleanedArtist}_${track.cleanedTitle}" or videoId
    private val activeRequests = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<UnifiedLyricsBundle>>()
    private val cachedBundles = ConcurrentHashMap<String, UnifiedLyricsBundle>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Synchronous blocking query for LyricsProvider.search() compatibility.
     */
    fun getBundleBlocking(context: Context, track: TrackMetadata): UnifiedLyricsBundle {
        val cacheKey = buildCacheKey(track)
        cachedBundles[cacheKey]?.let { return it }

        return runBlocking(Dispatchers.IO) {
            getBundle(context, track)
        }
    }

    /**
     * Asynchronously fetches or awaits the active unified streaming bundle for the track.
     */
    suspend fun getBundle(context: Context, track: TrackMetadata): UnifiedLyricsBundle {
        val cacheKey = buildCacheKey(track)
        cachedBundles[cacheKey]?.let { return it }

        val deferred = activeRequests.computeIfAbsent(cacheKey) {
            scope.async {
                try {
                    fetchUnifiedStream(context, track)
                } finally {
                    activeRequests.remove(cacheKey)
                }
            }
        }

        return deferred.await()
    }

    private suspend fun fetchUnifiedStream(
        context: Context,
        track: TrackMetadata,
        isRetry: Boolean = false
    ): UnifiedLyricsBundle {
        val cacheKey = buildCacheKey(track)
        val bundle = UnifiedLyricsBundle()

        val jwtToken = BetterLyricsTokenManager.getOrRefreshToken(context, forceRefresh = isRetry)
        if (jwtToken.isNullOrEmpty()) {
            Log.w(TAG, "No valid JWT token available for unified search")
            return bundle
        }

        val durationSec = (track.durationMs / 1000).toInt().coerceAtLeast(0)
        val formBodyBuilder = FormBody.Builder()
            .add("videoId", track.videoId ?: "")
            .add("song", track.cleanedTitle)
            .add("artist", track.cleanedArtist)
            .add("duration", durationSec.toString())
            .add("alwaysFetchMetadata", "false")
            .add("token", jwtToken)

        if (track.album.isNotBlank()) {
            formBodyBuilder.add("album", track.album)
        }

        val request = Request.Builder()
            .url(UNIFIED_API_URL)
            .post(formBodyBuilder.build())
            .header("Accept", "text/event-stream")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 403 && !isRetry) {
                    Log.w(TAG, "Unified API returned 403; forcing token refresh and retrying...")
                    return fetchUnifiedStream(context, track, isRetry = true)
                }

                if (!response.isSuccessful || response.body == null) {
                    Log.w(TAG, "Unified API stream failed: HTTP ${response.code}")
                    return bundle
                }

                val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
                var currentEvent = ""
                val dataBuffer = StringBuilder()

                var line: String? = reader.readLine()
                while (line != null) {
                    when {
                        line.startsWith("event:") -> {
                            currentEvent = line.substring(6).trim()
                        }
                        line.startsWith("data:") -> {
                            dataBuffer.append(line.substring(5).trim())
                        }
                        line.isEmpty() -> {
                            // End of SSE block
                            val data = dataBuffer.toString()
                            if (data.isNotEmpty() && data != "[DONE]") {
                                processStreamEvent(currentEvent, data, track, bundle)
                            }
                            currentEvent = ""
                            dataBuffer.setLength(0)
                        }
                    }
                    line = reader.readLine()
                }

                // Process trailing buffer
                val remainingData = dataBuffer.toString()
                if (remainingData.isNotEmpty() && remainingData != "[DONE]") {
                    processStreamEvent(currentEvent, remainingData, track, bundle)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading Unified API SSE stream for '${track.cleanedTitle}': ${e.message}")
        }

        cachedBundles[cacheKey] = bundle
        return bundle
    }

    private fun processStreamEvent(
        event: String,
        data: String,
        track: TrackMetadata,
        bundle: UnifiedLyricsBundle
    ) {
        if (event != "provider") return

        try {
            val json = JsonParser.parseString(data).asJsonObject
            val provider = json.get("provider")?.asString ?: return
            val results = json.getAsJsonObject("results") ?: return

            when (provider) {
                // 1. Portato (QQ Music QRC Word-by-word)
                "qq" -> {
                    val rawLyrics = results.get("lyrics")?.asString
                    if (!rawLyrics.isNullOrEmpty()) {
                        val qrcBody = try {
                            val inner = JsonParser.parseString(rawLyrics).asJsonObject
                            inner.get("lyrics")?.asString ?: rawLyrics
                        } catch (_: Exception) {
                            rawLyrics
                        }

                        val parsedQrc = QrcParser.parse(
                            qrcBody,
                            track.durationMs,
                            track.cleanedTitle,
                            track.cleanedArtist
                        )

                        if (parsedQrc.isNotEmpty()) {
                            bundle.portatoLyrics = LyricsResult(
                                matchedTitle = track.cleanedTitle,
                                matchedArtist = track.cleanedArtist,
                                providerName = "Better Lyrics Portato"
                            ).apply {
                                setSyncedLyrics(qrcBody)
                                setWordByWord(true)
                            }
                            Log.d(TAG, "Received Portato (QQ) word-by-word lyrics: ${parsedQrc.size} lines")
                        }
                    }
                }

                // 2. Musixmatch
                "musixmatch" -> {
                    val wordByWord = results.get("wordByWord")?.asString
                    val synced = results.get("synced")?.asString

                    if (!wordByWord.isNullOrEmpty()) {
                        bundle.musixmatchRichSync = LyricsResult(
                            matchedTitle = track.cleanedTitle,
                            matchedArtist = track.cleanedArtist,
                            providerName = "Musixmatch"
                        ).apply {
                            setSyncedLyrics(wordByWord)
                            setWordByWord(true)
                        }
                        Log.d(TAG, "Received Musixmatch word-by-word lyrics")
                    }

                    if (!synced.isNullOrEmpty()) {
                        bundle.musixmatchSynced = LyricsResult(
                            matchedTitle = track.cleanedTitle,
                            matchedArtist = track.cleanedArtist,
                            providerName = "Musixmatch"
                        ).apply {
                            setSyncedLyrics(synced)
                            setWordByWord(false)
                        }
                        Log.d(TAG, "Received Musixmatch synced line lyrics")
                    }
                }

                // 3. Syllable-synced (TTML)
                "golyrics" -> {
                    var ttml = results.get("lyrics")?.asString
                    if (!ttml.isNullOrEmpty()) {
                        try {
                            val parsed = JsonParser.parseString(ttml)
                            if (parsed.isJsonObject && parsed.asJsonObject.has("ttml")) {
                                ttml = parsed.asJsonObject.get("ttml")?.asString ?: ttml
                            }
                        } catch (_: Exception) {}

                        bundle.bLyricsRichSync = LyricsResult(
                            matchedTitle = track.cleanedTitle,
                            matchedArtist = track.cleanedArtist,
                            providerName = "Better Lyrics"
                        ).apply {
                            setSyncedLyrics(ttml)
                            setWordByWord(true)
                        }
                        Log.d(TAG, "Received Unified (golyrics) TTML lyrics")
                    }
                }

                // 4. Kugou LRC
                "kugou" -> {
                    val rawLyrics = results.get("lyrics")?.asString
                    if (!rawLyrics.isNullOrEmpty()) {
                        val lrc = try {
                            val inner = JsonParser.parseString(rawLyrics).asJsonObject
                            inner.get("lyrics")?.asString ?: rawLyrics
                        } catch (_: Exception) {
                            rawLyrics
                        }

                        bundle.legatoSynced = LyricsResult(
                            matchedTitle = track.cleanedTitle,
                            matchedArtist = track.cleanedArtist,
                            providerName = "Better Lyrics Legato"
                        ).apply {
                            setSyncedLyrics(lrc)
                        }
                        Log.d(TAG, "Received Unified Legato (KuGou) synced lyrics")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse provider stream JSON", e)
        }
    }

    private fun buildCacheKey(track: TrackMetadata): String {
        return if (!track.videoId.isNullOrBlank()) {
            track.videoId!!
        } else {
            "${track.cleanedArtist.lowercase()}_${track.cleanedTitle.lowercase()}"
        }
    }
}
