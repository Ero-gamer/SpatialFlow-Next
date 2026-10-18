package com.codetrio.spatialflow.ui.explore

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.viewmodel.ExploreViewModel

/**
 * Isolated Search stateful container component for Explore.
 * Collects ONLY Search & Playback StateFlows locally.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExploreSearch(
    viewModel: ExploreViewModel,
    searchListState: LazyListState,
    onTopResultPlayClick: (SearchItem.TopResult) -> Unit,
    onTopResultClick: (SearchItem.TopResult) -> Unit,
    onSaveClick: (SearchItem) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSearchItemClick: (SearchItem) -> Unit,
    onSearchItemTouchDown: (SearchItem) -> Unit,
    onLoadMoreResults: () -> Unit
) {
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searchQuery = searchState.searchQuery
    val searchResults = searchState.searchResults
    val isSearching = searchState.isSearching

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isLoadingStream = playbackState.isLoadingStream
    val currentSongId = playbackState.currentOnlineSong?.videoId

    ExploreSearchContent(
        searchQuery = searchQuery,
        searchResults = searchResults,
        isSearching = isSearching,
        currentSongId = currentSongId,
        isLoadingStream = isLoadingStream,
        searchListState = searchListState,
        onTopResultPlayClick = onTopResultPlayClick,
        onTopResultClick = onTopResultClick,
        onSaveClick = onSaveClick,
        onSongMenuClick = onSongMenuClick,
        onSearchItemClick = onSearchItemClick,
        onSearchItemTouchDown = onSearchItemTouchDown,
        onLoadMoreResults = onLoadMoreResults
    )
}

/**
 * Stateless Search results UI component for Explore.
 * Accepts narrow primitive currentSongId to minimize item recomposition passes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExploreSearchContent(
    searchQuery: String,
    searchResults: List<SearchItem>,
    isSearching: Boolean,
    currentSongId: String?,
    isLoadingStream: Boolean,
    searchListState: LazyListState,
    onTopResultPlayClick: (SearchItem.TopResult) -> Unit,
    onTopResultClick: (SearchItem.TopResult) -> Unit,
    onSaveClick: (SearchItem) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSearchItemClick: (SearchItem) -> Unit,
    onSearchItemTouchDown: (SearchItem) -> Unit,
    onLoadMoreResults: () -> Unit
) {
    val rememberedOnSongMenuClick = remember(onSongMenuClick) { onSongMenuClick }
    val rememberedOnSearchItemTouchDown = remember(onSearchItemTouchDown) { onSearchItemTouchDown }
    val rememberedOnSearchItemClick = remember(onSearchItemClick) { onSearchItemClick }

    Box(modifier = Modifier.fillMaxSize().animateContentSize()) {
        when {
            isSearching && searchResults.isEmpty() -> {
                SearchResultsSkeletonList()
            }

            searchResults.isNotEmpty() -> {
                LazyColumn(
                    state = searchListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    itemsIndexed(
                        items = searchResults,
                        key = { index, item ->
                            val prefix = when (item) {
                                is SearchItem.TopResult -> "top-${item.title}"
                                is SearchItem.Header -> "header-${item.title}"
                                is SearchItem.Song -> "song-${item.song.videoId}"
                                is SearchItem.Album -> "album-${item.album.browseId}"
                                is SearchItem.Artist -> "artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                            }
                            "search-${index}-${prefix}"
                        },
                        contentType = { _, item -> item::class.java.simpleName }
                    ) { _, item ->
                        when (item) {
                            is SearchItem.TopResult -> {
                                TopResultCard(
                                    topResult = item,
                                    isCurrentlyPlaying = item.song?.videoId == currentSongId,
                                    onPlayClick = { onTopResultPlayClick(item) },
                                    onSaveClick = { onSaveClick(item) },
                                    onMoreClick = { 
                                        item.song?.let { rememberedOnSongMenuClick(it) } ?: onSaveClick(item) 
                                    },
                                    onClick = { onTopResultClick(item) }
                                )
                            }
                            is SearchItem.Header -> {
                                SearchSectionHeader(title = item.title)
                            }
                            else -> {
                                SearchResultItem(
                                    item = item,
                                    isCurrentlyPlaying = when (item) {
                                        is SearchItem.Song -> item.song.videoId == currentSongId
                                        else -> false
                                    },
                                    isLoading = isLoadingStream && when (item) {
                                        is SearchItem.Song -> item.song.videoId == currentSongId
                                        else -> false
                                    },
                                    onSongMenuClick = rememberedOnSongMenuClick,
                                    onTouchDown = { rememberedOnSearchItemTouchDown(item) },
                                    onClick = { rememberedOnSearchItemClick(item) }
                                )
                            }
                        }
                    }
                    item(key = "search-load-more") {
                        if (isSearching) {
                            SearchPaginationSkeleton()
                        }
                    }
                }

                LaunchedEffect(searchListState, searchResults.size) {
                    snapshotFlow {
                        val lastIndex = searchListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        val total = searchListState.layoutInfo.totalItemsCount
                        lastIndex >= 0 && total > 0 && lastIndex >= total - 4
                    }
                    .distinctUntilChanged()
                    .collect { shouldLoadMore ->
                        if (shouldLoadMore) {
                            onLoadMoreResults()
                        }
                    }
                }
            }

            searchQuery.isNotBlank() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No results found for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
