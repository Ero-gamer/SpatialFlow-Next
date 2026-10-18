package com.codetrio.spatialflow.data.repository

import android.content.Context
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.Result
import com.codetrio.spatialflow.domain.repository.StreamRepository
import kotlinx.coroutines.CancellationException

class InnerTubeStreamRepository(
    private val context: Context? = null
) : StreamRepository {
    override suspend fun getStreamUrl(
        videoId: String,
        forceHighQuality: Boolean,
        title: String?,
        artist: String?,
        isrc: String?,
        overridePreferOpus: Boolean?,
        isRetry: Boolean,
        durationMs: Long?
    ): Result<String, DataError.Network> {
        return try {
            val result = YouTubeMusic.getStreamUrl(
                videoId = videoId,
                forceHighQuality = forceHighQuality,
                title = title,
                artist = artist,
                isrc = isrc,
                overridePreferOpus = overridePreferOpus,
                isRetry = isRetry,
                durationMs = durationMs
            )
            val url = result.getOrNull()
            if (url != null) {
                Result.Success(url)
            } else {
                Result.Error(DataError.Network.NOT_FOUND)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun getPlaybackPosition(videoId: String): Result<Long, DataError.Network> {
        return try {
            val result = YouTubeMusic.getPlaybackPosition(videoId)
            val pos = result.getOrNull()
            if (pos != null) {
                Result.Success(pos)
            } else {
                Result.Error(DataError.Network.NOT_FOUND)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
