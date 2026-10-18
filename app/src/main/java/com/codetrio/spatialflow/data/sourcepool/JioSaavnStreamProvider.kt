package com.codetrio.spatialflow.data.sourcepool

import android.util.Base64
import android.util.Log
import com.codetrio.spatialflow.util.ConsoleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * High-fidelity direct audio stream provider for JioSaavn.
 * Performs search and on-device DES-ECB CDN link decryption to acquire pristine 320 kbps AAC streams.
 */
object JioSaavnStreamProvider {

    private const val TAG = "JioSaavnProvider"
    private const val BASE_URL = "https://www.jiosaavn.com/api.php"
    private const val DES_SECRET_KEY = "38346591"
    private val SUFFIX_REGEX = Regex("_(48|96|160|320)\\.(mp4|aac|mp3)$")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    data class SaavnTrackCandidate(
        val id: String,
        val title: String,
        val artist: String,
        val durationSeconds: Long?,
        val encryptedMediaUrl: String,
        val isProOnly: Boolean
    )

    /**
     * Resolves a direct 320 kbps stream from JioSaavn by title, artist, and duration.
     */
    suspend fun resolveStream(
        title: String,
        artist: String? = null,
        durationSeconds: Long? = null
    ): LosslessStreamResult? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanArtist = artist?.trim().orEmpty()
        if (cleanTitle.isBlank()) return@withContext null

        val searchQuery = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle
        Log.d(TAG, "Resolving JioSaavn stream for: \"$searchQuery\"")

        try {
            val candidates = searchSongs(searchQuery)
            if (candidates.isEmpty()) {
                Log.d(TAG, "No JioSaavn candidates found for \"$searchQuery\"")
                return@withContext null
            }

            val wantedArtists = if (cleanArtist.isNotBlank()) listOf(cleanArtist) else emptyList()
            val wantedDurationMs = durationSeconds?.takeIf { it > 0 }?.times(1000L)

            val scoredCandidates = candidates
                .filter { !it.isProOnly && it.encryptedMediaUrl.isNotBlank() }
                .map { cand ->
                    val score = TrackMatching.score(
                        wantedTitle = cleanTitle,
                        wantedArtists = wantedArtists,
                        candidateTitle = cand.title,
                        candidateArtist = cand.artist,
                        wantedDurationMs = wantedDurationMs,
                        candidateDurationMs = cand.durationSeconds?.takeIf { it > 0 }?.times(1000L)
                    )
                    cand to score
                }
                .filter { it.second >= TrackMatching.MIN_MATCH_SCORE }
                .maxByOrNull { it.second }

            if (scoredCandidates == null) {
                Log.d(TAG, "No suitable JioSaavn candidate met strict match threshold for \"$searchQuery\"")
                return@withContext null
            }

            val bestCandidate = scoredCandidates.first
            Log.d(TAG, "JioSaavn strict match \"${bestCandidate.title}\" by \"${bestCandidate.artist}\" (score=${scoredCandidates.second})")

            // Decrypt URL on-device
            val decryptedUrl = decryptUrl(bestCandidate.encryptedMediaUrl)
            if (decryptedUrl.isBlank()) {
                Log.w(TAG, "Failed to decrypt media URL for candidate ${bestCandidate.id}")
                return@withContext null
            }

            val stream320 = formatStreamUrl(decryptedUrl, targetQuality = "320")
            if (stream320.isBlank() || (!stream320.startsWith("http://") && !stream320.startsWith("https://"))) {
                return@withContext null
            }

            ConsoleLogger.stream("⚡ JioSaavn 320kbps stream acquired: \"${bestCandidate.title}\" by \"${bestCandidate.artist}\"")
            LosslessStreamResult(
                url = stream320,
                formatTag = "AAC • 320 kbps",
                trackId = bestCandidate.id,
                bitDepth = 16,
                sampleRateHz = 44100,
                providerName = "JioSaavn"
            )
        } catch (e: Exception) {
            Log.w(TAG, "JioSaavn stream resolution failed for \"$searchQuery\": ${e.message}")
            null
        }
    }

    private fun searchSongs(query: String): List<SaavnTrackCandidate> {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "$BASE_URL?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=android&q=$encodedQuery&p=1&n=10"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .header("Accept-Language", "en-IN,en;q=0.9")
            .header("X-Forwarded-For", "49.36.0.1")
            .header("X-Real-IP", "49.36.0.1")
            .header("Cookie", "explicit_content=1")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body.string()
                if (body.isBlank()) return emptyList()

                val root = JSONObject(body)
                val results = root.optJSONArray("results") ?: return emptyList()

                val candidates = mutableListOf<SaavnTrackCandidate>()
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val title = item.optString("title")
                    val moreInfo = item.optJSONObject("more_info") ?: JSONObject()
                    val encryptedMediaUrl = moreInfo.optString("encrypted_media_url")
                    val durationSec = moreInfo.optString("duration").toLongOrNull()

                    val artistMap = moreInfo.optJSONObject("artistMap")
                    val primaryArtists = artistMap?.optJSONArray("primary_artists")
                    var artistName = ""
                    if (primaryArtists != null && primaryArtists.length() > 0) {
                        artistName = primaryArtists.optJSONObject(0)?.optString("name").orEmpty()
                    }
                    if (artistName.isBlank()) {
                        artistName = moreInfo.optString("music")
                    }

                    val rights = moreInfo.optJSONObject("rights")
                    val rightsCode = rights?.optString("code").orEmpty()
                    val rightsReason = rights?.optString("reason").orEmpty()
                    val isProOnly = rightsCode == "1" || rightsReason.contains("Pro Only", ignoreCase = true)

                    if (id.isNotBlank() && title.isNotBlank() && encryptedMediaUrl.isNotBlank()) {
                        candidates.add(
                            SaavnTrackCandidate(
                                id = id,
                                title = title,
                                artist = artistName,
                                durationSeconds = durationSec,
                                encryptedMediaUrl = encryptedMediaUrl,
                                isProOnly = isProOnly
                            )
                        )
                    }
                }
                candidates
            }
        } catch (e: Exception) {
            Log.w(TAG, "JioSaavn search query failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Decrypts JioSaavn encrypted CDN URL using DES/ECB/PKCS5Padding with key "38346591".
     */
    fun decryptUrl(encryptedUrl: String): String {
        if (encryptedUrl.isBlank()) return ""
        return try {
            val secretKey = SecretKeySpec(DES_SECRET_KEY.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val cleanEncrypted = encryptedUrl.replace("\\/", "/").trim()
            val decodedBytes = Base64.decode(cleanEncrypted, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            Log.e(TAG, "JioSaavn URL decryption failed: ${e.message}")
            ""
        }
    }

    /**
     * Transforms the base decrypted URL to target bitrate (e.g. 320kbps).
     */
    fun formatStreamUrl(decryptedUrl: String, targetQuality: String = "320"): String {
        if (decryptedUrl.isBlank()) return ""
        return if (decryptedUrl.contains(SUFFIX_REGEX)) {
            decryptedUrl.replace(SUFFIX_REGEX) { match ->
                "_${targetQuality}.${match.groupValues[2]}"
            }
        } else {
            decryptedUrl.replace("_96", "_$targetQuality")
        }
    }
}
