package com.codetrio.spatialflow.data.deezer

import android.util.Log
import com.codetrio.spatialflow.data.sourcepool.TrackMatching
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DeezerAudioProvider {

    private const val TAG = "DeezerAudioProvider"
    private const val SEARCH_LIMIT = 10
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L
    private const val STREAM_CACHE_MS = 30 * 60 * 1000L
    private const val FAILURE_CACHE_MS = 10 * 60 * 1000L
    private const val SESSION_TTL_MS = 45 * 60 * 1000L

    private const val GATEWAY = "https://www.deezer.com/ajax/gw-light.php"
    private const val MEDIA_ENDPOINT = "https://media.deezer.com/v1/get_url"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val MIME_FLAC = "audio/flac"
    private const val MIME_MPEG = "audio/mpeg"

    const val FORMAT_FLAC = "FLAC"
    const val FORMAT_MP3_320 = "MP3_320"
    const val FORMAT_MP3_128 = "MP3_128"

    private val FORMAT_TIERS = listOf(FORMAT_FLAC, FORMAT_MP3_320, FORMAT_MP3_128)
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaTypeOrNull()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    data class Account(
        val arl: String,
        val premium: Boolean = true
    )

    data class AccountInfo(
        val name: String,
        val lossless: Boolean
    )

    @Volatile
    private var manualAccount: Account? = null

    fun setManualArl(arl: String?, premium: Boolean = false) {
        val trimmed = arl?.trim()
        val next = if (trimmed.isNullOrEmpty()) null else Account(arl = trimmed, premium = premium)
        val previous = manualAccount
        manualAccount = next
        if (previous != null && previous.arl != next?.arl) {
            sessions.remove(previous.arl)
        }
    }

    fun hasAccounts(): Boolean = manualAccount != null

    fun verifyArl(arl: String): AccountInfo? = runCatching {
        val json = gateway(arl.trim(), apiToken = "", method = "deezer.getUserData", payload = null)
        val user = json.optJSONObject("results")?.optJSONObject("USER") ?: return@runCatching null
        if (user.optLong("USER_ID", 0L) == 0L) return@runCatching null
        val options = user.optJSONObject("OPTIONS")
        val name = sequenceOf(
            user.optString("BLOG_NAME"),
            user.optString("FIRSTNAME"),
            user.optString("EMAIL")
        ).firstOrNull { it.isNotBlank() } ?: "Deezer"

        AccountInfo(
            name = name,
            lossless = options?.optBoolean("web_lossless", false) == true ||
                    options?.optBoolean("mobile_lossless", false) == true
        )
    }.onFailure { Log.w(TAG, "ARL verification failed: ${it.message}") }
        .getOrNull()

    private data class Session(
        val arl: String,
        val apiToken: String,
        val licenseToken: String,
        val lossless: Boolean,
        val establishedAt: Long
    )

    data class Resolved(
        val uri: String,
        val mimeType: String,
        val codecs: String,
        val contentLength: Long?,
        val label: String,
        val matchedTitle: String,
        val matchedArtist: String?,
        val matchedAlbum: String?,
        val matchedDurationMs: Long?,
        val sampleRate: Int?,
        val bitDepth: Int?
    )

    private class CachedStream(
        val stream: Resolved,
        val expiresAt: Long
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val searchCache = ConcurrentHashMap<String, Pair<TrackMatching.Candidate?, Long>>()
    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val failureCache = ConcurrentHashMap<String, Long>()

    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
        val isrc: String? = null
    )

    private fun Query.cacheKey(): String =
        listOf(mediaId, title.lowercase(), artists.joinToString(",").lowercase(), album.orEmpty().lowercase())
            .joinToString("|")

    fun resolve(
        title: String,
        artist: String,
        durationSeconds: Long?,
        customArl: String? = null
    ): Resolved? {
        if (!customArl.isNullOrBlank()) {
            setManualArl(customArl, premium = true)
        }
        val query = Query(
            mediaId = "",
            title = title,
            artists = if (artist.isNotBlank()) listOf(artist) else emptyList(),
            album = null,
            durationMs = durationSeconds?.times(1000L)
        )
        return resolve(query, FORMAT_FLAC)
    }

    fun resolve(query: Query, format: String = FORMAT_FLAC): Resolved? {
        val account = manualAccount ?: return null
        val now = System.currentTimeMillis()
        val cacheKey = query.cacheKey() + ":" + format

        streamCache[cacheKey]?.let { cached ->
            if (cached.expiresAt > now) return cached.stream
            streamCache.remove(cacheKey)
        }
        failureCache[cacheKey]?.let { failedUntil ->
            if (failedUntil > now) return null
            failureCache.remove(cacheKey)
        }

        val session = runCatching { session(account) }
            .onFailure { Log.w(TAG, "Failed to establish session: ${it.message}") }
            .getOrNull() ?: return null

        val match = runCatching { matchTrack(session, query) }
            .onFailure { Log.w(TAG, "Search match failed: ${it.message}") }
            .getOrNull() ?: return null

        val trackId = match.id
        val media = runCatching { requestUrl(session, trackId, format) }
            .onFailure { Log.w(TAG, "requestUrl failed for track $trackId: ${it.message}") }
            .getOrNull()

        if (media == null) {
            sessions.remove(account.arl)
            failureCache[cacheKey] = now + FAILURE_CACHE_MS
            return null
        }

        val stream = Resolved(
            uri = DeezerCrypto.buildUri(media.url, trackId),
            mimeType = if (media.flac) MIME_FLAC else MIME_MPEG,
            codecs = if (media.flac) "flac" else "mp3",
            contentLength = media.contentLength,
            label = when (media.format.uppercase()) {
                FORMAT_FLAC -> "Deezer FLAC"
                FORMAT_MP3_320 -> "Deezer MP3 320"
                FORMAT_MP3_128 -> "Deezer MP3 128"
                else -> "Deezer"
            },
            matchedTitle = match.title,
            matchedArtist = match.artists.firstOrNull(),
            matchedAlbum = match.album,
            matchedDurationMs = match.durationMs,
            sampleRate = if (media.flac) 44_100 else null,
            bitDepth = if (media.flac) 16 else null
        )
        streamCache[cacheKey] = CachedStream(stream, now + STREAM_CACHE_MS)
        return stream
    }

    private fun session(account: Account): Session {
        val now = System.currentTimeMillis()
        sessions[account.arl]?.let { if (now - it.establishedAt < SESSION_TTL_MS) return it }

        val json = gateway(account.arl, apiToken = "", method = "deezer.getUserData", payload = null)
        val results = json.optJSONObject("results")
        val user = requireNotNull(results?.optJSONObject("USER")) { "no USER in session payload" }

        if (user.optLong("USER_ID", 0L) == 0L) {
            throw IllegalStateException("ARL rejected by gateway")
        }

        val options = requireNotNull(user.optJSONObject("OPTIONS")) { "no OPTIONS in session payload" }
        val licenseToken = options.optString("license_token")
        require(licenseToken.isNotBlank()) { "no license_token in session" }

        val apiToken = results?.optString("checkForm").orEmpty()
        require(apiToken.isNotBlank()) { "no api token in session" }

        val lossless = options.optBoolean("web_lossless", false) ||
                options.optBoolean("mobile_lossless", false)

        val session = Session(
            arl = account.arl,
            apiToken = apiToken,
            licenseToken = licenseToken,
            lossless = lossless,
            establishedAt = now
        )
        sessions[account.arl] = session
        return session
    }

    private fun gateway(
        arl: String,
        apiToken: String,
        method: String,
        payload: JSONObject?
    ): JSONObject {
        val url = GATEWAY.toHttpUrl()
            .newBuilder()
            .addQueryParameter("method", method)
            .addQueryParameter("input", "3")
            .addQueryParameter("api_version", "1.0")
            .addQueryParameter("api_token", apiToken)
            .build()
        val body = (payload ?: JSONObject()).toString().toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "arl=$arl")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("gateway HTTP ${response.code}")
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun matchTrack(session: Session, query: Query): TrackMatching.Candidate? {
        val key = query.cacheKey()
        val now = System.currentTimeMillis()
        searchCache[key]?.let { (candidate, expiresAt) ->
            if (expiresAt > now) return candidate
            searchCache.remove(key)
        }

        query.isrc?.takeIf { it.isNotBlank() }?.let { isrc ->
            val exact = runCatching { lookupByIsrc(isrc) }.getOrNull()
            if (exact != null) {
                searchCache[key] = exact to (now + SEARCH_CACHE_MS)
                return exact
            }
        }

        val terms = listOf(query.title) + query.artists.take(1)
        val payload = JSONObject()
            .put("query", terms.joinToString(" ").trim())
            .put("start", 0)
            .put("nb", SEARCH_LIMIT)
        val json = gateway(session.arl, session.apiToken, "search.music", payload)
        val data = json.optJSONObject("results")?.optJSONArray("data") ?: JSONArray()

        val candidates = (0 until data.length()).mapNotNull { i ->
            val obj = data.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("SNG_ID").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TrackMatching.Candidate(
                id = id,
                title = obj.optString("SNG_TITLE"),
                artists = listOfNotNull(obj.optString("ART_NAME").takeIf { it.isNotBlank() }),
                album = obj.optString("ALB_TITLE").takeIf { it.isNotBlank() },
                durationMs = obj.optLong("DURATION", 0L).takeIf { it > 0L }?.times(1000L)
            )
        }

        val best = TrackMatching.best(
            target = TrackMatching.Target(
                title = query.title,
                artists = query.artists,
                album = query.album,
                durationMs = query.durationMs
            ),
            candidates = candidates
        )
        searchCache[key] = best to (now + SEARCH_CACHE_MS)
        return best
    }

    private fun lookupByIsrc(isrc: String): TrackMatching.Candidate? {
        val req = Request.Builder()
            .url("https://api.deezer.com/track/isrc:${URLEncoder.encode(isrc, "UTF-8")}")
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val obj = JSONObject(res.body?.string() ?: return null)
            if (obj.has("error")) return null
            val id = obj.optLong("id", 0L).takeIf { it > 0L }?.toString() ?: return null
            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: return null
            val artist = obj.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }
            val album = obj.optJSONObject("album")?.optString("title")?.takeIf { it.isNotBlank() }
            val durationMs = obj.optLong("duration", 0L).takeIf { it > 0L }?.times(1000L)
            return TrackMatching.Candidate(
                id = id,
                title = title,
                artists = listOfNotNull(artist),
                album = album,
                durationMs = durationMs
            )
        }
    }

    private class Media(
        val url: String,
        val flac: Boolean,
        val format: String,
        val contentLength: Long?
    )

    private fun requestUrl(
        session: Session,
        trackId: String,
        format: String
    ): Media? {
        val trackJson = gateway(
            session.arl,
            session.apiToken,
            "song.getData",
            JSONObject().put("sng_id", trackId)
        )
        val trackToken = trackJson.optJSONObject("results")?.optString("TRACK_TOKEN").orEmpty()
        if (trackToken.isBlank()) return null

        val requested = if (format == FORMAT_FLAC && !session.lossless) FORMAT_MP3_320 else format
        val formats = FORMAT_TIERS.dropWhile { it != requested }.ifEmpty { listOf(FORMAT_MP3_320, FORMAT_MP3_128) }
        val formatArray = JSONArray()
        formats.forEach { tier ->
            formatArray.put(JSONObject().put("cipher", "BF_CBC_STRIPE").put("format", tier))
        }
        val mediaArray = JSONArray().put(
            JSONObject()
                .put("type", "FULL")
                .put("formats", formatArray)
        )
        val cipherPayload = JSONObject()
            .put("license_token", session.licenseToken)
            .put("media", mediaArray)
            .put("track_tokens", JSONArray().put(trackToken))

        val request = Request.Builder()
            .url(MEDIA_ENDPOINT)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "arl=${session.arl}")
            .post(cipherPayload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val json = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("get_url HTTP ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }

        val first = json.optJSONArray("data")?.optJSONObject(0) ?: return null
        val media = first.optJSONArray("media")?.optJSONObject(0) ?: return null
        val source = media.optJSONArray("sources")?.optJSONObject(0) ?: return null
        val url = source.optString("url").takeIf { it.isNotBlank() } ?: return null

        val servedFormat = media.optString("format")
        val flac = servedFormat.equals(FORMAT_FLAC, ignoreCase = true)

        val sizeField = "FILESIZE_${servedFormat.uppercase()}"
        val results = trackJson.optJSONObject("results")
        val contentLength = results?.optString(sizeField)?.toLongOrNull()?.takeIf { it > 0L }
            ?: results?.optString("FILESIZE")?.toLongOrNull()?.takeIf { it > 0L }

        return Media(url = url, flac = flac, format = servedFormat, contentLength = contentLength)
    }
}
