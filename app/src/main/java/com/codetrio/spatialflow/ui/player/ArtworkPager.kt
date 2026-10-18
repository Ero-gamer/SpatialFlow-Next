package com.codetrio.spatialflow.ui.player

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlayer
import com.codetrio.spatialflow.ui.player.metrics.computeScreenMetrics
import com.codetrio.spatialflow.ui.theme.observeKey
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtworkPager(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem,
    songList: List<SongItem>,
    currentSongIndex: Int,
    context: Context,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    allowCanvas: Boolean = true,
    isPlaying: Boolean? = null
) {
    val pagerState = rememberPagerState(
        initialPage = currentSongIndex.coerceAtLeast(0)
    ) {
        songList.size.coerceAtLeast(1)
    }

    // Single top-level state collection for active canvas artwork (Phase-Optimized)
    val activeCanvasArtwork by viewModel.canvasArtwork.collectAsStateWithLifecycle()

    val prefs = remember(context) { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
    val isSwipeToSkipEnabled by prefs.observeKey("swipe_to_skip", true)
    val showAnimatedArtPref by prefs.observeKey("show_animated_art", true)
    val artworkTransition by prefs.observeKey("artwork_transition", "shuffle")

    val latestCurrentSongIndex by rememberUpdatedState(currentSongIndex)
    val latestSongList by rememberUpdatedState(songList)

    // Synchronize Pager user swipe completion -> ViewModel (only for slide transition)
    LaunchedEffect(pagerState, artworkTransition) {
        if (artworkTransition == "slide") {
            snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { (page, isScrolling) ->
                    if (!isScrolling && page in latestSongList.indices && page != latestCurrentSongIndex) {
                        viewModel.playSongAtIndex(page)
                    }
                }
        }
    }

    // Synchronize ViewModel active index -> Pager page index instantly (only for slide transition)
    LaunchedEffect(currentSongIndex, artworkTransition) {
        if (artworkTransition == "slide" && currentSongIndex in 0 until pagerState.pageCount && pagerState.currentPage != currentSongIndex) {
            pagerState.scrollToPage(currentSongIndex)
        }
    }

    // Pre-fetch adjacent track artwork into Coil memory cache for zero-lag track switching
    LaunchedEffect(currentSongIndex, songList, context) {
        withContext(Dispatchers.IO) {
            val adjacentIndices = listOf(currentSongIndex - 1, currentSongIndex + 1)
            val loader = context.imageLoader
            for (idx in adjacentIndices) {
                val adjacentSong = songList.getOrNull(idx) ?: continue
                val rawUri = adjacentSong.getAlbumArtUri()
                val videoId = adjacentSong.videoId
                val url = when {
                    rawUri != null && rawUri.toString().isNotEmpty() -> SongItem.enhanceThumbnailUrl(rawUri.toString())
                    !videoId.isNullOrEmpty() -> "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                    else -> null
                }
                if (!url.isNullOrEmpty()) {
                    val req = ImageRequest.Builder(context)
                        .data(url)
                        .precision(Precision.INEXACT)
                        .allowHardware(true)
                        .diskCacheKey(url)
                        .memoryCacheKey(url)
                        .build()
                    loader.enqueue(req)
                }
            }
        }
    }

    val effectiveUserScrollEnabled = userScrollEnabled && isSwipeToSkipEnabled
    val effectiveShowAnimatedArt = showAnimatedArtPref

    val onSwipeNextAction: (() -> Unit)? = if (effectiveUserScrollEnabled) {
        {
            val nextIdx = currentSongIndex + 1
            if (nextIdx in songList.indices) {
                viewModel.playSongAtIndex(nextIdx)
            }
        }
    } else null

    val onSwipePrevAction: (() -> Unit)? = if (effectiveUserScrollEnabled) {
        {
            val prevIdx = currentSongIndex - 1
            if (prevIdx in songList.indices) {
                viewModel.playSongAtIndex(prevIdx)
            }
        }
    } else null

    val isPlayingState = isPlaying ?: viewModel.isPlaying.collectAsStateWithLifecycle().value
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
    val isHalfExpanded by remember(viewModel) {
        viewModel.playerExpansionFraction
            .map { it >= 0.5f }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val metrics = computeScreenMetrics()
    val isTwoPane = metrics.isLandscape

    // In Full Player (or 2-pane landscape), when playback is paused, scale artwork down (Apple Music / Expressive spec: 0.85f).
    // In mini-player or top-bar thumbnail (lyrics mode in portrait), keep artwork at 1.0f to preserve slot sizing.
    val shouldScaleDown = (isPlayerExpanded || isHalfExpanded) &&
            (!isLyricsModeEnabled || isTwoPane) &&
            !isPlayingState

    val targetScale = if (shouldScaleDown) 0.85f else 1.0f

    val playPauseScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        label = "PlayPauseArtworkScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = playPauseScale
                scaleY = playPauseScale
                transformOrigin = TransformOrigin.Center
            },
        contentAlignment = Alignment.Center
    ) {
        when (artworkTransition) {
            "flip" -> {
                FlipArtworkTransition(
                    currentSong = currentSong,
                    context = context,
                    modifier = Modifier.fillMaxSize(),
                    showAnimatedArt = effectiveShowAnimatedArt,
                    allowCanvas = allowCanvas,
                    activeCanvasArtwork = activeCanvasArtwork,
                    onSwipeNext = onSwipeNextAction,
                    onSwipePrevious = onSwipePrevAction
                )
            }
            "push" -> {
                PushArtworkTransition(
                    currentSong = currentSong,
                    context = context,
                    modifier = Modifier.fillMaxSize(),
                    showAnimatedArt = effectiveShowAnimatedArt,
                    allowCanvas = allowCanvas,
                    activeCanvasArtwork = activeCanvasArtwork,
                    onSwipeNext = onSwipeNextAction,
                    onSwipePrevious = onSwipePrevAction
                )
            }
            "crossfade" -> {
                CrossfadeArtworkTransition(
                    currentSong = currentSong,
                    context = context,
                    modifier = Modifier.fillMaxSize(),
                    showAnimatedArt = effectiveShowAnimatedArt,
                    allowCanvas = allowCanvas,
                    activeCanvasArtwork = activeCanvasArtwork,
                    onSwipeNext = onSwipeNextAction,
                    onSwipePrevious = onSwipePrevAction
                )
            }
            "slide" -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalPager(
                        state = pagerState,
                        key = { page -> "artwork_${page}_${songList.getOrNull(page)?.id ?: "none"}" },
                        userScrollEnabled = effectiveUserScrollEnabled,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val song = if (page == currentSongIndex) currentSong else (songList.getOrNull(page) ?: currentSong)
                        val isCurrentPage = page == currentSongIndex

                        // Hardware-accelerated 3D Parallax transformation (Zero Recompositions via deferred reads)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                    val absOffset = abs(pageOffset)
                                    val fraction = absOffset.coerceIn(0f, 1f)

                                    val scale = lerp(1f, 0.72f, fraction)
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = pageOffset * (size.width * 0.20f)
                                    rotationX = fraction * 12f
                                    rotationY = (pageOffset * -20f).coerceIn(-30f, 30f)
                                    cameraDistance = 16f * density
                                    alpha = lerp(1f, 0.55f, fraction)
                                    shape = RoundedCornerShape(20.dp)
                                    shadowElevation = lerp(10.dp.toPx(), 0f, fraction)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            ArtworkPageItem(
                                song = song,
                                isCurrentPage = isCurrentPage,
                                showAnimatedArt = effectiveShowAnimatedArt,
                                allowCanvas = allowCanvas,
                                activeCanvasArtwork = if (isCurrentPage) activeCanvasArtwork else null,
                                context = context
                            )
                        }
                    }
                }
            }
            else -> {
                // Default "shuffle" (3D dolly deal)
                ShuffleArtworkTransition(
                    currentSong = currentSong,
                    context = context,
                    modifier = Modifier.fillMaxSize(),
                    showAnimatedArt = effectiveShowAnimatedArt,
                    allowCanvas = allowCanvas,
                    activeCanvasArtwork = activeCanvasArtwork,
                    onSwipeNext = onSwipeNextAction,
                    onSwipePrevious = onSwipePrevAction
                )
            }
        }
    }
}

