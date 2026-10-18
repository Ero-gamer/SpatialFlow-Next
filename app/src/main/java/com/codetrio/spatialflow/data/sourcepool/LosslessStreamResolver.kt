package com.codetrio.spatialflow.data.sourcepool

import android.util.Log
import androidx.compose.runtime.Immutable
import com.codetrio.spatialflow.util.ConsoleLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Result of a resolved lossless or high-fidelity audio stream.
 */
@Immutable
data class LosslessStreamResult(
    val url: String,
    val formatTag: String,
    val trackId: String,
    val bitDepth: Int = 16,
    val sampleRateHz: Int = 44100,
    val providerName: String = "Lossless"
)

/**
 * Multi-source resolver that acquires direct FLAC / Hi-Fi audio streams
 * across MLS (mls.kouzu.in), Qobuz Source Pool, and JioSaavn.
 *
 * Includes in-memory caching, metadata sanitization, duration validation,
 * and strict timeout boundaries to guarantee zero playback stalling.
 */
object LosslessStreamResolver {

    private const val TAG = "LosslessStreamResolver"
    private const val RESOLVER_TIMEOUT_MS = 3500L
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours

    private const val DEFAULT_QOBUZ_ENDPOINT = "https://qobuz-dll.vercel.app"
    private val communityGatewayBase: String
        get() = com.codetrio.spatialflow.BuildConfig.COMMUNITY_GATEWAY_URL.trim().trimEnd('/')

    private fun getQobuzBaseUrl(): String {
        return if (communityGatewayBase.isNotBlank()) communityGatewayBase else DEFAULT_QOBUZ_ENDPOINT
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private data class CachedEntry(
        val result: LosslessStreamResult,
        val expiresAt: Long
    )

    private val cache = ConcurrentHashMap<String, CachedEntry>()
    private val failedUrls = ConcurrentHashMap.newKeySet<String>()

    fun markUrlFailed(url: String) {
        if (url.isNotBlank()) {
            failedUrls.add(url)
            cache.entries.removeIf { it.value.result.url == url }
            Log.w(TAG, "Blacklisted failing stream URL: $url")
        }
    }

    fun isUrlBlacklisted(url: String): Boolean = failedUrls.contains(url)

    fun invalidate(videoId: String) {
        if (videoId.isNotBlank()) {
            cache.entries.removeIf { it.key.contains(videoId) || it.value.result.trackId == videoId }
        }
    }

    fun invalidateAll() {
        cache.clear()
        failedUrls.clear()
    }

    /**
     * Speculatively resolves and caches the lossless stream for the upcoming track in the queue,
     * ensuring zero-latency gapless playback handoff.
     */
    fun preloadNextTrack(
        videoId: String?,
        title: String?,
        artist: String?,
        durationSeconds: Long? = null,
        preferHiRes: Boolean = false,
        pipeline: List<LosslessProvider>? = null,
        context: android.content.Context? = null
    ) {
        val cleanVideoId = videoId?.trim().orEmpty()
        val cleanTitle = title?.trim().orEmpty()
        if (cleanVideoId.isBlank() && cleanTitle.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                resolveStream(
                    videoId = cleanVideoId,
                    title = cleanTitle,
                    artist = artist,
                    durationSeconds = durationSeconds,
                    preferHiRes = preferHiRes,
                    pipeline = pipeline,
                    context = context
                )
            } catch (_: Throwable) {
                // Preload exceptions ignored so normal playback is unaffected
            }
        }
    }

