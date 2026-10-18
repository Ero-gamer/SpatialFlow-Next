package com.codetrio.spatialflow.ui.explore

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.data.innertube.HomeSection
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.innertube.UserProfile
import com.codetrio.spatialflow.data.spotify.SpotifyHomeSection
import com.codetrio.spatialflow.ui.spotify.SpotifyExploreConnectCard
import com.codetrio.spatialflow.ui.spotify.SpotifyHomeSectionRow
import com.codetrio.spatialflow.viewmodel.AccountViewModel
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.SpotifyAuthState
import com.codetrio.spatialflow.viewmodel.SpotifyViewModel

/**
 * Isolated Home stateful container for Explore.
 * Collects ONLY Home-related state flows locally to prevent invalidation from Search or Detail state.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExploreHome(
    viewModel: ExploreViewModel,
    accountVM: AccountViewModel,
    spotifyViewModel: SpotifyViewModel?,
    effectiveExplorePriority: String,
    homeListState: LazyListState,
    isLandscape: Boolean,
    onSongTouchDown: (OnlineSong) -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSectionClick: (browseId: String, params: String?, title: String) -> Unit,
    onRadioClick: (SearchItem) -> Unit,
    onSaveClick: (SearchItem) -> Unit,
    onMoodClick: (String, String, String?) -> Unit,
    onGenresClick: () -> Unit,
    onNavigateToSpotifySignIn: () -> Unit,
    onNavigateToSpotifyPlaylist: (String) -> Unit = {}
) {
    // 1. ISOLATED LOCAL STATEFLOW COLLECTIONS
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val homeSections = homeState.homeSections
    val isLoadingHome = homeState.isLoadingHome
    val isRefreshing = homeState.isRefreshing

    val isLoadingMoreHome by viewModel.isLoadingMoreHome.collectAsStateWithLifecycle()

    val currentOnlineSong by viewModel.currentOnlineSong.collectAsStateWithLifecycle()
    val currentSongId = currentOnlineSong?.videoId

    ExploreHomeContent(
        homeSections = homeSections,
        isLoadingHome = isLoadingHome,
        isRefreshing = isRefreshing,
        accountVM = accountVM,
        spotifyViewModel = spotifyViewModel,
        effectiveExplorePriority = effectiveExplorePriority,
        currentSongId = currentSongId,
        isLoadingMoreHome = isLoadingMoreHome,
        homeListState = homeListState,
        isLandscape = isLandscape,
        onRefreshHomeFeed = {
            viewModel.refreshHomeFeed()
            spotifyViewModel?.reload()
        },
        onLoadMoreHomeSections = { viewModel.loadMoreHomeSections() },
        onSongTouchDown = onSongTouchDown,
        onSongClick = onSongClick,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onPlaylistClick = onPlaylistClick,
        onSongMenuClick = onSongMenuClick,
        onSectionClick = onSectionClick,
        onRadioClick = onRadioClick,
        onSaveClick = onSaveClick,
        onMoodClick = onMoodClick,
        onGenresClick = onGenresClick,
        onNavigateToSpotifySignIn = onNavigateToSpotifySignIn,
        onNavigateToSpotifyPlaylist = onNavigateToSpotifyPlaylist
    )
}

/**
 * Pure Stateless UI component for Explore Home.
 * Accepts narrow primitives and models to maximize recomposition skipping.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExploreHomeContent(
    homeSections: List<HomeSection>,
    isLoadingHome: Boolean,
    isRefreshing: Boolean,
    accountVM: AccountViewModel,
    spotifyViewModel: SpotifyViewModel?,
    effectiveExplorePriority: String,
    currentSongId: String?,
    isLoadingMoreHome: Boolean,
    homeListState: LazyListState,
    isLandscape: Boolean,
    onRefreshHomeFeed: () -> Unit,
    onLoadMoreHomeSections: () -> Unit,
    onSongTouchDown: (OnlineSong) -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSectionClick: (browseId: String, params: String?, title: String) -> Unit,
    onRadioClick: (SearchItem) -> Unit,
    onSaveClick: (SearchItem) -> Unit,
    onMoodClick: (String, String, String?) -> Unit,
    onGenresClick: () -> Unit,
    onNavigateToSpotifySignIn: () -> Unit,
    onNavigateToSpotifyPlaylist: (String) -> Unit = {}
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val spotifyAuthState by spotifyViewModel?.authState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val spotifyHomeFeed by spotifyViewModel?.homeFeed?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefreshHomeFeed,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        when {
            homeSections.isNotEmpty() || spotifyViewModel != null -> {
                LazyColumn(
                    state = homeListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    item(key = "greeting-banner", contentType = "greeting") {
                        AccountGreetingHost(accountVM = accountVM)
                    }

                    if (effectiveExplorePriority == "spotify") {
                        renderSpotifySectionsHost(
                            spotifyAuthState = spotifyAuthState,
                            spotifySections = spotifyHomeFeed?.sections.orEmpty(),
                            onNavigateToSpotifySignIn = onNavigateToSpotifySignIn,
                            onNavigateToSpotifyPlaylist = onNavigateToSpotifyPlaylist
                        )
                        renderYtSections(
                            homeSections = homeSections,
                            currentSongId = currentSongId,
                            onSongTouchDown = onSongTouchDown,
                            onSongClick = onSongClick,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            onPlaylistClick = onPlaylistClick,
                            onSongMenuClick = onSongMenuClick,
                            onSectionClick = onSectionClick,
                            onRadioClick = onRadioClick,
                            onSaveClick = onSaveClick
                        )
                    } else {
                        renderYtSections(
                            homeSections = homeSections,
                            currentSongId = currentSongId,
                            onSongTouchDown = onSongTouchDown,
                            onSongClick = onSongClick,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            onPlaylistClick = onPlaylistClick,
                            onSongMenuClick = onSongMenuClick,
                            onSectionClick = onSectionClick,
                            onRadioClick = onRadioClick,
                            onSaveClick = onSaveClick
                        )
                        renderSpotifySectionsHost(
                            spotifyAuthState = spotifyAuthState,
                            spotifySections = spotifyHomeFeed?.sections.orEmpty(),
                            onNavigateToSpotifySignIn = onNavigateToSpotifySignIn,
                            onNavigateToSpotifyPlaylist = onNavigateToSpotifyPlaylist
                        )
                    }

                    item(key = "home-load-more", contentType = "load_more") {
                        if (isLoadingMoreHome) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                LaunchedEffect(homeListState) {
                    snapshotFlow {
                        val layoutInfo = homeListState.layoutInfo
                        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val total = layoutInfo.totalItemsCount
                        total > 0 && lastVisible >= total - 3
                    }
                    .distinctUntilChanged()
                    .collect { shouldLoadMore ->
                        if (shouldLoadMore) {
                            onLoadMoreHomeSections()
                        }
                    }
                }
            }

            isLoadingHome -> {
                HomeFeedSkeleton()
            }

            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "No content available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRefreshHomeFeed,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Load Home Feed")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountGreetingHost(
    accountVM: AccountViewModel
) {
    val userProfile by accountVM.userProfile.collectAsStateWithLifecycle()
    WelcomeGreetingBanner(userName = userProfile?.name)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.renderSpotifySectionsHost(
    spotifyAuthState: SpotifyAuthState?,
    spotifySections: List<SpotifyHomeSection>,
    onNavigateToSpotifySignIn: () -> Unit,
    onNavigateToSpotifyPlaylist: (String) -> Unit
) {
    if (spotifyAuthState == SpotifyAuthState.NOT_CONNECTED) {
        item(key = "spotify-connect-card", contentType = "spotify_connect") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                SpotifyExploreConnectCard(
                    onConnect = onNavigateToSpotifySignIn,
                    modifier = Modifier.widthIn(max = 840.dp)
                )
            }
        }
    } else if (spotifySections.isNotEmpty()) {
        itemsIndexed(
            items = spotifySections,
            key = { index, section -> "sp_sec_${index}_${section.uri.ifEmpty { section.title?.hashCode()?.toString() ?: "sec" }}" },
            contentType = { _, _ -> "spotify_section" }
        ) { _, spotifySection ->
            SpotifyHomeSectionRow(
                section = spotifySection,
                onPlaylistClick = onNavigateToSpotifyPlaylist
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.renderYtSections(
    homeSections: List<HomeSection>,
    currentSongId: String?,
    onSongTouchDown: (OnlineSong) -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSectionClick: (browseId: String, params: String?, title: String) -> Unit,
    onRadioClick: (SearchItem) -> Unit,
    onSaveClick: (SearchItem) -> Unit
) {
    itemsIndexed(
        items = homeSections,
        key = { index, section -> "yt_sec_${index}_${section.browseEndpoint ?: section.title.hashCode()}" },
        contentType = { _, section ->
            when {
                section.title.contains("personalized mix", ignoreCase = true) -> "bento_section"
                section.title.contains("speed dial", ignoreCase = true) -> "speed_dial_section"
                section.title.contains("daily discover", ignoreCase = true) -> "discover_section"
                section.title.contains("community", ignoreCase = true) -> "community_section"
                section.title.contains("video", ignoreCase = true) -> "video_carousel_section"
                section.items.isNotEmpty() && section.items.all { it is SearchItem.Song } -> "song_column_section"
                else -> "carousel_section"
            }
        }
    ) { _, section ->
        HomeSectionRow(
            section = section,
            currentSongId = currentSongId,
            onSongTouchDown = onSongTouchDown,
            onSongClick = onSongClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlaylistClick = onPlaylistClick,
            onSongMenuClick = onSongMenuClick,
            onSectionClick = onSectionClick,
            onRadioClick = onRadioClick,
            onSaveClick = onSaveClick
        )
    }
}

private val NewReleasesGradient = listOf(Color(0xFF2E1A47), Color(0xFF160B24))
private val ChartsGradient = listOf(Color(0xFF4C1D1D), Color(0xFF240E0E))
private val MoodsGenresGradient = listOf(Color(0xFF0F3040), Color(0xFF081820))
private val PodcastsGradient = listOf(Color(0xFF4A1E30), Color(0xFF240F18))


