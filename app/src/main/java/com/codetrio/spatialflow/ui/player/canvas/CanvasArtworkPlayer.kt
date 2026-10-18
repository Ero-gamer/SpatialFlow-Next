@file:OptIn(androidx.media3.common.util.UnstableApi::class)
@file:Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")

package com.codetrio.spatialflow.ui.player.canvas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale

private const val STALL_CHECK_INTERVAL_MS = 1_000L
private const val STALL_TIMEOUT_MS = 5_000L
private const val CANVAS_MAX_VIDEO_WIDTH = 1_920
private const val CANVAS_MAX_VIDEO_HEIGHT = 1_920

/**
 * Renders a looped, muted motion artwork video using ExoPlayer on a TextureView surface.
 *
 * - [primaryUrl]: Preferred URL (typically HLS m3u8 or direct MP4).
 * - [fallbackUrl]: Fallback URL (typically a cached local MP4 file URI).
 * - Implements a 5-second stall detector: if the primary HLS stream stalls, automatically
 *   switches to [fallbackUrl].
 * - Crossfades from transparent to fully opaque over 300ms after the first frame renders,
 *   hiding the initial black frame.
 */
@Composable
internal fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    visible: Boolean = true,
    maxVideoEdgePx: Int? = null,
    syncToPlayer: Player? = null,
    isSyncSlave: Boolean = false,
    onPlayerCreated: ((ExoPlayer) -> Unit)? = null,
    onVideoAspectRatioDetected: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    val initial = primary ?: fallback ?: return

    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    val shouldPlay by rememberUpdatedState(isPlaying && visible)

    val dataSourceFactory = remember(context) {
        CanvasPlayerResources.getDataSourceFactory(context)
    }

    val mediaSourceFactory = remember(context, dataSourceFactory) {
        DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    }

    // Lightweight load control optimized for short, looping motion artwork (saves ~80% memory)
    val canvasLoadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 4_000,
                /* maxBufferMs = */ 12_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(/* backBufferDurationMs = */ 0, /* retainBackBufferFromKeyframe = */ false)
            .build()
    }

    val exoPlayer = remember(initial, mediaSourceFactory, maxVideoEdgePx) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(canvasLoadControl)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .let { params ->
                        if (maxVideoEdgePx != null) {
                            // Backdrop player: blurred at 72dp, capped resolution & conservative bitrate to relieve hardware decoders
                            params.setForceHighestSupportedBitrate(false)
                                .setMaxVideoSize(maxVideoEdgePx, maxVideoEdgePx)
                                .setMaxVideoBitrate(1_500_000)
                        } else {
                            // Foreground sharp stage: force highest bitrate for pristine visual sharpness
                            params.setForceHighestSupportedBitrate(true)
                        }
                    }
                    .build()
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = isPlaying && visible
            }
    }

    LaunchedEffect(isPlaying, visible) {
        exoPlayer.setCanvasPlayback(isPlaying && visible)
    }

    // ── Stall monitor: switch from primary HLS to fallback MP4 after 5 s of no position advance ──
    LaunchedEffect(currentUrl, isPlaying, primary, fallback, exoPlayer) {
        if (!isPlaying || fallback.isNullOrBlank() || currentUrl != primary) return@LaunchedEffect
        var lastPosition = exoPlayer.currentPosition
        var stalledForMs = 0L

        while (isActive && isPlaying && currentUrl == primary) {
            delay(STALL_CHECK_INTERVAL_MS)
            val currentPosition = exoPlayer.currentPosition
            val isActivelyRendering = exoPlayer.playbackState == Player.STATE_READY &&
                exoPlayer.isPlaying && currentPosition != lastPosition
            stalledForMs = if (isActivelyRendering) 0L else stalledForMs + STALL_CHECK_INTERVAL_MS
            if (stalledForMs >= STALL_TIMEOUT_MS) {
                currentUrl = fallback
                isVideoReady = false
                return@LaunchedEffect
            }
            lastPosition = currentPosition
        }
    }

    // ── Lifecycle: pause/resume with app lifecycle (Strict battery preservation) ──
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.setCanvasPlayback(shouldPlay)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.pause()
        }
    }

    // ── Error / state listeners ──
    DisposableEffect(exoPlayer, primary, fallback) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w("CanvasArtworkPlayer", "Canvas playback error for $currentUrl: ${error.message}", error)
                val next = when (currentUrl) {
                    primary -> fallback?.takeIf { it != currentUrl }
                    else -> null
                }
                if (!next.isNullOrBlank()) {
                    currentUrl = next
                    isVideoReady = false
                }
            }

            override fun onRenderedFirstFrame() {
                isVideoReady = true
                if (shouldPlay) exoPlayer.setCanvasPlayback(isPlaying = true)
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                    onVideoAspectRatioDetected?.invoke(ratio)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isVideoReady = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // ── Load new URL into player ──
    LaunchedEffect(currentUrl, exoPlayer) {
        val normalized = currentUrl.trim()
        isVideoReady = false
        val lowercaseUrl = normalized.lowercase(Locale.ROOT)
        val mimeType = when {
            lowercaseUrl.contains(".m3u8") || lowercaseUrl.contains("format=m3u8") || lowercaseUrl.contains("/hls/") -> MimeTypes.APPLICATION_M3U8
            lowercaseUrl.contains(".mp4") || lowercaseUrl.contains("canvaz") || lowercaseUrl.contains("canvas") || lowercaseUrl.contains("hf.space") || lowercaseUrl.contains("video") || lowercaseUrl.contains("stream") -> MimeTypes.VIDEO_MP4
            else -> null // Auto-detect container format without forcing M3U8
        }

        val mediaItemBuilder = MediaItem.Builder().setUri(normalized)
        if (mimeType != null) {
            mediaItemBuilder.setMimeType(mimeType)
        }
        val mediaItem = mediaItemBuilder.build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.setCanvasPlayback(isPlaying)
    }

    val currentOnPlayerCreated by rememberUpdatedState(onPlayerCreated)
    DisposableEffect(exoPlayer) {
        currentOnPlayerCreated?.invoke(exoPlayer)
        onDispose { exoPlayer.release() }
    }

    // ── Synchronization for backdrop slave player ──
    DisposableEffect(exoPlayer, syncToPlayer, isSyncSlave) {
        if (!isSyncSlave || syncToPlayer == null) return@DisposableEffect onDispose {}

        val syncListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                exoPlayer.setCanvasPlayback(isPlaying && visible)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                    reason == Player.DISCONTINUITY_REASON_SEEK
                ) {
                    exoPlayer.seekTo(newPosition.positionMs)
                }
            }
        }

        syncToPlayer.addListener(syncListener)

        if (syncToPlayer.isPlaying) {
            val masterPos = syncToPlayer.currentPosition
            if (masterPos > 0) {
                exoPlayer.seekTo(masterPos)
            }
            exoPlayer.setCanvasPlayback(visible)
        }

        onDispose {
            syncToPlayer.removeListener(syncListener)
        }
    }

    LaunchedEffect(exoPlayer, syncToPlayer, isSyncSlave) {
        if (!isSyncSlave || syncToPlayer == null) return@LaunchedEffect
        while (isActive) {
            delay(2500L)
            if (syncToPlayer.isPlaying && exoPlayer.isPlaying && !exoPlayer.isLoading) {
                val masterPos = syncToPlayer.currentPosition
                val slavePos = exoPlayer.currentPosition
                val drift = kotlin.math.abs(masterPos - slavePos)
                // Only correct substantial drift (> 1000ms) to avoid clearing decoder buffers and causing frame drops
                if (drift > 1000L) {
                    exoPlayer.seekTo(masterPos)
                }
            }
        }
    }

    // ── 300 ms crossfade on first frame ──
    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha",
    )

    if (visible) {
        ContentFrame(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = resizeMode.toContentScale(),
            keepContentOnReset = false,
            shutter = {},
            modifier = modifier
                .clipToBounds()
                .graphicsLayer { this.alpha = alpha },
        )
    }
}