@Composable
internal fun ArtworkPageItem(
    song: SongItem,
    isCurrentPage: Boolean,
    showAnimatedArt: Boolean,
    allowCanvas: Boolean,
    activeCanvasArtwork: CanvasArtwork?,
    context: Context
) {
    val artworkModel: Any? = remember(song.id, song.thumbnailUrl, song.videoId, song.path) {
        val thumb = song.thumbnailUrl
        val uri = song.getAlbumArtUri()
        val vid = song.videoId
        when {
            !thumb.isNullOrBlank() -> SongItem.enhanceThumbnailUrl(thumb)
            uri != null && uri.toString().isNotBlank() -> uri
            !vid.isNullOrBlank() -> "https://img.youtube.com/vi/$vid/hqdefault.jpg"
            else -> null
        }
    }

    var localEmbeddedArt by remember(song.id, song.path) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(song.id, song.path) {
        val p = song.path
        if (artworkModel == null && p != null && !p.startsWith("http")) {
            withContext(Dispatchers.IO) {
                localEmbeddedArt = song.getEmbeddedPicture(context)
            }
        }
    }

    val finalModel = artworkModel ?: localEmbeddedArt

    val effectiveCanvasArtwork = remember(song.id, isCurrentPage, showAnimatedArt, activeCanvasArtwork, song.animatedThumbnailUrl) {
        if (!showAnimatedArt || !isCurrentPage) {
            null
        } else if (activeCanvasArtwork != null) {
            activeCanvasArtwork
        } else if (!song.animatedThumbnailUrl.isNullOrEmpty()) {
            CanvasArtwork(
                animated = song.animatedThumbnailUrl,
                videoUrl = song.animatedThumbnailUrl,
            )
        } else {
            null
        }
    }

    val canvasPrimaryUrl = effectiveCanvasArtwork?.preferredVerticalAnimationUrl ?: effectiveCanvasArtwork?.preferredAnimationUrl
    val canvasFallbackUrl = effectiveCanvasArtwork?.videoUrlVertical ?: effectiveCanvasArtwork?.videoUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Base placeholder layer with music note
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        // Static album art layer using SpatialImage
        if (finalModel != null) {
            SpatialImage(
                model = finalModel,
                contentDescription = song.title,
                size = ImageSemanticSize.Original,
                shape = RoundedCornerShape(20.dp),
                crossfade = false,
                showShimmer = false,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Canvas motion artwork overlay
        if (showAnimatedArt && allowCanvas && (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank())) {
            AnimatedVisibility(
                visible = isCurrentPage,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.fillMaxSize()
            ) {
                CanvasArtworkPlayer(
                    primaryUrl = canvasPrimaryUrl,
                    fallbackUrl = canvasFallbackUrl,
                    isPlaying = isCurrentPage,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
