package com.codetrio.spatialflow.data.lastfm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * High-performance client for Last.fm 2.0 Web Services.
 * Handles Now Playing updates, single/batch Scrobbling, Loved Tracks sync,
 * and user profile info.
 */
object LastFmClient {

    private const val TAG = "LastFmClient"

    /**
     * Checks if internet connectivity is available and respects the "Wi-Fi Only" setting.
     */
    fun canPerformNetworkCall(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return false

        if (LastFmPrefs.isWifiOnly(context)) {
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                   caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        return true
    }

    /**
     * Updates the "Now Playing" status for the currently playing track on Last.fm.
     */
    suspend fun updateNowPlaying(
        context: Context,
        artist: String,
        track: String,
        album: String? = null,
        durationSec: Long = 0L
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!LastFmPrefs.isLoggedIn(context) || !LastFmPrefs.isNowPlayingEnabled(context)) {
            return@withContext Result.success(false)
        }
        if (!canPerformNetworkCall(context)) {
            return@withContext Result.failure(Exception("Network unavailable or Wi-Fi only restriction active"))
        }

        try {
            val apiKey = LastFmAuth.getApiKey(context)
            val apiSecret = LastFmAuth.getApiSecret(context)
            val sessionKey = LastFmPrefs.getSessionKey(context)

            val params = mutableMapOf(
                "method" to "track.updateNowPlaying",
                "api_key" to apiKey,
                "sk" to sessionKey,
                "artist" to artist.trim(),
                "track" to track.trim()
            )
            if (!album.isNullOrBlank()) {
                params["album"] = album.trim()
            }
            if (durationSec > 0) {
                params["duration"] = durationSec.toString()
            }

            val apiSig = LastFmAuth.generateApiSignature(params, apiSecret)
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }
            formBuilder.add("api_sig", apiSig)
            formBuilder.add("format", "json")

            val request = Request.Builder()
                .url(LastFmAuth.API_ENDPOINT)
                .post(formBuilder.build())
                .header("User-Agent", LastFmAuth.USER_AGENT)
                .build()

            val response = LastFmAuth.httpClient.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "updateNowPlaying HTTP error: ${response.code} body: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val nowPlayingResp = LastFmAuth.json.decodeFromString<LastFmNowPlayingResponse>(responseBody)
            if (nowPlayingResp.error != null && nowPlayingResp.error != 0) {
                val errorMsg = nowPlayingResp.message ?: "Error ${nowPlayingResp.error}"
                Log.w(TAG, "updateNowPlaying API error: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }

            Log.d(TAG, "Now Playing updated: $artist - $track")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "updateNowPlaying failed", e)
            Result.failure(e)
        }
    }

