package com.codetrio.spatialflow.data.lyrics.providers

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.BiniLyricsApi
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import kotlin.math.abs

/**
 * Lyrics Provider for BiniLyrics (https://lyrics-api.binimum.org/)
 * Delivers TTML syllable/word and line-synced lyrics.
 */
class BiniLyricsProvider(
    private val api: BiniLyricsApi
) : LyricsProvider {

    companion object {
        private const val TAG = "BiniLyricsProvider"
    }

    override fun getName(): String = "BiniLyrics"

    override fun getPriority(): Int = 3

    override fun search(track: TrackMetadata): LyricsResult? {
        val targetDurationSec = (track.durationMs / 1000).toInt()

        val queries = listOf(
            Pair(track.cleanedArtist, track.cleanedTitle),
            Pair(track.rawArtist, track.rawTitle)
        ).distinct()

        for ((artist, title) in queries) {
            if (artist.isBlank() || title.isBlank()) continue

            try {
                val call = api.search(title = title, artist = artist)
                val response = call.execute()

                if (response.isSuccessful && response.body() != null) {
                    val searchResponse = response.body()!!
                    val results = searchResponse.results ?: emptyList()

                    if (results.isEmpty()) continue

                    // Find best candidate matching duration or first candidate
                    val bestMatch = if (targetDurationSec > 0) {
                        results.minByOrNull { item ->
                            val dur = item.duration ?: 0
                            abs(dur - targetDurationSec)
                        } ?: results.first()
                    } else {
                        results.first()
                    }

                    val downloadUrl = bestMatch.lyricsUrl
                    if (!downloadUrl.isNullOrBlank()) {
                        val dlCall = api.downloadLyrics(downloadUrl)
                        val dlResp = dlCall.execute()
                        if (dlResp.isSuccessful && dlResp.body() != null) {
                            val content = dlResp.body()!!.string()

                            if (content.isNotBlank()) {
                                val result = LyricsResult(
                                    providerName = "BiniLyrics",
                                    matchedTitle = bestMatch.track_name ?: title,
                                    matchedArtist = bestMatch.artist_name ?: artist
                                )

                                val isWordTiming = bestMatch.timing_type == "word"
                                if (content.contains("<tt") || content.contains("<p begin=") || content.contains("xmlns=")) {
                                    result.setSyncedLyrics(content)
                                    result.setWordByWord(isWordTiming || content.contains("<span") || content.contains("begin="))
                                    result.confidence = 0.94f
                                    return result
                                } else if (content.contains("[00:")) {
                                    result.setSyncedLyrics(content)
                                    result.setWordByWord(isWordTiming)
                                    result.confidence = 0.88f
                                    return result
                                } else {
                                    result.setPlainLyrics(content)
                                    result.confidence = 0.65f
                                    return result
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search failed on BiniLyrics for '$title' by '$artist': ${e.message}")
            }
        }
        return null
    }
}
