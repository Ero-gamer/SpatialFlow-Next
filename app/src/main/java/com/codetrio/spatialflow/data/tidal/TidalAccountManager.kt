package com.codetrio.spatialflow.data.tidal

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object TidalAccountManager {

    private const val TAG = "TidalAccountManager"

    private const val PKCE_CLIENT_ID = "6BDSRdpK9hqEBTgU"
    private const val PKCE_CLIENT_SECRET = "xeuPmY7nbpZ9IIbLAcQ93shka1VNheUAqN6IcszjTG8="
    private const val CLIENT_ID = "zU4XHVVkc2tDPo4t"
    private const val CLIENT_SECRET = "VJKhDFqJPqvsPVNBV6ukXTJmwlvbttP7wlMlrc72se4="

    private const val PKCE_AUTHORIZE_ENDPOINT = "https://login.tidal.com/authorize"
    const val PKCE_REDIRECT_URI = "https://tidal.com/android/login/auth"

    const val FLOW_PKCE = "pkce"
    const val FLOW_WEBCAPTURE = "webcapture"
    const val FLOW_OAUTH = "oauth"

    private const val TOKEN_ENDPOINT = "https://auth.tidal.com/v1/oauth2/token"
    private const val API_BASE = "https://api.tidal.com/v1"
    private const val SCOPE = "r_usr+w_usr+w_sub"
    private const val COUNTRY_CODE = "US"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long,
        val userId: Long?,
        val username: String?,
        val countryCode: String? = null
    )

    enum class Subscription {
        UNKNOWN,
        PREMIUM,
        FREE
    }

    data class PkceChallenge(
        val verifier: String,
        val challenge: String,
        val uniqueKey: String,
        val authUrl: String
    )

    private fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun buildPkceChallenge(): PkceChallenge {
        val random = SecureRandom()
        val verifierBytes = ByteArray(64).also { random.nextBytes(it) }
        val verifier = base64UrlNoPad(verifierBytes)
        val challenge = base64UrlNoPad(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val uniqueKeyBytes = ByteArray(8).also { random.nextBytes(it) }
        val uniqueKey = uniqueKeyBytes.joinToString("") { "%02x".format(it) }

        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        val authUrl = buildString {
            append(PKCE_AUTHORIZE_ENDPOINT)
            append("?response_type=code")
            append("&redirect_uri=").append(enc(PKCE_REDIRECT_URI))
            append("&client_id=").append(PKCE_CLIENT_ID)
            append("&lang=EN")
            append("&appMode=android")
            append("&client_unique_key=").append(uniqueKey)
            append("&code_challenge=").append(challenge)
            append("&code_challenge_method=S256")
            append("&restrict_signup=true")
        }
        return PkceChallenge(verifier, challenge, uniqueKey, authUrl)
    }

    suspend fun exchangePkceCode(
        code: String,
        verifier: String,
        uniqueKey: String
    ): TokenResult? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("code", code)
            .add("client_id", PKCE_CLIENT_ID)
            .add("client_secret", PKCE_CLIENT_SECRET)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", PKCE_REDIRECT_URI)
            .add("scope", SCOPE)
            .add("code_verifier", verifier)
            .add("client_unique_key", uniqueKey)
            .build()
        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(body)
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful || payload.isBlank()) {
                    Log.w(TAG, "PKCE code exchange failed: ${response.code} ${payload.take(200)}")
                    return@use null
                }
                val json = JSONObject(payload)
                val user = json.optJSONObject("user")
                TokenResult(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token").ifBlank { null },
                    expiresAtMillis = System.currentTimeMillis() + (json.optLong("expires_in", 3600L) * 1000L),
                    userId = user?.optLong("userId")?.takeIf { it > 0 },
                    username = user?.optString("username")?.ifBlank { null },
                    countryCode = user?.optString("countryCode")?.ifBlank { null }
                )
            }
        }.getOrElse {
            Log.w(TAG, "PKCE code exchange error: ${it.message}")
            null
        }
    }

    suspend fun buildSessionFromBearer(accessToken: String): TokenResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API_BASE/sessions")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful || payload.isBlank()) {
                    Log.w(TAG, "Bearer session validation failed: ${response.code}")
                    return@use null
                }
                val json = JSONObject(payload)
                TokenResult(
                    accessToken = accessToken,
                    refreshToken = null,
                    expiresAtMillis = System.currentTimeMillis() + 3600L * 1000L,
                    userId = json.optLong("userId").takeIf { it > 0 },
                    username = json.optString("username").ifBlank { null },
                    countryCode = json.optString("countryCode").ifBlank { null }
                )
            }
        }.getOrElse {
            Log.w(TAG, "Bearer session validation error: ${it.message}")
            null
        }
    }

    suspend fun refreshAccessToken(
        refreshToken: String,
        flow: String = FLOW_PKCE
    ): TokenResult? = withContext(Dispatchers.IO) {
        if (flow == FLOW_WEBCAPTURE) {
            Log.w(TAG, "Web-capture session has no refresh token; re-login required")
            return@withContext null
        }
        val clientId = if (flow == FLOW_PKCE) PKCE_CLIENT_ID else CLIENT_ID
        val clientSecret = if (flow == FLOW_PKCE) PKCE_CLIENT_SECRET else CLIENT_SECRET
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("scope", SCOPE)
            .build()
        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(body)
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful || payload.isBlank()) {
                    Log.w(TAG, "Token refresh failed: ${response.code}")
                    return@use null
                }
                val json = JSONObject(payload)
                val user = json.optJSONObject("user")
                TokenResult(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token").ifBlank { null },
                    expiresAtMillis = System.currentTimeMillis() + (json.optLong("expires_in", 3600L) * 1000L),
                    userId = user?.optLong("userId")?.takeIf { it > 0 },
                    username = user?.optString("username")?.ifBlank { null },
                    countryCode = user?.optString("countryCode")?.ifBlank { null }
                )
            }
        }.getOrElse {
            Log.w(TAG, "Token refresh error: ${it.message}")
            null
        }
    }

    suspend fun fetchSubscription(
        accessToken: String,
        userId: Long
    ): Subscription = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API_BASE/users/$userId/subscription?countryCode=$COUNTRY_CODE")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Subscription lookup failed: ${response.code}")
                    return@use Subscription.UNKNOWN
                }
                val json = JSONObject(payload)
                val type = json.optJSONObject("subscription")?.optString("type")?.uppercase().orEmpty()
                val soundQuality = json.optString("highestSoundQuality").uppercase()

                when {
                    json.has("premiumAccess") ->
                        if (json.optBoolean("premiumAccess", false)) Subscription.PREMIUM else Subscription.FREE
                    type.contains("FREE") -> Subscription.FREE
                    soundQuality.contains("LOSSLESS") || soundQuality.contains("HI_RES") -> Subscription.PREMIUM
                    soundQuality == "LOW" -> Subscription.FREE
                    type.contains("HIFI") || type.contains("PREMIUM") || type.contains("PLUS") -> Subscription.PREMIUM
                    else -> Subscription.UNKNOWN
                }
            }
        }.getOrElse {
            Log.w(TAG, "Subscription lookup error: ${it.message}")
            Subscription.UNKNOWN
        }
    }

    data class DirectStream(
        val uri: String,
        val mimeType: String,
        val codecs: String,
        val contentLength: Long? = null,
        val label: String,
        val sampleRate: Int? = null,
        val bitDepth: Int? = null,
        val matchedTitle: String? = null,
        val matchedArtist: String? = null,
        val matchedAlbum: String? = null,
        val matchedDurationMs: Long? = null
    )

    private data class SearchMatch(
        val id: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val durationMs: Long?
    )

    suspend fun resolveDirectStream(
        accessToken: String,
        title: String,
        artists: List<String>,
        durationMs: Long?,
        audioQuality: String = "LOSSLESS",
        countryCode: String = COUNTRY_CODE
    ): DirectStream? = withContext(Dispatchers.IO) {
        val country = countryCode.ifBlank { COUNTRY_CODE }
        val match = searchTrack(accessToken, title, artists, durationMs, country) ?: return@withContext null
        resolvePlaybackInfo(
            accessToken = accessToken,
            trackId = match.id,
            audioQuality = audioQuality
        )?.copy(
            matchedTitle = match.title,
            matchedArtist = match.artist,
            matchedAlbum = match.album,
            matchedDurationMs = match.durationMs
        )
    }

    private fun searchTrack(
        accessToken: String,
        title: String,
        artists: List<String>,
        durationMs: Long?,
        countryCode: String = COUNTRY_CODE
    ): SearchMatch? {
        val primaryArtist = artists.firstOrNull().orEmpty()
        val query = URLEncoder.encode("$title $primaryArtist".trim(), "UTF-8")
        val request = Request.Builder()
            .url("$API_BASE/search/tracks?query=$query&limit=15&countryCode=$countryCode")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val payload = response.body?.string().orEmpty()
                val items = JSONObject(payload).optJSONArray("items") ?: return@use null

                var bestMatch: SearchMatch? = null
                var bestScore = Int.MIN_VALUE
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optLong("id").takeIf { it > 0 }?.toString() ?: continue
                    val candTitle = item.optString("title")
                    val candArtists = item.optJSONArray("artists")?.let { arr ->
                        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                    }.orEmpty()
                    val candArtistStr = candArtists.joinToString(" ")
                    val candDurationMs = item.optLong("duration").takeIf { it > 0 }?.times(1000L)

                    val score = com.codetrio.spatialflow.data.sourcepool.TrackMatching.score(
                        wantedTitle = title,
                        wantedArtists = artists,
                        candidateTitle = candTitle,
                        candidateArtist = candArtistStr,
                        wantedDurationMs = durationMs,
                        candidateDurationMs = candDurationMs
                    )

                    if (score > bestScore && score >= com.codetrio.spatialflow.data.sourcepool.TrackMatching.MIN_MATCH_SCORE) {
                        bestScore = score
                        bestMatch = SearchMatch(
                            id = id,
                            title = candTitle,
                            artist = candArtists.joinToString(", ").takeIf { it.isNotBlank() },
                            album = item.optJSONObject("album")?.optString("title")?.takeIf { it.isNotBlank() },
                            durationMs = candDurationMs
                        )
                    }
                }
                bestMatch
            }
        }.getOrNull()
    }

    /**
     * Infers sample rate from the Tidal audioQuality string when the manifest doesn't carry it.
     */
    private fun inferSampleRateFromQuality(quality: String): Int = when {
        quality.contains("HI_RES_LOSSLESS", ignoreCase = true) -> 96000   // Tidal Max: up to 192kHz, conservative default
        quality.contains("HI_RES", ignoreCase = true) -> 96000            // MQA Master
        quality.contains("LOSSLESS", ignoreCase = true) -> 44100          // CD quality
        else -> 44100
    }

    private fun inferBitDepthFromQuality(quality: String): Int = when {
        quality.contains("HI_RES", ignoreCase = true) -> 24
        else -> 16
    }

    private fun resolvePlaybackInfo(
        accessToken: String,
        trackId: String,
        audioQuality: String
    ): DirectStream? {
        val url = "$API_BASE/tracks/$trackId/playbackinfopostpaywall?audioquality=$audioQuality&playbackmode=STREAM&assetpresentation=FULL"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val payload = response.body?.string().orEmpty()
                val json = JSONObject(payload)
                if (json.optString("assetPresentation").equals("PREVIEW", ignoreCase = true)) return@use null

                // Extract actual quality tier from the response (may differ from requested)
                val actualQuality = json.optString("audioQuality").ifBlank { audioQuality }
                val fallbackSampleRate = inferSampleRateFromQuality(actualQuality)
                val fallbackBitDepth = inferBitDepthFromQuality(actualQuality)

                // Try direct URL (rare, some regions)
                val directJsonUrl = json.optString("url").takeIf { it.startsWith("http") }
                    ?: json.optJSONArray("urls")?.optString(0)?.takeIf { it.startsWith("http") }
                if (directJsonUrl != null) {
                    return@use DirectStream(
                        uri = directJsonUrl,
                        mimeType = "audio/flac",
                        codecs = "flac",
                        label = "Tidal $actualQuality",
                        sampleRate = json.optInt("sampleRate").takeIf { it > 0 } ?: fallbackSampleRate,
                        bitDepth = json.optInt("bitDepth").takeIf { it > 0 } ?: fallbackBitDepth
                    )
                }

                val manifestB64 = json.optString("manifest").takeIf { it.isNotBlank() } ?: return@use null
                val decoded = String(Base64.decode(manifestB64.trim(), Base64.DEFAULT), Charsets.UTF_8).trim()
                if (decoded.startsWith("{")) {
                    val root = JSONObject(decoded)
                    val urls = root.optJSONArray("urls") ?: return@use null
                    val directUrl = urls.optString(0).takeIf { it.isNotBlank() } ?: return@use null
                    val mimeType = root.optString("mimeType", "audio/flac")
                    val codecs = root.optString("codecs", "flac")
                    // Parse actual sample rate and bit depth from the manifest JSON
                    val manifestSampleRate = root.optInt("sampleRate").takeIf { it > 0 }
                        ?: root.optInt("sample_rate").takeIf { it > 0 }
                    val manifestBitDepth = root.optInt("bitDepth").takeIf { it > 0 }
                        ?: root.optInt("bit_depth").takeIf { it > 0 }
                    DirectStream(
                        uri = directUrl,
                        mimeType = mimeType,
                        codecs = codecs,
                        label = "Tidal $actualQuality",
                        sampleRate = manifestSampleRate ?: fallbackSampleRate,
                        bitDepth = manifestBitDepth ?: fallbackBitDepth
                    )
                } else if (decoded.contains("<BaseURL>", ignoreCase = true) || decoded.contains("<MPD", ignoreCase = true)) {
                    val baseUrlRegex = Regex("""<BaseURL>(https?://[^<]+)</BaseURL>""", RegexOption.IGNORE_CASE)
                    val match = baseUrlRegex.find(decoded)?.groupValues?.get(1)?.trim()
                    // Try to extract sample rate from MPD AudioSamplingRate attribute
                    val mpdSampleRateRegex = Regex("""audioSamplingRate=["'](\d+)["']""", RegexOption.IGNORE_CASE)
                    val mpdSampleRate = mpdSampleRateRegex.find(decoded)?.groupValues?.get(1)?.toIntOrNull()
                    if (!match.isNullOrEmpty()) {
                        DirectStream(
                            uri = match,
                            mimeType = "audio/flac",
                            codecs = "flac",
                            label = "Tidal $actualQuality",
                            sampleRate = mpdSampleRate ?: fallbackSampleRate,
                            bitDepth = fallbackBitDepth
                        )
                    } else null
                } else null
            }
        }.getOrNull()
    }
}
