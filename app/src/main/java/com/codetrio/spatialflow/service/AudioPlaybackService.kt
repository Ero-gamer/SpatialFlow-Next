@file:Suppress("DEPRECATION")

package com.codetrio.spatialflow.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.util.AudioFileManager
import com.codetrio.spatialflow.util.FFmpegCommandBuilder
import com.codetrio.spatialflow.util.PlaybackStateManager
import com.codetrio.spatialflow.util.SongDownloader
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    @Inject
    lateinit var cacheDataSourceFactory: androidx.media3.datasource.cache.CacheDataSource.Factory

    @Inject
    lateinit var playbackDataSourceFactory: com.codetrio.spatialflow.data.sourcepool.SchemeRoutingDataSource.Factory

    @Inject
    lateinit var streamRepository: com.codetrio.spatialflow.domain.repository.StreamRepository

    @Inject
    lateinit var lastFmScrobbleManager: com.codetrio.spatialflow.domain.lastfm.LastFmScrobbleManager

    companion object {
        private const val TAG = "AudioPlaybackService"
        
        const val ACTION_PLAY = "com.codetrio.spatialflow.ACTION_PLAY"
        const val ACTION_PAUSE = "com.codetrio.spatialflow.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.codetrio.spatialflow.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.codetrio.spatialflow.ACTION_NEXT"
        const val ACTION_TOGGLE_LOOP = "com.codetrio.spatialflow.ACTION_TOGGLE_LOOP"
        const val ACTION_CYCLE_PLAYBACK_MODE = "com.codetrio.spatialflow.ACTION_CYCLE_PLAYBACK_MODE"
        const val ACTION_TOGGLE_FAV = "com.codetrio.spatialflow.ACTION_TOGGLE_FAV"

        private const val PREFS_NAME = "AppSettings"
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_AUDIO_FOCUS = "audio_focus"
        private const val KEY_DISCONNECT_ACTION = "disconnect_action"
        private const val KEY_RESUME_ON_CONNECT = "resume_on_connect"

        val CMD_TOGGLE_LOOP = SessionCommand(ACTION_TOGGLE_LOOP, Bundle.EMPTY)
        val CMD_CYCLE_PLAYBACK_MODE = SessionCommand(ACTION_CYCLE_PLAYBACK_MODE, Bundle.EMPTY)
        val CMD_TOGGLE_FAV = SessionCommand(ACTION_TOGGLE_FAV, Bundle.EMPTY)

        @Volatile
        var currentInstance: AudioPlaybackService? = null
            internal set

        internal fun computeCrossfadeDurationMs(
            crossfadeEnabled: Boolean,
            crossfadeMode: String,
            configuredDurationSec: Float,
            isBitPerfect: Boolean
        ): Int = CrossfadeGuard.computeCrossfadeDurationMs(
            crossfadeEnabled = crossfadeEnabled,
            crossfadeMode = crossfadeMode,
            configuredDurationSec = configuredDurationSec,
            isBitPerfect = isBitPerfect
        )

        internal fun shouldExecuteCrossfade(
            crossfadeDurationMs: Int,
            isBitPerfectProvider: () -> Boolean,
            onBypassed: (() -> Unit)? = null
        ): Boolean = CrossfadeGuard.shouldExecuteCrossfade(
            crossfadeDurationMs = crossfadeDurationMs,
            isBitPerfectProvider = isBitPerfectProvider,
            onBypassed = onBypassed
        )
    }

    private val binder: IBinder = LocalBinder()
    private val serviceScope = MainScope()
    internal lateinit var playerManager: CrossfadePlayerManager
    internal val player: ExoPlayer get() = playerManager.player

    private var mediaSession: MediaSession? = null
    internal var viewModel: PlayerSharedViewModel? = null
    private lateinit var handler: Handler
    private lateinit var progressUpdateRunnable: Runnable
    private val effectsExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val appSettings by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private var viewModelObservationJob: Job? = null
    private var persistenceJob: Job? = null
    private var lastPersistedMs: Long = 0L
    private var cachedAudioFormat: String = ""

    val queuePreloader by lazy {
        QueuePreloader(applicationContext, cacheDataSourceFactory, serviceScope)
    }

    val artworkPreloader by lazy {
        com.codetrio.spatialflow.util.ArtworkPreloader(applicationContext, serviceScope)
    }

    val hapticManager by lazy {
        com.codetrio.spatialflow.util.PlayerHapticManager(applicationContext)
    }

    fun cancelInFlightLoads() {
        preloadJob?.cancel()
        precacheJob?.cancel()
        queuePreloader.cancel()
        artworkPreloader.cancel()
    }

    private var artworkRequestToken: Long = 0L
    private var artworkPrepareToken: Long = 0L
    private var activeGlideTarget: com.bumptech.glide.request.target.CustomTarget<Bitmap>? = null
    private var activeGlideUrl: String? = null
    private var currentArtworkIdentity: String? = null
    private val streamRetryAttempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    @Volatile private var consecutivePlaybackFailures: Int = 0

    private var notificationProvider: CustomMediaNotificationProvider? = null

    // Scalar state tracking for notification deduplication (Artwork / Notification Memory Rule)
    private var lastNotifTitle: String? = null
    private var lastNotifArtist: String? = null
    private var lastNotifArtworkIdentity: String? = null
    private var lastNotifIsPlaying: Boolean? = null
    private var lastNotifRepeatMode: Int? = null
    private var lastNotifShuffleState: Boolean? = null
    private var lastNotifIsFav: Boolean? = null

    // Pre-created IconCompat buttons created exactly once per service lifecycle (Icon Cache Rule)
    private val iconCache by lazy {
        mapOf(
            "shuffle" to Icons.Rounded.Shuffle.toIconCompat(this),
            "repeat" to Icons.Rounded.Repeat.toIconCompat(this),
            "repeat_one" to Icons.Rounded.RepeatOne.toIconCompat(this),
            "favorite" to Icons.Rounded.Favorite.toIconCompat(this),
            "favorite_border" to Icons.Rounded.FavoriteBorder.toIconCompat(this),
            "play" to Icons.Rounded.PlayArrow.toIconCompat(this),
            "pause" to Icons.Rounded.Pause.toIconCompat(this),
            "next" to Icons.Rounded.SkipNext.toIconCompat(this),
            "previous" to Icons.Rounded.SkipPrevious.toIconCompat(this)
        )
    }

    private var currentSourceUri: Uri? = null
    private var originalSourceUri: Uri? = null
    private var isProcessing = false
    private var isNextSongPreCached = false
    var currentSong: SongItem? = null
        private set
    private var currentSongName = "SpatialFlow"
    private var currentArtist = "Unknown Artist"
    private var currentAlbumArt: Bitmap? = null
    private var notificationAlbumArt: Bitmap? = null
    private var is8DEnabled = false
    private var hasProcessed8D = false
    private var currentlyLoadedPath: String? = null
    private var shouldAutoPlay = false
    private var pendingSeekPos: Long = -1
    
    // Instant cancellation of stale stream resolutions — prevents race conditions on rapid clicks
    private var resolveJob: Job? = null
    // Track the unique song ID that the current resolution is targeting
    private var resolveTargetSongId: String? = null
    private var isFetchingRelatedProactively = false

    private lateinit var telemetryManager: com.codetrio.spatialflow.data.innertube.YouTubeTelemetryManager

    private var audioFocusEnabled = true
    @Volatile internal var hapticPauseInBackground = true
    private var autoplayEnabled = true
    private var disconnectAction = "pause"
    private var resumeOnConnect = false
    private var audioDeviceReceiver: BroadcastReceiver? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPausedByFocusLoss = false
    private var audioManager: AudioManager? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus GAIN: wasPausedByFocusLoss=$wasPausedByFocusLoss")
                if (!isCrossfading) {
                    player.volume = currentBaseVolume
                }
                if (wasPausedByFocusLoss) {
                    play()
                    wasPausedByFocusLoss = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus LOSS: pausing playback")
                wasPausedByFocusLoss = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus LOSS_TRANSIENT: pausing playback")
                if (player.isPlaying || (nextPlayer?.isPlaying == true)) {
                    wasPausedByFocusLoss = true
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus LOSS_TRANSIENT_CAN_DUCK: ducking volume")
                if (player.isPlaying) {
                    player.volume = currentBaseVolume * 0.2f
                }
                if (nextPlayer?.isPlaying == true) {
                    nextPlayer?.volume = currentBaseVolume * 0.2f
                }
            }
        }
    }


    private var equalizerEffect: Equalizer?
        get() = if (::playerManager.isInitialized) playerManager.equalizerEffect else null
        set(value) { if (::playerManager.isInitialized) playerManager.equalizerEffect = value }
    private var bassBoostEffect: BassBoost?
        get() = if (::playerManager.isInitialized) playerManager.bassBoostEffect else null
        set(value) { if (::playerManager.isInitialized) playerManager.bassBoostEffect = value }
    private var loudnessEnhancer: LoudnessEnhancer?
        get() = if (::playerManager.isInitialized) playerManager.loudnessEnhancer else null
        set(value) { if (::playerManager.isInitialized) playerManager.loudnessEnhancer = value }
    private var lastEffectsSessionId: Int
        get() = if (::playerManager.isInitialized) playerManager.lastEffectsSessionId else -1
        set(value) { if (::playerManager.isInitialized) playerManager.lastEffectsSessionId = value }

    private val mainProcessor get() = playerManager.mainProcessor
    private val mainReverbProcessor get() = playerManager.mainReverbProcessor
    private val nextProcessor get() = playerManager.nextProcessor
    private val nextReverbProcessor get() = playerManager.nextReverbProcessor

    private var crossfadeDurationMs: Int
        get() = if (::playerManager.isInitialized) playerManager.crossfadeDurationMs else 0
        set(value) { if (::playerManager.isInitialized) playerManager.crossfadeDurationMs = value }
    private val isCrossfading: Boolean
        get() = if (::playerManager.isInitialized) playerManager.isCrossfading else false
    private val crossfadeNextSongStarted: Boolean
        get() = if (::playerManager.isInitialized) playerManager.crossfadeNextSongStarted else false
    private val nextPlayer: ExoPlayer?
        get() = if (::playerManager.isInitialized) playerManager.nextPlayer else null

    private var currentBaseVolume: Float
        get() = if (::playerManager.isInitialized) playerManager.currentBaseVolume else 1.0f
        set(value) { if (::playerManager.isInitialized) playerManager.currentBaseVolume = value }

    private var reverbIntensity: Float = 1.0f
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var preloadJob: Job? = null


    private val mainPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            viewModel?.postIsPlaying(isPlaying)
            telemetryManager.onPlaybackStateChanged(isPlaying)
            if (::lastFmScrobbleManager.isInitialized) {
                lastFmScrobbleManager.onPlaybackStateChanged(isPlaying)
            }
            if (isPlaying) {
                handler.removeCallbacks(progressUpdateRunnable)
                handler.post(progressUpdateRunnable)
                startPersistenceJob()
            } else {
                handler.removeCallbacks(progressUpdateRunnable)
                persistenceJob?.cancel()
                persistenceJob = null
            }
            updateMediaSessionCustomLayout()
            refreshNotification()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            viewModel?.let { vm ->
                if (vm.isShuffleEnabled.value != shuffleModeEnabled) {
                    vm.setShuffleEnabled(shuffleModeEnabled)
                }
            }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            // Only invalidate UI cache — resolvedStreamFormatTag stays intact.
            // getActiveAudioFormat() will re-derive from Tier 1 (resolved tag) first.
            invalidateAudioFormatCache()
            val fmt = getActiveAudioFormat()
            if (fmt.isNotEmpty()) viewModel?.updatePlaybackFormat(fmt)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPlaybackStateChanged(state: Int) {
            val isPlayingState = player.playWhenReady && state == Player.STATE_READY
            viewModel?.postIsPlaying(isPlayingState)

            if (state == Player.STATE_BUFFERING) {
                viewModel?.postIsProcessing(true)
                startBufferStallWatchdog()
            } else {
                cancelBufferStallWatchdog()
                if (state == Player.STATE_READY || state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                    viewModel?.postIsProcessing(false)
                }
            }

            if (state == Player.STATE_READY) {
                consecutivePlaybackFailures = 0
                viewModel?.setPlaybackReady(true)
                val activeVid = getCurrentVideoId()
                if (!activeVid.isNullOrEmpty()) {
                    streamRetryAttempts.remove(activeVid)
                }
                if (currentStreamContentLength > 0 && player.duration > 0) {
                    computeAndApplyRealBitrate(currentStreamContentLength, player.duration)
                }
                // Re-derive format from authoritative tag (Tier 1) or ExoPlayer (Tier 2)
                invalidateAudioFormatCache()
                val fmt = getActiveAudioFormat()
                if (fmt.isNotEmpty()) viewModel?.updatePlaybackFormat(fmt)
                if (pendingSeekPos >= 0) {
                    player.seekTo(pendingSeekPos)
                    pendingSeekPos = -1
                }
                initializeAudioEffectsSafe()
                
                // Preload next track only after current playback has reached STATE_READY and stabilized
                handler.postDelayed({
                    if (player.playbackState == Player.STATE_READY) {
                        preloadNextSongIntoQueue()
                    }
                }, 1200)
            }
            
            if (state == Player.STATE_ENDED) {
                handlePlaybackCompleted()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            viewModel?.postIsProcessing(false)
            Log.e(TAG, "ExoPlayer error: [${error.errorCodeName}] code=${error.errorCode}: ${error.message}", error)
            
            // --- LOCAL AUDIO FALLBACK RECOVERY ---
            val curUri = currentSourceUri
            val currentSong = viewModel?.currentSong?.value
            val directPath = currentSong?.path
            if (curUri != null && curUri.scheme == "content" && !directPath.isNullOrBlank()) {
                val file = File(directPath)
                if (file.exists() && file.canRead()) {
                    Log.w(TAG, "Content URI failed playback ($curUri). Retrying with direct file: $directPath")
                    val fileUri = Uri.fromFile(file)
                    currentSourceUri = fileUri
                    loadAndPlay(fileUri)
                    return
                }
            }

            // --- EXPIRED STREAM URL RECOVERY & PLAYBACK RESILIENCE ---
            var currentCause: Throwable? = error.cause
            var isRecoverableStreamError = when (error.errorCode) {
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> true
                else -> false
            }
            var httpException: androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException? = null
            while (currentCause != null) {
                if (currentCause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                    isRecoverableStreamError = true
                    httpException = currentCause
                    break
                }
                if (currentCause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException ||
                    currentCause is androidx.media3.common.ParserException ||
                    currentCause is java.io.IOException) {
                    isRecoverableStreamError = true
                    break
                }
                currentCause = currentCause.cause
            }

            if (isRecoverableStreamError) {
                val currentPos = player.currentPosition
                val originalUri = originalSourceUri
                val videoId = originalUri?.host
                if (originalUri != null && originalUri.scheme == "innertube" && !videoId.isNullOrEmpty()) {
                    val attempts = streamRetryAttempts.getOrDefault(videoId, 0)
                    if (attempts < 2) {
                        streamRetryAttempts[videoId] = attempts + 1
                        val errDetail = httpException?.responseCode?.let { "HTTP $it" } ?: (error.localizedMessage ?: "Stream Error")
                        Log.w(TAG, "Streaming URL failed ($errDetail) [Attempt ${attempts + 1}/2]. Re-resolving SAME SONG via fallback YouTube Music for $videoId...")

                        // Blacklist failing URL so LosslessStreamResolver never returns it again
                        currentSourceUri?.toString()?.let { failedUrl ->
                            com.codetrio.spatialflow.data.sourcepool.LosslessStreamResolver.markUrlFailed(failedUrl)
                        }

                        // Invalidate YouTubeMusic, Lossless, and NewPipe caches
                        YouTubeMusic.clearVideoCache(videoId)
                        com.codetrio.spatialflow.data.sourcepool.LosslessStreamResolver.invalidate(videoId)
                        com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor.clearVideoCache(videoId)
                        val fallbackProvider = "YouTube Music (NewPipe Opus/AAC)"
                        YouTubeMusic.setResolvedProvider(videoId, fallbackProvider)
                        setResolvedStreamProvider(fallbackProvider)

                        // Crucial: Invalidate QueuePreloader so it doesn't return the stale 403/404 URI!
                        val targetSong = viewModel?.songList?.value?.find { it.videoId == videoId || it.id.toString() == videoId }
                                         ?: viewModel?.currentSong?.value
                        targetSong?.let { queuePreloader.invalidate(it.id) }

                        val wasPlaying = player.playWhenReady
                        if (wasPlaying) {
                            loadAndPlay(originalUri, currentPos)
                        } else {
                            loadAudio(originalUri, currentPos)
                        }
                        return // Bypass skip-to-next fallback
                    } else {
                        Log.e(TAG, "Exceeded retry limit for $videoId.")
                        streamRetryAttempts.remove(videoId)
                    }
                }
            }
            // ------------------------------------

            viewModel?.setIsPlaying(false)
            
            // Auto-recovery: reset player and check circuit breaker before skipping
            try {
                player.stop()
                player.clearMediaItems()
            } catch (e: Exception) {
                Log.w(TAG, "Player reset during error recovery failed: ${e.message}")
            }

            consecutivePlaybackFailures++
            if (consecutivePlaybackFailures >= 3) {
                Log.e(TAG, "Playback circuit breaker tripped: $consecutivePlaybackFailures consecutive failures. Halting auto-skip.")
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Playback paused: Unable to stream tracks")
                viewModel?.postIsProcessing(false)
                return
            }

            handler.postDelayed({
                viewModel?.playNextSong(true)
            }, 600)
        }

    private var bufferStallWatchdogJob: Job? = null

    private fun startBufferStallWatchdog() {
        bufferStallWatchdogJob?.cancel()
        bufferStallWatchdogJob = serviceScope.launch {
            delay(8000L)
            if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
                val currentMedia = originalSourceUri?.host
                Log.w(TAG, "⚡ Initial buffer stalled for $currentMedia; invalidating cache and recovering playback")
                if (!currentMedia.isNullOrEmpty()) {
                    YouTubeMusic.clearVideoCache(currentMedia)
                }
                viewModel?.postIsProcessing(false)
                originalSourceUri?.let { uri ->
                    loadAndPlay(uri)
                }
            }
        }
    }

    private fun cancelBufferStallWatchdog() {
        bufferStallWatchdogJob?.cancel()
        bufferStallWatchdogJob = null
    }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                Log.d(TAG, "Auto-transition detected natively")
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.i("MEDIA_CHANGE", "reason=$reason index=${player.currentMediaItemIndex} id=${mediaItem?.mediaId}")
            com.codetrio.spatialflow.util.ConsoleLogger.player("Track Transition: reason=$reason index=${player.currentMediaItemIndex} id=${mediaItem?.mediaId}")
            streamRetryAttempts.clear()
            applyEffectsFromViewModelAsync()
            // Song changed: clear both tiers so the new song gets fresh resolution
            clearFormatForSongChange()
            val fmt = getActiveAudioFormat()
            if (fmt.isNotEmpty()) viewModel?.updatePlaybackFormat(fmt)
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                Log.d(TAG, "⚡ NATIVE GAPLESS TRANSITION COMPLETED! Resynchronizing UI.")
                
                // Sleep Timer Interception flags
                var interceptForSleepTimer = false
                viewModel?.let { vm ->
                    if (vm.sleepTimerMode.value == PlayerSharedViewModel.SleepTimerMode.END_OF_SONG) {
                        interceptForSleepTimer = true
                    }
                    if (vm.sleepTimerMode.value == PlayerSharedViewModel.SleepTimerMode.END_OF_QUEUE) {
                        if (vm.currentSongIndex.value == vm.songList.value.size - 1) {
                            interceptForSleepTimer = true
                        }
                    }
                }

                // Maintain a 2-item sliding window queue: 
                // Safely excise the deprecated previous item to purge expired URL reference and free heap memory.
                if (player.mediaItemCount > 1) {
                    player.removeMediaItem(0)
                }

                // Use mediaId-based resync for accuracy in Shuffle Mode
                val transitionedMediaId = mediaItem?.mediaId
                viewModel?.let { vm ->
                    val songs = vm.songList.value
                    
                    // Find exact match by mediaId (stable song identifier)
                    val matchedIdx = if (!transitionedMediaId.isNullOrEmpty()) {
                        songs.indexOfFirst { it.id.toString() == transitionedMediaId }
                    } else {
                        -1
                    }
                    
                    val nextIdx = if (matchedIdx >= 0) matchedIdx else vm.getNextSongIndex()
                    if (nextIdx >= 0) {
                        vm.updateSongIndexOnly(nextIdx)
                        
                        songs.getOrNull(nextIdx)?.let { song ->
                            this@AudioPlaybackService.currentSong = song
                            currentSongName = song.title
                            currentArtist = song.artist
                            if (song.thumbnailUrl != null) {
                                setSongMetadataByUrl(song.title, song.thumbnailUrl, song.id.toString(), song.artist)
                            } else {
                                setSongMetadataById(song.title, song.albumId, song.artist)
                            }
                            if (!song.videoId.isNullOrEmpty()) {
                                telemetryManager.onSongChanged(song.videoId!!, song.duration)
                            }
                            if (::lastFmScrobbleManager.isInitialized) {
                                lastFmScrobbleManager.onTrackChanged(song)
                            }
                            PlaybackStateManager.saveState(applicationContext, song, 0L, songs, nextIdx)
                        }
                        
                        if (interceptForSleepTimer) {
                            pause()
                            vm.setSleepTimerMode(PlayerSharedViewModel.SleepTimerMode.OFF)
                        }

                        updateBaseVolume()
                        handler.postDelayed({ 
                            preloadNextSongIntoQueue() 
                            vm.checkQueueAndFetchMore()
                        }, 300)
                    }
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        currentInstance = this
        handler = Handler(Looper.getMainLooper())
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        telemetryManager = com.codetrio.spatialflow.data.innertube.YouTubeTelemetryManager(
            context = this,
            httpClient = com.codetrio.spatialflow.data.innertube.InnerTubeClient.httpClient,
            clientProvider = { com.codetrio.spatialflow.data.innertube.InnerTubeClient }
        )
        
        initializePlayer()
        setupMediaSession()
        loadAudioPreferences()
        setupPreferenceListener()
        registerAudioDeviceReceiver()
        setupProgressTracking()
        cleanCache()

        com.codetrio.spatialflow.service.dac.UsbDacManager.init(this)
        com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.init(this)
        fixedLineOutVolumeManager.restoreAtStartup(com.codetrio.spatialflow.service.dac.UsbDacManager.isDacConnected.value)
        serviceScope.launch {
            telemetryProcessor.pipelineStats.collect { stats ->
                com.codetrio.spatialflow.service.dac.UsbDacManager.updatePipelineStats(stats)
            }
        }
        serviceScope.launch {
            com.codetrio.spatialflow.service.dac.UsbDacManager.isDacConnected.collect {
                loadAudioPreferences()
                checkAndApplyDacRouting()
            }
        }
        serviceScope.launch {
            com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.isAtmosActive.collect { isAtmosActive ->
                applySpatialAndDspRouting(isAtmosActive)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_NEXT -> playNext()
                ACTION_TOGGLE_LOOP -> {
                    viewModel?.toggleLoopMode()

                }
                ACTION_CYCLE_PLAYBACK_MODE -> {
                    cycleNotificationPlaybackMode()
                }
                ACTION_TOGGLE_FAV -> {
                    viewModel?.toggleFavorite()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        fixedLineOutVolumeManager.restoreSavedVolume()
        if (mediaSession?.player?.playWhenReady == false) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    fun dismissService() {
        player.stop()
        player.clearMediaItems()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        fixedLineOutVolumeManager.restoreSavedVolume()
        if (currentInstance == this) {
            currentInstance = null
        }
        if (::playerManager.isInitialized && player.isPlaying) {
            val pos = player.currentPosition
            if (pos > 0) {
                PlaybackStateManager.savePosition(applicationContext, pos)
            }
        }
        unregisterAudioDeviceReceiver()
        abandonAudioFocus()
        broadcastAudioEffectSession(false)
        com.codetrio.spatialflow.service.dac.UsbDacManager.clearMixerConfig()
        com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.release()
        try { hapticManager.release() } catch (_: Exception) {}
        prefListener?.let {
            appSettings.unregisterOnSharedPreferenceChangeListener(it)
        }
        handler.removeCallbacksAndMessages(null)
        
        viewModelObservationJob?.cancel()
        viewModelObservationJob = null
        resolveJob?.cancel()
        resolveJob = null
        preloadJob?.cancel()
        preloadJob = null
        precacheJob?.cancel()
        precacheJob = null
        persistenceJob?.cancel()
        persistenceJob = null
        
        activeGlideTarget?.let { target ->
            try { com.bumptech.glide.Glide.with(applicationContext).clear(target) } catch (_: Exception) {}
        }
        activeGlideTarget = null
        activeGlideUrl = null
        currentArtworkIdentity = null
        notificationProvider = null
        invalidateArtworkBytes()

        playerManager.release()
        viewModel?.cleanupBgScope()
        mediaSession?.release()
        effectsExecutor.shutdownNow()
        serviceScope.cancel()
    }

    private fun initializePlayer() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        audioFocusEnabled = prefs.getBoolean(KEY_AUDIO_FOCUS, true)
        Log.d(TAG, "initializePlayer: audioFocusEnabled=$audioFocusEnabled")
        playerManager = CrossfadePlayerManager(this, serviceScope) { proc, rev, eightD, tel ->
            buildExoPlayer(proc, rev, eightD, tel, false)
        }
        player.addListener(mainPlayerListener)
        checkAndApplyDacRouting()
    }

    val telemetryProcessor: com.codetrio.spatialflow.service.dac.AudioTelemetryProcessor
        get() = playerManager.mainTelemetryProcessor

    private fun applySpatialAndDspRouting(isAtmosActive: Boolean) {
        val shouldBypassDsp = if (::playerManager.isInitialized) playerManager.isDspBypassed else false
        if (shouldBypassDsp) {
            playerManager.setVirtualizerEnabled(false)
            broadcastAudioEffectSession(false)
            return
        }
        playerManager.setVirtualizerEnabled(isAtmosActive)
        broadcastAudioEffectSession(isAtmosActive)
        Log.d(TAG, "Dolby Atmos spatial routing updated: isAtmosActive=$isAtmosActive")
    }

    private fun broadcastAudioEffectSession(enabled: Boolean) {
        val shouldBypassDsp = if (::playerManager.isInitialized) playerManager.isDspBypassed else false
        val finalEnabled = enabled && !shouldBypassDsp
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            val action = if (finalEnabled) {
                android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
            } else {
                android.media.audiofx.AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
            }
            val intent = Intent(action).apply {
                putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
            }
            sendBroadcast(intent)
            Log.d(TAG, "Broadcasted AudioEffect session: action=$action, sessionId=$sessionId, dspBypassed=$shouldBypassDsp")
        } catch (e: Exception) {
            Log.w(TAG, "Failed broadcasting AudioEffect session: ${e.message}")
        }
    }

    fun setPlaybackMode(mode: com.codetrio.spatialflow.util.PlaybackMode) {
        loadAudioPreferences()
        checkAndApplyDacRouting()
        val dacConnected = com.codetrio.spatialflow.service.dac.UsbDacManager.isDacConnected.value
        if (mode == com.codetrio.spatialflow.util.PlaybackMode.BIT_PERFECT && dacConnected) {
            player.volume = 1.0f
            playerManager.nextPlayer?.volume = 1.0f
        } else {
            applyCurrentBaseVolume()
        }
        val currentSource = com.codetrio.spatialflow.service.dac.UsbDacManager.liveSourceStats.value
        if (currentSource.sampleRateHz > 0) {
            com.codetrio.spatialflow.service.dac.UsbDacManager.updateTrackFormat(
                currentSource.sampleRateHz,
                currentSource.bitDepth,
                currentSource.channelCount
            )
        } else {
            com.codetrio.spatialflow.service.dac.UsbDacManager.inspectConnectedDevices()
        }
    }

    internal val fixedLineOutVolumeManager by lazy {
        com.codetrio.spatialflow.service.dac.FixedLineOutVolumeManager(
            prefs = appSettings,
            setStreamVolume = { stream, index, flags ->
                (getSystemService(AUDIO_SERVICE) as? AudioManager)?.setStreamVolume(stream, index, flags)
            },
            getStreamVolume = { stream ->
                (getSystemService(AUDIO_SERVICE) as? AudioManager)?.getStreamVolume(stream) ?: 0
            },
            getStreamMaxVolume = { stream ->
                (getSystemService(AUDIO_SERVICE) as? AudioManager)?.getStreamMaxVolume(stream) ?: 15
            }
        )
    }

    internal fun applyDacVolumeSettings() {
        if (!::playerManager.isInitialized) return
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        val dacConnected = com.codetrio.spatialflow.service.dac.UsbDacManager.isDacConnected.value
        val isFixedVolume = dacConnected && com.codetrio.spatialflow.util.QualityModeSelector.isDacFixedVolumeEnabled(this)

        if (isFixedVolume) {
            fixedLineOutVolumeManager.applyFixedVolume {
                player.volume = 1.0f
                playerManager.nextPlayer?.volume = 1.0f
            }
        } else {
            fixedLineOutVolumeManager.restoreSavedVolume()
            applyCurrentBaseVolume()
        }
    }

    private data class DacRoutingState(
        val isDirectUsbBound: Boolean,
        val isDspBypassed: Boolean,
        val activeList: List<String>,
        val dacDeviceId: Int? = null
    )

    private var lastDacRoutingState: DacRoutingState? = null

    internal fun checkAndApplyDacRouting() {
        if (!::playerManager.isInitialized) return
        val dacConnected = com.codetrio.spatialflow.service.dac.UsbDacManager.isDacConnected.value
        val dacDirectEnabled = com.codetrio.spatialflow.util.QualityModeSelector.isDacDirectModeEnabled(this)
        val bitPerfectEnabled = com.codetrio.spatialflow.util.QualityModeSelector.isDacBitPerfectEnabled(this)
        val usbExclusiveEnabled = com.codetrio.spatialflow.util.QualityModeSelector.isUsbExclusiveModeEnabled(this)
        val playbackMode = com.codetrio.spatialflow.util.QualityModeSelector.getPlaybackMode(this)
        // Bit-Perfect applies exclusively to USB DAC devices. For Wireless (Bluetooth) or Speaker outputs, Bit-Perfect is bypassed.
        val isBitPerfectMode = dacConnected && (bitPerfectEnabled || playbackMode == com.codetrio.spatialflow.util.PlaybackMode.BIT_PERFECT)
        val nativeDspEnabled = !isBitPerfectMode && com.codetrio.spatialflow.util.QualityModeSelector.isNativeDspModeEnabled(this)
        val dacDevice = com.codetrio.spatialflow.service.dac.UsbDacManager.activeDacDevice

        val shouldBypassDsp = isBitPerfectMode || (dacConnected && !nativeDspEnabled)
        val activeList = mutableListOf<String>()
        if (!shouldBypassDsp && (playerManager.mainEightDProcessor.enabled || is8DEnabled)) activeList.add("8D Spatial Audio")
        if (!shouldBypassDsp && playerManager.mainReverbProcessor.enabled) activeList.add("Moorer Reverb")

        // Direct USB controls whether audio is specifically bound to the USB DAC hardware
        val isDirectUsbBound = dacConnected && (usbExclusiveEnabled && dacDirectEnabled) && dacDevice != null

        val desiredState = DacRoutingState(
            isDirectUsbBound = isDirectUsbBound,
            isDspBypassed = shouldBypassDsp,
            activeList = activeList.toList(),
            dacDeviceId = if (isDirectUsbBound) dacDevice?.id else null
        )

        val stateChanged = lastDacRoutingState != desiredState
        if (stateChanged) {
            if (isDirectUsbBound) {
                player.setPreferredAudioDevice(dacDevice)
                playerManager.nextPlayer?.setPreferredAudioDevice(dacDevice)
            } else {
                // When Direct USB is turned off or disconnected, fall back to default system routing
                player.setPreferredAudioDevice(null)
                playerManager.nextPlayer?.setPreferredAudioDevice(null)
            }

            if (shouldBypassDsp) {
                playerManager.releaseAllAudioEffects()
                broadcastAudioEffectSession(false)
            }

            lastDacRoutingState = desiredState
        }

        playerManager.isDspBypassed = shouldBypassDsp
        playerManager.mainTelemetryProcessor.activeDspList = activeList
        playerManager.nextTelemetryProcessor?.activeDspList = activeList
        playerManager.mainTelemetryProcessor.isDspBypassed = shouldBypassDsp
        playerManager.nextTelemetryProcessor?.isDspBypassed = shouldBypassDsp
        playerManager.mainProcessor.enabled = !shouldBypassDsp
        playerManager.nextProcessor?.enabled = !shouldBypassDsp

        applyDacVolumeSettings()
    }

    private fun isBitPerfectNow(): Boolean {
        val dacConnected = com.codetrio.spatialflow.service.dac.UsbDacManager.isDacConnected.value
        return dacConnected && com.codetrio.spatialflow.util.QualityModeSelector.isBitPerfectActive(this)
    }

    private fun buildExoPlayer(
        processor: StereoBalanceProcessor,
        reverb: ReverbAudioProcessor,
        eightD: EightDAudioProcessor,
        telemetry: com.codetrio.spatialflow.service.dac.AudioTelemetryProcessor,
        handleAudioFocus: Boolean
    ): ExoPlayer {
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            init {
                setEnableDecoderFallback(true)
                setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
            }

            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                // TODO(AudioPlaybackService.kt:925): DefaultAudioSink is built once; a mode change requires rebuilding the player or the sink.
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(!isBitPerfectNow())
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        com.codetrio.spatialflow.service.dac.BitPerfectAudioProcessorChain(
                            telemetryProcessor = telemetry,
                            dspProcessors = arrayOf(eightD, reverb, processor)
                        ) {
                            (if (::playerManager.isInitialized) playerManager.isDspBypassed else false) ||
                                com.codetrio.spatialflow.util.QualityModeSelector.isBitPerfectActive(this@AudioPlaybackService)
                        }
                    )
                    .build()
            }

            override fun buildVideoRenderers(
                context: Context, extensionRendererMode: Int, mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean, eventHandler: Handler, eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long, out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // AudioPlaybackService only plays audio. Never instantiate hardware video decoders.
            }
        }
        
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp3ExtractorFlags(androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setFlacExtractorFlags(androidx.media3.extractor.flac.FlacExtractor.FLAG_DISABLE_ID3_METADATA)

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            playbackDataSourceFactory,
            extractorsFactory
        )

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20000,  // minBufferMs (20s buffer for mobile data resilience)
                60000,  // maxBufferMs (up to 60s in memory)
                500,    // bufferForPlaybackMs (instant start)
                1500    // bufferForPlaybackAfterRebufferMs (fast recovery on cell re-buffer)
            )
            .setBackBuffer(10000, true) // Retain 10s of back buffer for instantaneous rewinds
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val playerInstance = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()

        playerInstance.trackSelectionParameters = playerInstance.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        playerInstance.setAudioAttributes(attrs, handleAudioFocus)
        playerInstance.setWakeMode(C.WAKE_MODE_NETWORK)
        playerInstance.skipSilenceEnabled = false

        // Add real-time format and DAC synchronization analytics listener
        playerInstance.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onLoadCompleted(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData
            ) {
                val dataLen = loadEventInfo.dataSpec.length
                if (dataLen > 0 && currentStreamContentLength <= 0) {
                    currentStreamContentLength = dataLen
                    val dur = player.duration
                    if (dur > 0) {
                        computeAndApplyRealBitrate(dataLen, dur)
                    }
                }
            }

            override fun onAudioInputFormatChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                val sampleRate = format.sampleRate
                val bitDepth = when (format.pcmEncoding) {
                    C.ENCODING_PCM_24BIT -> 24
                    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
                    C.ENCODING_PCM_16BIT -> 16
                    else -> if (format.sampleMimeType?.contains("flac", ignoreCase = true) == true) 24 else 16
                }
                val rawBitrateBps = if (format.bitrate > 0) format.bitrate else 0
                val existingBitrate = com.codetrio.spatialflow.service.dac.UsbDacManager.liveSourceStats.value.bitrateBps
                val finalBitrateBps = if (rawBitrateBps > 0) rawBitrateBps else existingBitrate

                com.codetrio.spatialflow.service.dac.UsbDacManager.updateSourceStats(
                    com.codetrio.spatialflow.service.dac.SourceAudioStats(
                        mimeType = format.sampleMimeType ?: "audio/raw",
                        sampleRateHz = sampleRate,
                        bitDepth = bitDepth,
                        channelCount = format.channelCount,
                        bitrateBps = finalBitrateBps
                    )
                )
                com.codetrio.spatialflow.service.dac.UsbDacManager.updateTrackFormat(sampleRate, bitDepth, format.channelCount)
                
                val isAtmosFormat = with(com.codetrio.spatialflow.audio.dolby.DolbyAtmosExtensions) {
                    format.isDolbyAtmos()
                }
                val curSong = viewModel?.currentSong?.value
                val hasAtmos = isAtmosFormat || curSong?.hasDolbyAtmos == true
                com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.updateCurrentTrackAtmosStatus(
                    hasAtmosStream = hasAtmos,
                    mimeType = format.sampleMimeType,
                    channelCount = format.channelCount
                )

                checkAndApplyDacRouting()

                if (currentStreamContentLength > 0 && player.duration > 0) {
                    computeAndApplyRealBitrate(currentStreamContentLength, player.duration)
                }
            }
        })

        return playerInstance
    }

    private fun requestAudioFocus(): Boolean {
        if (!audioFocusEnabled) return true
        if (audioManager == null) {
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttrs)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (audioManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    /**
     * Format detection pipeline (rebuilt from scratch).
     *
     * Two-tier authority:
     *   Tier 1 — `resolvedStreamFormatTag`: Set once during stream URL resolution in
     *            loadAudio(). This is the ground-truth format from InnerTube/NewPipe
     *            and is NEVER cleared by ExoPlayer lifecycle callbacks. Only cleared
     *            on explicit song change (onMediaItemTransition).
     *   Tier 2 — ExoPlayer format inspection: Used only as a fallback for local files
     *            or when Tier 1 is unavailable (should be rare for streaming).
     *
     * `cachedAudioFormat` is the final formatted string cache for UI consumption.
     */

    /** Authoritative format tag from stream resolution — never touched by ExoPlayer callbacks */
    private var resolvedStreamFormatTag: String = ""

    private val probeOkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private var streamProbeJob: Job? = null
    @Volatile private var currentStreamContentLength: Long = -1L

    fun computeAndApplyRealBitrate(totalBytes: Long, durationMs: Long) {
        if (totalBytes <= 0 || durationMs <= 0) return
        val durationSec = durationMs / 1000.0
        if (durationSec <= 0) return
        val bps = (totalBytes * 8) / durationSec
        val kbps = (bps / 1000).toInt()
        if (kbps <= 0) return

        Log.d(TAG, "Calculated real streaming VBR bitrate: $kbps kbps (bytes: $totalBytes, duration: ${durationMs}ms)")

        // Update live source telemetry stats
        val currentSource = com.codetrio.spatialflow.service.dac.UsbDacManager.liveSourceStats.value
        com.codetrio.spatialflow.service.dac.UsbDacManager.updateSourceStats(
            currentSource.copy(bitrateBps = kbps * 1000)
        )

        // Re-derive active format tag with real bitrate
        val currentTag = resolvedStreamFormatTag.ifEmpty { getActiveAudioFormat() }
        val codec = when {
            currentTag.contains("FLAC", ignoreCase = true) || currentSource.mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            currentTag.contains("WAV", ignoreCase = true) || currentSource.mimeType.contains("wav", ignoreCase = true) -> "WAV"
            currentTag.contains("OPUS", ignoreCase = true) || currentSource.mimeType.contains("opus", ignoreCase = true) -> "OPUS"
            currentTag.contains("AAC", ignoreCase = true) || currentSource.mimeType.contains("mp4a", ignoreCase = true) -> "AAC"
            currentTag.contains("MP3", ignoreCase = true) || currentSource.mimeType.contains("mpeg", ignoreCase = true) -> "MP3"
            else -> "FLAC"
        }

        val isLossless = codec == "FLAC" || codec == "WAV"
        val newTag = if (isLossless) {
            if (currentSource.bitDepth >= 24) {
                val sampleRateKhz = if (currentSource.sampleRateHz > 0) "${currentSource.sampleRateHz / 1000}kHz" else "Hi-Res"
                "$codec • 24-bit $sampleRateKhz • $kbps kbps"
            } else {
                "$codec • Lossless • $kbps kbps"
            }
        } else {
            "$codec • $kbps kbps"
        }

        setResolvedStreamFormat(newTag)
        viewModel?.updatePlaybackFormat(newTag)
    }

    private fun probeStreamContentLength(uri: Uri, targetDurationMs: Long = 0L) {
        streamProbeJob?.cancel()
        currentStreamContentLength = -1L
        val urlStr = uri.toString()
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            val file = File(uri.path ?: "")
            if (file.exists() && file.length() > 0) {
                currentStreamContentLength = file.length()
                val dur = if (targetDurationMs > 0) targetDurationMs else player.duration
                if (dur > 0) {
                    computeAndApplyRealBitrate(currentStreamContentLength, dur)
                }
            }
            return
        }

        streamProbeJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(urlStr)
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", "SpatialFlow/1.8.2 (Android)")
                    .get()
                    .build()

                probeOkHttpClient.newCall(req).execute().use { resp ->
                    var total = -1L
                    val contentRange = resp.header("Content-Range")
                    if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                        val totalPart = contentRange.substringAfter("/").trim()
                        total = totalPart.toLongOrNull() ?: -1L
                    }
                    if (total <= 0L) {
                        total = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                    if (total > 0L) {
                        currentStreamContentLength = total
                        withContext(Dispatchers.Main) {
                            val dur = if (targetDurationMs > 0) targetDurationMs else player.duration
                            if (dur > 0) {
                                computeAndApplyRealBitrate(total, dur)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Content length probe non-critical: ${e.message}")
            }
        }
    }

    fun getActiveAudioFormat(): String {
        // Fast path: return cached result
        if (cachedAudioFormat.isNotEmpty()) return cachedAudioFormat

        // Tier 1: Check authoritative resolved tag (from stream resolution)
        if (resolvedStreamFormatTag.isNotEmpty()) {
            cachedAudioFormat = resolvedStreamFormatTag
            return resolvedStreamFormatTag
        }

        // Tier 1b: Check YouTubeMusic.resolvedFormatTags map by videoId
        val videoId = getCurrentVideoId()
        if (!videoId.isNullOrEmpty()) {
            val fetchedTag = YouTubeMusic.getResolvedFormatTag(videoId)
            if (!fetchedTag.isNullOrEmpty()) {
                resolvedStreamFormatTag = fetchedTag
                cachedAudioFormat = fetchedTag
                return fetchedTag
            }
        }

        // Tier 2: ExoPlayer format inspection (local files, rare streaming fallback)
        if (Looper.myLooper() != Looper.getMainLooper()) return ""
        return detectFormatFromExoPlayer()
    }

    private var resolvedStreamProvider: String = ""

    fun getActiveStreamProvider(): String = resolvedStreamProvider

    fun setResolvedStreamProvider(provider: String) {
        if (provider.isNotBlank()) {
            resolvedStreamProvider = provider
            viewModel?.updatePlaybackProvider(provider)
            Log.d(TAG, "setResolvedStreamProvider: $provider")
        }
    }

    /**
     * Set the authoritative stream format tag. Called from loadAudio() after
     * stream URL resolution. This is the single source of truth for streaming formats.
     */
    fun setResolvedStreamFormat(tag: String) {
        if (tag.isNotBlank()) {
            resolvedStreamFormatTag = tag
            cachedAudioFormat = tag
            Log.d(TAG, "setResolvedStreamFormat: $tag")
        }
    }

    /**
     * Called ONLY on song change (media item transition). Clears both tiers
     * so the next song gets a fresh resolution.
     */
    fun clearFormatForSongChange() {
        resolvedStreamFormatTag = ""
        cachedAudioFormat = ""
        resolvedStreamProvider = ""
        currentStreamContentLength = -1L
        streamProbeJob?.cancel()
    }

    fun invalidateAudioFormatCache() {
        // Only clear the UI cache, NEVER touch resolvedStreamFormatTag.
        // This allows ExoPlayer callbacks to re-derive from the authoritative tag
        // without losing the ground-truth format.
        cachedAudioFormat = ""
    }

    private fun getCurrentVideoId(): String? {
        val currentSong = viewModel?.currentSongIndex?.value?.let { idx ->
            viewModel?.songList?.value?.getOrNull(idx)
        } ?: viewModel?.currentSong?.value

        return currentSong?.videoId
            ?: currentSong?.contentUri?.let { if (it.scheme == "innertube") it.host else null }
            ?: player.currentMediaItem?.mediaId?.takeIf { it.length == 11 }
    }

    /**
     * Tier 2: Detect format from ExoPlayer's actual decoded audio track.
     * Used for local files or as last resort for streaming content.
     */
    private fun detectFormatFromExoPlayer(): String {
        val format = player.audioFormat
        val uriString = currentSourceUri?.toString() ?: ""

        val sample = format?.sampleMimeType ?: ""
        var codecResult = when {
            sample.contains("opus", ignoreCase = true)                              -> "OPUS"
            sample.contains("aac", ignoreCase = true) ||
                    sample.contains("mp4a", ignoreCase = true)                      -> "AAC"
            sample.contains("mpeg", ignoreCase = true) ||
                    sample.contains("mp3", ignoreCase = true)                       -> "MP3"
            sample.contains("flac", ignoreCase = true)                              -> "FLAC"
            sample.contains("wav", ignoreCase = true) ||
                    sample.contains("wave", ignoreCase = true)                      -> "WAV"
            else -> {
                val codecs = format?.codecs ?: ""
                when {
                    codecs.contains("opus", ignoreCase = true)                      -> "OPUS"
                    codecs.contains("mp4a", ignoreCase = true) ||
                            codecs.contains("aac", ignoreCase = true)               -> "AAC"
                    codecs.contains("mp3", ignoreCase = true) ||
                            codecs.contains("mpeg", ignoreCase = true)              -> "MP3"
                    codecs.contains("flac", ignoreCase = true)                      -> "FLAC"
                    else -> {
                        val container = format?.containerMimeType ?: ""
                        when {
                            container.contains("opus", ignoreCase = true)           -> "OPUS"
                            container.contains("ogg", ignoreCase = true)            -> "OGG"
                            container.contains("aac", ignoreCase = true) ||
                                    container.contains("mp4a", ignoreCase = true)   -> "AAC"
                            container.contains("flac", ignoreCase = true)           -> "FLAC"
                            else                                                    -> ""
                        }
                    }
                }
            }
        }

        // Fallback to URI inspection if format was null/empty
        if (codecResult.isEmpty() && uriString.isNotEmpty()) {
            val uriLower = uriString.lowercase()
            codecResult = when {
                uriLower.contains("mime=audio/webm") ||
                        uriLower.contains("mime=audio%2fwebm") ||
                        uriLower.contains(".opus") ||
                        uriLower.contains("codecs=opus") ||
                        uriLower.contains("audio/opus") ||
                        uriLower.contains("webm") ||
                        uriLower.contains("itag=251") ||
                        uriLower.contains("itag=250") ||
                        uriLower.contains("itag=249")                           -> "OPUS"
                uriLower.contains("audio/flac") ||
                        uriLower.contains("mime=audio%2fflac") ||
                        uriLower.contains(".flac")                              -> "FLAC"
                uriLower.contains("mime=audio/mp4") ||
                        uriLower.contains("mime=audio%2fmp4") ||
                        uriLower.contains(".m4a") ||
                        uriLower.contains(".aac") ||
                        uriLower.contains("codecs=mp4a") ||
                        uriLower.contains("itag=140") ||
                        uriLower.contains("itag=139")                           -> "AAC"
                uriLower.contains(".mp3") ||
                        uriLower.contains("audio/mpeg") ||
                        uriLower.contains("audio/mp3")                          -> "MP3"
                uriLower.contains(".wav") ||
                        uriLower.contains("audio/wav")                          -> "WAV"
                else -> ""
            }
        }

        if (codecResult.isEmpty()) return ""

        // Lossless codecs: mark as lossless but still compute bitrate below
        val isLosslessCodec = codecResult == "FLAC" || codecResult == "WAV"

        var kbps = -1

        // 1. Inspect raw bitrate, ignoring 48000/44100 sample rate false positives in ExoPlayer Format for Opus/AAC
        val rawBitrate = if (format != null && format.bitrate > 0) format.bitrate else if (format != null && format.peakBitrate > 0) format.peakBitrate else -1
        if (rawBitrate > 0 && rawBitrate != 48000 && rawBitrate != 44100 && rawBitrate != 96000) {
            kbps = rawBitrate / 1000
        }

        // 2. If kbps is missing or was a sample rate false positive, calculate exact VBR bitrate from file size and duration
        if (kbps <= 0) {
            val durationMs = player.duration
            if (durationMs > 0) {
                if (currentStreamContentLength > 0) {
                    val durationSec = durationMs / 1000.0
                    val bps = (currentStreamContentLength * 8) / durationSec
                    kbps = (bps / 1000).toInt()
                } else {
                    val song = viewModel?.currentSongIndex?.value?.let { idx ->
                        viewModel?.songList?.value?.getOrNull(idx)
                    }
                    if (song != null && !song.path.isNullOrBlank()) {
                        val file = File(song.path)
                        if (file.exists() && file.length() > 0) {
                            val durationSec = durationMs / 1000.0
                            val bps = (file.length() * 8) / durationSec
                            kbps = (bps / 1000).toInt()
                        }
                    }
                }
            }
        }

        if (kbps <= 0 && !isLosslessCodec) {
            if (codecResult == "OPUS") kbps = 160
            else if (codecResult == "AAC") kbps = 128
            else if (codecResult == "MP3") kbps = 320
        }

        val result = when {
            isLosslessCodec && kbps > 0 -> "$codecResult • Lossless • $kbps kbps"
            isLosslessCodec             -> "$codecResult • Lossless"
            kbps > 0                    -> "$codecResult • $kbps kbps"
            else                        -> codecResult
        }
        cachedAudioFormat = result
        return result
    }

    fun setViewModel(vm: PlayerSharedViewModel) {
        val oldVm = this.viewModel
        if (oldVm != null && oldVm !== vm) {
            // Memory Leak Extermination: 
            // The old ViewModel instance became a zombie when the Activity was destroyed.
            // Before discarding it, copy its queue and playback state to the new UI ViewModel.
            vm.restoreStateFrom(oldVm)
            oldVm.cleanupBgScope()
        }
        this.viewModel = vm
        vm.hapticManager = hapticManager
        
        player.shuffleModeEnabled = vm.isShuffleEnabled.value
        
        // Single observation tree (Directives #3 & #6)
        viewModelObservationJob?.cancel()
        viewModelObservationJob = serviceScope.launch {
            launch {
                vm.repeatMode.collect { mode ->
                    val exoMode = when (mode) {
                        PlayerSharedViewModel.REPEAT_ONE -> Player.REPEAT_MODE_ONE
                        PlayerSharedViewModel.REPEAT_ALL -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                    if (player.repeatMode != exoMode) {
                        player.repeatMode = exoMode
                    }
                    updateMediaSessionCustomLayout()
                }
            }
            launch {
                vm.isCurrentSongFavorite.collect {
                    updateMediaSessionCustomLayout()
                }
            }
            launch {
                vm.isShuffleEnabled.collect { enabled ->
                    if (player.shuffleModeEnabled != enabled) {
                        player.shuffleModeEnabled = enabled
                    }
                    updateMediaSessionCustomLayout()
                }
            }
        }
    }

    @JvmOverloads
    fun loadAudio(uri: Uri, startPosition: Long = -1) {
        shouldAutoPlay = false
        pendingSeekPos = startPosition
        resolveAndLoad(uri)
    }

    @JvmOverloads
    fun loadAndPlay(uri: Uri, startPosition: Long = -1) {
        shouldAutoPlay = true
        pendingSeekPos = startPosition
        resolveAndLoad(uri)
    }

    /**
     * Centralized execution gate resolving dynamic identifiers OUTSIDE the buffer pipeline
     * prior to injecting raw HTTP/File resource pointers into ExoPlayer.
     */
    private fun resolveAndLoad(uri: Uri) {
        originalSourceUri = uri
        if (uri.scheme == "innertube") {
            val videoId = uri.host ?: return

            // Check if pre-resolved stream or local downloaded file exists
            val targetSong = viewModel?.songList?.value?.find { it.videoId == videoId || it.id.toString() == videoId }
                             ?: viewModel?.currentSong?.value
            val preResolvedUri = targetSong?.let { queuePreloader.getResolvedUri(it.id) }
            if (preResolvedUri != null) {
                Log.d(TAG, "⚡ INSTANT PRE-RESOLVED STREAM HIT for $videoId: $preResolvedUri")
                resolveJob?.cancel()
                resolveTargetSongId = null
                loadAudioInternal(preResolvedUri)
                return
            }

            // INSTANT CANCELLATION: Kill any in-flight resolution immediately
            resolveJob?.cancel()
            resolveTargetSongId = videoId

            resolveJob = serviceScope.launch {
                // The offline-file lookup hits MediaStore and stats external storage, which can
                // block for tens of milliseconds. It used to run inline on the main thread here
                // and was a direct cause of dropped frames on every tap, so it now runs on IO.
                val localUri = targetSong?.let {
                    withContext(Dispatchers.IO) { SongDownloader.getDownloadedSongUri(this@AudioPlaybackService, it) }
                }
                ensureActive()
                if (resolveTargetSongId != videoId) return@launch

                if (localUri != null) {
                    Log.d(TAG, "Redirecting playback of $videoId to local offline file: $localUri")
                    resolveTargetSongId = null
                    setResolvedStreamProvider("Local Device Storage (Direct File)")
                    loadAudioInternal(localUri)
                    return@launch
                }

                val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
                if (prefs.getBoolean("offline_only_mode", false)) {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Offline Only Mode is enabled")
                    return@launch
                }

                Log.d(TAG, "Pre-resolving stream ahead of injector injection: $videoId")
                
                val targetVideoId = if (videoId.length != 11) {
                    val query = targetSong?.let { "${it.artist} ${it.title}".trim() } ?: videoId
                    val searchRes = withContext(Dispatchers.IO) {
                        YouTubeMusic.search(query, com.codetrio.spatialflow.data.innertube.SearchFilter.SONGS).getOrNull()
                    }
                    val match = searchRes?.items?.filterIsInstance<com.codetrio.spatialflow.data.innertube.SearchItem.Song>()?.firstOrNull()?.song
                    match?.videoId ?: videoId
                } else videoId

                // Fetch stream URL only on IO
                val attempts = streamRetryAttempts.getOrDefault(videoId, 0)
                val isRetry = attempts > 0
                // If this is a retry attempt (e.g. after HTTP 403/404 or stream error on Opus/Lossless), fallback to YouTube AAC/Opus!
                val overrideOpus = if (isRetry) false else null
                val streamResult = withContext(Dispatchers.IO) {
                    streamRepository.getStreamUrl(
                        videoId = targetVideoId,
                        title = targetSong?.title,
                        artist = targetSong?.artist,
                        overridePreferOpus = overrideOpus,
                        isRetry = isRetry
                    )
                }
                val streamUrl = (streamResult as? com.codetrio.spatialflow.domain.error.Result.Success)?.data
                
                // Guard: If user clicked another song while we were resolving, abort silently
                ensureActive()
                if (resolveTargetSongId != videoId) {
                    Log.d(TAG, "Stale resolution for $videoId discarded (target changed to $resolveTargetSongId)")
                    return@launch
                }
                
                if (!streamUrl.isNullOrEmpty()) {
                    if (isRetry) {
                        streamRetryAttempts.remove(videoId)
                    }
                    val resolvedTag = YouTubeMusic.getResolvedFormatTag(targetVideoId)
                        ?: YouTubeMusic.getResolvedFormatTag(videoId)
                    if (!resolvedTag.isNullOrEmpty()) {
                        // Set the authoritative Tier 1 format BEFORE starting ExoPlayer
                        setResolvedStreamFormat(resolvedTag)
                        viewModel?.updatePlaybackFormat(resolvedTag)
                    }
                    val resolvedProvider = YouTubeMusic.getResolvedProvider(targetVideoId)
                        ?: YouTubeMusic.getResolvedProvider(videoId)
                    if (!resolvedProvider.isNullOrEmpty()) {
                        setResolvedStreamProvider(resolvedProvider)
                    }
                    loadAudioInternal(streamUrl.toUri(), keepPlayingUntilReady = true)
                    
                    // Asynchronously fetch and apply resume position with a 2-second timeout
                    if (pendingSeekPos <= 0 && targetVideoId.length == 11) {
                        launch(Dispatchers.IO) {
                            val position = kotlinx.coroutines.withTimeoutOrNull(2000L.milliseconds) {
                                val posResult = streamRepository.getPlaybackPosition(targetVideoId)
                                (posResult as? com.codetrio.spatialflow.domain.error.Result.Success)?.data ?: 0L
                            } ?: return@launch
                            
                            if (position > 2000L) {
                                withContext(Dispatchers.Main) {
                                    Log.d(TAG, "Synced cross-device playback position: ${position}ms asynchronously")
                                    pendingSeekPos = position
                                    // If player already started playing from 0, seek it now
                                    if (player.playbackState == Player.STATE_READY) {
                                        player.seekTo(position)
                                        pendingSeekPos = -1
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Failed resolving upfront for: $videoId ($targetVideoId)")
                    viewModel?.postIsProcessing(false)
                    consecutivePlaybackFailures++
                    if (consecutivePlaybackFailures >= 3) {
                        Log.e(TAG, "Circuit breaker tripped: $consecutivePlaybackFailures consecutive resolution failures. Halting auto-skipping.")
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Playback paused: Unable to stream tracks")
                        viewModel?.setIsPlaying(false)
                    } else {
                        handler.postDelayed({
                            viewModel?.playNextSong(true)
                        }, 600)
                    }
                }
            }
        } else {
            // Local or pre-determined resource, injection is instant
            resolveJob?.cancel()
            resolveTargetSongId = null
            var targetUri = uri
            if (uri.scheme == "content") {
                val canOpen = try {
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                } catch (e: Exception) {
                    false
                }
                if (!canOpen) {
                    val currentSong = viewModel?.currentSong?.value
                    val directPath = currentSong?.path
                    if (!directPath.isNullOrBlank()) {
                        val file = File(directPath)
                        if (file.exists() && file.canRead()) {
                            Log.i(TAG, "Content URI unreadable, falling back to direct file: $directPath")
                            targetUri = Uri.fromFile(file)
                        }
                    }
                }
            }
            loadAudioInternal(targetUri)
        }
    }

    /**
     * The Ultimate Solution: Proactively builds the native ExoPlayer queue by resolving the NEXT track
     * in background and adding it to the pipeline BEFORE the current track completes.
     */
    private fun preloadNextSongIntoQueue() {
        val vm = viewModel ?: return
        val songs = vm.songList.value
        if (songs.isEmpty()) return
        
        // Shuffle-aware next index prediction
        val nextIndex = vm.getNextSongIndex() 
        if (nextIndex < 0 || nextIndex >= songs.size) {
            return
        }
        
        val nextSong = songs[nextIndex]
        Log.d(TAG, "Initializing proactive queue preloading for native transition: ${nextSong.title}")

        val currentIdx = vm.currentSongIndex.value
        val mode = com.codetrio.spatialflow.util.QualityModeSelector.getActiveQualityMode(applicationContext)
        val depth = com.codetrio.spatialflow.util.QualityModeSelector.getPreloadDepth(mode)

        queuePreloader.updatePreloadQueue(songs, currentIdx, depth = 1)
        artworkPreloader.preloadQueueArtwork(songs, currentIdx, lookaheadDepth = 1)

        preloadJob?.cancel()
        preloadJob = serviceScope.launch {
            var nextUri = nextSong.contentUri

            // 1. Check pre-resolved cache, local download & resolve if online source
            val targetVideoId = nextSong.videoId
            val preResolved = queuePreloader.getResolvedUri(nextSong.id)
            if (preResolved != null) {
                nextUri = preResolved
                Log.d(TAG, "Preloader cache hit for next song stream: $nextUri")
            } else {
                // Resolved lazily and on IO: this touches MediaStore and external storage, and
                // is pointless when the preloader cache already answered above.
                val localUri = withContext(Dispatchers.IO) {
                    SongDownloader.getDownloadedSongUri(this@AudioPlaybackService, nextSong)
                }
                if (localUri != null) {
                    nextUri = localUri
                    Log.d(TAG, "Preloader redirected next song to local offline file: $localUri")
                } else if (!targetVideoId.isNullOrEmpty()) {
                    val resolvedUrl = withContext(Dispatchers.IO) {
                        YouTubeMusic.getStreamUrl(
                            videoId = targetVideoId,
                            title = nextSong.title,
                            artist = nextSong.artist
                        ).getOrNull()
                    }
                    if (!resolvedUrl.isNullOrEmpty()) {
                        nextUri = resolvedUrl.toUri()
                        Log.d(TAG, "Next track stream URL pre-resolved successfully")

                        // First-chunk byte pre-caching: only on unmetered/Wi-Fi to prevent cellular bandwidth starvation
                        val cm = getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                        val isCellular = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
                        if (!isCellular) {
                            preCacheFirstChunk(resolvedUrl)
                        }
                    }
                }
            }

            // 2. Warm-inject into ExoPlayer Natively with target-song and job active checks
            withContext(Dispatchers.Main) {
                // Safety Verification: confirm job is active & target next song hasn't changed
                ensureActive()
                val currentVm = viewModel ?: return@withContext
                val currentNextIdx = currentVm.getNextSongIndex()
                val currentNextSong = currentVm.songList.value.getOrNull(currentNextIdx)
                if (currentNextSong?.id != nextSong.id) {
                    Log.d(TAG, "Preload target song changed from ${nextSong.title} to ${currentNextSong?.title}, aborting stale queue mutation")
                    return@withContext
                }

                if (crossfadeDurationMs > 0) {
                    if (nextUri.scheme != "innertube") {
                        playerManager.prepareNextPlayer(
                            uri = nextUri,
                            title = nextSong.title,
                            artist = nextSong.artist,
                            mediaId = nextSong.id.toString(),
                            viewModel = viewModel
                        ) { wrapper ->
                            handler.post {
                                playerManager.cancelCrossfade()
                            }
                        }
                    }
                } else {
                    if (player.mediaItemCount < 2 && nextUri.scheme != "innertube") {
                        val nextItem = buildMediaItem(nextUri, nextSong.title, nextSong.artist, nextSong.id.toString())
                        player.addMediaItem(nextItem)
                        Log.d(TAG, "⚡ SUCCESSFULLY HOT-QUEUED NEXT SONG INTO NATIVE PIPELINE! READY FOR ZERO-GAP TRANSITION.")
                    }
                }
            }
        }
    }

    fun refreshNativeQueue() {
        // Clear out any currently preloaded next song
        if (crossfadeDurationMs > 0) {
            playerManager.cancelCrossfade()
        } else {
            // Keep the currently playing item (index 0) but remove the next ones
            while (player.mediaItemCount > 1) {
                player.removeMediaItem(1)
            }
        }
        // Force a fresh proactive preload with the newly updated queue
        preloadNextSongIntoQueue()
    }

    
    private var precacheJob: Job? = null

    /**
     * Pre-cache the first 512KB of the audio stream into SimpleCache for instant playback start.
     */
    private fun preCacheFirstChunk(streamUrl: String) {
        precacheJob?.cancel()
        precacheJob = serviceScope.launch(Dispatchers.IO) {
            try {
                ensureActive()
                val dataSpec = DataSpec.Builder()
                    .setUri(streamUrl)
                    .setLength(512 * 1024) // 512KB first chunk
                    .build()
                
                val cacheWriter = CacheWriter(
                    cacheDataSourceFactory.createDataSource(),
                    dataSpec,
                    null,
                    { _, _, _ ->
                        if (!isActive) throw kotlinx.coroutines.CancellationException("Precache cancelled")
                    }
                )
                cacheWriter.cache()
                Log.d(TAG, "⚡ First-chunk pre-cached (512KB) for instant start")
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w(TAG, "First-chunk pre-cache failed (non-critical): ${e.message}")
                }
            }
        }
    }

    fun play() {
        Log.d(TAG, "play: player.currentMediaItem = ${player.currentMediaItem}, isPlaying = ${player.isPlaying}")
        if (player.currentMediaItem == null) {
            val uri = viewModel?.songUri?.value
            if (uri != null) {
                val pos = viewModel?.currentPosition?.value ?: 0
                loadAndPlay(uri, pos.toLong())
                return
            }
        }
        if (!player.isPlaying) {
            if (requestAudioFocus()) {
                player.play()
                wasPausedByFocusLoss = false
                if (isCrossfading) {
                    nextPlayer?.play()
                }
            } else {
                Log.w(TAG, "play: Audio focus request denied.")
            }
        }
    }

    fun pause() {
        Log.d(TAG, "pause: isPlaying = ${player.isPlaying}, isCrossfading = $isCrossfading")
        player.pause()
        if (isCrossfading) {
            nextPlayer?.pause()
        }
        if (::playerManager.isInitialized) {
            PlaybackStateManager.savePosition(applicationContext, player.currentPosition)
        }
        if (!wasPausedByFocusLoss) {
            abandonAudioFocus()
        }
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        if (player.shuffleModeEnabled != enabled) {
            player.shuffleModeEnabled = enabled
        }
    }

    fun stop() {
        player.stop()
        if (isCrossfading) {
            playerManager.cancelCrossfade()
        }
        abandonAudioFocus()
    }

    fun seekTo(position: Int) {
        if (isCrossfading) {
            playerManager.cancelCrossfade()
            // Re-trigger preload after seek settles
            handler.postDelayed({ preloadNextSongIntoQueue() }, 1000)
        }
        if (isProcessing) pendingSeekPos = position.toLong()
        player.seekTo(position.toLong())
    }

    fun playPrevious() = viewModel?.playPreviousSong()
    fun playNext() {
        if (crossfadeDurationMs > 0 && nextPlayer != null && !isCrossfading) {
            startCrossfadeOut()
        } else {
            viewModel?.playNextSong()
        }
    }

    private var cachedArtworkBitmap: Bitmap? = null
    private var cachedArtworkBytes: ByteArray? = null

    /** Encodes artwork for the media session. Callers must be off the main thread. */
    private fun compressArtwork(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        return stream.toByteArray()
    }

    /**
     * Returns already-encoded artwork bytes, or null if they aren't ready yet. This is
     * deliberately non-blocking: encoding happens in [setSongMetadata] off the main thread, and
     * that call also refreshes the media item once the bytes land, so a miss here is transient.
     */
    private fun getCachedArtworkBytes(bitmap: Bitmap?): ByteArray? {
        if (bitmap == null) return null
        return if (bitmap === cachedArtworkBitmap) cachedArtworkBytes else null
    }

    internal fun buildMediaItem(uri: Uri): MediaItem {
        val currentSong = viewModel?.currentSong?.value
        val songId = currentSong?.id?.toString() ?: ""
        if (currentSong != null) {
            currentSongName = currentSong.title
        }
        return buildMediaItem(uri, currentSongName, currentSong?.artist ?: "Unknown Artist", songId)
    }

    internal fun buildMediaItem(uri: Uri, title: String, artist: String, songId: String = ""): MediaItem {
        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
        
        // Use cached compressed cover bytes for active track
        if (title == currentSongName) {
            getCachedArtworkBytes(currentAlbumArt)?.let { bytes ->
                metadataBuilder.setArtworkData(bytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        
        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(songId) // Stable ID for shuffle-mode resync
            .setMediaMetadata(metadataBuilder.build())

        var detectedMimeType: String? = null
        if (uri.scheme == "content") {
            try {
                val type = contentResolver.getType(uri)
                if (!type.isNullOrEmpty() &&
                    !type.equals("application/octet-stream", ignoreCase = true) &&
                    !type.equals("binary/octet-stream", ignoreCase = true) &&
                    !type.equals("audio/*", ignoreCase = true) &&
                    !type.equals("*/*", ignoreCase = true)
                ) {
                    when {
                        type.contains("flac", ignoreCase = true) -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_FLAC
                        type.contains("mpeg", ignoreCase = true) || type.contains("mp3", ignoreCase = true) -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_MPEG
                        type.contains("mp4", ignoreCase = true) || type.contains("m4a", ignoreCase = true) -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_MP4
                        type.contains("opus", ignoreCase = true) -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_OPUS
                        type.contains("ogg", ignoreCase = true) -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_OGG
                        type.contains("wav", ignoreCase = true) || type.contains("wave", ignoreCase = true) -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_WAV
                        type.contains("webm", ignoreCase = true) -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_WEBM
                        type.startsWith("audio/") -> detectedMimeType = type
                    }
                }
            } catch (_: Exception) {}
        }

        val currentSong = viewModel?.currentSong?.value
        val checkPath = (currentSong?.path ?: uri.path ?: uri.lastPathSegment ?: "").lowercase()
        if (detectedMimeType.isNullOrEmpty() && checkPath.isNotEmpty()) {
            when {
                checkPath.endsWith(".flac") -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_FLAC
                checkPath.endsWith(".mp3") -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_MPEG
                checkPath.endsWith(".m4a") || checkPath.endsWith(".mp4") -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_MP4
                checkPath.endsWith(".opus") -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_OPUS
                checkPath.endsWith(".ogg") -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_OGG
                checkPath.endsWith(".wav") -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_WAV
                checkPath.endsWith(".aac") -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_AAC
                checkPath.endsWith(".webm") -> detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_WEBM
            }
        }

        if (detectedMimeType.isNullOrEmpty()) {
            val uriString = uri.toString().lowercase()
            when {
                uriString.contains(".flac") || uriString.contains("mime=audio/flac") || uriString.contains("mime=audio%2fflac") || uriString.contains("audio/flac") || uriString.contains("sp-pr-cf.audio") -> {
                    detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_FLAC
                }
                uriString.contains("mime=audio/webm") || uriString.contains("mime=audio%2fwebm") || uriString.contains(".webm") || uriString.contains("itag=251") || uriString.contains("itag=250") || uriString.contains("itag=249") || uriString.contains("codecs=opus") -> {
                    detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_WEBM
                }
                uriString.contains("mime=audio/mp4") || uriString.contains("mime=audio%2fmp4") || uriString.contains(".m4a") || uriString.contains(".mp4") || uriString.contains("itag=140") || uriString.contains("itag=139") || uriString.contains("codecs=mp4a") -> {
                    detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_MP4
                }
                uriString.contains(".opus") || uriString.contains("audio/opus") || uriString.contains("audio/ogg") -> {
                    detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_OPUS
                }
                uriString.contains(".mp3") || uriString.contains("audio/mpeg") || uriString.contains("audio/mp3") -> {
                    detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_MPEG
                }
                uriString.contains(".wav") || uriString.contains("audio/wav") || uriString.contains("audio/x-wav") -> {
                    detectedMimeType = androidx.media3.common.MimeTypes.AUDIO_WAV
                }
            }
        }

        // Only explicitly set MIME type if a valid audio MIME type was resolved.
        // Omission allows ExoPlayer to sniff the file container headers natively.
        if (!detectedMimeType.isNullOrEmpty()) {
            builder.setMimeType(detectedMimeType)
        }

        return builder.build()
    }

    /**
     * Applies now-playing metadata. The artwork crop/scale and PNG encode are genuinely expensive
     * (a 512x512 lossless encode costs tens of milliseconds), so they run on IO and only the
     * cheap player/notification mutation is posted back to Main. Text metadata is applied
     * immediately so the notification and lockscreen update without waiting on artwork.
     */
    @JvmOverloads
    fun setSongMetadata(songName: String?, albumArt: Bitmap?, artistName: String? = null) {
        this.currentSongName = songName ?: "SpatialFlow"
        if (!artistName.isNullOrBlank()) {
            this.currentArtist = artistName
        }

        if (albumArt == null) {
            this.currentAlbumArt = null
            this.notificationAlbumArt = null
            applySongMetadataToPlayer(null)
            return
        }

        // Stale-guard for the async prepare below. This is deliberately a separate counter from
        // artworkRequestToken, which guards Glide's in-flight loads — bumping that one here would
        // cancel the very callback delivering this bitmap.
        artworkPrepareToken++
        val requestToken = artworkPrepareToken

        serviceScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                try {
                    val size = min(albumArt.width, albumArt.height)
                    val square = if (albumArt.width == size && albumArt.height == size) {
                        albumArt
                    } else {
                        Bitmap.createBitmap(
                            albumArt,
                            (albumArt.width - size) / 2,
                            (albumArt.height - size) / 2,
                            size,
                            size
                        )
                    }
                    val scaled = if (square.width > 1024 || square.height > 1024) {
                        square.scale(1024, 1024, true)
                    } else {
                        square
                    }
                    scaled to compressArtwork(scaled)
                } catch (e: Exception) {
                    Log.w(TAG, "Artwork preparation failed: ${e.message}")
                    null
                }
            }

            if (requestToken != artworkPrepareToken) {
                Log.d(TAG, "Discarded stale artwork preparation for token $requestToken")
                return@launch
            }

            currentAlbumArt = prepared?.first
            notificationAlbumArt = prepared?.first?.let { bmp ->
                if (bmp.width > 256 || bmp.height > 256) {
                    androidx.core.graphics.BitmapCompat.createScaledBitmap(bmp, 256, 256, null, true)
                } else bmp
            }
            cachedArtworkBitmap = prepared?.first
            cachedArtworkBytes = prepared?.second
            applySongMetadataToPlayer(prepared?.second)
        }
    }

    /** Main-thread-only tail of [setSongMetadata]: cheap builder work, no bitmap processing. */
    private fun applySongMetadataToPlayer(artworkBytes: ByteArray?) {
        val mediaItem = player.currentMediaItem
        val effectiveArtist = currentSong?.artist ?: currentArtist.ifBlank { viewModel?.currentSong?.value?.artist ?: "Unknown Artist" }
        if (mediaItem != null) {
            val metadataBuilder = mediaItem.mediaMetadata.buildUpon()
                .setTitle(currentSongName)
                .setArtist(effectiveArtist)
            artworkBytes?.let { bytes ->
                metadataBuilder.setArtworkData(bytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            // Do NOT call player.replaceMediaItem(0, newItem) as that disrupts ExoPlayer playback pipeline!
            // Updating MediaMetadata on mediaSession is handled cleanly by MediaSession and notification
        }
        updateMediaSessionCustomLayout()
        refreshNotification()
    }

    @JvmOverloads
    fun setSongMetadataById(songName: String?, albumId: Long, artistName: String? = null) {
        if (!artistName.isNullOrBlank()) {
            this.currentArtist = artistName
        }
        serviceScope.launch(Dispatchers.IO) {
            var albumArt: Bitmap? = null
            if (albumId > 0) {
                try {
                    val artUri = "content://media/external/audio/albumart/$albumId".toUri()
                    albumArt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentResolver.loadThumbnail(artUri, Size(1024, 1024), null)
                    } else {
                        @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(contentResolver, artUri)
                    }
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                setSongMetadata(songName, albumArt, artistName)
            }
        }
    }

    private fun invalidateArtworkBytes() {
        cachedArtworkBitmap = null
        cachedArtworkBytes = null
    }

    @JvmOverloads
    fun setSongMetadataByUrl(songName: String?, url: String?, songId: String = "", artistName: String? = null) {
        if (!artistName.isNullOrBlank()) {
            this.currentArtist = artistName
        }
        if (url.isNullOrEmpty()) {
            activeGlideTarget?.let { target ->
                try { com.bumptech.glide.Glide.with(applicationContext).clear(target) } catch (_: Exception) {}
            }
            activeGlideTarget = null
            activeGlideUrl = null
            currentArtworkIdentity = null
            invalidateArtworkBytes()
            setSongMetadata(songName, null, artistName)
            return
        }

        val highResUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            SongItem.enhanceThumbnailUrl(url)
        } else {
            url
        }

        // 1. Monotonic Token & Request Identity Creation
        artworkRequestToken++
        val requestToken = artworkRequestToken
        val raceIdentity = "$songId|$highResUrl|$requestToken"
        currentArtworkIdentity = "$songId|$highResUrl"

        // 2. Clear Previous Active Glide Target (Prevent Bitmap Accumulation)
        activeGlideTarget?.let { target ->
            try { com.bumptech.glide.Glide.with(applicationContext).clear(target) } catch (_: Exception) {}
        }
        activeGlideTarget = null
        activeGlideUrl = highResUrl

        // 3. Invalidate Stale Compressed Artwork Bytes
        invalidateArtworkBytes()

        // 4. Create New Active Target with Request Token Verification
        val target = object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?) {
                // Reject stale async callbacks
                if (requestToken != artworkRequestToken) {
                    Log.d(TAG, "Discarded stale artwork callback for token $requestToken (current token is $artworkRequestToken)")
                    return
                }
                setSongMetadata(songName, resource, artistName)
            }

            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                invalidateArtworkBytes()
            }

            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                if (requestToken == artworkRequestToken) {
                    setSongMetadata(songName, null, artistName)
                }
            }
        }

        activeGlideTarget = target
        com.bumptech.glide.Glide.with(applicationContext)
            .asBitmap()
            .load(highResUrl)
            .override(1024, 1024)
            .into(target)
    }


    fun setReverbEnabled(enabled: Boolean) {
        effectsExecutor.execute {
            playerManager.mainReverbProcessor.clearBuffers()
            playerManager.nextReverbProcessor?.clearBuffers()
            playerManager.mainReverbProcessor.enabled = enabled
            playerManager.nextReverbProcessor?.enabled = enabled
            applyEffectsFromViewModelAsync()
        }
    }

    fun setReverbIntensity(intensity: Float) {
        effectsExecutor.execute {
            reverbIntensity = intensity.coerceIn(0f, 1f)
            playerManager.mainReverbProcessor.intensity = reverbIntensity
            playerManager.nextReverbProcessor?.intensity = reverbIntensity
            Log.d(TAG, "Reverb intensity set to: $reverbIntensity")
        }
    }

    fun setReverbPreset(preset: Short) {
        effectsExecutor.execute {
            val p = preset.toInt()
            playerManager.mainReverbProcessor.clearBuffers()
            playerManager.nextReverbProcessor?.clearBuffers()
            playerManager.mainReverbProcessor.preset = p
            playerManager.nextReverbProcessor?.preset = p
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        playerManager.setEqualizerEnabled(enabled, viewModel)
    }

    fun setEqBandGain(bandIndex: Int, gainDb: Int) {
        playerManager.setEqBandGain(bandIndex, gainDb)
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        playerManager.setLoudnessEnabled(enabled)
    }

    fun setLoudnessGain(gainDb: Int) {
        playerManager.setLoudnessGain(gainDb)
    }

    fun setBalance(balanceValue: Float) {
        playerManager.setBalance(balanceValue)
    }

    fun setPlaybackSpeed(speed: Float, matchPitch: Boolean) {
        playerManager.setPlaybackSpeed(speed, matchPitch)
    }

    fun isPlaying(): Boolean = player.isPlaying
    fun getCurrentSourceUri(): Uri? = originalSourceUri ?: currentSourceUri

    fun set8DEnabled(enabled: Boolean) {
        is8DEnabled = enabled
        playerManager.set8DEnabled(enabled)
        checkAndApplyDacRouting()
    }

    fun set8DSpeed(speed: Float) {
        playerManager.set8DSpeed(speed)
    }

    fun set8DReverb(reverb: Float) {
        playerManager.set8DReverb(reverb)
    }

    fun applyEffects(enable8D: Boolean, speed8D: Float) {
        set8DEnabled(enable8D)
        set8DSpeed(speed8D)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun loadAudioInternal(uri: Uri, keepPlayingUntilReady: Boolean = false) {
        if (!keepPlayingUntilReady) {
            player.pause()
            player.stop()
            player.clearMediaItems()
        }

        // Move cache cleanup OFF the critical path
        serviceScope.launch(Dispatchers.IO) { cleanCache() }
        
        if (isCrossfading) {
            playerManager.cancelCrossfade()
        }

        currentSourceUri = uri
        hasProcessed8D = false
        isNextSongPreCached = false
        
        try {
            val scheme = uri.scheme
            if (scheme == "http" || scheme == "https" || scheme == "innertube") {
                Log.d(TAG, "Loading remote stream: ${uri.toString().take(80)}...")
            } else {
                Log.d(TAG, "Loading local audio source directly: $uri")
            }
            
            val currentSong = viewModel?.currentSong?.value ?: this.currentSong
            if (currentSong != null) {
                this.currentSong = currentSong
                this.currentSongName = currentSong.title
                this.currentArtist = currentSong.artist
            }
            val durMs = currentSong?.duration ?: 0L
            probeStreamContentLength(uri, durMs)

            if (currentSong != null && !currentSong.videoId.isNullOrEmpty()) {
                telemetryManager.onSongChanged(currentSong.videoId!!, currentSong.duration)
            }
            if (::lastFmScrobbleManager.isInitialized) {
                lastFmScrobbleManager.onTrackChanged(currentSong)
            }
            
            player.setMediaItem(buildMediaItem(uri))
            
            // Seek BEFORE prepare to avoid double-buffering pass
            if (pendingSeekPos >= 0) {
                player.seekTo(pendingSeekPos)
                pendingSeekPos = -1
            }
            
            updateBaseVolume()
            player.prepare()
            
            if (shouldAutoPlay) {
                play()
                shouldAutoPlay = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load audio: ${e.message}")
        }
    }

    private fun initializeAudioEffectsSafe() {
        val shouldBypassDsp = if (::playerManager.isInitialized) playerManager.isDspBypassed else false
        if (!shouldBypassDsp) {
            playerManager.initializeAudioEffectsSafe(viewModel)
            broadcastAudioEffectSession(com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.isAtmosActive.value)
        } else {
            playerManager.releaseAllAudioEffects()
            broadcastAudioEffectSession(false)
        }
        handler.post {
            viewModel?.let { vm ->
                if (vm.hapticManager == null) {
                    vm.hapticManager = hapticManager
                }
            }
        }
    }

    fun applyEffectsFromViewModelAsync() {
        playerManager.applyEffectsFromViewModelAsync(viewModel)
    }

    private fun releaseAudioEffects() {
        playerManager.releaseAllAudioEffects()
    }

    private var lastTelemetryUpdateMs: Long = 0L

    private fun startPersistenceJob() {
        persistenceJob?.cancel()
        persistenceJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5000L.milliseconds)
                if (!isActive) break
                if (::playerManager.isInitialized) {
                    withContext(Dispatchers.Main) {
                        if (player.isPlaying) {
                            val pos = player.currentPosition
                            if (kotlin.math.abs(pos - lastPersistedMs) >= 5000L) {
                                lastPersistedMs = pos
                                PlaybackStateManager.savePosition(applicationContext, pos)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun triggerPreCacheNextSong() {
        viewModel?.let { vm ->
            val songs = vm.songList.value
            val nextIndex = vm.getNextSongIndex()
            if (nextIndex >= 0 && nextIndex < songs.size) {
                val nextSong = songs[nextIndex]
                YouTubeMusic.preCacheNextSong(nextSong)
            }
        }
    }

    private fun checkPlaybackMilestones(pos: Long, dur: Long) {
        if (!player.isPlaying || dur <= 0) return

        // 1. Predictive Pre-Caching at 70% threshold (triggered once per track, giving ample time for lossless stream resolution)
        if (!isNextSongPreCached && (pos.toFloat() / dur.toFloat() >= 0.70f)) {
            isNextSongPreCached = true
            triggerPreCacheNextSong()
        }

        // 2. Smart Crossfade Start Detection
        if (crossfadeDurationMs > 0 && !isCrossfading && pos > 5000 && pos > dur / 2) {
            val tenPercentOfSong = (dur / 10).toInt()
            val targetCrossfadeMs = crossfadeDurationMs.coerceAtMost(tenPercentOfSong).coerceAtLeast(1000)
            if ((dur - pos) <= targetCrossfadeMs) {
                playerManager.crossfadeDurationMs = targetCrossfadeMs
                startCrossfadeOut()
            }
        }
    }

    private fun setupProgressTracking() {
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                val isPlaying = player.isPlaying
                val isMounted = viewModel?.isSliderUiMounted?.value ?: false

                if (player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
                    val pos = if (isCrossfading && nextPlayer != null) nextPlayer!!.currentPosition else player.currentPosition
                    val dur = if (isCrossfading && nextPlayer != null) nextPlayer!!.duration else player.duration
                    viewModel?.setCurrentPosition(pos.toInt())
                    if (dur > 0) viewModel?.setDuration(dur.toInt())

                    // Throttle low-frequency milestone checks and telemetry to ~1 second interval
                    val now = System.currentTimeMillis()
                    if (now - lastTelemetryUpdateMs >= 1000L) {
                        lastTelemetryUpdateMs = now
                        telemetryManager.updateProgress(pos, dur)
                        if (::lastFmScrobbleManager.isInitialized) {
                            lastFmScrobbleManager.onPlaybackProgress(pos, dur)
                        }
                        checkPlaybackMilestones(pos, dur)
                    }
                }
                // Adaptive tick rate: 250ms when FullPlayer slider is visible and playing, 1000ms when collapsed or paused
                val tickDelayMs = if (isPlaying && isMounted) 250L else 1000L
                handler.postDelayed(this, tickDelayMs)
            }
        }
        handler.post(progressUpdateRunnable)
    }



    private fun finishProcessing(success: Boolean) {
        isProcessing = false
        viewModel?.postIsProcessing(false)
        viewModel?.setProcessingProgress(if (success) 100 else 0)

    }

    private fun setupMediaSession() {
        createNotificationChannel()
        val intent = Intent(this, com.codetrio.spatialflow.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Force standard commands to be available so buttons show in System UI
        val forwardingPlayer = buildForwardingPlayer(player)
        val session = MediaSession.Builder(this, forwardingPlayer)
            .setId(TAG)
            .setCallback(mediaSessionCallback)
            .setSessionActivity(pendingIntent)
            .build()
            
        mediaSession = session
        addSession(session)

        val provider = notificationProvider ?: CustomMediaNotificationProvider(this).also {
            it.setSmallIcon(R.drawable.ic_applogo)
            notificationProvider = it
        }
        setMediaNotificationProvider(provider)
        updateMediaSessionCustomLayout()
    }

    private fun buildForwardingPlayer(playerToWrap: ExoPlayer): ForwardingPlayer {
        return object : ForwardingPlayer(playerToWrap) {
            override fun getWrappedPlayer(): Player {
                return this@AudioPlaybackService.player
            }

            override fun getAvailableCommands(): Player.Commands {
                return wrappedPlayer.availableCommands.buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(COMMAND_SET_SHUFFLE_MODE)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SET_SHUFFLE_MODE -> true
                    else -> wrappedPlayer.isCommandAvailable(command)
                }
            }

            override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
                wrappedPlayer.shuffleModeEnabled = shuffleModeEnabled
                viewModel?.let { vm ->
                    if (vm.isShuffleEnabled.value != shuffleModeEnabled) {
                        vm.setShuffleEnabled(shuffleModeEnabled)
                    }
                }
            }

            override fun play() {
                this@AudioPlaybackService.play()
            }

            override fun pause() {
                this@AudioPlaybackService.pause()
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady) {
                    this@AudioPlaybackService.play()
                } else {
                    this@AudioPlaybackService.pause()
                }
            }

            override fun seekToNext() { playNext() }
            override fun seekToPrevious() { playPrevious() }
            override fun seekToNextMediaItem() { playNext() }
            override fun seekToPreviousMediaItem() { playPrevious() }
        }
    }

    private enum class NotificationPlaybackMode {
        SHUFFLE,
        LOOP_ONE,
        LOOP_ALL
    }

    private fun currentNotificationPlaybackMode(vm: PlayerSharedViewModel?): NotificationPlaybackMode {
        val repeatMode = vm?.repeatMode?.value ?: PlayerSharedViewModel.REPEAT_OFF
        val shuffleEnabled = vm?.isShuffleEnabled?.value == true
        return when {
            shuffleEnabled -> NotificationPlaybackMode.SHUFFLE
            repeatMode == PlayerSharedViewModel.REPEAT_ONE -> NotificationPlaybackMode.LOOP_ONE
            repeatMode == PlayerSharedViewModel.REPEAT_ALL -> NotificationPlaybackMode.LOOP_ALL
            else -> NotificationPlaybackMode.SHUFFLE
        }
    }

    private fun notificationPlaybackModeIcon(mode: NotificationPlaybackMode): Int {
        return when (mode) {
            NotificationPlaybackMode.SHUFFLE -> R.drawable.ic_shuffle
            NotificationPlaybackMode.LOOP_ONE -> R.drawable.ic_repeat_one
            NotificationPlaybackMode.LOOP_ALL -> R.drawable.ic_repeat
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun cycleNotificationPlaybackMode() {
        val vm = viewModel ?: return
        val repeatMode = vm.repeatMode.value
        val shuffleEnabled = vm.isShuffleEnabled.value
        when {
            !shuffleEnabled && repeatMode == PlayerSharedViewModel.REPEAT_OFF -> {
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_OFF)
                vm.setShuffleEnabled(true)
            }
            shuffleEnabled -> {
                vm.setShuffleEnabled(false)
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_ONE)
            }
            repeatMode == PlayerSharedViewModel.REPEAT_ONE -> {
                vm.setShuffleEnabled(false)
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_ALL)
            }
            else -> {
                vm.setShuffleEnabled(false)
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_OFF)
            }
        }
        updateMediaSessionCustomLayout()

    }

    fun refreshNotification() {
        try {
            val isPlaying = player.isPlaying
            val currentTitle = currentSongName
            val currentArtist = viewModel?.currentSong?.value?.artist ?: "Unknown Artist"
            val currentArtworkId = currentArtworkIdentity
            val currentMode = currentNotificationPlaybackMode(viewModel).ordinal
            val currentShuffle = player.shuffleModeEnabled
            val currentFav = viewModel?.isCurrentSongFavorite?.value == true

            if (lastNotifTitle == currentTitle &&
                lastNotifArtist == currentArtist &&
                lastNotifArtworkIdentity == currentArtworkId &&
                lastNotifIsPlaying == isPlaying &&
                lastNotifRepeatMode == currentMode &&
                lastNotifShuffleState == currentShuffle &&
                lastNotifIsFav == currentFav
            ) {
                return // Visible scalar notification state has not changed, skip notification rebuilding!
            }

            lastNotifTitle = currentTitle
            lastNotifArtist = currentArtist
            lastNotifArtworkIdentity = currentArtworkId
            lastNotifIsPlaying = isPlaying
            lastNotifRepeatMode = currentMode
            lastNotifShuffleState = currentShuffle
            lastNotifIsFav = currentFav

            updateMediaSessionCustomLayout()
        } catch (_: Exception) {}
    }

    fun updateMediaSessionCustomLayout() {
        val session = mediaSession ?: return
        val vm = viewModel

        val mode = currentNotificationPlaybackMode(vm)
        val modeButton = CommandButton.Builder()
            .setDisplayName("Playback Mode")
            .setSessionCommand(CMD_CYCLE_PLAYBACK_MODE)
            .setIconResId(notificationPlaybackModeIcon(mode))
            .setEnabled(true)
            .build()
            
        val favIcon = if (vm?.isCurrentSongFavorite?.value == true) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        val favButton = CommandButton.Builder()
            .setDisplayName("Favorite")
            .setSessionCommand(CMD_TOGGLE_FAV)
            .setIconResId(favIcon)
            .setEnabled(true)
            .build()
            
        session.setCustomLayout(listOf(modeButton, favButton))
        refreshNotification()
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()

            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(CMD_TOGGLE_LOOP)
                .add(CMD_CYCLE_PLAYBACK_MODE)
                .add(CMD_TOGGLE_FAV)
                .build()

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_LOOP -> {
                    viewModel?.toggleLoopMode()

                }
                ACTION_CYCLE_PLAYBACK_MODE -> {
                    cycleNotificationPlaybackMode()
                }
                ACTION_TOGGLE_FAV -> viewModel?.toggleFavorite()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun getActionIntent(action: String, requestCode: Int): android.app.PendingIntent {
        val intent = Intent(this, AudioPlaybackService::class.java).apply { this.action = action }
        return android.app.PendingIntent.getService(
            this, requestCode, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "audio_playback_channel", "Audio Playback", android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows currently playing audio"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }






    private fun startCrossfadeOut() {
        if (!shouldExecuteCrossfade(crossfadeDurationMs, isBitPerfectProvider) {
            Log.d(TAG, "Crossfade is disabled in Bit-Perfect mode; bypassing startCrossfadeOut.")
        }) {
            return
        }
        playerManager.startCrossfade(
            viewModel = viewModel,
            onSongTransition = { nextIdx ->
                viewModel?.let { vm ->
                    vm.updateSongIndexOnly(nextIdx)
                    vm.songList.value.getOrNull(nextIdx)?.let { song ->
                        this@AudioPlaybackService.currentSong = song
                        currentSongName = song.title
                        currentArtist = song.artist
                        if (song.thumbnailUrl != null) {
                            setSongMetadataByUrl(song.title, song.thumbnailUrl, song.id.toString(), song.artist)
                        } else {
                            setSongMetadataById(song.title, song.albumId, song.artist)
                        }
                        if (!song.videoId.isNullOrEmpty()) {
                            telemetryManager.onSongChanged(song.videoId!!, song.duration)
                        }
                        PlaybackStateManager.saveState(applicationContext, song, 0L, vm.songList.value, nextIdx)
                    }
                }
            },
            onComplete = {
                completeCrossfadeHandoff()
            }
        )
    }

    private fun completeCrossfadeHandoff() {
        // Unregister listener from the old player to prevent stop() from triggering a pause callback
        player.removeListener(mainPlayerListener)

        playerManager.completeCrossfadeHandoff(viewModel) { promotedPlayer ->
            // Apply listeners to the new primary player
            promotedPlayer.addListener(mainPlayerListener)
            
            // Promote focus attributes of the new player
            val attrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            promotedPlayer.setAudioAttributes(attrs, false)

            mediaSession?.player = buildForwardingPlayer(promotedPlayer)

            // Trigger the listener callback manually to synchronize all UI, Notification, and progress states!
            mainPlayerListener.onIsPlayingChanged(promotedPlayer.isPlaying)

            // Preload the next song into the newly initialized nextPlayer
            handler.postDelayed({ preloadNextSongIntoQueue() }, 300)
        }
    }

    private fun handlePlaybackCompleted() {
        if (isCrossfading && crossfadeNextSongStarted) return
        viewModel?.let { vm ->
            vm.setCurrentPosition(0)
            
            handler.post { vm.playNextSong() }
        }
    }

    private fun cleanCache() {
        val dayInMs = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()

        // One-time migration: delete legacy root-level "8d*" files
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean("migrated_8d_cache_to_subdir", false)) {
            cacheDir.listFiles()?.filter { it.name.startsWith("8d") }?.forEach { it.delete() }
            prefs.edit().putBoolean("migrated_8d_cache_to_subdir", true).apply()
        }

        // Clean cacheDir/8d/ subdirectory
        val eightDDir = File(cacheDir, "8d")
        if (eightDDir.exists()) {
            eightDDir.listFiles()?.filter { it.lastModified() < now - dayInMs }?.forEach { it.delete() }
        }
    }

    internal var isBitPerfectProvider: () -> Boolean = { isBitPerfectNow() }

    internal fun loadAudioPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isBitPerfect = isBitPerfectProvider()
        val enabled = prefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
        val mode = prefs.getString("crossfade_mode", "auto") ?: "auto"
        
        val customDuration = prefs.getFloat(KEY_CROSSFADE_DURATION, 3f)
        
        crossfadeDurationMs = computeCrossfadeDurationMs(
            crossfadeEnabled = enabled,
            crossfadeMode = mode,
            configuredDurationSec = customDuration,
            isBitPerfect = isBitPerfect
        )
        audioFocusEnabled = prefs.getBoolean(KEY_AUDIO_FOCUS, true)
        autoplayEnabled = prefs.getBoolean("autoplay_enabled", true)
        hapticPauseInBackground = prefs.getBoolean("haptic_pause_background", true)
        disconnectAction = prefs.getString(KEY_DISCONNECT_ACTION, "pause") ?: "pause"
        resumeOnConnect = prefs.getBoolean(KEY_RESUME_ON_CONNECT, false)
        Log.d(TAG, "loadAudioPreferences: audioFocusEnabled=$audioFocusEnabled, crossfadeDurationMs=$crossfadeDurationMs, autoplayEnabled=$autoplayEnabled, disconnectAction=$disconnectAction, resumeOnConnect=$resumeOnConnect, isBitPerfect=$isBitPerfect")

        if (crossfadeDurationMs == 0) {
            playerManager.cancelCrossfade()
        }
        
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        if (::playerManager.isInitialized) {
            player.setAudioAttributes(attrs, false)
        }
        nextPlayer?.setAudioAttributes(attrs, false)
    }

    private fun setupPreferenceListener() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            Log.d(TAG, "Preference changed: $key")
            if (key == "audio_quality" || key == "data_saver") {
                Log.d(TAG, "Audio quality preference changed ($key), flushing stream caches and updating format...")
                com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor.clearAllCaches()
                serviceScope.launch {
                    clearFormatForSongChange()
                    preloadNextSongIntoQueue()
                    val fmt = getActiveAudioFormat()
                    if (fmt.isNotEmpty()) viewModel?.updatePlaybackFormat(fmt)
                }
            }
            if (KEY_CROSSFADE_DURATION == key || KEY_CROSSFADE_ENABLED == key || "crossfade_mode" == key || KEY_AUDIO_FOCUS == key || "autoplay_enabled" == key || KEY_DISCONNECT_ACTION == key || KEY_RESUME_ON_CONNECT == key) {
                loadAudioPreferences()
            }
            if ("haptic_pause_background" == key) {
                hapticPauseInBackground = prefs.getBoolean("haptic_pause_background", true)
            }
            if (com.codetrio.spatialflow.ui.SettingsViewModel.KEY_VOLUME_NORMALIZATION_ENABLED == key || 
                com.codetrio.spatialflow.ui.SettingsViewModel.KEY_TARGET_LUFS == key) {
                updateBaseVolume()
            }
            if (key == com.codetrio.spatialflow.util.QualityModeSelector.KEY_NATIVE_DSP_MODE ||
                key == com.codetrio.spatialflow.util.QualityModeSelector.KEY_USB_EXCLUSIVE_MODE ||
                key == com.codetrio.spatialflow.util.QualityModeSelector.KEY_DAC_DIRECT_MODE ||
                key == com.codetrio.spatialflow.util.QualityModeSelector.KEY_DAC_BIT_PERFECT ||
                key == com.codetrio.spatialflow.util.QualityModeSelector.KEY_DAC_FIXED_VOLUME ||
                key == com.codetrio.spatialflow.util.QualityModeSelector.KEY_PLAYBACK_MODE
            ) {
                Log.d(TAG, "Audiophile preference changed ($key), refreshing DAC & DSP routing...")
                loadAudioPreferences()
                checkAndApplyDacRouting()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    /**
     * BroadcastReceiver for audio peripheral plug/unplug events (headset, bluetooth).
     * NOTE: This receiver handles ONLY playback policy (pause on disconnect, resume on connect).
     * Device routing and DAC configuration is owned exclusively by UsbDacManager's AudioDeviceCallback.
     */
    private fun registerAudioDeviceReceiver() {
        if (audioDeviceReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        audioDeviceReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                Log.d(TAG, "AudioDeviceReceiver action: $action")
                when (action) {
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        handleAudioDisconnected()
                    }
                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        if (state == 0) {
                            handleAudioDisconnected()
                        } else if (state == 1) {
                            handleAudioConnected()
                        }
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        handleAudioConnected()
                    }
                }
            }
        }
        try {
            ContextCompat.registerReceiver(
                this,
                audioDeviceReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register audioDeviceReceiver", e)
        }
    }

    private fun unregisterAudioDeviceReceiver() {
        audioDeviceReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            audioDeviceReceiver = null
        }
    }

    private fun handleAudioDisconnected() {
        Log.d(TAG, "handleAudioDisconnected: disconnectAction=$disconnectAction")
        wasPausedByFocusLoss = false
        when (disconnectAction) {
            "none" -> {
                // "none" -> do nothing
            }
            else -> {
                // "pause" -> default pause playback
                pause()
            }
        }
    }

    private fun handleAudioConnected() {
        Log.d(TAG, "handleAudioConnected: resumeOnConnect=$resumeOnConnect, isPlaying=${player.isPlaying}")
        if (resumeOnConnect && !player.isPlaying) {
            play()
        }
    }

    fun getPlayerInstance(): ExoPlayer = player

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    inner class StereoBalanceProcessor : androidx.media3.common.audio.BaseAudioProcessor() {
        private var currentBalance = 0f
        private var targetBalance = 0f

        fun setBalance(bal: Float) {
            targetBalance = bal.coerceIn(-1f, 1f)
        }

        @Volatile var enabled = true

        override fun onConfigure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
            val encoding = inputAudioFormat.encoding
            if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT &&
                encoding != C.ENCODING_PCM_24BIT && encoding != C.ENCODING_PCM_32BIT) {
                throw androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }
            // In bit-perfect mode or when DSP is bypassed, passthrough untouched original format (supports 24-bit / 32-bit to DAC)
            if (!enabled) {
                return inputAudioFormat
            }
            // If balance is centered (0f), passthrough untouched format
            if (targetBalance == 0f && currentBalance == 0f) {
                return inputAudioFormat
            }
            // Universal compatibility mode: output 16-bit stereo PCM
            return androidx.media3.common.audio.AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
        }

        @OptIn(UnstableApi::class)
        override fun queueInput(inputBuffer: ByteBuffer) {
            if (!inputBuffer.hasRemaining()) return

            if (!enabled || (targetBalance == 0f && currentBalance == 0f)) {
                val remaining = inputBuffer.remaining()
                val outBuffer = replaceOutputBuffer(remaining)
                outBuffer.put(inputBuffer)
                outBuffer.flip()
                return
            }

            val isStereo = inputAudioFormat.channelCount == 2
            val inEncoding = inputAudioFormat.encoding
            val inBytesPerSample = when (inEncoding) {
                C.ENCODING_PCM_16BIT -> 2
                C.ENCODING_PCM_24BIT -> 3
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
                else -> 2
            }
            val inputFrameSize = inBytesPerSample * inputAudioFormat.channelCount
            val frameCount = inputBuffer.remaining() / inputFrameSize
            if (frameCount <= 0) return

            // Output is ALWAYS 16-bit Stereo PCM: 2 bytes * 2 channels = 4 bytes per frame
            val outBuffer = replaceOutputBuffer(frameCount * 4)

            val leftVol = if (targetBalance > 0) 1f - targetBalance else 1f
            val rightVol = if (targetBalance < 0) 1f + targetBalance else 1f

            when (inEncoding) {
                C.ENCODING_PCM_16BIT -> {
                    while (inputBuffer.remaining() >= inputFrameSize) {
                        val left = inputBuffer.short
                        val right = if (isStereo) inputBuffer.short else left
                        outBuffer.putShort((left * leftVol).toInt().coerceIn(-32768, 32767).toShort())
                        outBuffer.putShort((right * rightVol).toInt().coerceIn(-32768, 32767).toShort())
                    }
                }
                C.ENCODING_PCM_FLOAT -> {
                    while (inputBuffer.remaining() >= inputFrameSize) {
                        val left = inputBuffer.float
                        val right = if (isStereo) inputBuffer.float else left
                        val sL = (left * leftVol * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                        val sR = (right * rightVol * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                        outBuffer.putShort(sL)
                        outBuffer.putShort(sR)
                    }
                }
                C.ENCODING_PCM_24BIT -> {
                    val inv24 = 1.0f / 8388608.0f
                    while (inputBuffer.remaining() >= inputFrameSize) {
                        val b0 = inputBuffer.get().toInt() and 0xFF
                        val b1 = inputBuffer.get().toInt() and 0xFF
                        val b2 = inputBuffer.get().toInt() and 0xFF
                        val raw24 = (b2 shl 16) or (b1 shl 8) or b0
                        val signed24 = if ((raw24 and 0x800000) != 0) raw24 or -0x1000000 else raw24
                        val sampleL = signed24 * inv24
                        val sampleR = if (isStereo) {
                            val r0 = inputBuffer.get().toInt() and 0xFF
                            val r1 = inputBuffer.get().toInt() and 0xFF
                            val r2 = inputBuffer.get().toInt() and 0xFF
                            val rawR = (r2 shl 16) or (r1 shl 8) or r0
                            val signedR = if ((rawR and 0x800000) != 0) rawR or -0x1000000 else rawR
                            signedR * inv24
                        } else sampleL
                        val sL = (sampleL * leftVol * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                        val sR = (sampleR * rightVol * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                        outBuffer.putShort(sL)
                        outBuffer.putShort(sR)
                    }
                }
                C.ENCODING_PCM_32BIT -> {
                    val inv32 = 1.0f / 2147483648.0f
                    while (inputBuffer.remaining() >= inputFrameSize) {
                        val sampleL = inputBuffer.int * inv32
                        val sampleR = if (isStereo) inputBuffer.int * inv32 else sampleL
                        val sL = (sampleL * leftVol * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                        val sR = (sampleR * rightVol * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                        outBuffer.putShort(sL)
                        outBuffer.putShort(sR)
                    }
                }
            }
            outBuffer.flip()
        }
    }

    private fun androidx.compose.ui.graphics.vector.ImageVector.toIconCompat(
        context: Context
    ): IconCompat {
        val density = context.resources.displayMetrics.density
        val sizePx = (28 * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        val scaleX = sizePx / viewportWidth
        val scaleY = sizePx / viewportHeight

        fun drawGroup(group: androidx.compose.ui.graphics.vector.VectorGroup) {
            for (node in group) {
                when (node) {
                    is androidx.compose.ui.graphics.vector.VectorPath -> {
                        val composePath = androidx.compose.ui.graphics.vector.PathParser().addPathNodes(node.pathData).toPath()
                        val nativePath = composePath.asAndroidPath()
                        val matrix = android.graphics.Matrix()
                        matrix.postScale(scaleX, scaleY)
                        nativePath.transform(matrix)
                        canvas.drawPath(nativePath, paint)
                    }
                    is androidx.compose.ui.graphics.vector.VectorGroup -> {
                        drawGroup(node)
                    }
                }
            }
        }
        drawGroup(root)
        return IconCompat.createWithBitmap(bitmap)
    }

    @OptIn(UnstableApi::class)
    private inner class CustomMediaNotificationProvider(context: Context) : 
        DefaultMediaNotificationProvider(context) {
        
        override fun addNotificationActions(
            mediaSession: MediaSession,
            mediaButtons: com.google.common.collect.ImmutableList<CommandButton>,
            notificationBuilder: androidx.core.app.NotificationCompat.Builder,
            actionFactory: androidx.media3.session.MediaNotification.ActionFactory
        ): IntArray {
            val isPlaying = player.isPlaying
            val displayTitle = currentSong?.title ?: currentSongName
            val displayArtist = currentSong?.artist ?: currentArtist.ifBlank { viewModel?.currentSong?.value?.artist ?: "Unknown Artist" }
            Log.d(TAG, "Notification update: isPlaying=$isPlaying, song=$displayTitle, artist=$displayArtist")
            
            notificationBuilder.setContentTitle(displayTitle)
            notificationBuilder.setContentText(displayArtist)
            notificationBuilder.setSubText(if (is8DEnabled) "SpatialFlow • 🎧 8D Audio" else "SpatialFlow")
            notificationBuilder.setChannelId("audio_playback_channel")
            notificationBuilder.setOngoing(isPlaying)
            notificationBuilder.setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            notificationBuilder.setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            notificationBuilder.setOnlyAlertOnce(true)
            notificationBuilder.setShowWhen(false)
            notificationBuilder.setCategory(androidx.core.app.NotificationCompat.CATEGORY_TRANSPORT)
            
            (notificationAlbumArt ?: currentAlbumArt)?.let {
                notificationBuilder.setLargeIcon(it)
            }

            // Order: Mode | Prev | Play | Next | Fav
            notificationBuilder.clearActions()
            
            // 0: Playback mode cycle: Shuffle -> Loop One -> Loop
            val mode = currentNotificationPlaybackMode(viewModel)
            val modeIconKey = when (mode) {
                NotificationPlaybackMode.SHUFFLE -> "shuffle"
                NotificationPlaybackMode.LOOP_ONE -> "repeat_one"
                NotificationPlaybackMode.LOOP_ALL -> "repeat"
            }
            val modeIcon = iconCache.getValue(modeIconKey)
            notificationBuilder.addAction(
                androidx.core.app.NotificationCompat.Action.Builder(
                    modeIcon,
                    "Mode",
                    getActionIntent(ACTION_CYCLE_PLAYBACK_MODE, 10)
                ).build()
            )
            
            // 1: Previous
            val prevIcon = iconCache.getValue("previous")
            notificationBuilder.addAction(
                androidx.core.app.NotificationCompat.Action.Builder(
                    prevIcon,
                    "Previous",
                    getActionIntent(ACTION_PREVIOUS, 11)
                ).build()
            )
            
            // 2: Play/Pause
            val playPauseIconKey = if (isPlaying) "pause" else "play"
            val playPauseIcon = iconCache.getValue(playPauseIconKey)
            notificationBuilder.addAction(
                androidx.core.app.NotificationCompat.Action.Builder(
                    playPauseIcon,
                    if (isPlaying) "Pause" else "Play",
                    if (isPlaying) getActionIntent(ACTION_PAUSE, 12) else getActionIntent(ACTION_PLAY, 13)
                ).build()
            )
            
            // 3: Next
            val nextIcon = iconCache.getValue("next")
            notificationBuilder.addAction(
                androidx.core.app.NotificationCompat.Action.Builder(
                    nextIcon,
                    "Next",
                    getActionIntent(ACTION_NEXT, 14)
                ).build()
            )
            
            // 4: Favorite
            val isFav = viewModel?.isCurrentSongFavorite?.value == true
            val favIconKey = if (isFav) "favorite" else "favorite_border"
            val favIcon = iconCache.getValue(favIconKey)
            notificationBuilder.addAction(
                androidx.core.app.NotificationCompat.Action.Builder(
                    favIcon,
                    "Favorite",
                    getActionIntent(ACTION_TOGGLE_FAV, 15)
                ).build()
            )
            
            // Compact view indices (Prev, Play, Next)
            val style = androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(1, 2, 3)
            notificationBuilder.setStyle(style)

            return intArrayOf(1, 2, 3)
        }
    }

    private fun updateBaseVolume() {
        val prefs = getSharedPreferences(com.codetrio.spatialflow.ui.SettingsViewModel.PREFS_NAME,
            MODE_PRIVATE
        )
        val enabled = prefs.getBoolean(com.codetrio.spatialflow.ui.SettingsViewModel.KEY_VOLUME_NORMALIZATION_ENABLED, false)
        
        Log.d(TAG, "updateBaseVolume: normalizationEnabled=$enabled")
        
        if (!enabled) {
            currentBaseVolume = 1.0f
            applyCurrentBaseVolume()
            return
        }

        val targetLufs = prefs.getFloat(com.codetrio.spatialflow.ui.SettingsViewModel.KEY_TARGET_LUFS, -14f)
        val song = viewModel?.currentSong?.value
        
        Log.d(TAG, "updateBaseVolume: targetLufs=$targetLufs, song=${song?.title}, videoId=${song?.videoId}")
        
        if (song == null) return
        
        if (song.lufs != null) {
            Log.d(TAG, "updateBaseVolume: Using pre-cached LUFS (${song.lufs})")
            applyLufsGain(song.lufs!!, targetLufs)
        } else if (song.videoId.isNullOrEmpty() && !song.path.isNullOrEmpty() && song.path.startsWith("http") == false) {
            // Local file with missing LUFS metadata
            Log.d(TAG, "updateBaseVolume: Analyzing local file LUFS for ${song.path}")
            serviceScope.launch(Dispatchers.IO) {
                val measured = com.codetrio.spatialflow.player.LoudnessAnalyzer.analyzeLufsFast(song.path)
                if (measured != null) {
                    // Update in-memory so we don't re-analyze
                    song.lufs = measured
                    // Save to playlist db if applicable
                    val db = com.codetrio.spatialflow.data.db.MusicDatabase.getDatabase(applicationContext)
                    db.playlistDao().updateSongLufs(song.id.toString(), measured)
                    
                    withContext(Dispatchers.Main) {
                        applyLufsGain(measured, targetLufs)
                    }
                }
            }
        } else if (!song.videoId.isNullOrEmpty()) {
            // Online stream: fetch true loudness offset from YouTube Music API
            Log.d(TAG, "updateBaseVolume: Fetching online loudnessDb for videoId=${song.videoId}")
            serviceScope.launch(Dispatchers.IO) {
                val loudnessDb = YouTubeMusic.getLoudnessDb(song.videoId!!).getOrNull()
                val estimatedLufs = if (loudnessDb != null) {
                    // YouTube target is roughly -14 LUFS. The loudnessDb is the relative offset.
                    -14.0f + loudnessDb
                } else {
                    // Fallback if API doesn't provide the loudness
                    Log.d(TAG, "updateBaseVolume: Falling back to -10 LUFS")
                    -10f 
                }
                
                withContext(Dispatchers.Main) {
                    applyLufsGain(estimatedLufs, targetLufs)
                }
            }
        } else {
            // Unrecognized streams get a fallback normalization of -10 LUFS
            Log.d(TAG, "updateBaseVolume: Unrecognized stream, using fallback -10 LUFS")
            applyLufsGain(-10f, targetLufs)
        }
    }

    private fun applyLufsGain(lufs: Float, targetLufs: Float) {
        val gain = com.codetrio.spatialflow.player.VolumeNormalizer.calculateGain(lufs, targetLufs)
        val linearVolume = com.codetrio.spatialflow.player.VolumeNormalizer.dbToLinear(gain)
        
        // ExoPlayer's volume property is a multiplier from 0.0 to 1.0. It cannot amplify > 100%.
        // If linearVolume > 1.0, it means the target LUFS is louder than the song's intrinsic loudness.
        currentBaseVolume = linearVolume.coerceIn(0.0f, 1.0f)
        
        if (linearVolume > 1.0f) {
            Log.w(TAG, "Volume Normalization: Requested gain ($gain dB) requires volume > 100% ($linearVolume). Clamped to 1.0. Target LUFS ($targetLufs) may be set too high.")
        } else {
            Log.d(TAG, "Applied Volume Normalization: lufs=$lufs, target=$targetLufs, gain=$gain, baseVol=$currentBaseVolume")
        }
        
        applyCurrentBaseVolume()
    }

    private fun applyCurrentBaseVolume() {
        if (!::playerManager.isInitialized) return
        val dacConnected = com.codetrio.spatialflow.service.dac.UsbDacManager.isDacConnected.value
        val isBitPerfect = dacConnected && com.codetrio.spatialflow.util.QualityModeSelector.getPlaybackMode(this) == com.codetrio.spatialflow.util.PlaybackMode.BIT_PERFECT
        if (isBitPerfect) {
            player.volume = 1.0f
            return
        }
        if (!isCrossfading && player.volume != 0f) {
            player.volume = currentBaseVolume
        }
    }

    private data class FilterBank(
        var leftCombs: Array<CombFilter> = emptyArray(),
        var rightCombs: Array<CombFilter> = emptyArray(),
        var leftAllpasses: Array<AllpassFilter> = emptyArray(),
        var rightAllpasses: Array<AllpassFilter> = emptyArray()
    ) {
        fun clearBuffers() {
            leftCombs.forEach { it.clear() }
            rightCombs.forEach { it.clear() }
            leftAllpasses.forEach { it.clear() }
            rightAllpasses.forEach { it.clear() }
        }
    }

    class ReverbAudioProcessor : androidx.media3.common.audio.BaseAudioProcessor() {
        @Volatile var enabled = false
        @Volatile var intensity = 0f
        @Volatile var preset = 0 // 0=None, 1=Room, 2=Hall, 3=Stadium, 4=Plate, 5=Spring

        private var activeSampleRate = 44100
        private var activePreset = -1
        
        @Volatile
        private var filterSwitch = false
        private var filterSetA: FilterBank? = null
        private var filterSetB: FilterBank? = null

        private val baseCombSizes = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val baseAllpassSizes = intArrayOf(556, 441, 341, 225)
        private val stereoSpread = 23

        fun clearBuffers() {
            filterSetA?.clearBuffers()
            filterSetB?.clearBuffers()
        }

        private fun updateEngineParameters(sampleRate: Int) {
            if (sampleRate == activeSampleRate && preset == activePreset && (filterSetA != null || filterSetB != null)) {
                return
            }
            activeSampleRate = sampleRate
            activePreset = preset

            val rateScale = (sampleRate.toFloat() / 44100f).coerceIn(0.5f, 2.2f)

            val (sizeScale, feedback, damp) = when (preset) {
                1 -> Triple(0.5f, 0.72f, 0.45f)   // Room: small size, high damp
                2 -> Triple(1.0f, 0.84f, 0.25f)   // Hall: medium size, balanced damp
                3 -> Triple(2.0f, 0.92f, 0.15f)   // Stadium: massive size, low damp
                4 -> Triple(0.85f, 0.80f, 0.08f)  // Plate: bright, very low damp
                5 -> Triple(0.75f, 0.85f, 0.35f)  // Spring: fluttery, warm
                else -> Triple(1.0f, 0.5f, 0.2f)
            }

            val newFilters = FilterBank()

            newFilters.leftCombs = Array(baseCombSizes.size) { i ->
                val size = (baseCombSizes[i] * rateScale * sizeScale).toInt().coerceIn(10, 8192)
                CombFilter(size, feedback, damp)
            }
            newFilters.rightCombs = Array(baseCombSizes.size) { i ->
                val size = ((baseCombSizes[i] + stereoSpread) * rateScale * sizeScale).toInt().coerceIn(10, 8192)
                CombFilter(size, feedback, damp)
            }

            newFilters.leftAllpasses = Array(baseAllpassSizes.size) { i ->
                val size = (baseAllpassSizes[i] * rateScale * sizeScale).toInt().coerceIn(5, 4096)
                AllpassFilter(size, 0.5f)
            }
            newFilters.rightAllpasses = Array(baseAllpassSizes.size) { i ->
                val size = ((baseAllpassSizes[i] + stereoSpread) * rateScale * sizeScale).toInt().coerceIn(5, 4096)
                AllpassFilter(size, 0.5f)
            }
            
            // Atomic swap
            if (filterSwitch) {
                filterSetA = newFilters
            } else {
                filterSetB = newFilters
            }
            filterSwitch = !filterSwitch
        }

        override fun onConfigure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
            if (!enabled || intensity <= 0.01f || preset == 0) {
                return inputAudioFormat
            }
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
                return androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
            }
            return androidx.media3.common.audio.AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
        }

        @Deprecated("Deprecated in Java")
        override fun onFlush() {
            clearBuffers()
        }

        override fun onReset() {
            filterSetA = null
            filterSetB = null
        }

        private val reusablePair = SamplePair()

        private fun processSample(left: Float, right: Float): SamplePair {
            val activeFilters = if (filterSwitch) filterSetB else filterSetA
            
            if (activeFilters == null || activeFilters.leftCombs.isEmpty()) {
                reusablePair.left = left
                reusablePair.right = right
                return reusablePair
            }
            
            var combOutL = 0f
            var combOutR = 0f
            for (i in activeFilters.leftCombs.indices) {
                combOutL += activeFilters.leftCombs[i].process(left)
                combOutR += activeFilters.rightCombs[i].process(right)
            }

            // Attenuate parallel comb filter sum (1/8) to keep level safe and avoid clipping
            combOutL *= 0.125f
            combOutR *= 0.125f

            var apfOutL = combOutL
            var apfOutR = combOutR
            for (i in activeFilters.leftAllpasses.indices) {
                apfOutL = activeFilters.leftAllpasses[i].process(apfOutL)
                apfOutR = activeFilters.rightAllpasses[i].process(apfOutR)
            }

            // Mix original (dry) and reverberated (wet) signal based on intensity
            val wet = intensity * 0.25f
            val dry = 1.0f - (intensity * 0.15f)
            
            reusablePair.left = softClip(left * dry + apfOutL * wet)
            reusablePair.right = softClip(right * dry + apfOutR * wet)

            return reusablePair
        }

        private fun softClip(x: Float): Float {
            return when {
                x > 0.7f -> {
                    val diff = x - 0.7f
                    0.7f + 0.3f * (diff / (0.3f + diff))
                }
                x < -0.7f -> {
                    val diff = -x - 0.7f
                    -0.7f - 0.3f * (diff / (0.3f + diff))
                }
                else -> x
            }
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            if (!inputBuffer.hasRemaining()) return

            if (!enabled || intensity <= 0.01f || preset == 0) {
                val remaining = inputBuffer.remaining()
                val outBuffer = replaceOutputBuffer(remaining)
                outBuffer.put(inputBuffer)
                outBuffer.flip()
                return
            }

            val isStereo = inputAudioFormat.channelCount == 2
            val is16Bit = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
            val bytesPerSample = if (is16Bit) 2 else 4
            val inputFrameSize = bytesPerSample * inputAudioFormat.channelCount
            val frameCount = inputBuffer.remaining() / inputFrameSize
            val outBuffer = replaceOutputBuffer(frameCount * 2 * bytesPerSample)

            updateEngineParameters(inputAudioFormat.sampleRate)

            if (is16Bit) {
                while (inputBuffer.hasRemaining()) {
                    val leftShort = inputBuffer.short
                    val rightShort = if (isStereo) inputBuffer.short else leftShort
                    
                    val leftIn = leftShort.toFloat() / 32768.0f
                    val rightIn = rightShort.toFloat() / 32768.0f
                    
                    val outPair = processSample(leftIn, rightIn)
                    
                    val leftClamped = (outPair.left * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort()
                    val rightClamped = (outPair.right * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort()
                    
                    outBuffer.putShort(leftClamped)
                    outBuffer.putShort(rightClamped)
                }
            } else {
                while (inputBuffer.hasRemaining()) {
                    val leftIn = inputBuffer.float
                    val rightIn = if (isStereo) inputBuffer.float else leftIn
                    
                    val outPair = processSample(leftIn, rightIn)
                    
                    outBuffer.putFloat(outPair.left)
                    outBuffer.putFloat(outPair.right)
                }
            }
            outBuffer.flip()
        }
    }
}


private class CombFilter(val size: Int, feedbackCoeff: Float, dampingCoeff: Float) {
    private val buffer = FloatArray(size)
    private var index = 0
    private var lastOutput = 0f
    
    // Clamp feedback to prevent instability
    private val feedback = feedbackCoeff.coerceIn(0.0f, 0.95f)
    private val damp = dampingCoeff.coerceIn(0.0f, 0.99f)
    
    // Subnormal number prevention (flush-to-zero)
    private val minAmp = 1e-10f

    fun process(input: Float): Float {
        val output = buffer[index]
        
        // Apply damping filter with state clamping
        lastOutput = lastOutput * (1.0f - damp) + output * damp
        lastOutput = if (kotlin.math.abs(lastOutput) < minAmp) 0f else lastOutput
        
        // Feedback loop with amplitude ceiling to prevent explosion
        val feedbackSample = (lastOutput * feedback).coerceIn(-1.0f, 1.0f)
        
        buffer[index] = (input + feedbackSample).coerceIn(-1.5f, 1.5f)
        
        index++
        if (index >= size) {
            index = 0
        }
        return output
    }
    
    fun clear() {
        buffer.fill(0f)
        lastOutput = 0f
        index = 0
    }
}

private class AllpassFilter(val size: Int, val feedback: Float) {
    private val buffer = FloatArray(size)
    private var index = 0
    private val minAmp = 1e-10f

    fun process(input: Float): Float {
        val bufOut = buffer[index]
        
        val output = -input + bufOut
        
        buffer[index] = (input + (bufOut * feedback)).coerceIn(-1.5f, 1.5f)
        
        index++
        if (index >= size) {
            index = 0
        }
        
        return if (kotlin.math.abs(output) < minAmp) 0f else output
    }
    
    fun clear() {
        buffer.fill(0f)
        index = 0
    }
}

private class SamplePair {
    var left = 0f
    var right = 0f
}

