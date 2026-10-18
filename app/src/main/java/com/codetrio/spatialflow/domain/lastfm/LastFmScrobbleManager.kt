package com.codetrio.spatialflow.domain.lastfm

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.data.db.LastFmScrobbleDao
import com.codetrio.spatialflow.data.db.toEntity
import com.codetrio.spatialflow.data.db.toScrobbleItem
import com.codetrio.spatialflow.data.lastfm.LastFmClient
import com.codetrio.spatialflow.data.lastfm.LastFmPrefs
import com.codetrio.spatialflow.data.lastfm.ScrobbleItem
import com.codetrio.spatialflow.model.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Last.fm telemetry, Now Playing status, playback duration accumulation,
 * scrobble threshold triggers, offline caching, and queue synchronization.
 */
@Singleton
class LastFmScrobbleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lastFmScrobbleDao: LastFmScrobbleDao
) {

    companion object {
        private const val TAG = "LastFmScrobbleManager"
        private const val MIN_TRACK_DURATION_MS = 30_000L // 30 seconds
        private const val MAX_SCROBBLE_TIME_MS = 240_000L // 4 minutes (Last.fm rule)
        private const val NOW_PLAYING_DELAY_MS = 4_000L // 4 seconds before sending Now Playing
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Current track playback tracking state
    private var currentSong: SongItem? = null
    private var currentArtist: String = ""
    private var currentTitle: String = ""
    private var currentAlbum: String? = null
    private var currentDurationMs: Long = 0L
    private var trackStartedTimestampSec: Long = 0L

    private var accumulatedListenMs: Long = 0L
    private var lastTickTimeMs: Long = 0L
    private var isNowPlayingSent: Boolean = false
    private var isScrobbled: Boolean = false
    private var nowPlayingJob: Job? = null

    private val isFlushingQueue = AtomicBoolean(false)

    /**
     * Called when a new track begins playing in AudioPlaybackService.
     */
    fun onTrackChanged(song: SongItem?) {
        nowPlayingJob?.cancel()
        nowPlayingJob = null

        // Check if previous track was eligible for scrobbling before replacing
        checkAndTriggerScrobble(forceIfEligible = false)

        currentSong = song
        if (song == null) {
            currentArtist = ""
            currentTitle = ""
            currentAlbum = null
            currentDurationMs = 0L
            accumulatedListenMs = 0L
            isNowPlayingSent = false
            isScrobbled = false
            return
        }

        currentArtist = song.artist
        currentTitle = song.title
        currentAlbum = song.albumName
        currentDurationMs = song.duration
        trackStartedTimestampSec = System.currentTimeMillis() / 1000L

        accumulatedListenMs = 0L
        lastTickTimeMs = System.currentTimeMillis()
        isNowPlayingSent = false
        isScrobbled = false

        if (!LastFmPrefs.isLoggedIn(context) || !LastFmPrefs.isScrobblingEnabled(context)) {
            return
        }

        // Schedule "Now Playing" update after 4 seconds of uninterrupted playback
        if (LastFmPrefs.isNowPlayingEnabled(context)) {
            val targetArtist = currentArtist
            val targetTitle = currentTitle
            val targetAlbum = currentAlbum
            val targetDuration = currentDurationMs / 1000L

            nowPlayingJob = scope.launch {
                delay(NOW_PLAYING_DELAY_MS)
                if (currentTitle == targetTitle && currentArtist == targetArtist) {
                    LastFmClient.updateNowPlaying(
                        context = context,
                        artist = targetArtist,
                        track = targetTitle,
                        album = targetAlbum,
                        durationSec = targetDuration
                    )
                    isNowPlayingSent = true
                }
            }
        }

        // Try flushing any pending offline queue opportunistically on track transition
        flushPendingScrobbles()
    }

    /**
     * Called periodically during active playback to accumulate actual listening time
     * and check if the scrobble threshold has been crossed.
     */
    fun onPlaybackProgress(currentPositionMs: Long, durationMs: Long) {
        if (currentSong == null || isScrobbled) return
        if (!LastFmPrefs.isLoggedIn(context) || !LastFmPrefs.isScrobblingEnabled(context)) return

        val now = System.currentTimeMillis()
        if (lastTickTimeMs > 0) {
            val delta = (now - lastTickTimeMs).coerceIn(0L, 2500L)
            accumulatedListenMs += delta
        }
        lastTickTimeMs = now

        if (durationMs > 0) {
            currentDurationMs = durationMs
        }

        checkAndTriggerScrobble(forceIfEligible = false)
    }

    /**
     * Called on playback pause / resume / stop events.
     */
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        lastTickTimeMs = if (isPlaying) System.currentTimeMillis() else 0L
    }

    /**
     * Synchronizes SpatialFlow Favorites with Last.fm Loved Tracks.
     */
    fun onTrackFavoriteChanged(song: SongItem, isFavorite: Boolean) {
        if (!LastFmPrefs.isLoggedIn(context) || !LastFmPrefs.isSyncLovedTracks(context)) return

        scope.launch {
            if (isFavorite) {
                LastFmClient.loveTrack(context, song.artist, song.title)
            } else {
                LastFmClient.unloveTrack(context, song.artist, song.title)
            }
        }
    }

    /**
     * Checks if the current track meets Last.fm scrobble criteria:
     * - Track length >= 30 seconds
     * - Played for >= threshold% (default 50%) or >= 4 minutes (240s)
     */
    private fun checkAndTriggerScrobble(forceIfEligible: Boolean) {
        if (isScrobbled || currentTitle.isBlank() || currentArtist.isBlank()) return

        val duration = currentDurationMs
        if (duration < MIN_TRACK_DURATION_MS && !forceIfEligible) return

        val thresholdPercent = LastFmPrefs.getScrobbleThresholdPercent(context)
        val percentThresholdMs = (duration * thresholdPercent) / 100L
        val requiredThresholdMs = percentThresholdMs.coerceAtMost(MAX_SCROBBLE_TIME_MS)

        if (accumulatedListenMs >= requiredThresholdMs || (forceIfEligible && accumulatedListenMs >= MIN_TRACK_DURATION_MS / 2)) {
            isScrobbled = true

            val item = ScrobbleItem(
                artist = currentArtist,
                title = currentTitle,
                album = currentAlbum,
                durationSec = duration / 1000L,
                timestampSec = trackStartedTimestampSec
            )

            scope.launch {
                executeScrobbleOrCache(item)
            }
        }
    }

    private suspend fun executeScrobbleOrCache(item: ScrobbleItem) {
        Log.i(TAG, "Submitting scrobble: ${item.artist} - ${item.title} (ts=${item.timestampSec})")
        val result = LastFmClient.scrobble(context, listOf(item))
        if (result.isFailure) {
            Log.w(TAG, "Direct scrobble failed (${result.exceptionOrNull()?.message}). Caching locally in Room.")
            lastFmScrobbleDao.insertScrobble(item.toEntity())
        }
    }

    /**
     * Flushes cached offline scrobbles in batches of up to 50 items.
     */
    fun flushPendingScrobbles() {
        if (!LastFmPrefs.isLoggedIn(context) || isFlushingQueue.get()) return

        scope.launch {
            if (!isFlushingQueue.compareAndSet(false, true)) return@launch
            try {
                val pending = lastFmScrobbleDao.getPendingScrobblesList()
                if (pending.isEmpty()) return@launch

                Log.d(TAG, "Flushing ${pending.size} cached scrobbles...")
                // Process in chunks of 50
                pending.chunked(50).forEach { chunk ->
                    val scrobbleItems = chunk.map { it.toScrobbleItem() }
                    val result = LastFmClient.scrobble(context, scrobbleItems)
                    if (result.isSuccess) {
                        val idsToDelete = chunk.map { it.id }
                        lastFmScrobbleDao.deleteScrobbles(idsToDelete)
                        Log.d(TAG, "Successfully flushed ${chunk.size} offline scrobbles")
                    } else {
                        Log.w(TAG, "Offline scrobble flush failed: ${result.exceptionOrNull()?.message}")
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during offline scrobble flush", e)
            } finally {
                isFlushingQueue.set(false)
            }
        }
    }
}
