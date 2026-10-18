package com.codetrio.spatialflow.ui.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SearchFilter
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.viewmodel.AccountViewModel
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.SpotifyViewModel

/**
 * Isolated Navigation Host component for Explore.
 * Encapsulates SharedTransitionLayout and AnimatedContent for screen switching.
 * SearchHeader observes search state locally to prevent invalidating Home.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExploreNavigationHost(
    currentScreen: String,
    screenPriority: Map<String, Int>,
    exploreViewModel: ExploreViewModel,
    accountVM: AccountViewModel,
    spotifyViewModel: SpotifyViewModel?,
    onCloseAccount: () -> Unit,
    onNavigateToGoogleSignIn: () -> Unit,
    onNavigateToSpotifySignIn: () -> Unit,
    onNavigateToSpotifyPlaylist: (String) -> Unit = {},
    onDetailBack: () -> Unit,
    onDetailSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onSubscribeClick: (String) -> Unit,
    onSectionClick: (String, String?, String) -> Unit,
    onGenreClick: (String, String, String?) -> Unit,
    isSearchActive: Boolean,
    effectiveExplorePriority: String,
    homeListState: LazyListState,
    searchListState: LazyListState,
    isLandscape: Boolean,
    onSongTouchDown: (OnlineSong) -> Unit,
    onRadioClick: (SearchItem) -> Unit,
    onSaveClick: (SearchItem) -> Unit,
    onMoodClick: (String?) -> Unit,
    onGenresClick: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onAccountVisibleChange: (Boolean) -> Unit,
    onAccountHistorySongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onClearSearch: () -> Unit,
    onTopResultPlayClick: (SearchItem.TopResult) -> Unit,
    onTopResultClick: (SearchItem.TopResult) -> Unit,
    onSearchItemClick: (SearchItem) -> Unit,
    onSearchItemTouchDown: (SearchItem) -> Unit
) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val scaleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val detailScreens = setOf("album", "artist", "playlist", "section", "genres", "mood")
                    val isDetailTransition = initialState in detailScreens || targetState in detailScreens

                    if (isDetailTransition) {
                        (fadeIn(animationSpec = effectsSpec) + scaleIn(initialScale = 0.96f, animationSpec = scaleSpec))
                            .togetherWith(fadeOut(animationSpec = effectsSpec) + scaleOut(targetScale = 0.96f, animationSpec = scaleSpec))
                    } else {
                        val initialWeight = screenPriority[initialState] ?: 0
                        val targetWeight = screenPriority[targetState] ?: 0

                        if (targetWeight > initialWeight) {
                            (slideInHorizontally(animationSpec = spatialSpec) { it } + fadeIn(effectsSpec))
                                .togetherWith(slideOutHorizontally(animationSpec = spatialSpec) { -it / 3 } + fadeOut(effectsSpec))
                        } else {
                            (slideInHorizontally(animationSpec = spatialSpec) { -it / 3 } + fadeIn(effectsSpec))
                                .togetherWith(slideOutHorizontally(animationSpec = spatialSpec) { it } + fadeOut(effectsSpec))
                        }
                    }
                },
                label = "Explore Expressive Transitions"
            ) { screen ->
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                    when (screen) {
                        "account" -> {
                            AccountScreen(
                                viewModel = accountVM,
                                spotifyViewModel = spotifyViewModel,
                                onBack = onCloseAccount,
                                onSongClick = { song, queue, index -> onDetailSongClick(song, queue, index) },
                                onNavigateToSignIn = onNavigateToGoogleSignIn,
                                onNavigateToSpotifySignIn = onNavigateToSpotifySignIn
                            )
                        }

                        "home" -> {
                            // Isolated Search State Collections for Header
                            val searchState by exploreViewModel.searchState.collectAsStateWithLifecycle()
                            val searchQuery = searchState.searchQuery
                            val searchResults = searchState.searchResults
                            val suggestions = searchState.suggestions
                            val searchHistory = searchState.searchHistory
                            val isLoadingSuggestions = searchState.isLoadingSuggestions
                            val currentFilter by exploreViewModel.searchFilter.collectAsStateWithLifecycle()

                            val userProfile by accountVM.userProfile.collectAsStateWithLifecycle()
                            val accountHistory by accountVM.history.collectAsStateWithLifecycle()

                            Column(modifier = Modifier.fillMaxSize()) {
                                SearchHeader(
                                    searchQuery = searchQuery,
                                    onQueryChange = { query: String -> exploreViewModel.setSearchQuery(query) },
                                    onSearch = { query: String -> exploreViewModel.search(query); onSearchActiveChange(false) },
                                    isSearchActive = isSearchActive,
                                    searchListState = searchListState,
                                    accountHistory = accountHistory,
                                    onAccountHistorySongClick = onAccountHistorySongClick,
                                    onSearchActiveChange = onSearchActiveChange,
                                    searchResults = searchResults,
                                    searchHistory = searchHistory,
                                    onClearSearchHistory = { exploreViewModel.clearSearchHistory() },
                                    onRemoveFromSearchHistory = { item: String -> exploreViewModel.removeFromSearchHistory(item) },
                                    onHistoryItemClick = onHistoryItemClick,
                                    suggestions = suggestions,
                                    isLoadingSuggestions = isLoadingSuggestions,
                                    onSuggestionClick = onSuggestionClick,
                                    onAccountVisibleChange = onAccountVisibleChange,
                                    userProfile = userProfile,
                                    homeMoods = emptyList<String>(),
                                    currentMood = null,
                                    onMoodClick = onMoodClick,
                                    currentFilter = currentFilter,
                                    onFilterClick = { filter: SearchFilter? -> exploreViewModel.setSearchFilter(filter) },
                                    isLandscape = isLandscape,
                                    onClearSearch = onClearSearch,
                                    onCategoryClick = { title: String, browseId: String, params: String? ->
                                        onSearchActiveChange(false)
                                        onGenreClick(title, browseId, params)
                                    },
                                    onGenresClick = {
                                        onSearchActiveChange(false)
                                        onGenresClick()
                                    }
                                )

                                Box(modifier = Modifier.weight(1f)) {
                                    if (isSearchActive || searchQuery.isNotBlank() || searchResults.isNotEmpty()) {
                                        ExploreSearch(
                                            viewModel = exploreViewModel,
                                            searchListState = searchListState,
                                            onTopResultPlayClick = onTopResultPlayClick,
                                            onTopResultClick = onTopResultClick,
                                            onSaveClick = onSaveClick,
                                            onSongMenuClick = onSongMenuClick,
                                            onSearchItemClick = onSearchItemClick,
                                            onSearchItemTouchDown = onSearchItemTouchDown,
                                            onLoadMoreResults = { exploreViewModel.loadMoreResults() }
                                        )
                                    } else {
                                        ExploreHome(
                                            viewModel = exploreViewModel,
                                            accountVM = accountVM,
                                            spotifyViewModel = spotifyViewModel,
                                            effectiveExplorePriority = effectiveExplorePriority,
                                            homeListState = homeListState,
                                            isLandscape = isLandscape,
                                            onSongTouchDown = onSongTouchDown,
                                            onSongClick = onDetailSongClick,
                                            onAlbumClick = onAlbumClick,
                                            onArtistClick = onArtistClick,
                                            onPlaylistClick = onPlaylistClick,
                                            onSongMenuClick = onSongMenuClick,
                                            onSectionClick = onSectionClick,
                                            onRadioClick = onRadioClick,
                                            onSaveClick = onSaveClick,
                                            onMoodClick = { title, browseId, params -> onGenreClick(title, browseId, params) },
                                            onGenresClick = onGenresClick,
                                            onNavigateToSpotifySignIn = onNavigateToSpotifySignIn,
                                            onNavigateToSpotifyPlaylist = onNavigateToSpotifyPlaylist
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            ExploreDetailHost(
                                screen = screen,
                                viewModel = exploreViewModel,
                                onBack = onDetailBack,
                                onSongClick = onDetailSongClick,
                                onSongMenuClick = onSongMenuClick,
                                onStartRadioClick = onStartRadioClick,
                                onAlbumClick = onAlbumClick,
                                onArtistClick = onArtistClick,
                                onPlaylistClick = onPlaylistClick,
                                onSubscribeClick = onSubscribeClick,
                                onSectionClick = onSectionClick,
                                onGenreClick = onGenreClick
                            )
                        }
                    }
                }
            }
        }
    }
}
