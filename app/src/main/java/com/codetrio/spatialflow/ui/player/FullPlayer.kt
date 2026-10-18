package com.codetrio.spatialflow.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.codetrio.spatialflow.R
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.player.queue.SlidingQueueDrawer
import com.codetrio.spatialflow.ui.player.metrics.LocalScreenMetrics
import com.codetrio.spatialflow.ui.player.metrics.computeScreenMetrics
import com.codetrio.spatialflow.ui.theme.observeKey
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.launch

/**
 * Stateful wrapper for the FullPlayer UI component.
 * Decouples the UI component from ViewModel, Activity, and direct permission handling.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    accentColor: Color,
    context: Context,
    onCollapse: () -> Unit,
    dragModifier: Modifier = Modifier,
    lyricsArtworkAnim: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    allowCanvas: Boolean = true,
    allowRealtimeUpdates: Boolean = true,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val hapticManager = viewModel.hapticManager
    val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
    val syncedLyrics by viewModel.syncedLyrics.collectAsStateWithLifecycle()
    val plainLyrics by viewModel.plainLyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val lyricsError by viewModel.lyricsError.collectAsStateWithLifecycle()
    val providerResults by viewModel.providerResults.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val isAutoplayEnabled by viewModel.isAutoplayEnabled.collectAsStateWithLifecycle()

    // Handle back button when lyrics mode is enabled
    BackHandler(enabled = isLyricsModeEnabled) {
        viewModel.setLyricsModeEnabled(false)
    }

    // Dynamically register the active Compose view hosting FullPlayer inside PlayerHapticManager.
    DisposableEffect(view, hapticManager) {
        hapticManager?.attachView(view)
        onDispose {
            hapticManager?.detachView()
        }
    }

    // Modern Compose-way of handling audio recording permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setHapticsEnabled(true)
        } else {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Microphone permission required for haptics")
        }
    }

    val localPlaylists by viewModel.localPlaylistsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var onlinePlaylists by remember {
        mutableStateOf<List<com.codetrio.spatialflow.data.innertube.OnlinePlaylist>>(
            emptyList()
        )
    }
    val isLoggedIn = remember(context) { com.codetrio.spatialflow.data.innertube.AccountManager.isLoggedIn(context) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCreateQueuePlaylistDialog by remember { mutableStateOf(false) }
    var queueSongsToSave by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var createIsOnline by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn, showAddToPlaylistDialog, showSaveQueueDialog) {
        if (isLoggedIn && (showAddToPlaylistDialog || showSaveQueueDialog)) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.codetrio.spatialflow.data.innertube.YouTubeMusic.libraryPlaylists().onSuccess { playlists ->
                    onlinePlaylists = playlists
                }
            }
        }
    }

    if (showSaveQueueDialog) {
        val songsFromCurrent = remember(songList, uiState.currentSongIndex) {
            if (uiState.currentSongIndex in songList.indices) songList.subList(
                uiState.currentSongIndex,
                songList.size
            ) else songList
        }

        com.codetrio.spatialflow.ui.dialogs.AddToPlaylistBottomSheet(
            songs = songList,
            songsFromCurrentIndex = songsFromCurrent,
            localPlaylists = localPlaylists,
            onlinePlaylists = onlinePlaylists,
            isLoggedIn = isLoggedIn,
            onCreateNewLocalWithSongs = { targetSongs ->
                createIsOnline = false
                queueSongsToSave = targetSongs
                showCreateQueuePlaylistDialog = true
                showSaveQueueDialog = false
            },
            onCreateNewOnlineWithSongs = { targetSongs ->
                createIsOnline = true
                queueSongsToSave = targetSongs
                showCreateQueuePlaylistDialog = true
                showSaveQueueDialog = false
            },
            onSelectLocalPlaylistWithSongs = { playlist, targetSongs ->
                viewModel.addSongsToLocalPlaylist(playlist.id, targetSongs)
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Added ${targetSongs.size} tracks to '${playlist.title}'")
                showSaveQueueDialog = false
            },
            onSelectOnlinePlaylistWithSongs = { onlinePlaylist, targetSongs ->
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saving ${targetSongs.size} tracks to YouTube Music...")
                viewModel.addSongsToYouTubePlaylist(onlinePlaylist.playlistId, targetSongs) { success, msg ->
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                }
                showSaveQueueDialog = false
            },
            onDismiss = { showSaveQueueDialog = false }
        )
    }

    if (showCreateQueuePlaylistDialog) {
        com.codetrio.spatialflow.ui.dialogs.CreatePlaylistDialog(
            isOnline = createIsOnline,
            onConfirm = { name ->
                if (!createIsOnline) {
                    viewModel.createLocalPlaylistWithSongs(name, queueSongsToSave)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saved playlist '$name' with ${queueSongsToSave.size} tracks")
                } else {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Creating YouTube Music playlist...")
                    viewModel.createYouTubePlaylistWithSongs(name, queueSongsToSave) { success, playlistId ->
                        if (success) {
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Created YouTube playlist '$name'")
                        } else {
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to create YouTube playlist")
                        }
                    }
                }
                showCreateQueuePlaylistDialog = false
            },
            onDismiss = { showCreateQueuePlaylistDialog = false }
        )
    }

    if (showAddToPlaylistDialog) {
        com.codetrio.spatialflow.ui.dialogs.AddToPlaylistDialog(
            song = uiState.currentSong,
            localPlaylists = localPlaylists,
            onlinePlaylists = onlinePlaylists,
            isLoggedIn = isLoggedIn,
            onCreateNewLocal = {
                createIsOnline = false
                showCreatePlaylistDialog = true
                showAddToPlaylistDialog = false
            },
            onCreateNewOnline = {
                createIsOnline = true
                showCreatePlaylistDialog = true
                showAddToPlaylistDialog = false
            },
            onSelectLocalPlaylist = { playlist ->
                val currentSong = uiState.currentSong
                if (currentSong != null) {
                    viewModel.addSongToLocalPlaylist(playlist.id, currentSong)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Added to playlist: ${playlist.title}")
                }
                showAddToPlaylistDialog = false
            },
            onSelectOnlinePlaylist = { onlinePlaylist ->
                val currentSong = uiState.currentSong
                if (currentSong != null) {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Adding to YouTube Music...")
                    viewModel.addSongToYouTubePlaylist(onlinePlaylist.playlistId, currentSong) { success, msg ->
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                    }
                }
                showAddToPlaylistDialog = false
            },
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    if (showCreatePlaylistDialog) {
        com.codetrio.spatialflow.ui.dialogs.CreatePlaylistDialog(
            isOnline = createIsOnline,
            onConfirm = { name ->
                if (!createIsOnline) {
                    viewModel.createLocalPlaylist(name)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Created local playlist '$name'")
                } else {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Creating YouTube Music playlist...")
                    viewModel.createYouTubePlaylist(name, uiState.currentSong) { success, _ ->
                        if (success) {
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Created YouTube playlist '$name'")
                        } else {
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to create YouTube playlist")
                        }
                    }
                }
                showCreatePlaylistDialog = false
                showAddToPlaylistDialog = true
            },
            onDismiss = {
                showCreatePlaylistDialog = false
                showAddToPlaylistDialog = true
            }
        )
    }

    FullPlayer(
        viewModel = viewModel,
        uiState = uiState,
        songList = songList,
        accentColor = accentColor,
        isLyricsModeEnabled = isLyricsModeEnabled,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        isLyricsLoading = isLyricsLoading,
        lyricsError = lyricsError,
        providerResults = providerResults,
        selectedProvider = selectedProvider,
        onProviderSelected = remember(viewModel) { { viewModel.selectLyricsProvider(it) } },
        currentPositionProvider = remember(viewModel) { { viewModel.currentPosition.value } },
        isAutoplayEnabled = isAutoplayEnabled,
        onAutoplayToggle = remember(viewModel) { { viewModel.setAutoplayEnabled(!viewModel.isAutoplayEnabled.value) } },
        onCollapse = onCollapse,
        onPlayPauseClick = remember(viewModel) {
            { if (viewModel.isPlaying.value) viewModel.pauseAudio() else viewModel.playAudio() }
        },
        onPreviousClick = remember(viewModel) { { viewModel.playPreviousSong() } },
        onNextClick = remember(viewModel) { { viewModel.playNextSong(force = true, fromUser = true) } },
        onHapticChipClick = remember(viewModel, context, permissionLauncher) {
            {
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                } else {
                    viewModel.setHapticsEnabled(!viewModel.isHapticsEnabled.value)
                }
            }
        },
        onLyricsModeChanged = remember(viewModel) { { enabled: Boolean -> viewModel.setLyricsModeEnabled(enabled) } },
        onFetchLyrics = remember(viewModel) { { viewModel.fetchLyricsForCurrentSong() } },
        onRetryLyrics = remember(viewModel) { { viewModel.retryLyrics() } },
        onSeekTo = remember(viewModel) { { position: Int -> viewModel.seekTo(position) } },
        onFavoriteClick = remember(viewModel) { { viewModel.toggleFavorite() } },
        onDislikeClick = remember(viewModel) { { viewModel.toggleDislike() } },
        onSaveClick = remember { { showAddToPlaylistDialog = true } },
        onSaveQueueClick = remember { { showSaveQueueDialog = true } },
        onClearUpcomingClick = remember(viewModel) {
            {
                viewModel.clearUpcomingQueue()
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Cleared upcoming queue")
            }
        },
        onClearAllClick = remember(viewModel) {
            {
                viewModel.clearAllExceptCurrent()
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Cleared queue except current track")
            }
        },
        onArtistClick = remember(activity, onCollapse) {
            { artistId: String?, artistName: String ->
                onCollapse()
                activity.showArtistPage(artistId, artistName)
            }
        },
        onAlbumClick = remember(activity, onCollapse, viewModel) {
            { albumId: String?, albumTitle: String ->
                onCollapse()
                val song = viewModel.currentSong.value
                activity.showAlbumPage(
                    albumId = albumId,
                    albumTitle = albumTitle,
                    artistName = song?.artist,
                    albumName = song?.albumName,
                    videoId = song?.videoId
                )
            }
        },
        dragModifier = dragModifier,
        lyricsArtworkAnim = lyricsArtworkAnim,
        allowCanvas = allowCanvas,
        allowRealtimeUpdates = allowRealtimeUpdates,
        modifier = modifier
    )
}

/**
 * Purely stateless UI representation of the Full Player.
 * Does not depend on ViewModels or Activities, ensuring great previewability and testability.
 */
