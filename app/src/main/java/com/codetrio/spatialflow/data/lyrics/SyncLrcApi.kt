package com.codetrio.spatialflow.data.lyrics

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface SyncLrcApi {
    @GET("/lyrics")
    fun getLyrics(
        @Query("track") track: String,
        @Query("artist") artist: String,
        @Query("type") type: String? = null,
        @Query("album") album: String? = null,
        @Query("duration") duration: Int? = null // duration in seconds
    ): Call<SyncLrcResponse>
}
