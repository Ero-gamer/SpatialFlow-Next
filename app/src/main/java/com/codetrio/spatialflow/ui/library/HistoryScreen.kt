package com.codetrio.spatialflow.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.codetrio.spatialflow.ui.components.LiquidGlassActionPill
import com.codetrio.spatialflow.ui.components.LiquidGlassPillIconButton
import com.codetrio.spatialflow.ui.components.rememberThrottledBackdrop
import com.codetrio.spatialflow.ui.components.throttledLayerBackdrop
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.ui.explore.SearchResultItem

/**
 * A beautiful dedicated page showing Listening History as a list of songs,
 * strictly focused on the song playback rather than account settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<OnlineSong>,
    currentOnlineSong: OnlineSong?,
    isLoadingStream: Boolean,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.background
    val historyBackdrop = rememberThrottledBackdrop(surfaceColor)
    val topBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 68.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        val pullToRefreshState = rememberPullToRefreshState()
        
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .throttledLayerBackdrop(historyBackdrop),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topBarPadding)
                )
            }
        ) {
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No listening history yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Songs you listen to from your account will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topBarPadding, bottom = 120.dp)
                ) {
                    itemsIndexed(
                        items = history,
                        key = { index, song -> "history-${song.videoId}-$index" },
                        contentType = { _, _ -> "history_song" }
                    ) { index, song ->
                        SearchResultItem(
                            item = SearchItem.Song(song),
                            isCurrentlyPlaying = song.videoId == currentOnlineSong?.videoId,
                            isLoading = isLoadingStream && song.videoId == currentOnlineSong?.videoId,
                            onSongMenuClick = onSongMenuClick,
                            onClick = { onSongClick(song, history, index) }
                        )
                    }
                }
            }
        }

        // Floating Liquid Glass Action Pill Header (Sibling on top)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassActionPill(backdrop = historyBackdrop) {
                LiquidGlassPillIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    }
}