    /**
     * Fast range probe (bytes=0-0) verifying the audio URL is alive and does not return HTTP 404, 403, 5xx or HTML error pages.
     */
    private fun verifyStreamUrlLive(url: String): Boolean {
        if (failedUrls.contains(url)) return false
        if (url.contains("range=")) {
            Log.w(TAG, "Liveness probe rejected preview range stream URL: $url")
            return false
        }
        // deezer:// and tidal-dash:// are custom internal schemes handled by ExoPlayer DataSources
        if (url.startsWith("deezer://") || url.startsWith("tidal-dash://")) return true
        return try {
            val probeClient = httpClient.newBuilder()
                .connectTimeout(1200, TimeUnit.MILLISECONDS)
                .readTimeout(1200, TimeUnit.MILLISECONDS)
                .callTimeout(1500, TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
                .header("Range", "bytes=0-0")
                .get()
                .build()

            probeClient.newCall(request).execute().use { response ->
                if (response.code == 404 || response.code == 410 || response.code == 403 || response.code >= 400) {
                    Log.w(TAG, "Liveness probe failed with HTTP ${response.code} for $url")
                    return@use false
                }
                val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT).orEmpty()
                if (contentType.contains("text/html") || contentType.contains("application/json") || contentType.contains("text/plain")) {
                    Log.w(TAG, "Liveness probe rejected non-audio Content-Type ($contentType) for $url")
                    return@use false
                }
                response.isSuccessful || response.code == 206 || response.code in 300..399
            }
        } catch (e: Exception) {
            Log.w(TAG, "Liveness probe exception for $url: ${e.message}")
            // Reject on network timeout / failure so dead URLs are never passed to ExoPlayer
            false
        }
    }

    /**
     * Resolves a direct audio stream for the specified track across configured providers in priority order.
     * Evaluates Kouzu (MLS), Deezer, Tidal, and Qobuz sequentially until a live stream is verified.
     */
    suspend fun resolveStream(
        videoId: String? = null,
        title: String? = null,
        artist: String? = null,
        durationSeconds: Long? = null,
        preferHiRes: Boolean = false,
        pipeline: List<LosslessProvider>? = null,
        provider: LosslessProvider? = null,
        context: android.content.Context? = null
    ): LosslessStreamResult? = withContext(Dispatchers.IO) {
        val rawTitle = title?.trim().orEmpty()
        val rawArtist = artist?.trim().orEmpty()
        val cleanVideoId = videoId?.trim().orEmpty()

        if (cleanVideoId.isBlank() && rawTitle.isBlank()) return@withContext null

        val basePipeline = pipeline
            ?: (if (provider != null) listOf(provider) else LosslessProvider.DEFAULT_PRIORITY_ORDER)

        val effectivePipeline = if (preferHiRes) {
            // Prioritize Hi-Res capable services (Qobuz 24-bit, Tidal Master) ahead of 16-bit CD-only services
            basePipeline.sortedByDescending { it == LosslessProvider.QOBUZ || it == LosslessProvider.TIDAL }
        } else {
            basePipeline
        }

        val pipelineKey = effectivePipeline.joinToString("-") { it.id }
        val cacheKey = buildCacheKey(cleanVideoId, rawTitle, rawArtist, preferHiRes, pipelineKey)
        val cached = cache[cacheKey]
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            ConsoleLogger.stream("⚡ Lossless cache hit [${cached.result.providerName}]: \"$rawTitle\" [${cached.result.formatTag}]")
            Log.d(TAG, "Lossless stream cache hit: $cacheKey")
            return@withContext cached.result
        }

        ConsoleLogger.stream("Searching Lossless streams concurrently across [${effectivePipeline.joinToString(" ➔ ") { it.displayName }}] for: \"$rawTitle\"${if (rawArtist.isNotBlank()) " by \"$rawArtist\"" else ""}")

        // Refresh leased credentials from SourcePool (Tidal, Qobuz, Deezer) non-blockingly if not yet populated
        if (SourcePoolAccountManager.getQobuzTokens().isEmpty()) {
            try {
                SourcePoolAccountManager.refreshIfNeeded(context)
            } catch (e: Exception) {
                Log.w(TAG, "SourcePool refresh on resolve error: ${e.message}")
            }
        }

