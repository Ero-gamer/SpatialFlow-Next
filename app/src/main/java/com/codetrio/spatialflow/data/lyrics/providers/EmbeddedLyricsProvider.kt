package com.codetrio.spatialflow.data.lyrics.providers

import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import java.io.File

/**
 * Reads embedded lyrics from audio containers (.mp3, .opus, .m4a) using MediaMetadataRetriever.
 * Highest trust level since lyrics come directly from the file's ID3 USLT/SYLT tags or Vorbis Comments.
 */
class EmbeddedLyricsProvider : LyricsProvider {

    companion object {
        private const val TAG = "EmbeddedLyricsProvider"
        private const val METADATA_KEY_LYRICS = 29
    }

    override fun getName(): String = "EmbeddedID3"

    override fun getPriority(): Int = 0 // Absolute highest priority — local file, no network needed

    override fun search(track: TrackMetadata): LyricsResult? {
        val targetFile = resolveAudioFile(track) ?: return null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(targetFile.absolutePath)

            var rawLyrics: String? = null
            try {
                rawLyrics = retriever.extractMetadata(METADATA_KEY_LYRICS)
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract lyrics metadata key from ${targetFile.name}: ${e.message}")
            }

            if (rawLyrics.isNullOrBlank()) {
                try {
                    val mediaInfo = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(targetFile.absolutePath)
                    val tags = mediaInfo?.mediaInformation?.tags
                    if (tags != null) {
                        val keys = tags.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            if (key.contains("lyric", ignoreCase = true) ||
                                key.equals("USLT", ignoreCase = true) ||
                                key.equals("SYLT", ignoreCase = true) ||
                                key.equals("comment", ignoreCase = true)
                            ) {
                                val valStr = tags.optString(key)
                                if (!valStr.isNullOrBlank() && (valStr.contains("\n") || valStr.contains("[") || valStr.length > 15)) {
                                    rawLyrics = valStr
                                    Log.d(TAG, "Extracted embedded lyrics via FFprobe key '$key' for ${targetFile.name}")
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "FFprobe extraction fallback failed for ${targetFile.name}", e)
                }
            }

            if (!rawLyrics.isNullOrBlank()) {
                val trimmed = rawLyrics.trim()
                val result = LyricsResult(
                    providerName = getName(),
                    matchedTitle = track.cleanedTitle.ifBlank { track.rawTitle },
                    matchedArtist = track.cleanedArtist.ifBlank { track.rawArtist },
                    matchedDuration = track.durationMs / 1000f,
                    confidence = 1.0f
                )

                val hasWordTimestamps = trimmed.contains(Regex("<\\d{2}:\\d{2}\\.\\d{2,3}>")) ||
                                        trimmed.contains(Regex("<\\d{2}:\\d{2}>"))
                val hasLineTimestamps = trimmed.contains(Regex("\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]")) ||
                                        trimmed.contains(Regex("\\[\\d{2}:\\d{2}\\]")) ||
                                        trimmed.contains("<tt") ||
                                        trimmed.contains("<p begin=")

                if (hasWordTimestamps) {
                    result.setSyncedLyrics(trimmed)
                    result.setWordByWord(true)
                    result.isSynced = true
                } else if (hasLineTimestamps) {
                    result.setSyncedLyrics(trimmed)
                    result.isSynced = true
                } else {
                    result.setPlainLyrics(trimmed)
                }

                val plainText = trimmed
                    .replace(Regex("<\\d{2}:\\d{2}(\\.\\d{2,3})?>"), "")
                    .replace(Regex("\\[\\d{2}:\\d{2}(\\.\\d{2,3})?\\]"), "")
                    .trim()
                result.plainLyrics = plainText

                Log.d(TAG, "Successfully extracted embedded lyrics for: ${targetFile.name} (synced=${result.isSynced}, wordByWord=${result.isWordByWord})")
                return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading embedded lyrics from ${targetFile.absolutePath}: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return null
    }

    private fun resolveAudioFile(track: TrackMetadata): File? {
        if (track.filePath.isNotEmpty()) {
            val file = File(track.filePath)
            if (file.exists()) return file
        }

        val cleanTitleStr = track.cleanedTitle.ifBlank { track.rawTitle }
        val cleanArtistStr = track.cleanedArtist.ifBlank { track.rawArtist }

        if (cleanTitleStr.isBlank()) return null

        val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SpatialFlow")
        val candidates = listOf(
            "$cleanTitleStr - $cleanArtistStr.mp3",
            "$cleanTitleStr - $cleanArtistStr.opus",
            "$cleanTitleStr - $cleanArtistStr.m4a",
            "$cleanTitleStr.mp3",
            "$cleanTitleStr.opus"
        )

        for (name in candidates) {
            val file = File(musicDir, name)
            if (file.exists()) return file
        }

        return null
    }
}
