package com.codetrio.spatialflow.ui.explore

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.data.innertube.AccountManager
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.spotify.SpotifyPrefs
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.viewmodel.AccountViewModel
import com.codetrio.spatialflow.viewmodel.DetailType
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.codetrio.spatialflow.viewmodel.SpotifyAuthState
import com.codetrio.spatialflow.viewmodel.SpotifyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

// ===== Shared Transition Locals =====

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.sharedElementIfAvailable(key: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedVisibilityScope.current
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.geometry.Rect>()
    return if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            this@sharedElementIfAvailable.sharedElement(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedScope,
                boundsTransform = { _, _ -> spatialSpec }
            )
        }
    } else this
}

// ===== Explore Screen (Lightweight Root Host) =====

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    playerSharedViewModel: PlayerSharedViewModel,
    onNavigateToLibrary: () -> Unit = {},
    spotifyViewModel: SpotifyViewModel? = null,
    onNavigateToSpotifySignIn: () -> Unit = {},
    onNavigateToSpotifyPlaylist: (String) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    val scope = rememberCoroutineScope()

    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()

    var isSearchActive by remember { mutableStateOf(false) }

    // 1. ISOLATED EVENT & SIDE-EFFECT HANDLING
    ExploreEffects(
        viewModel = viewModel,
        playerSharedViewModel = playerSharedViewModel,
        onScrollToTop = {
            scope.launch {
                try {
                    searchListState.animateScrollToItem(0)
                } catch (_: Exception) {
                    searchListState.scrollToItem(0)
                }
            }
        },
        onOpenSearch = {
            isSearchActive = true
        },
        onNavigateToSpotifyPlaylist = onNavigateToSpotifyPlaylist
    )

    // 2. TARGETED FLOW COLLECTIONS (Only navigation stack & error at root level!)
    val accountVM: AccountViewModel = viewModel()

    val detailStack by remember(viewModel) {
        viewModel.detailState.map { it.detailStack }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(emptyList())

    val isLoadingDetail by remember(viewModel) {
        viewModel.detailState.map { it.isLoadingDetail }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(false)

    val error by viewModel.error.collectAsStateWithLifecycle()

    // 3. OVERLAY & DIALOG LOCAL STATES
    var selectedSongForMenu by remember { mutableStateOf<OnlineSong?>(null) }
    var showCreditsForSong by remember { mutableStateOf<OnlineSong?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songToAddPlaylist by remember { mutableStateOf<SongItem?>(null) }
    var isAccountVisible by rememberSaveable { mutableStateOf(false) }

    val searchResults by remember(viewModel) {
        viewModel.searchState.map { it.searchResults }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(emptyList())

    val searchQuery by remember(viewModel) {
        viewModel.searchState.map { it.searchQuery }.distinctUntilChanged()
    }.collectAsStateWithLifecycle("")

    val hasSearchResults = searchResults.isNotEmpty()
    val isSearchScrolledDown by remember(hasSearchResults, searchListState) {
        derivedStateOf {
            hasSearchResults && (searchListState.firstVisibleItemIndex > 0 || searchListState.firstVisibleItemScrollOffset > 50)
        }
    }

    LaunchedEffect(isSearchScrolledDown) {
        viewModel.setScrolledDown(isSearchScrolledDown)
    }

    val isPlayerExpanded by playerSharedViewModel.isPlayerExpanded.collectAsStateWithLifecycle()

    // 4. BACK HANDLERS
    androidx.activity.compose.BackHandler(enabled = showCreditsForSong != null && !isPlayerExpanded) {
        showCreditsForSong = null
    }



    val appPrefs = remember { context.getSharedPreferences("AppSettings", 0) }
    var exploreSectionPriority by remember { mutableStateOf(appPrefs.getString("explore_section_priority", "yt") ?: "yt") }
    DisposableEffect(context) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "yt_cookies") viewModel.forceReloadHomeFeed()
            if (key == "explore_section_priority") exploreSectionPriority = p.getString("explore_section_priority", "yt") ?: "yt"
        }
        appPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { appPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val userProfile by accountVM.userProfile.collectAsStateWithLifecycle()
    val spotifyAuthState by (spotifyViewModel?.authState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    val isYtLoggedIn = remember(userProfile) { AccountManager.isLoggedIn(context) }
    val isSpotifyLoggedIn = spotifyAuthState == SpotifyAuthState.CONNECTED || SpotifyPrefs.isConnected(context)

    val effectiveExplorePriority = remember(isYtLoggedIn, isSpotifyLoggedIn, exploreSectionPriority) {
        when {
            isSpotifyLoggedIn && !isYtLoggedIn -> "spotify"
            isYtLoggedIn && !isSpotifyLoggedIn -> "yt"
            else -> exploreSectionPriority
        }
    }

    val detailScreenActive = detailStack.isNotEmpty() || isLoadingDetail

    val handleDetailBack = remember(viewModel, mainActivity) {
        {
            val currentStackSize = viewModel.detailState.value.detailStack.size
            val isLastDetail = currentStackSize <= 1
            val wasFromLibrary = viewModel.cameFromLibrary && isLastDetail

            viewModel.popDetailStack()

            if (isLastDetail) {
                viewModel.cameFromLibrary = false
            }

            if (wasFromLibrary) {
                onNavigateToLibrary()
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = detailScreenActive && !isPlayerExpanded) {
        handleDetailBack()
    }

    androidx.activity.compose.BackHandler(enabled = isSearchActive && !isPlayerExpanded) {
        isSearchActive = false
    }

    androidx.activity.compose.BackHandler(enabled = searchQuery.isNotBlank() && !isPlayerExpanded) {
        viewModel.clearSearch()
    }

    androidx.activity.compose.BackHandler(enabled = isAccountVisible && !isPlayerExpanded) {
        isAccountVisible = false
    }

    LaunchedEffect(viewModel) { viewModel.loadHomeFeed() }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            private var isNavVisible = true

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!isLandscape && mainActivity != null) {
                    if (consumed.y < -10f && isNavVisible) {
                        isNavVisible = false
                        mainActivity.hideBottomNavWithAnimation()
                    } else if (consumed.y > 10f && !isNavVisible) {
                        isNavVisible = true
                        mainActivity.showBottomNavWithAnimation()
                    }
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    val screenPriority = remember {
        mapOf(
            "home" to 0,
            "account" to 1,
            "loading" to 2,
            "album" to 3,
            "playlist" to 3,
            "artist" to 3,
            "section" to 3,
            "genres" to 3,
            "mood" to 3
        )
    }

    val currentScreen = remember(detailStack, isAccountVisible) {
        when {
            isAccountVisible -> "account"
            detailStack.isNotEmpty() -> {
                when (detailStack.last().type) {
                    DetailType.ALBUM -> "album"
                    DetailType.PLAYLIST -> "playlist"
                    DetailType.ARTIST -> "artist"
                    DetailType.SECTION -> "section"
                    DetailType.GENRES -> "genres"
                    DetailType.MOOD -> "mood"
                }
            }
            else -> "home"
        }
    }

    val onSearchItemTouchDown = remember(playerSharedViewModel) {
        { item: SearchItem ->
            if (item is SearchItem.Song && item.song.videoId.length == 11) {
                playerSharedViewModel.specPrefetch(
                    SongItem.createOnlineSong(
                        item.song.videoId,
                        item.song.title,
                        item.song.artist,
                        "",
                        item.song.durationMs,
                        item.song.thumbnailUrl,
                        item.song.artistId
                    )
                )
            }
        }
    }

    val onSongTouchDown = remember(playerSharedViewModel) {
        { song: OnlineSong ->
            playerSharedViewModel.specPrefetch(
                SongItem.createOnlineSong(
                    song.videoId,
                    song.title,
                    song.artist,
                    "",
                    song.durationMs,
                    song.thumbnailUrl,
                    song.artistId
                )
            )
        }
    }

    val color1 = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val color2 = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
    val color3 = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
    val backgroundColor = MaterialTheme.colorScheme.background

    val homeSectionsEmpty by remember(viewModel) {
        viewModel.homeState.map { it.homeSections.isEmpty() }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(true)

    val isLoadingHome by remember(viewModel) {
        viewModel.homeState.map { it.isLoadingHome }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(false)

    val gradientAlpha by animateFloatAsState(
        targetValue = if (isLoadingHome || homeSectionsEmpty) 0f else 1f,
        animationSpec = tween(durationMillis = 1200, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "GradientFadeIn"
    )

    val activeListState = if (isSearchActive || searchQuery.isNotBlank() || searchResults.isNotEmpty())
        searchListState else homeListState

    val sharedShimmer = rememberSharedShimmerOffset()

    CompositionLocalProvider(
        LocalSharedShimmer provides sharedShimmer
    ) {
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .graphicsLayer {
                    val scrollY = if (activeListState.firstVisibleItemIndex == 0)
                        activeListState.firstVisibleItemScrollOffset.toFloat() else 2000f
                    val scrollAlpha = (1f - scrollY / 600f).coerceIn(0f, 1f)
                    alpha = scrollAlpha * gradientAlpha
                    translationY = -scrollY * 0.1f
                }
                .drawWithCache {
                    val brush = Brush.verticalGradient(
                        colors = listOf(color1, color2, color3, backgroundColor)
                    )
                    onDrawBehind {
                        drawRect(brush)
                    }
                }
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
                // 5. NAVIGATION HOST (SharedTransitionLayout + AnimatedContent)
                ExploreNavigationHost(
                    currentScreen = currentScreen,
                    screenPriority = screenPriority,
                    exploreViewModel = viewModel,
                    accountVM = accountVM,
                    spotifyViewModel = spotifyViewModel,
                    onCloseAccount = { isAccountVisible = false },
                    onNavigateToGoogleSignIn = { mainActivity?.navigateToGoogleSignIn() },
                    onNavigateToSpotifySignIn = { mainActivity?.navigateToSpotifySignIn() },
                    onNavigateToSpotifyPlaylist = onNavigateToSpotifyPlaylist,
                    onDetailBack = { handleDetailBack() },
                    onDetailSongClick = { song, queue, index -> viewModel.playOnlineSongWithQueue(song, queue, index) },
                    onSongMenuClick = { selectedSongForMenu = it },
                    onStartRadioClick = { videoId -> viewModel.startRadio(videoId) },
                    onAlbumClick = { album -> viewModel.loadAlbum(album.browseId) },
                    onArtistClick = { artist -> viewModel.loadArtist(artist.browseId, artist.thumbnailUrl) },
                    onPlaylistClick = { playlist -> viewModel.loadPlaylist(playlist.playlistId) },
                    onSubscribeClick = { channelId ->
                        if (viewModel.detailState.value.artistDetail?.artist?.isSubscribed == true) {
                            viewModel.unsubscribeFromArtist(channelId)
                        } else {
                            viewModel.subscribeToArtist(channelId)
                        }
                    },
                    onSectionClick = { browseId, params, title -> viewModel.loadSectionDetails(browseId, params, title) },
                    onGenreClick = { title, browseId, params -> viewModel.loadMood(title, browseId, params) },
                    isSearchActive = isSearchActive,
                    effectiveExplorePriority = effectiveExplorePriority,
                    homeListState = homeListState,
                    searchListState = searchListState,
                    isLandscape = isLandscape,
                    onSongTouchDown = onSongTouchDown,
                    onRadioClick = { item -> viewModel.startRadioForItem(item) },
                    onSaveClick = { item -> viewModel.saveItemToLibrary(item) },
                    onMoodClick = { mood -> viewModel.setMood(mood) },
                    onGenresClick = { viewModel.loadGenres() },
                    onSearchActiveChange = { active -> isSearchActive = active },
                    onHistoryItemClick = { historyItem ->
                        scope.launch {
                            isSearchActive = false
                            delay(50.milliseconds)
                            viewModel.search(historyItem)
                        }
                    },
                    onSuggestionClick = { suggestion ->
                        scope.launch {
                            isSearchActive = false
                            delay(50.milliseconds)
                            viewModel.search(suggestion)
                        }
                    },
                    onAccountVisibleChange = { visible -> isAccountVisible = visible },
                    onAccountHistorySongClick = { song, queue, index -> viewModel.playOnlineSongWithQueue(song, queue, index) },
                    onClearSearch = { viewModel.clearSearch() },
                    onTopResultPlayClick = { item ->
                        when {
                            item.artist != null -> viewModel.startRadioForItem(item)
                            item.album != null -> viewModel.loadAlbum(item.album.browseId)
                            item.playlist != null -> viewModel.loadPlaylist(item.playlist.playlistId)
                            item.song != null -> viewModel.playSearchSong(item.song)
                            else -> viewModel.startRadioForItem(item)
                        }
                    },
                    onTopResultClick = { item ->
                        when {
                            item.artist != null -> viewModel.loadArtist(item.artist.browseId, item.artist.thumbnailUrl)
                            item.album != null -> viewModel.loadAlbum(item.album.browseId)
                            item.playlist != null -> viewModel.loadPlaylist(item.playlist.playlistId)
                            item.song != null -> viewModel.playSearchSong(item.song)
                            else -> {}
                        }
                    },
                    onSearchItemClick = { item ->
                        when (item) {
                            is SearchItem.Song -> viewModel.playSearchSong(item.song)
                            is SearchItem.Album -> viewModel.loadAlbum(item.album.browseId)
                            is SearchItem.Artist -> viewModel.loadArtist(item.artist.browseId, item.artist.thumbnailUrl)
                            is SearchItem.Playlist -> viewModel.loadPlaylist(item.playlist.playlistId)
                            else -> {}
                        }
                    },
                    onSearchItemTouchDown = onSearchItemTouchDown
                )
            }
        }

        // Error Snackbar
        error?.let { errorMsg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
            ) { Text(errorMsg) }
        }

        // 6. OVERLAYS HOST (Bottom Sheets & Dialogs)
        ExploreOverlayHost(
            selectedSongForMenu = selectedSongForMenu,
            showCreditsForSong = showCreditsForSong,
            showAddToPlaylistDialog = showAddToPlaylistDialog,
            showCreatePlaylistDialog = showCreatePlaylistDialog,
            songToAddPlaylist = songToAddPlaylist,
            accountVM = accountVM,
            spotifyViewModel = spotifyViewModel,
            playerSharedViewModel = playerSharedViewModel,
            exploreViewModel = viewModel,
            onDismissSongMenu = { selectedSongForMenu = null },
            onDismissCredits = { showCreditsForSong = null },
            onShowAddToPlaylistDialogChange = { showAddToPlaylistDialog = it },
            onShowCreatePlaylistDialogChange = { showCreatePlaylistDialog = it },
            onSongToAddPlaylistChange = { songToAddPlaylist = it },
            onPlaylistAddClick = { onlineSongItem ->
                songToAddPlaylist = onlineSongItem
                showAddToPlaylistDialog = true
            },
            onViewCreditsClick = { song -> showCreditsForSong = song },
            onNavigateToSignIn = { mainActivity?.navigateToGoogleSignIn() },
            onNavigateToSpotifySignIn = { mainActivity?.navigateToSpotifySignIn() }
        )
    }
}
}

