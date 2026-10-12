package com.codetrio.spatialflow.data.sourcepool

import android.util.Log
import com.codetrio.spatialflow.BuildConfig
import com.codetrio.spatialflow.util.ConsoleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Direct lossless provider integrating MLS (Music Lossless Service - mls.kouzu.in).
 * Resolves direct FLAC streams using exact YouTube Video ID matching and pre-warms backend cache.
 */
object MlsLosslessProvider {

    private const val TAG = "MlsLosslessProvider"
    private const val BASE_URL = "https://mls.kouzu.in"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Track IDs already sent to /api/stream/add to prevent spamming the queue endpoint
    private val enqueuedIds = ConcurrentHashMap.newKeySet<String>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3500, TimeUnit.MILLISECONDS)
            .readTimeout(3500, TimeUnit.MILLISECONDS)
            .callTimeout(4000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Resolves a direct FLAC stream from MLS using the YouTube video ID.
     * Returns null if not cached/processing, or on network failure.
     */
    suspend fun resolveStream(videoId: String): LosslessStreamResult? = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
        if (cleanId.isBlank()) return@withContext null

        val encodedId = URLEncoder.encode(cleanId, StandardCharsets.UTF_8.toString())
        val url = "$BASE_URL/api/stream?id=$encodedId"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SpatialFlow/${BuildConfig.VERSION_NAME} (Android)")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "MLS stream request returned HTTP ${response.code} for $cleanId")
                    enqueueTracks(listOf(cleanId))
                    return@withContext null
                }

                val body = response.body.string()
                if (body.isBlank() || !body.trim().startsWith("{")) return@withContext null

                val json = JSONObject(body)
                val losslessUrl = json.optString("lossless").trim()

                if ((losslessUrl.startsWith("http://") || losslessUrl.startsWith("https://")) &&
                    !losslessUrl.contains("range=") &&
                    !LosslessStreamResolver.isUrlBlacklisted(losslessUrl)) {
                    ConsoleLogger.stream("⚡ MLS Lossless FLAC stream acquired for $cleanId")
                    return@withContext LosslessStreamResult(
                        url = losslessUrl,
                        formatTag = "FLAC • Lossless",
                        trackId = cleanId,
                        bitDepth = 16,
                        sampleRateHz = 44100,
                        providerName = "MLS Lossless"
                    )
                } else {
                    Log.d(TAG, "MLS stream for $cleanId not ready: \"$losslessUrl\". Triggering enqueue.")
                    enqueueTracks(listOf(cleanId))
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MLS resolveStream failed for $cleanId: ${e.message}")
            null
        }
    }

    /**
     * Pre-warms the MLS backend queue with YouTube video IDs using /api/stream/add.
     * Runs asynchronously in background to ensure non-blocking performance.
     */
    fun enqueueTracks(videoIds: List<String>) {
        val toEnqueue = videoIds.map { it.trim() }.filter { it.isNotBlank() && enqueuedIds.add(it) }
        if (toEnqueue.isEmpty()) return

        scope.launch {
            try {
                // Batch up to 10 IDs per call
                toEnqueue.chunked(10).forEach { batch ->
                    val joinedIds = batch.joinToString(",") { URLEncoder.encode(it, StandardCharsets.UTF_8.toString()) }
                    val url = "$BASE_URL/api/stream/add?id=$joinedIds"

                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "SpatialFlow/${BuildConfig.VERSION_NAME} (Android)")
                        .header("Accept", "application/json")
                        .get()
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d(TAG, "Successfully enqueued ${batch.size} track(s) to MLS backend")
                        } else {
                            Log.d(TAG, "MLS enqueue returned HTTP ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enqueue tracks to MLS: ${e.message}")
            }
        }
    }

    fun clearCache() {
        enqueuedIds.clear()
    }
}
