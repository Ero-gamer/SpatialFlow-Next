package com.codetrio.spatialflow.data.lyrics.providers

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.BetterLyricsUnifiedApi
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata

/**
 * Lyrics Provider for "Musixmatch" via Unified SSE API stream,
 * replacing the defunct Paxsenix Musixmatch endpoint.
 *
 * Delivers word-by-word richsync and line-synced LRC.
 * Priority 5 ("musixmatch-richsync").
 */
class MusixmatchUnifiedProvider(private val context: Context) : LyricsProvider {

    companion object {
        private const val TAG = "MusixmatchUnified"
    }

    override fun getName(): String = "Musixmatch"

    override fun getPriority(): Int = 5

    override fun search(track: TrackMetadata): LyricsResult? {
        return try {
            val bundle = BetterLyricsUnifiedApi.getBundleBlocking(context, track)
            bundle.musixmatchRichSync ?: bundle.musixmatchSynced
        } catch (e: Exception) {
            Log.w(TAG, "Search failed for '${track.cleanedTitle}': ${e.message}")
            null
        }
    }
}
