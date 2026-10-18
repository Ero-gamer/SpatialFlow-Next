package com.codetrio.spatialflow.data.lyrics.providers

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.PaxsenixLyrics
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Kouzu MLC Lyrics Provider (https://mlc.kouzu.in/api/lyrics).
 *
 * Features:
 * - Ultra fast (<200ms) direct YouTube videoId lookup
 * - Full syllable-level word-by-word karaoke timing
 * - Fallback search endpoint (https://mlc.kouzu.in/api/search)
 */
class KouzuLyricsProvider : LyricsProvider {

    companion object {
        private const val TAG = "KouzuLyricsProvider"
        private const val BASE_URL = "https://mlc.kouzu.in/api"

        private val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override fun getName(): String = "Kouzu"
    override fun getPriority(): Int = 4 // Word-by-word karaoke provider

    override fun search(track: TrackMetadata): LyricsResult? {
        // 1. If videoId is present, attempt direct lookup
        val videoId = track.videoId?.trim()?.takeIf { it.isNotBlank() }
        if (videoId != null) {
            val result = fetchLyricsByVideoId(videoId, track.cleanedTitle, track.cleanedArtist)
            if (result != null) return result
        }

        val title = track.cleanedTitle
        val artist = track.cleanedArtist
        if (title.isBlank() || artist.isBlank()) return null

        // 2. Search Kouzu search endpoint
        try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val req = Request.Builder()
                .url("$BASE_URL/search?q=$query&limit=3")
                .header("Accept", "application/json")
                .header("User-Agent", "SpatialFlow/1.0.0")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body.string()
                    if (body.isNotBlank()) {
                        val items = JsonParser.parseString(body).asJsonArray
                        for (itemElem in items) {
                            val item = itemElem.asJsonObject
                            val foundId = item.get("id")?.asString
                            if (!foundId.isNullOrBlank()) {
                                val result = fetchLyricsByVideoId(foundId, title, artist)
                                if (result != null) return result
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search failed on Kouzu for '$title $artist': ${e.message}")
        }

        return null
    }

    private fun fetchLyricsByVideoId(videoId: String, title: String, artist: String): LyricsResult? {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/lyrics/$videoId")
                .header("Accept", "application/json")
                .header("User-Agent", "SpatialFlow/1.0.0")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body.string()
                    if (body.isNotBlank()) {
                        val lrc = parseKouzuLyricsResponse(body)
                        if (!lrc.isNullOrBlank()) {
                            val result = LyricsResult(
                                providerName = getName(),
                                matchedTitle = title,
                                matchedArtist = artist
                            )
                            result.setSyncedLyrics(lrc)
                            if (PaxsenixLyrics.isWordByWord(lrc) || lrc.contains("<")) {
                                result.setWordByWord(true)
                            }
                            Log.d(TAG, "Successfully fetched Kouzu lyrics for videoId=$videoId ($title - $artist)")
                            return result
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Kouzu lyrics for $videoId: ${e.message}")
        }
        return null
    }

    private fun parseKouzuLyricsResponse(jsonStr: String): String? {
        return runCatching {
            val root = JsonParser.parseString(jsonStr).asJsonObject
            val lyricsArr = root.getAsJsonArray("lyrics") ?: return null
            if (lyricsArr.size() == 0) return null

            val isWordType = root.get("type")?.asString?.equals("Word", ignoreCase = true) == true
            val sb = StringBuilder()

            for (i in 0 until lyricsArr.size()) {
                val lineObj = lyricsArr.get(i).asJsonObject
                val timeMs = lineObj.get("time")?.asLong ?: continue
                val timeTag = formatLrcTimestamp(timeMs, bracketed = true)
                sb.append(timeTag)

                val syllables = lineObj.getAsJsonArray("syllabus")
                if (isWordType && syllables != null && syllables.size() > 0) {
                    for (j in 0 until syllables.size()) {
                        val sylObj = syllables.get(j).asJsonObject
                        val sylText = sylObj.get("text")?.asString ?: ""
                        val sylTimeMs = sylObj.get("time")?.asLong ?: 0L
                        val sylTag = formatLrcTimestamp(sylTimeMs, bracketed = false)
                        sb.append(sylTag).append(sylText)
                    }
                } else {
                    val text = lineObj.get("text")?.asString ?: ""
                    sb.append(text)
                }
                sb.append("\n")
            }

            sb.toString().trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun formatLrcTimestamp(timeMs: Long, bracketed: Boolean): String {
        val safeTime = timeMs.coerceAtLeast(0L)
        val minutes = safeTime / 60000L
        val seconds = (safeTime % 60000L) / 1000L
        val hundredths = (safeTime % 1000L) / 10L
        val formatted = String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
        return if (bracketed) "[$formatted]" else "<$formatted>"
    }
}
