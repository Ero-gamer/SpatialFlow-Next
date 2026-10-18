package com.codetrio.spatialflow.data.lyrics

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit API interface for BiniLyrics (https://lyrics-api.binimum.org/)
 */
interface BiniLyricsApi {
    @GET("lyrics")
    fun search(
        @Query("title") title: String,
        @Query("artist") artist: String
    ): Call<BiniLyricsSearchResponse>

    @GET
    fun downloadLyrics(@Url url: String): Call<ResponseBody>
}

data class BiniLyricsSearchResponse(
    val total: Int? = null,
    val source: String? = null,
    val results: List<BiniLyricsItem>? = null
)

data class BiniLyricsItem(
    val id: String? = null,
    val track_name: String? = null,
    val artist_name: String? = null,
    val album_name: String? = null,
    val duration: Int? = null,
    val isrc: String? = null,
    val timing_type: String? = null, // "word" or "line"
    val lyricsUrl: String? = null
)
