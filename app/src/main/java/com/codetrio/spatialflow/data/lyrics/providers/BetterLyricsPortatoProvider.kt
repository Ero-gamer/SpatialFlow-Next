package com.codetrio.spatialflow.data.lyrics.providers

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.BetterLyricsUnifiedApi
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata

/**
 * Lyrics Provider for Portato (QQ Music QRC word-by-word karaoke).
 *
 * Priority 4 (Word-level synchronization).
 */
class BetterLyricsPortatoProvider(private val context: Context) : LyricsProvider {

    companion object {
        private const val TAG = "BetterLyricsPortato"
    }

    override fun getName(): String = "Better Lyrics Portato"

    override fun getPriority(): Int = 4

    override fun search(track: TrackMetadata): LyricsResult? {
        return try {
            val bundle = BetterLyricsUnifiedApi.getBundleBlocking(context, track)
            bundle.portatoLyrics
        } catch (e: Exception) {
            Log.w(TAG, "Search failed for '${track.cleanedTitle}': ${e.message}")
            null
        }
    }
}
