package com.codetrio.spatialflow.data.lyrics.providers

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.codetrio.spatialflow.data.lyrics.UnisonLyricsApi
import com.codetrio.spatialflow.data.lyrics.security.DeviceKeyIdentityManager

/**
 * Lyrics Provider for Unison community rich-synced & syllable-synced lyrics.
 * Authenticates requests with the per-device cryptographic keyId.
 */
class UnisonLyricsProvider(
    private val context: Context,
    private val api: UnisonLyricsApi
) : LyricsProvider {

    companion object {
        private const val TAG = "UnisonLyricsProvider"
    }

    override fun getName(): String = "Unison"

    override fun getPriority(): Int = 2

    override fun search(track: TrackMetadata): LyricsResult? {
        val durationSec = (track.durationMs / 1000).toInt()
        val videoId = track.videoId ?: ""
        val album = track.album.takeIf { it.isNotBlank() }

        val queries = listOf(
            Pair(track.cleanedArtist, track.cleanedTitle),
            Pair(track.rawArtist, track.rawTitle)
        ).distinct()

        val keyId = try {
            DeviceKeyIdentityManager.getKeyId(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not obtain device keyId: ${e.message}")
            null
        }

        for ((artist, title) in queries) {
            if (artist.isBlank() || title.isBlank()) continue

            try {
                val call = api.getLyrics(
                    videoId = videoId,
                    song = title,
                    artist = artist,
                    durationSec = durationSec.coerceAtLeast(0),
                    album = album,
                    keyId = keyId
                )

                val response = call.execute()
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.data
                    if (data != null && !data.lyrics.isNullOrBlank()) {
                        val lyricsText = data.lyrics
                        val format = data.format?.lowercase() ?: "ttml"
                        val syncType = data.syncType?.lowercase() ?: "richsync"

                        val result = LyricsResult(
                            providerName = "Unison",
                            matchedTitle = data.song ?: title,
                            matchedArtist = data.artist ?: artist
                        )

                        when (format) {
                            "ttml" -> {
                                result.setSyncedLyrics(lyricsText)
                                result.setWordByWord(
                                    syncType == "richsync" ||
                                            lyricsText.contains("<span") ||
                                            lyricsText.contains("begin=")
                                )
                                result.confidence = 0.96f
                                return result
                            }
                            "lrc" -> {
                                result.setSyncedLyrics(lyricsText)
                                result.setWordByWord(syncType == "richsync")
                                result.confidence = 0.90f
                                return result
                            }
                            "plain" -> {
                                result.setPlainLyrics(lyricsText)
                                result.isSynced = false
                                result.isWordByWord = false
                                result.confidence = 0.70f
                                return result
                            }
                            else -> {
                                if (lyricsText.contains("<tt") || lyricsText.contains("[00:")) {
                                    result.setSyncedLyrics(lyricsText)
                                    result.setWordByWord(syncType == "richsync")
                                    result.confidence = 0.88f
                                } else {
                                    result.setPlainLyrics(lyricsText)
                                    result.confidence = 0.65f
                                }
                                return result
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search failed on Unison for '$title' by '$artist': ${e.message}")
            }
        }
        return null
    }
}
