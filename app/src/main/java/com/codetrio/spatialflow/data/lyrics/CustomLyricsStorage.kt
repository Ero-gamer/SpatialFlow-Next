package com.codetrio.spatialflow.data.lyrics

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.model.SongItem
import java.io.File

/**
 * Handles persistent storage and retrieval of custom/edited/imported lyrics per track.
 */
object CustomLyricsStorage {
    private const val TAG = "CustomLyricsStorage"
    private const val PREFS_NAME = "spatial_custom_lyrics_prefs"
    private const val LYRICS_DIR_NAME = "custom_lyrics"

    private fun getStorageKey(song: SongItem): String {
        return song.videoId ?: song.path ?: song.id.toString()
    }

    private fun getLyricsDir(context: Context): File {
        val dir = File(context.filesDir, LYRICS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getLyricsFile(context: Context, song: SongItem): File {
        val key = getStorageKey(song).replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return File(getLyricsDir(context), "$key.lyrics")
    }

    fun saveLyrics(context: Context, song: SongItem, lyricsContent: String) {
        try {
            val file = getLyricsFile(context, song)
            file.writeText(lyricsContent, Charsets.UTF_8)
            
            // Mark in SharedPreferences for fast lookup
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(getStorageKey(song), true).apply()
            Log.d(TAG, "Saved custom lyrics for track ${song.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom lyrics for ${song.title}", e)
        }
    }

    fun getCustomLyrics(context: Context, song: SongItem): String? {
        try {
            val key = getStorageKey(song)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(key, false)) {
                return null
            }
            val file = getLyricsFile(context, song)
            if (file.exists()) {
                val content = file.readText(Charsets.UTF_8)
                if (content.isNotBlank()) {
                    return content
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom lyrics for ${song.title}", e)
        }
        return null
    }

    fun clearCustomLyrics(context: Context, song: SongItem) {
        try {
            val key = getStorageKey(song)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(key).apply()
            val file = getLyricsFile(context, song)
            if (file.exists()) {
                file.delete()
            }
            Log.d(TAG, "Cleared custom lyrics for track ${song.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing custom lyrics for ${song.title}", e)
        }
    }

    /**
     * Auto-scans track's parent directory for sidecar .lrc or .txt files (matching audio filename).
     */
    fun findSidecarLyrics(song: SongItem): String? {
        val audioPath = song.path ?: return null
        val audioFile = File(audioPath)
        if (!audioFile.exists()) return null

        val parentDir = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        val candidateExtensions = listOf("lrc", "qrc", "ttml", "xml", "txt", "srt", "vtt")
        for (ext in candidateExtensions) {
            val sidecar = File(parentDir, "$baseName.$ext")
            if (sidecar.exists() && sidecar.isFile) {
                try {
                    val text = sidecar.readText(Charsets.UTF_8)
                    if (text.isNotBlank()) {
                        Log.d(TAG, "Found sidecar lyrics file: ${sidecar.absolutePath}")
                        return text
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading sidecar file: ${sidecar.absolutePath}", e)
                }
            }
        }
        return null
    }
}