    /**
     * Submits one or multiple scrobbles to Last.fm (supports batch requests up to 50 tracks).
     * Returns the count of accepted scrobbles.
     */
    suspend fun scrobble(
        context: Context,
        scrobbles: List<ScrobbleItem>
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (scrobbles.isEmpty()) return@withContext Result.success(0)
        if (!LastFmPrefs.isLoggedIn(context)) {
            return@withContext Result.failure(Exception("Not logged in to Last.fm"))
        }
        if (!canPerformNetworkCall(context)) {
            return@withContext Result.failure(Exception("Network unavailable or Wi-Fi only restriction active"))
        }

        try {
            val apiKey = LastFmAuth.getApiKey(context)
            val apiSecret = LastFmAuth.getApiSecret(context)
            val sessionKey = LastFmPrefs.getSessionKey(context)

            // Last.fm supports max 50 scrobbles per batch
            val batch = scrobbles.take(50)
            val params = mutableMapOf(
                "method" to "track.scrobble",
                "api_key" to apiKey,
                "sk" to sessionKey
            )

            if (batch.size == 1) {
                val item = batch[0]
                params["artist"] = item.artist.trim()
                params["track"] = item.title.trim()
                params["timestamp"] = item.timestampSec.toString()
                if (!item.album.isNullOrBlank()) params["album"] = item.album.trim()
                if (item.durationSec > 0) params["duration"] = item.durationSec.toString()
            } else {
                batch.forEachIndexed { i, item ->
                    params["artist[$i]"] = item.artist.trim()
                    params["track[$i]"] = item.title.trim()
                    params["timestamp[$i]"] = item.timestampSec.toString()
                    if (!item.album.isNullOrBlank()) params["album[$i]"] = item.album.trim()
                    if (item.durationSec > 0) params["duration[$i]"] = item.durationSec.toString()
                }
            }

            val apiSig = LastFmAuth.generateApiSignature(params, apiSecret)
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }
            formBuilder.add("api_sig", apiSig)
            formBuilder.add("format", "json")

            val request = Request.Builder()
                .url(LastFmAuth.API_ENDPOINT)
                .post(formBuilder.build())
                .header("User-Agent", LastFmAuth.USER_AGENT)
                .build()

            val response = LastFmAuth.httpClient.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "scrobble HTTP error: ${response.code} body: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val scrobbleResp = LastFmAuth.json.decodeFromString<LastFmScrobbleResponse>(responseBody)
            if (scrobbleResp.error != null && scrobbleResp.error != 0) {
                val errorMsg = scrobbleResp.message ?: "Scrobble error ${scrobbleResp.error}"
                Log.w(TAG, "scrobble API error: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val accepted = scrobbleResp.scrobbles?.attr?.accepted ?: batch.size
            Log.d(TAG, "Successfully scrobbled $accepted track(s)")
            
            // Increment local scrobble counter
            val currentCount = LastFmPrefs.getScrobbleCount(context)
            LastFmPrefs.saveScrobbleCount(context, currentCount + accepted)

            Result.success(accepted)
        } catch (e: Exception) {
            Log.e(TAG, "scrobble failed", e)
            Result.failure(e)
        }
    }

    /**
     * Marks a track as "Loved" on Last.fm.
     */
    suspend fun loveTrack(context: Context, artist: String, track: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!LastFmPrefs.isLoggedIn(context) || !LastFmPrefs.isSyncLovedTracks(context)) {
            return@withContext Result.success(false)
        }
        if (!canPerformNetworkCall(context)) {
            return@withContext Result.failure(Exception("Network unavailable"))
        }

        try {
            val apiKey = LastFmAuth.getApiKey(context)
            val apiSecret = LastFmAuth.getApiSecret(context)
            val sessionKey = LastFmPrefs.getSessionKey(context)

            val params = mapOf(
                "method" to "track.love",
                "api_key" to apiKey,
                "sk" to sessionKey,
                "artist" to artist.trim(),
                "track" to track.trim()
            )

            val apiSig = LastFmAuth.generateApiSignature(params, apiSecret)
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }
            formBuilder.add("api_sig", apiSig)
            formBuilder.add("format", "json")

            val request = Request.Builder()
                .url(LastFmAuth.API_ENDPOINT)
                .post(formBuilder.build())
                .header("User-Agent", LastFmAuth.USER_AGENT)
                .build()

            val response = LastFmAuth.httpClient.newCall(request).execute()
            val responseBody = response.body.string()

            if (response.isSuccessful) {
                Log.d(TAG, "Loved track on Last.fm: $artist - $track")
                Result.success(true)
            } else {
                Log.w(TAG, "loveTrack error: ${response.code} body: $responseBody")
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "loveTrack failed", e)
            Result.failure(e)
        }
    }

    /**
     * Unmarks a track from "Loved" on Last.fm.
     */
    suspend fun unloveTrack(context: Context, artist: String, track: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!LastFmPrefs.isLoggedIn(context) || !LastFmPrefs.isSyncLovedTracks(context)) {
            return@withContext Result.success(false)
        }
        if (!canPerformNetworkCall(context)) {
            return@withContext Result.failure(Exception("Network unavailable"))
        }

        try {
            val apiKey = LastFmAuth.getApiKey(context)
            val apiSecret = LastFmAuth.getApiSecret(context)
            val sessionKey = LastFmPrefs.getSessionKey(context)

            val params = mapOf(
                "method" to "track.unlove",
                "api_key" to apiKey,
                "sk" to sessionKey,
                "artist" to artist.trim(),
                "track" to track.trim()
            )

            val apiSig = LastFmAuth.generateApiSignature(params, apiSecret)
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }
            formBuilder.add("api_sig", apiSig)
            formBuilder.add("format", "json")

            val request = Request.Builder()
                .url(LastFmAuth.API_ENDPOINT)
                .post(formBuilder.build())
                .header("User-Agent", LastFmAuth.USER_AGENT)
                .build()

            val response = LastFmAuth.httpClient.newCall(request).execute()
            val responseBody = response.body.string()

            if (response.isSuccessful) {
                Log.d(TAG, "Unloved track on Last.fm: $artist - $track")
                Result.success(true)
            } else {
                Log.w(TAG, "unloveTrack error: ${response.code} body: $responseBody")
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "unloveTrack failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches user profile metadata, avatar, and total scrobble count.
     */
    suspend fun getUserInfo(context: Context, username: String): Result<LastFmUser> = withContext(Dispatchers.IO) {
        try {
            val apiKey = LastFmAuth.getApiKey(context)
            val urlBuilder = LastFmAuth.API_ENDPOINT.toHttpUrlOrNull()?.newBuilder() ?: return@withContext Result.failure(Exception("Invalid URL"))
            urlBuilder.addQueryParameter("method", "user.getInfo")
            urlBuilder.addQueryParameter("user", username.trim())
            urlBuilder.addQueryParameter("api_key", apiKey)
            urlBuilder.addQueryParameter("format", "json")

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("User-Agent", LastFmAuth.USER_AGENT)
                .build()

            val response = LastFmAuth.httpClient.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "getUserInfo HTTP error: ${response.code} body: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val userResp = LastFmAuth.json.decodeFromString<LastFmUserResponse>(responseBody)
            val user = userResp.user
            if (user != null) {
                // Save avatar & total playcount
                user.getAvatarUrl()?.let { avatarUrl ->
                    LastFmPrefs.saveAvatarUrl(context, avatarUrl)
                }
                user.playcount.toLongOrNull()?.let { count ->
                    LastFmPrefs.saveScrobbleCount(context, count)
                }
                Result.success(user)
            } else {
                val errorMsg = userResp.message ?: "Failed to fetch user info"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserInfo failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches recent tracks for the given Last.fm user.
     */
    suspend fun getRecentTracks(
        context: Context,
        username: String,
        limit: Int = 10
    ): Result<List<LastFmRecentTrack>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = LastFmAuth.getApiKey(context)
            val urlBuilder = LastFmAuth.API_ENDPOINT.toHttpUrlOrNull()?.newBuilder() ?: return@withContext Result.failure(Exception("Invalid URL"))
            urlBuilder.addQueryParameter("method", "user.getRecentTracks")
            urlBuilder.addQueryParameter("user", username.trim())
            urlBuilder.addQueryParameter("limit", limit.toString())
            urlBuilder.addQueryParameter("api_key", apiKey)
            urlBuilder.addQueryParameter("format", "json")

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("User-Agent", LastFmAuth.USER_AGENT)
                .build()

            val response = LastFmAuth.httpClient.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "getRecentTracks HTTP error: ${response.code} body: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val recentResp = LastFmAuth.json.decodeFromString<LastFmRecentTracksResponse>(responseBody)
            val tracks = recentResp.recenttracks?.track.orEmpty()
            Result.success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "getRecentTracks failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches top tracks for the given Last.fm user over a period ("overall", "7day", "1month", "12month").
     */
    suspend fun getTopTracks(
        context: Context,
        username: String,
        period: String = "7day",
        limit: Int = 15
    ): Result<List<LastFmTopTrack>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = LastFmAuth.getApiKey(context)
            val urlBuilder = LastFmAuth.API_ENDPOINT.toHttpUrlOrNull()?.newBuilder() ?: return@withContext Result.failure(Exception("Invalid URL"))
            urlBuilder.addQueryParameter("method", "user.getTopTracks")
            urlBuilder.addQueryParameter("user", username.trim())
            urlBuilder.addQueryParameter("period", period)
            urlBuilder.addQueryParameter("limit", limit.toString())
            urlBuilder.addQueryParameter("api_key", apiKey)
            urlBuilder.addQueryParameter("format", "json")

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("User-Agent", LastFmAuth.USER_AGENT)
                .build()

            val response = LastFmAuth.httpClient.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "getTopTracks HTTP error: ${response.code} body: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val topResp = LastFmAuth.json.decodeFromString<LastFmTopTracksResponse>(responseBody)
            val tracks = topResp.toptracks?.track.orEmpty()
            Result.success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "getTopTracks failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches loved tracks for the given Last.fm user.
     */
    suspend fun getLovedTracks(
        context: Context,
        username: String,
        limit: Int = 15
    ): Result<List<LastFmLovedTrack>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = LastFmAuth.getApiKey(context)
            val urlBuilder = LastFmAuth.API_ENDPOINT.toHttpUrlOrNull()?.newBuilder() ?: return@withContext Result.failure(Exception("Invalid URL"))
            urlBuilder.addQueryParameter("method", "user.getLovedTracks")
            urlBuilder.addQueryParameter("user", username.trim())
            urlBuilder.addQueryParameter("limit", limit.toString())
            urlBuilder.addQueryParameter("api_key", apiKey)
            urlBuilder.addQueryParameter("format", "json")

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("User-Agent", LastFmAuth.USER_AGENT)
                .build()

            val response = LastFmAuth.httpClient.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "getLovedTracks HTTP error: ${response.code} body: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val lovedResp = LastFmAuth.json.decodeFromString<LastFmLovedTracksResponse>(responseBody)
            val tracks = lovedResp.lovedtracks?.track.orEmpty()
            Result.success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "getLovedTracks failed", e)
            Result.failure(e)
        }
    }
}
