package com.codetrio.spatialflow.data.lyrics

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofit API interface for Unison community lyrics endpoint.
 */
interface UnisonLyricsApi {
    @GET("lyrics")
    fun getLyrics(
        @Query("v") videoId: String,
        @Query("song") song: String,
        @Query("artist") artist: String,
        @Query("duration") durationSec: Int,
        @Query("album") album: String? = null,
        @Header("x-key-id") keyId: String? = null
    ): Call<UnisonApiResponse>
}

data class UnisonApiResponse(
    val success: Boolean? = null,
    val data: UnisonLyricData? = null
)

data class UnisonLyricData(
    val id: Long? = null,
    val videoId: String? = null,
    val song: String? = null,
    val artist: String? = null,
    val lyrics: String? = null,
    val format: String? = null,    // "ttml", "lrc", "plain"
    val syncType: String? = null,  // "richsync", "linesync", "plain"
    val effectiveScore: Int? = null,
    val voteCount: Int? = null,
    val userVote: Int? = null
)