private fun Int.toContentScale(): ContentScale = when (this) {
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop
    AspectRatioFrameLayout.RESIZE_MODE_FILL -> ContentScale.FillBounds
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> ContentScale.FillWidth
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT -> ContentScale.FillHeight
    else -> ContentScale.Fit
}

private fun ExoPlayer.setCanvasPlayback(isPlaying: Boolean) {
    if (isPlaying) {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        play()
    } else {
        pause()
    }
}

private const val CANVAS_PLAYBACK_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

internal object CanvasPlayerResources {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val okHttpDataSourceFactory: OkHttpDataSource.Factory by lazy {
        OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(CANVAS_PLAYBACK_UA)
    }

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Synchronized
    private fun getCache(context: android.content.Context): SimpleCache {
        return simpleCache ?: run {
            val cacheDir = File(context.applicationContext.cacheDir, "canvas_stream_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(50L * 1024L * 1024L) // 50MB shared LRU
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            SimpleCache(cacheDir, evictor, databaseProvider).also { simpleCache = it }
        }
    }

    fun clearCache() {
        simpleCache?.let { cache ->
            try {
                cache.keys.toList().forEach { key ->
                    try { cache.removeResource(key) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun getDataSourceFactory(context: android.content.Context): DataSource.Factory {
        val upstreamFactory = DefaultDataSource.Factory(context.applicationContext, okHttpDataSourceFactory)
        val cache = getCache(context)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