        val resolverTimeout = if (preferHiRes) 5000L else RESOLVER_TIMEOUT_MS
        withTimeoutOrNull(resolverTimeout) {
            try {
                coroutineScope {
                    val winner = CompletableDeferred<LosslessStreamResult>()
                    val bestFallbackCandidate = java.util.concurrent.atomic.AtomicReference<LosslessStreamResult?>(null)
                    val jobs = effectivePipeline.map { targetProvider ->
                        launch(Dispatchers.IO) {
                            try {
                                val candidate = when (targetProvider) {
                                    LosslessProvider.KOUZU -> {
                                        if (cleanVideoId.isNotBlank()) {
                                            MlsLosslessProvider.resolveStream(cleanVideoId)
                                        } else null
                                    }
                                    LosslessProvider.QOBUZ -> {
                                        if (rawTitle.isNotBlank()) {
                                            resolveQobuzStream(rawTitle, rawArtist, durationSeconds, preferHiRes)
                                        } else null
                                    }
                                    LosslessProvider.DEEZER -> {
                                        if (rawTitle.isNotBlank()) {
                                            resolveDeezerStream(rawTitle, rawArtist, durationSeconds, context)
                                        } else null
                                    }
                                    LosslessProvider.TIDAL -> {
                                        if (rawTitle.isNotBlank()) {
                                            resolveTidalStream(rawTitle, rawArtist, durationSeconds, preferHiRes, context)
                                        } else null
                                    }
                                    LosslessProvider.SAAVN -> {
                                        if (rawTitle.isNotBlank()) {
                                            JioSaavnStreamProvider.resolveStream(rawTitle, rawArtist, durationSeconds)
                                        } else null
                                    }
                                }

                                if (candidate != null) {
                                    val isFlac = candidate.formatTag.contains("FLAC", ignoreCase = true) ||
                                        candidate.providerName.contains("FLAC", ignoreCase = true) ||
                                        candidate.bitDepth > 16 || candidate.sampleRateHz > 44100

                                    if (verifyStreamUrlLive(candidate.url)) {
                                        if (isFlac) {
                                            winner.complete(candidate)
                                        } else {
                                            // Retain high-fidelity 320 kbps stream as fallback if no pure FLAC is matched
                                            bestFallbackCandidate.compareAndSet(null, candidate)
                                        }
                                    } else {
                                        Log.w(TAG, "Lossless candidate URL from ${candidate.providerName} failed liveness check (404/dead): ${candidate.url}")
                                        markUrlFailed(candidate.url)
                                        ConsoleLogger.stream("⚠️ Lossless link from ${candidate.providerName} dead/404 for: \"$rawTitle\"")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Provider ${targetProvider.displayName} failed: ${e.message}")
                            }
                        }
                    }

                    // Complete with winner or best high-fidelity fallback if no FLAC candidate resolved
                    launch {
                        jobs.joinAll()
                        val fallback = bestFallbackCandidate.get()
                        if (fallback != null) {
                            winner.complete(fallback)
                        } else {
                            winner.completeExceptionally(NoSuchElementException("No provider resolved"))
                        }
                    }

                    val result = try {
                        winner.await()
                    } catch (_: Throwable) {
                        null
                    }

                    // Cancel pending slower jobs immediately once winner found or all failed
                    jobs.forEach { it.cancel() }

                    if (result != null) {
                        cache[cacheKey] = CachedEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
                        ConsoleLogger.stream("🎵 Stream acquired [${result.providerName}]: \"$rawTitle\" [${result.formatTag}]")
                        Log.i(TAG, "Successfully resolved stream via ${result.providerName} for \"$rawTitle\" [${result.formatTag}]")
                        result
                    } else {
                        ConsoleLogger.stream("No lossless match found in pipeline for: \"$rawTitle\" (falling back to YouTube)")
                        null
                    }
                }
            } catch (e: Exception) {
                ConsoleLogger.stream("Stream resolution error: ${e.message ?: "unknown"}")
                Log.w(TAG, "Stream resolution failed for \"$rawTitle\": ${e.message}")
                null
            }
        }
    }

    private suspend fun resolveQobuzStream(
        rawTitle: String,
        rawArtist: String,
        durationSeconds: Long?,
        preferHiRes: Boolean
    ): LosslessStreamResult? {
        val cleanTitle = cleanTrackTitle(rawTitle)
        val cleanArtist = cleanArtistName(rawArtist)
        val searchQuery = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle

        Log.d(TAG, "Resolving Qobuz stream for query: \"$searchQuery\"")

        // Step 1: Search track items (try combined query, fallback to clean title)
        var candidate = searchBestCandidate(searchQuery, cleanTitle, cleanArtist, durationSeconds)
        if (candidate == null && cleanArtist.isNotBlank()) {
            candidate = searchBestCandidate(cleanTitle, cleanTitle, cleanArtist, durationSeconds)
        }
        if (candidate == null) return null
        Log.d(TAG, "Qobuz match: \"${candidate.title}\" by \"${candidate.artist}\" (score=${candidate.score})")

        // Step 2: Download stream URL for matched track ID
        // Quality 27 = FLAC 24-bit up to 192kHz, Quality 7 = FLAC 24-bit up to 96kHz, Quality 6 = FLAC 16-bit 44.1kHz
        // Try direct authenticated tokens from SourcePool first (full FLAC direct stream via Akamai CDN)
        val tokens = SourcePoolAccountManager.getQobuzTokens()
        Log.d(TAG, "Qobuz candidate match: \"${candidate.title}\" (id=${candidate.trackId}) with ${tokens.size} active pool tokens")
        for (token in tokens) {
            val direct = if (preferHiRes) {
                fetchDownloadDirect(token, candidate.trackId, 27)
                    ?: fetchDownloadDirect(token, candidate.trackId, 7)
                    ?: fetchDownloadDirect(token, candidate.trackId, 6)
            } else {
                fetchDownloadDirect(token, candidate.trackId, 6)
                    ?: fetchDownloadDirect(token, candidate.trackId, 7)
                    ?: fetchDownloadDirect(token, candidate.trackId, 27)
            }
            if (direct != null) {
                return direct
            }
        }

        val targetQuality = if (preferHiRes) 7 else 6
        return (if (preferHiRes) fetchDownloadStream(candidate.trackId, 27) else null)
            ?: (if (preferHiRes) fetchDownloadStream(candidate.trackId, 7) else null)
            ?: fetchDownloadStream(candidate.trackId, targetQuality)
    }

    private data class TrackCandidate(
        val trackId: String,
        val title: String,
        val artist: String,
        val durationSeconds: Long?,
        val score: Int
    )

    private fun searchQobuzCatalogDirect(
        query: String,
        cleanTitle: String,
        cleanArtist: String,
        targetDurationSeconds: Long?
    ): TrackCandidate? {
        val tokens = SourcePoolAccountManager.getQobuzTokens()
        if (tokens.isEmpty()) return null
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        for (token in tokens) {
            val url = "https://www.qobuz.com/api.json/0.2/catalog/search?query=$encodedQuery&limit=10&app_id=${token.appId}"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
                    .header("X-App-Id", token.appId)
                    .header("X-User-Auth-Token", token.token)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body.string()
                    if (body.isBlank()) return@use

                    val tracksArray = extractTracksArray(body) ?: return@use
                    val cand = parseBestCandidateFromTracksArray(tracksArray, cleanTitle, cleanArtist, targetDurationSeconds)
                    if (cand != null) {
                        Log.d(TAG, "Direct Qobuz search found: \"${cand.title}\" (id=${cand.trackId}, score=${cand.score})")
                        return cand
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct Qobuz search error with token: ${e.message}")
            }
        }
        return null
    }

    private fun parseBestCandidateFromTracksArray(
        tracksArray: JSONArray,
        cleanTitle: String,
        cleanArtist: String,
        targetDurationSeconds: Long?
    ): TrackCandidate? {
        var bestCandidate: TrackCandidate? = null
        var highestScore = -1

        val normalizedExpectedTitle = normalize(cleanTitle)
        val normalizedExpectedArtist = normalize(cleanArtist)

        for (i in 0 until tracksArray.length()) {
            val rawItem = tracksArray.optJSONObject(i) ?: continue
            val item = rawItem.optJSONObject("content") ?: rawItem
            val trackId = item.optString("id").ifBlank {
                item.optLong("id", 0L).takeIf { it > 0 }?.toString()
            } ?: continue

            val itemTitle = item.optString("title")
            if (itemTitle.isBlank()) continue

            val itemArtist = item.optJSONObject("performer")?.optString("name")
                ?: item.optString("artist")
                ?: item.optJSONObject("album")?.optJSONObject("artist")?.optString("name")
                ?: ""

            val itemDuration = item.optLong("duration", -1L).takeIf { it > 0 }

            val score = scoreMatch(
                candidateTitle = itemTitle,
                candidateArtist = itemArtist,
                candidateDuration = itemDuration,
                expectedTitle = cleanTitle,
                expectedArtist = cleanArtist,
                expectedDuration = targetDurationSeconds
            )

            if (score > highestScore && score >= TrackMatching.MIN_MATCH_SCORE) {
                highestScore = score
                bestCandidate = TrackCandidate(
                    trackId = trackId,
                    title = itemTitle,
                    artist = itemArtist,
                    durationSeconds = itemDuration,
                    score = score
                )
            }
        }
        return bestCandidate
    }

    private fun searchBestCandidate(
        query: String,
        cleanTitle: String,
        cleanArtist: String,
        targetDurationSeconds: Long?
    ): TrackCandidate? {
        // 1. First Priority: Direct official Qobuz catalog search via authenticated pool tokens
        val directCand = searchQobuzCatalogDirect(query, cleanTitle, cleanArtist, targetDurationSeconds)
            ?: (if (cleanArtist.isNotBlank()) searchQobuzCatalogDirect(cleanTitle, cleanTitle, cleanArtist, targetDurationSeconds) else null)
        if (directCand != null) {
            return directCand
        }

        // 2. Second Priority: Fallback via configured community gateway
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val candidateBases = listOf(
            if (communityGatewayBase.isNotBlank()) communityGatewayBase else null
        ).filterNotNull()

        for (base in candidateBases.distinct()) {
            val url = "$base/api/get-music?q=$encodedQuery&offset=0"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
                    .header("Token-Country", "JP")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body.string()
                    if (body.isBlank()) return@use

                    val tracksArray = extractTracksArray(body) ?: return@use
                    val cand = parseBestCandidateFromTracksArray(tracksArray, cleanTitle, cleanArtist, targetDurationSeconds)
                    if (cand != null) {
                        return cand
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search query error against $base: ${e.message}")
            }
        }
        return null
    }

    private fun extractTracksArray(jsonStr: String): JSONArray? {
        return try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[")) {
                return JSONArray(trimmed)
            }
            val obj = JSONObject(trimmed)
            val dataObj = obj.optJSONObject("data") ?: obj
            dataObj.optJSONObject("tracks")?.optJSONArray("items")
                ?: dataObj.optJSONArray("tracks")
                ?: dataObj.optJSONArray("items")
                ?: obj.optJSONObject("tracks")?.optJSONArray("items")
                ?: obj.optJSONArray("tracks")
                ?: obj.optJSONArray("data")
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchDownloadStream(trackId: String, quality: Int): LosslessStreamResult? {
        val base = getQobuzBaseUrl()
        val url = "$base/api/download-music?track_id=$trackId&quality=$quality"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
            .header("Token-Country", "JP")
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                if (body.isBlank()) return null

                val json = JSONObject(body)
                val isSample = json.optBoolean("sample", false) || json.optBoolean("preview", false)

                val streamUrl = json.optString("url").takeIf { it.isNotBlank() }
                    ?: json.optJSONObject("data")?.optString("url")
                    ?: return null

                if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
                    return null
                }

                // Reject preview snippets (e.g. 10s range=20-30 preview clip from Qobuz)
                if (isSample || streamUrl.contains("range=") || streamUrl.contains("preview")) {
                    Log.d(TAG, "Track $trackId is sample/preview clip ($streamUrl), discarding")
                    return null
                }

                val bitDepth = if (quality >= 7) 24 else 16
                val sampleRate = if (quality == 27) 192000 else if (quality == 7) 96000 else 44100
                val tag = if (quality >= 7) "FLAC • 24-bit ${sampleRate / 1000}kHz" else "FLAC • Lossless"

                LosslessStreamResult(
                    url = streamUrl,
                    formatTag = tag,
                    trackId = trackId,
                    bitDepth = bitDepth,
                    sampleRateHz = sampleRate,
                    providerName = "Qobuz Studio"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download stream fetch error for $trackId: ${e.message}")
            null
        }
    }

    private fun fetchDownloadDirect(
        token: QobuzToken,
        trackId: String,
        formatId: Int
    ): LosslessStreamResult? {
        val ts = System.currentTimeMillis() / 1000L
        val sig = md5("trackgetFileUrlformat_id${formatId}intentstreamtrack_id$trackId$ts${token.appSecret}")
        val url = "https://www.qobuz.com/api.json/0.2/track/getFileUrl" +
                "?request_ts=$ts&request_sig=$sig&track_id=$trackId&format_id=$formatId&intent=stream&app_id=${token.appId}"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .header("X-App-Id", token.appId)
            .header("X-User-Auth-Token", token.token)
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                if (body.isBlank()) return null
                val root = JSONObject(body)
                val streamUrl = root.optString("url").takeIf { it.startsWith("http") } ?: return null
                val isPreview = root.optBoolean("sample", false) || root.optBoolean("preview", false) || streamUrl.contains("range=")
                if (isPreview) return null

                val bitDepth = root.optInt("bit_depth", if (formatId >= 7) 24 else 16)
                val sampleRateDouble = root.optDouble("sampling_rate", 0.0)
                val sampleRate = if (sampleRateDouble > 0) (sampleRateDouble * 1000).toInt() else if (formatId == 27) 192000 else if (formatId == 7) 96000 else 44100
                val tag = if (bitDepth > 16 || sampleRate > 44100) "FLAC • ${bitDepth}-bit ${sampleRate / 1000}kHz" else "FLAC • Lossless"

                LosslessStreamResult(
                    url = streamUrl,
                    formatTag = tag,
                    trackId = trackId,
                    bitDepth = bitDepth,
                    sampleRateHz = sampleRate,
                    providerName = "Qobuz Studio (Direct FLAC)"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct Qobuz download error for $trackId: ${e.message}")
            null
        }
    }

    private fun md5(input: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Scores candidate match using hardened TrackMatching engine.
     */
     private fun scoreMatch(
         candidateTitle: String,
         candidateArtist: String,
         candidateDuration: Long?,
         expectedTitle: String,
         expectedArtist: String,
         expectedDuration: Long?
     ): Int {
         val wantedArtists = if (expectedArtist.isNotBlank()) listOf(expectedArtist) else emptyList()
         val wantedDurationMs = expectedDuration?.takeIf { it > 0 }?.times(1000L)
         val candidateDurationMs = candidateDuration?.takeIf { it > 0 }?.times(1000L)
         return TrackMatching.score(
             wantedTitle = expectedTitle,
             wantedArtists = wantedArtists,
             candidateTitle = candidateTitle,
             candidateArtist = candidateArtist,
             wantedDurationMs = wantedDurationMs,
             candidateDurationMs = candidateDurationMs
         )
     }

    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\s*\\(official\\s*(music)?\\s*video\\)"), "")
            .replace(Regex("(?i)\\s*\\[official\\s*(music)?\\s*video\\]"), "")
            .replace(Regex("(?i)\\s*\\(official\\s*audio\\)"), "")
            .replace(Regex("(?i)\\s*\\[official\\s*audio\\]"), "")
            .replace(Regex("(?i)\\s*\\(lyrics?\\s*video\\)"), "")
            .replace(Regex("(?i)\\s*\\[lyrics?\\s*video\\]"), "")
            .replace(Regex("(?i)\\s*\\(visualizer\\)"), "")
            .replace(Regex("(?i)\\s*\\[visualizer\\]"), "")
            .replace(Regex("(?i)\\s*\\(audio\\)"), "")
            .replace(Regex("(?i)\\s*\\[audio\\]"), "")
            .replace(Regex("(?i)\\s*\\(hd\\)"), "")
            .replace(Regex("(?i)\\s*\\(4k\\)"), "")
            .replace(Regex("(?i)\\s*\\[4k\\]"), "")
            .replace(Regex("(?i)\\s+by\\s+.*$"), "")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("(?i)\\s*-\\s*topic$"), "")
            .replace(Regex("(?i)\\s*vevo$"), "")
            .trim()
    }