@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun FullPlayer(
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    accentColor: Color,
    isLyricsModeEnabled: Boolean,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLyricsLoading: Boolean,
    lyricsError: Throwable?,
    currentPositionProvider: () -> Int,
    onCollapse: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onHapticChipClick: () -> Unit,
    onLyricsModeChanged: (Boolean) -> Unit,
    onFetchLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onFavoriteClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveQueueClick: () -> Unit = {},
    onClearUpcomingClick: () -> Unit = {},
    onClearAllClick: () -> Unit = {},
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    isAutoplayEnabled: Boolean,
    onAutoplayToggle: (Boolean) -> Unit,
    onArtistClick: (String?, String) -> Unit = { _, _ -> },
    onAlbumClick: (String?, String) -> Unit = { _, _ -> },
    dragModifier: Modifier = Modifier,
    lyricsArtworkAnim: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    allowCanvas: Boolean = true,
    allowRealtimeUpdates: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val canvasArtwork by viewModel.canvasArtwork.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
    val showAnimatedArt by prefs.observeKey("show_animated_art", true)
    val playerTheme by prefs.observeKey("player_theme", "fluid")
    val isStatic = playerTheme == "static"
    val hasCanvas = !isStatic && showAnimatedArt && canvasArtwork != null

    val vibrant = PlayerPaletteState.vibrantColor.value
    val darkVibrant = PlayerPaletteState.darkVibrantColor.value
    val darkMuted = PlayerPaletteState.darkMutedColor.value

    val effectiveAccent = remember(hasCanvas, vibrant, accentColor) {
        if (hasCanvas && vibrant != Color.Black) vibrant else accentColor
    }

    val playerBackgroundColor =
        remember(uiState.playerBackgroundColor, isStatic, vibrant, darkVibrant, darkMuted, hasCanvas) {
            if (hasCanvas && darkMuted != Color.Black) {
                darkMuted
            } else {
                resolvePlayerBackgroundColor(
                    storedBackgroundColor = uiState.playerBackgroundColor,
                    isStatic = isStatic,
                    vibrant = vibrant,
                    darkVibrant = darkVibrant,
                    darkMuted = darkMuted
                )
            }
        }

    val bgLuminance = remember(playerBackgroundColor) {
        androidx.core.graphics.ColorUtils.calculateLuminance(playerBackgroundColor.toArgb())
    }
    val isLightBackground = bgLuminance > 0.45f

    val (contentColor, contentSecondary) = remember(bgLuminance) {
        resolveContentColors(bgLuminance)
    }

    val dynamicAccentColor = remember(effectiveAccent, isLightBackground) {
        resolveDynamicAccentColor(effectiveAccent, isLightBackground)
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val hasLyrics = !syncedLyrics.isNullOrEmpty() || !plainLyrics.isNullOrBlank()

    // Sliding Queue Drawer State
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val isQueueExpanded by viewModel.isQueueExpanded.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
    val queuePeekOffset = remember { Animatable(0f) }
    LaunchedEffect(isQueueExpanded) {
        if (!isQueueExpanded) {
            queuePeekOffset.snapTo(0f)
        }
    }

    // Unify BackHandler to collapse the sliding Queue drawer first
    BackHandler(enabled = isLyricsModeEnabled || isQueueExpanded) {
        if (isLyricsModeEnabled) {
            onLyricsModeChanged(false)
        } else if (isQueueExpanded) {
            viewModel.setQueueExpanded(false)
        }
    }

    // Sleep Timer State & Controller Dialog state
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlayerOptionsSheet by remember { mutableStateOf(false) }
    var showLyricsOptionsSheet by remember { mutableStateOf(false) }
    var syncOffsetMs by remember { mutableStateOf(0L) }
    var selectedLyricForShare by remember { mutableStateOf<LyricLine?>(null) }
    val sleepTimerMode by viewModel.sleepTimerMode.collectAsStateWithLifecycle()
    val sleepTimerEndTime by viewModel.sleepTimerEndTime.collectAsStateWithLifecycle()

    val onShowPlayerOptions = remember { { showPlayerOptionsSheet = true } }
    val onShowLyricsOptions = remember { { showLyricsOptionsSheet = true } }
    val onLineLongClick = remember { { line: LyricLine -> selectedLyricForShare = line } }
    val onQueueExpandedChange = remember(viewModel) { { expanded: Boolean -> viewModel.setQueueExpanded(expanded) } }
    val onReorderQueue = remember(viewModel) { { from: Int, to: Int -> viewModel.reorderQueue(from, to) } }
    val onPlaySongAtIndex = remember(viewModel) { { index: Int -> viewModel.playSongAtIndex(index) } }
    val onRemoveSongAtIndex = remember(viewModel) { { index: Int -> viewModel.removeSongAtIndex(index) } }
    val onToggleShuffle = remember(viewModel) { { viewModel.toggleShuffle() } }
    val onToggleLoopMode = remember(viewModel) { { viewModel.toggleLoopMode() } }
    val onShowSleepTimerDialog = remember { { showSleepTimerDialog = true } }

    val lyricsProgressAnim = lyricsArtworkAnim

    val effectivePositionProvider = remember(currentPositionProvider, syncOffsetMs) {
        { (currentPositionProvider() + syncOffsetMs).toInt().coerceAtLeast(0) }
    }

    val artworkKey =
        remember(uiState.currentSong?.id, uiState.currentSong?.thumbnailUrl, uiState.currentSong?.videoId) {
            uiState.currentSong?.let { song ->
                song.getAlbumArtUri()?.toString()
                    ?: song.thumbnailUrl
                    ?: song.videoId?.let { "yt_$it" }
            }
        }

    PlayerPaletteEffect(
        artworkKey = artworkKey,
        currentSong = uiState.currentSong,
        canvasArtwork = canvasArtwork,
        context = context
    )

    val metrics = computeScreenMetrics()
    val twoPane = metrics.isLandscape
    val albumArtSize = metrics.albumArtSize

    AppleMusicBackground(
        song = uiState.currentSong,
        canvasArtwork = canvasArtwork,
        isPlaying = uiState.isPlaying && allowCanvas && isPlayerExpanded && allowRealtimeUpdates,
        isLyricsModeEnabled = isLyricsModeEnabled,
        allowCanvas = allowCanvas && isPlayerExpanded && allowRealtimeUpdates,
        modifier = modifier
            .fillMaxSize()
            .then(if (isQueueExpanded || isLyricsModeEnabled) Modifier else dragModifier)
            .pointerInput(isQueueExpanded, isLyricsModeEnabled) {
                if (isQueueExpanded || isLyricsModeEnabled) {
                    return@pointerInput
                }
                val queueDragActivationThresholdPx = 18.dp.toPx()
                val minFlingTravelPx = 32.dp.toPx()
                val dragThresholdPx = 48.dp.toPx()
                val quickFlickVelocityThreshold = -520f
                val queueGestureBottomExclusionPx = 220.dp.toPx()

                val offsetChannel =
                    kotlinx.coroutines.channels.Channel<Float>(kotlinx.coroutines.channels.Channel.CONFLATED)

                kotlinx.coroutines.coroutineScope {
                    launch {
                        for (targetOffset in offsetChannel) {
                            queuePeekOffset.snapTo(targetOffset)
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val bottomGestureBoundaryY =
                            (size.height.toFloat() - queueGestureBottomExclusionPx).coerceAtLeast(0f)
                        if (down.position.y >= bottomGestureBoundaryY) {
                            // Let transport controls, seekbar, and bottom controls handle all touches cleanly
                            return@awaitEachGesture
                        }

                        var dragConsumedByQueue = false
                        val velocityTracker = VelocityTracker()
                        var totalDrag = 0f
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        drag(down.id) { change ->
                            val dragAmount = change.positionChange().y
                            totalDrag += dragAmount
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val isDraggingUp = totalDrag < -queueDragActivationThresholdPx

                            if (isDraggingUp && !dragConsumedByQueue) {
                                dragConsumedByQueue = true
                            }

                            if (dragConsumedByQueue) {
                                change.consume()
                                val targetPeekOffset = (-totalDrag).coerceAtLeast(0f)
                                offsetChannel.trySend(targetPeekOffset)
                            }
                        }

                        val velocity = velocityTracker.calculateVelocity().y
                        val isFastUpward = velocity < quickFlickVelocityThreshold
                        val hasMeaningfulUpwardTravel = totalDrag < -minFlingTravelPx
                        val hasQuickUpwardTravel = totalDrag < -(minFlingTravelPx * 0.35f)
                        val shouldExpandFromQuickFling =
                            isFastUpward && (hasMeaningfulUpwardTravel || hasQuickUpwardTravel)
                        val shouldExpand = shouldExpandFromQuickFling || (totalDrag < -dragThresholdPx)

                        if (shouldExpand) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setQueueExpanded(true)
                        } else if (queuePeekOffset.value > 0f) {
                            scope.launch {
                                queuePeekOffset.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    }
                }
            }
    ) {

        CompositionLocalProvider(LocalScreenMetrics provides metrics) {
            val dimens = com.codetrio.spatialflow.ui.theme.LocalDimens.current

            // Standard vs lyrics cross-fade drivers (shared by both layouts).
            // Animation progress is consumed purely in graphicsLayer draw-phase to eliminate per-frame recomposition lag.
            val isLyricsAnimRunning = lyricsProgressAnim.isRunning
            val showStandardContent = !isLyricsModeEnabled || isLyricsAnimRunning
            val showLyricsContent = isLyricsModeEnabled || isLyricsAnimRunning

            // --- Apple Music-style lyrics controls auto hide & show ---
            var controlsRevealToken by remember { mutableIntStateOf(0) }
            var controlsHiddenByTimeout by remember { mutableStateOf(false) }

            val pokeLyricsControls: () -> Unit = remember { { controlsRevealToken++ } }

            // 3-second auto-hide countdown; cancels and restarts every time controlsRevealToken updates
            LaunchedEffect(uiState.currentSong?.id, isLyricsModeEnabled, controlsRevealToken) {
                controlsHiddenByTimeout = false
                if (!isLyricsModeEnabled) return@LaunchedEffect
                delay(3000L)
                controlsHiddenByTimeout = true
            }

            val controlsVisible = !isLyricsModeEnabled || !controlsHiddenByTimeout

            val controlsFadeDurationMs = 260
            val controlsFadeInSpec = remember { tween<Float>(durationMillis = controlsFadeDurationMs, easing = FastOutSlowInEasing) }
            val controlsFadeOutSpec = remember { tween<Float>(durationMillis = controlsFadeDurationMs, easing = FastOutSlowInEasing) }
            val controlsExpandSpec = remember { tween<IntSize>(durationMillis = controlsFadeDurationMs, easing = FastOutSlowInEasing) }
            val controlsShrinkSpec = remember { tween<IntSize>(durationMillis = controlsFadeDurationMs, easing = FastOutSlowInEasing) }

            val lyricsScrollConnection = remember(isLyricsModeEnabled) {
                var lastScrollPokeMs = 0L
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        // Only trigger on explicit user touch scroll gestures; song auto-advancing line by line does NOT trigger this
                        if (isLyricsModeEnabled && source == NestedScrollSource.UserInput && abs(available.y) > 0.5f) {
                            val now = System.currentTimeMillis()
                            if (now - lastScrollPokeMs > 80L) {
                                lastScrollPokeMs = now
                                pokeLyricsControls()
                            }
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        if (isLyricsModeEnabled) {
                            pokeLyricsControls()
                        }
                        return super.onPostFling(consumed, available)
                    }
                }
            }

            // Bottom transport controls (seek bar + prev / play-pause / next). Defined once and
            // placed per layout: under the right (metadata) pane in two-pane, full width in portrait.
            val transportControls: @Composable () -> Unit = {
                PlayerTransportControls(
                    currentPositionProvider = currentPositionProvider,
                    duration = uiState.duration,
                    isPlaying = uiState.isPlaying,
                    isProcessing = uiState.isProcessing,
                    playbackFormat = uiState.playbackFormat,
                    contentColor = contentColor,
                    contentSecondary = contentSecondary,
                    dynamicAccentColor = dynamicAccentColor,
                    isDark = isDark,
                    metrics = metrics,
                    onSeekTo = { pos ->
                        pokeLyricsControls()
                        onSeekTo(pos)
                    },
                    onPreviousClick = {
                        pokeLyricsControls()
                        onPreviousClick()
                    },
                    onPlayPauseClick = {
                        pokeLyricsControls()
                        onPlayPauseClick()
                    },
                    onNextClick = {
                        pokeLyricsControls()
                        onNextClick()
                    },
                    currentPositionFlow = viewModel.currentPosition,
                    allowRealtimeUpdates = allowRealtimeUpdates
                )
            }

            val songInfo: @Composable () -> Unit = {
                PlayerSongInfo(
                    currentSong = uiState.currentSong,
                    contentColor = contentColor,
                    metrics = metrics,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick
                )
            }

            val pillGroup: @Composable () -> Unit = {
                PlayerPillGroup(
                    currentSong = uiState.currentSong,
                    isCurrentSongFavorite = uiState.isCurrentSongFavorite,
                    isCurrentSongDisliked = uiState.isCurrentSongDisliked,
                    likesCount = uiState.likesCount,
                    isHapticsEnabled = uiState.isHapticsEnabled,
                    isLyricsModeEnabled = isLyricsModeEnabled,
                    hasLyrics = hasLyrics,
                    isLyricsLoading = isLyricsLoading,
                    isCurrentSongDownloaded = uiState.isCurrentSongDownloaded,
                    currentSongDownloadProgress = uiState.currentSongDownloadProgress,
                    contentColor = contentColor,
                    dynamicAccentColor = dynamicAccentColor,
                    isDark = isDark,
                    onFavoriteClick = onFavoriteClick,
                    onDislikeClick = onDislikeClick,
                    onHapticChipClick = onHapticChipClick,
                    onLyricsModeChanged = onLyricsModeChanged,
                    onFetchLyrics = onFetchLyrics,
                    onSaveClick = onSaveClick,
                    onDownloadClick = {},
                    chipSpacing = metrics.chipSpacing
                )
            }

            val metadataChips: @Composable () -> Unit = {
                PlayerMetadataChips(
                    currentSong = uiState.currentSong,
                    isCurrentSongFavorite = uiState.isCurrentSongFavorite,
                    isCurrentSongDisliked = uiState.isCurrentSongDisliked,
                    likesCount = uiState.likesCount,
                    isHapticsEnabled = uiState.isHapticsEnabled,
                    isLyricsModeEnabled = isLyricsModeEnabled,
                    hasLyrics = hasLyrics,
                    isLyricsLoading = isLyricsLoading,
                    isCurrentSongDownloaded = uiState.isCurrentSongDownloaded,
                    currentSongDownloadProgress = uiState.currentSongDownloadProgress,
                    contentColor = contentColor,
                    dynamicAccentColor = dynamicAccentColor,
                    isDark = isDark,
                    metrics = metrics,
                    onFavoriteClick = onFavoriteClick,
                    onDislikeClick = onDislikeClick,
                    onHapticChipClick = onHapticChipClick,
                    onLyricsModeChanged = onLyricsModeChanged,
                    onFetchLyrics = onFetchLyrics,
                    onSaveClick = onSaveClick,
                    onDownloadClick = {},
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick
                )
            }

            val lyricsView: @Composable () -> Unit = {
                PlayerLyricsView(
                    syncedLyrics = syncedLyrics,
                    plainLyrics = plainLyrics,
                    isLyricsLoading = isLyricsLoading,
                    lyricsError = lyricsError,
                    providerResults = providerResults,
                    selectedProvider = selectedProvider,
                    currentSong = uiState.currentSong,
                    contentColor = contentColor,
                    dynamicAccentColor = dynamicAccentColor,
                    isPlaying = uiState.isPlaying,
                    effectivePositionProvider = effectivePositionProvider,
                    onSeekTo = onSeekTo,
                    onRetryLyrics = onRetryLyrics,
                    onFetchLyrics = onFetchLyrics,
                    onLineLongClick = onLineLongClick,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Fades the standard (metadata) content out and up as lyrics take over.
            val standardFade: Modifier = Modifier.graphicsLayer {
                val progress = lyricsProgressAnim.value
                alpha = (1f - progress).coerceIn(0f, 1f)
                translationY = -24.dp.toPx() * progress
                scaleX = 1f - 0.04f * progress
                scaleY = 1f - 0.04f * progress
            }
            // Fades the lyrics content in and up from below.
            val lyricsFade: Modifier = Modifier.graphicsLayer {
                val progress = lyricsProgressAnim.value
                alpha = progress.coerceIn(0f, 1f)
                translationY = 24.dp.toPx() * (1f - progress)
                scaleX = 0.96f + 0.04f * progress
                scaleY = 0.96f + 0.04f * progress
            }

            if (twoPane) {
                // TWO-PANE (landscape / tablet): Header removed so player covers the entire area.
                // Album art lives in the LEFT pane and STAYS there even in lyrics mode.
                // The RIGHT pane cross-fades metadata <-> lyrics on top, with transport controls pinned below.
                // Minimize player button and 3-dots options button float at the top corners without the "NOW PLAYING" header.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = dimens.screenMargin, vertical = dimens.smallPadding)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LEFT: artwork placeholder (real artwork is the shared floating layer in the host)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(albumArtSize))
                        }

                        // RIGHT: metadata/lyrics area (weight) + controls (wrap) below it.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (showStandardContent) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(standardFade),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom
                                        ) {
                                            songInfo()
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                    }
                                }
                                if (showLyricsContent) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(lyricsFade)
                                            .nestedScroll(lyricsScrollConnection)
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = { pokeLyricsControls() }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        lyricsView()
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = controlsVisible,
                                enter = fadeIn(animationSpec = controlsFadeInSpec) + expandVertically(
                                    animationSpec = controlsExpandSpec,
                                    expandFrom = Alignment.Bottom
                                ),
                                exit = fadeOut(animationSpec = controlsFadeOutSpec) + shrinkVertically(
                                    animationSpec = controlsShrinkSpec,
                                    shrinkTowards = Alignment.Bottom
                                ),
                                label = "lyrics-playback-controls-landscape"
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // In landscape, pill group is ALWAYS on top of the seekbar (just like Portrait)
                                    pillGroup()

                                    transportControls()

                                    AudioOutputIndicator(
                                        contentColor = contentColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Floating top controls: minimize player (left) and 3-dots options (right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onCollapse
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                                contentDescription = "Collapse Player",
                                tint = contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isLyricsModeEnabled) onShowLyricsOptions() else onShowPlayerOptions()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = if (isLyricsModeEnabled) "Lyrics Options" else "Player Options",
                                tint = contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            } else {
                // PORTRAIT: single column with PlayerHeaderBar ("NOW PLAYING", minimize, 3 dots),
                // artwork + metadata, with lyrics overlaying the full area; controls pinned below.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = dimens.screenMargin, vertical = dimens.smallPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    PlayerHeaderBar(
                        isLyricsModeEnabled = isLyricsModeEnabled,
                        hasCanvas = hasCanvas,
                        currentSong = uiState.currentSong,
                        lyricsProgressAnim = lyricsProgressAnim,
                        contentColor = contentColor,
                        contentSecondary = contentSecondary,
                        onCollapse = onCollapse,
                        onLyricsModeChanged = onLyricsModeChanged,
                        onShowPlayerOptions = onShowPlayerOptions,
                        onShowLyricsOptions = onShowLyricsOptions
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showStandardContent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(standardFade),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Spacer(modifier = Modifier.weight(1f))

                                    Box(modifier = Modifier.size(albumArtSize))

                                    Spacer(modifier = Modifier.height(16.dp))

                                    metadataChips()
                                }
                            }
                        }

                        if (showLyricsContent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(lyricsFade)
                                    .nestedScroll(lyricsScrollConnection)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { pokeLyricsControls() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                lyricsView()
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(animationSpec = controlsFadeInSpec) + expandVertically(
                            animationSpec = controlsExpandSpec,
                            expandFrom = Alignment.Bottom
                        ),
                        exit = fadeOut(animationSpec = controlsFadeOutSpec) + shrinkVertically(
                            animationSpec = controlsShrinkSpec,
                            shrinkTowards = Alignment.Bottom
                        ),
                        label = "lyrics-playback-controls"
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            transportControls()

                            AudioOutputIndicator(
                                contentColor = contentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            // --- CUSTOM EMBEDDED SLIDING PLAY QUEUE ---
            if (!isLyricsModeEnabled) {
                SlidingQueueDrawer(
                    isQueueExpanded = isQueueExpanded,
                    onQueueExpandedChange = onQueueExpandedChange,
                    songList = songList,
                    currentSongIndex = currentSongIndex,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    sleepTimerMode = sleepTimerMode,
                    onReorderQueue = onReorderQueue,
                    onPlaySongAtIndex = onPlaySongAtIndex,
                    onRemoveSongAtIndex = onRemoveSongAtIndex,
                    onToggleShuffle = onToggleShuffle,
                    onToggleLoopMode = onToggleLoopMode,
                    onShowSleepTimerDialog = onShowSleepTimerDialog,
                    playerBackgroundColor = playerBackgroundColor.toArgb(),
                    dynamicAccentColor = dynamicAccentColor,
                    isDark = isDark,
                    isAutoplayEnabled = isAutoplayEnabled,
                    onAutoplayToggle = onAutoplayToggle,
                    queueTitle = viewModel.currentQueue?.currentTitle,
                    onSaveQueueClick = onSaveQueueClick,
                    onClearUpcomingClick = onClearUpcomingClick,
                    onClearAllClick = onClearAllClick,
                    peekOffsetPxProvider = { queuePeekOffset.value }
                )
            }

            // --- Lyrics Options Bottom Sheet ---
            if (showLyricsOptionsSheet) {
                val lyricsSheetState =
                    androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { showLyricsOptionsSheet = false },
                    sheetState = lyricsSheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
                    scrimColor = Color.Black.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp)
                    ) {
                        UnifiedLyricsBottomSheetContent(
                            providerResults = providerResults,
                            selectedProvider = selectedProvider,
                            syncOffsetMs = syncOffsetMs,
                            onSyncOffsetChange = { syncOffsetMs = it },
                            onProviderSelected = onProviderSelected,
                            onRefindClick = onRetryLyrics,
                            accentColor = dynamicAccentColor,
                            contentColor = contentColor,
                            onUpdateCustomLyrics = { viewModel.updateCustomLyrics(it) },
                            currentLyricsText = remember(syncedLyrics, plainLyrics) {
                                if (!syncedLyrics.isNullOrEmpty()) {
                                    syncedLyrics.joinToString("\n") { line ->
                                        val min = line.startTimeMs / 60000
                                        val sec = (line.startTimeMs % 60000) / 1000
                                        val csec = (line.startTimeMs % 1000) / 10
                                        val mTen = min / 10
                                        val mOne = min % 10
                                        val sTen = sec / 10
                                        val sOne = sec % 10
                                        val cTen = csec / 10
                                        val cOne = csec % 10
                                        "[$mTen$mOne:$sTen$sOne.$cTen$cOne]${line.content}"
                                    }
                                } else {
                                    plainLyrics ?: ""
                                }
                            },
                            songTitle = uiState.currentSong?.title ?: "Current Song",
                            syncedLyrics = syncedLyrics,
                            plainLyricsRaw = plainLyrics,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // --- Lyric Share Bottom Sheet ---
            if (selectedLyricForShare != null) {
                LyricShareBottomSheet(
                    currentSong = uiState.currentSong,
                    allLyrics = syncedLyrics ?: emptyList(),
                    initialLine = selectedLyricForShare!!,
                    onDismiss = { selectedLyricForShare = null }
                )
            }

            // --- Standalone Sleep Timer Bottom Sheet ---
            if (showSleepTimerDialog) {
                SleepTimerBottomSheet(
                    onDismissRequest = { showSleepTimerDialog = false },
                    sleepTimerEndTime = sleepTimerEndTime,
                    sleepTimerMode = sleepTimerMode,
                    onStartTimer = { mins ->
                        viewModel.startCustomSleepTimer(mins)
                    },
                    onCancelTimer = {
                        viewModel.cancelSleepTimer()
                    },
                    onSetEndOfSong = { enable ->
                        if (enable) viewModel.setSleepTimerMode(PlayerSharedViewModel.SleepTimerMode.END_OF_SONG)
                        else viewModel.cancelSleepTimer()
                    }
                )
            }

            // --- Single Consolidated Player Options Bottom Sheet ---
            if (showPlayerOptionsSheet) {
                PlayerOptionsBottomSheet(
                    currentSong = uiState.currentSong,
                    playbackFormat = uiState.playbackFormat,
                    playbackProvider = uiState.playbackProvider,
                    viewModel = viewModel,
                    onDismissRequest = { showPlayerOptionsSheet = false },
                    onArtistClick = { artistId, artistName ->
                        onArtistClick(artistId, artistName)
                    },
                    onAlbumClick = {
                        val song = uiState.currentSong
                        if (song != null) {
                            onAlbumClick(song.albumId.toString(), song.title)
                        }
                    },
                    onOpenEqualizer = {
                        onCollapse()
                        (context as? MainActivity)?.navigateToEffects()
                    },
                    onSaveClick = {
                        onSaveClick()
                    },
                    onSleepTimerClick = {
                        showSleepTimerDialog = true
                    }
                )
            }
        }
    }
}
