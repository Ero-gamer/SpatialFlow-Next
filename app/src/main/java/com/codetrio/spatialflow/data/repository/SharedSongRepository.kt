package com.codetrio.spatialflow.data.repository

import androidx.collection.LruCache
import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SharedSongMetadata(
    val shareId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val duration: Long?, // in seconds
    val provider: String,
    val providerId: String,
    val createdAt: String?
)

data class CreateShareRequest(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val duration: Long? = null, // in seconds
    val provider: String,
    val providerId: String
)

data class ShareSongResult(
    val shareId: String,
    val url: String
)

@Singleton
class SharedSongRepository @Inject constructor() {

    private val memoryCache = LruCache<String, SharedSongMetadata>(64)

    suspend fun getSharedSong(shareId: String): Result<SharedSongMetadata, DataError.Network> = withContext(Dispatchers.IO) {
        val trimmedId = shareId.trim()
        if (trimmedId.isBlank()) {
            return@withContext Result.Error(DataError.Network.BAD_REQUEST)
        }

        // Fast path: In-memory cache hit
        memoryCache[trimmedId]?.let { cached ->
            return@withContext Result.Success(cached)
        }

        val request = Request.Builder()
            .url("$BASE_URL/api/share/$trimmedId")
            .get()
            .header("Accept", "application/json")
            .build()

        try {
            sharedHttpClient.newCall(request).awaitResponse().use { response ->
                val bodyString = response.body.string()
                if (bodyString.isBlank()) {
                    return@withContext if (response.code == 404) {
                        Result.Error(DataError.Network.NOT_FOUND)
                    } else {
                        Result.Error(DataError.Network.SERVER_ERROR)
                    }
                }

                val json = try {
                    JSONObject(bodyString)
                } catch (_: Exception) {
                    return@withContext Result.Error(DataError.Network.SERIALIZATION)
                }

                val success = json.optBoolean("success", false)
                if (!response.isSuccessful || !success) {
                    val errorCode = json.optString("error", "")
                    return@withContext when {
                        response.code == 404 || errorCode == "song_not_found" -> Result.Error(DataError.Network.NOT_FOUND)
                        response.code == 400 || errorCode == "invalid_share_id" -> Result.Error(DataError.Network.BAD_REQUEST)
                        response.code == 429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
                        else -> Result.Error(DataError.Network.SERVER_ERROR)
                    }
                }

                val songObj = json.optJSONObject("song")
                    ?: return@withContext Result.Error(DataError.Network.SERIALIZATION)

                val metadata = SharedSongMetadata(
                    shareId = songObj.optString("shareId", trimmedId),
                    title = songObj.optString("title", "Unknown Title"),
                    artist = songObj.optString("artist", "Unknown Artist"),
                    album = songObj.optString("album").takeIf { it.isNotBlank() && it != "null" },
                    artworkUrl = songObj.optString("artwork_url").takeIf { it.isNotBlank() && it != "null" },
                    duration = if (songObj.has("duration") && !songObj.isNull("duration")) songObj.optLong("duration") else null,
                    provider = songObj.optString("provider", "youtube"),
                    providerId = songObj.optString("provider_id", ""),
                    createdAt = songObj.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                )

                memoryCache.put(trimmedId, metadata)
                Result.Success(metadata)
            }
        } catch (_: IOException) {
            Result.Error(DataError.Network.NO_INTERNET)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    suspend fun createShare(req: CreateShareRequest): Result<ShareSongResult, DataError.Network> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("title", req.title)
            put("artist", req.artist)
            req.album?.takeIf { it.isNotBlank() }?.let { put("album", it) }
            req.artworkUrl?.takeIf { it.isNotBlank() }?.let { put("artwork_url", it) }
            req.duration?.takeIf { it > 0 }?.let { put("duration", it) }
            put("provider", req.provider)
            put("provider_id", req.providerId)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$BASE_URL/api/share")
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()

        try {
            sharedHttpClient.newCall(request).awaitResponse().use { response ->
                val bodyString = response.body.string()
                if (bodyString.isBlank()) {
                    return@withContext Result.Error(DataError.Network.SERVER_ERROR)
                }

                val json = try {
                    JSONObject(bodyString)
                } catch (_: Exception) {
                    return@withContext Result.Error(DataError.Network.SERIALIZATION)
                }

                val success = json.optBoolean("success", false)
                if (!response.isSuccessful || !success) {
                    val errorCode = json.optString("error", "")
                    return@withContext when {
                        response.code == 400 || errorCode == "validation_error" -> Result.Error(DataError.Network.BAD_REQUEST)
                        response.code == 429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
                        else -> Result.Error(DataError.Network.SERVER_ERROR)
                    }
                }

                val shareId = json.optString("shareId")
                val url = json.optString("url")

                if (shareId.isBlank() || url.isBlank()) {
                    return@withContext Result.Error(DataError.Network.SERIALIZATION)
                }

                Result.Success(ShareSongResult(shareId = shareId, url = url))
            }
        } catch (_: IOException) {
            Result.Error(DataError.Network.NO_INTERNET)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    companion object {
        val BASE_URL: String
            get() = com.codetrio.spatialflow.BuildConfig.SHARE_API_URL.ifBlank { "https://spatialflowshare.vercel.app" }
        const val SHARE_HOST = "spatialflowshare.vercel.app"

        private val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        private suspend inline fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            })
        }
    }
}