    private fun normalize(str: String): String {
        val normalized = Normalizer.normalize(str, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private suspend fun resolveDeezerStream(
        rawTitle: String,
        rawArtist: String,
        durationSeconds: Long?,
        context: android.content.Context?
    ): LosslessStreamResult? {
        val cleanTitle = cleanTrackTitle(rawTitle)
        val cleanArtist = cleanArtistName(rawArtist)
        val customArl = context?.let { com.codetrio.spatialflow.util.QualityModeSelector.getDeezerArl(it) }

        val candidateArls = mutableListOf<String>()
        if (!customArl.isNullOrBlank()) {
            candidateArls.add(customArl)
        }
        candidateArls.addAll(SourcePoolAccountManager.getDeezerArls())
        Log.d(TAG, "Resolving Deezer for \"$cleanTitle\" (candidate ARLs: ${candidateArls.size})")

        for (arl in candidateArls) {
            val res = com.codetrio.spatialflow.data.deezer.DeezerAudioProvider.resolve(
                title = cleanTitle,
                artist = cleanArtist,
                durationSeconds = durationSeconds,
                customArl = arl
            )
            if (res != null) {
                return LosslessStreamResult(
                    url = res.uri,
                    formatTag = res.label,
                    trackId = "deezer",
                    bitDepth = res.bitDepth ?: 16,
                    sampleRateHz = res.sampleRate ?: 44100,
                    providerName = if (arl == customArl) "Deezer FLAC (User)" else "Deezer FLAC (Pool)"
                )
            }
        }

        return null
    }

    private suspend fun resolveTidalStream(
        rawTitle: String,
        rawArtist: String,
        durationSeconds: Long?,
        preferHiRes: Boolean,
        context: android.content.Context?
    ): LosslessStreamResult? {
        val cleanTitle = cleanTrackTitle(rawTitle)
        val cleanArtist = cleanArtistName(rawArtist)
        val artistsList = if (cleanArtist.isNotBlank()) listOf(cleanArtist) else emptyList()
        val durationMs = durationSeconds?.times(1000L)

        // Quality fallback chain — gracefully degrades if account tier doesn't support top quality
        val qualityChain = if (preferHiRes) {
            listOf("HI_RES_LOSSLESS", "HI_RES", "LOSSLESS")
        } else {
            listOf("LOSSLESS", "HIGH")
        }

        // 1. Priority A: User's authenticated Tidal account if logged in
        val userAccessToken = context?.let { com.codetrio.spatialflow.util.QualityModeSelector.getTidalAccessToken(it) }
        if (!userAccessToken.isNullOrBlank()) {
            for (quality in qualityChain) {
                val direct = com.codetrio.spatialflow.data.tidal.TidalAccountManager.resolveDirectStream(
                    accessToken = userAccessToken,
                    title = cleanTitle,
                    artists = artistsList,
                    durationMs = durationMs,
                    audioQuality = quality
                )
                if (direct != null) {
                    val isFlac = direct.mimeType.contains("flac", ignoreCase = true) ||
                        direct.codecs.contains("flac", ignoreCase = true)

                    if (preferHiRes && !isFlac) {
                        Log.d(TAG, "User Tidal stream for \"$cleanTitle\" is lossy (${direct.mimeType}, ${direct.codecs}); skipping in Hi-Res mode")
                        continue
                    }

                    val realSampleRate = direct.sampleRate ?: 44100
                    val realBitDepth = direct.bitDepth ?: 16
                    val isHiRes = realBitDepth > 16 || realSampleRate > 44100
                    val tag = when {
                        !isFlac -> "Tidal • 320 kbps AAC"
                        realSampleRate >= 176400 -> "Tidal • Hi-Res ${realBitDepth}-bit/${realSampleRate / 1000}kHz"
                        isHiRes -> "Tidal • Hi-Res FLAC"
                        else -> "Tidal • FLAC Lossless"
                    }
                    return LosslessStreamResult(
                        url = direct.uri,
                        formatTag = tag,
                        trackId = "tidal_direct",
                        bitDepth = if (isFlac) realBitDepth else 16,
                        sampleRateHz = realSampleRate,
                        providerName = if (isFlac) "Tidal Direct" else "Tidal Direct (Lossy AAC)"
                    )
                }
            }
        }

        // 2. Priority B: Leased Tidal credentials from SourcePool
        val poolAccounts = SourcePoolAccountManager.getTidalAccounts()
        Log.d(TAG, "Resolving Tidal for \"$cleanTitle\" (candidate pool accounts: ${poolAccounts.size})")
        for (account in poolAccounts) {
            for (quality in qualityChain) {
                val direct = com.codetrio.spatialflow.data.tidal.TidalAccountManager.resolveDirectStream(
                    accessToken = account.token,
                    title = cleanTitle,
                    artists = artistsList,
                    durationMs = durationMs,
                    audioQuality = quality,
                    countryCode = account.countryCode ?: "US"
                )
                if (direct != null) {
                    val isFlac = direct.mimeType.contains("flac", ignoreCase = true) ||
                        direct.codecs.contains("flac", ignoreCase = true)

                    if (preferHiRes && !isFlac) {
                        Log.d(TAG, "Pool Tidal stream for \"$cleanTitle\" is lossy (${direct.mimeType}, ${direct.codecs}); skipping in Hi-Res mode")
                        continue
                    }

                    val realSampleRate = direct.sampleRate ?: 44100
                    val realBitDepth = direct.bitDepth ?: 16
                    val isHiRes = realBitDepth > 16 || realSampleRate > 44100
                    val tag = when {
                        !isFlac -> "Tidal • 320 kbps AAC"
                        realSampleRate >= 176400 -> "Tidal • Hi-Res ${realBitDepth}-bit/${realSampleRate / 1000}kHz"
                        isHiRes -> "Tidal • Hi-Res FLAC"
                        else -> "Tidal • FLAC Lossless"
                    }
                    return LosslessStreamResult(
                        url = direct.uri,
                        formatTag = tag,
                        trackId = "tidal_pool",
                        bitDepth = if (isFlac) realBitDepth else 16,
                        sampleRateHz = realSampleRate,
                        providerName = if (isFlac) "Tidal Community Pool" else "Tidal (Lossy AAC)"
                    )
                }
            }
        }

        return null
    }

    private fun buildCacheKey(
        videoId: String,
        title: String,
        artist: String,
        preferHiRes: Boolean,
        pipelineKey: String
    ): String {
        val identifier = if (videoId.isNotBlank()) "vid:$videoId" else "${normalize(artist)}:${normalize(title)}"
        return "$identifier:$pipelineKey:${if (preferHiRes) "hires" else "std"}"
    }

    /**
     * Pre-warms the MLS backend queue with YouTube video IDs.
     */
    fun queueMlsTracks(videoIds: List<String>) {
        MlsLosslessProvider.enqueueTracks(videoIds)
    }

    fun clearCache() {
        cache.clear()
        MlsLosslessProvider.clearCache()
    }
}
