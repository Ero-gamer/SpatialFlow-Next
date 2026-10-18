package com.codetrio.spatialflow.ui.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.player.queue.YouTubeQueue
import com.codetrio.spatialflow.ui.ObserveAsEvents
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.viewmodel.ExploreEvent
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Isolated Single Event/Effect Host component for Explore.
 * Consolidates all UI event handling and playback sync collectors into one lifecycle-safe location.
 */
@Composable
fun ExploreEffects(
    viewModel: ExploreViewModel,
    playerSharedViewModel: PlayerSharedViewModel,
    onScrollToTop: () -> Unit,
    onOpenSearch: () -> Unit,
    onNavigateToSpotifyPlaylist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. SINGLE-RECEIVE CHANNEL EVENT CONSUMER (Lifecycle-Started)
    ObserveAsEvents(flow = viewModel.events, key1 = viewModel) { event ->
        when (event) {
            is ExploreEvent.ShowSnackbar -> {
                SnackbarController.showMessage(event.message.asString(context))
            }
            is ExploreEvent.TriggerInstantPlayback -> {
                val song = viewModel.playbackState.value.currentOnlineSong
                val onlineQueue = viewModel.onlineQueue.value
                val currentIndex = viewModel.currentOnlineIndex.value
                if (song != null) {
                    if (onlineQueue.isNotEmpty()) {
                        val songItems = onlineQueue.map { onlineSong ->
                            SongItem.createOnlineSong(
                                onlineSong.videoId,
                                onlineSong.title,
                                onlineSong.artist,
                                "",
                                onlineSong.durationMs,
                                onlineSong.thumbnailUrl,
                                onlineSong.artistId
                            )
                        }
                        val queue = com.codetrio.spatialflow.player.queue.LocalPlaylistQueue(
                            playlistTitle = "Playlist",
                            songs = songItems,
                            startIndex = currentIndex.coerceIn(0, (songItems.size - 1).coerceAtLeast(0))
                        )
                        playerSharedViewModel.setQueue(queue, currentIndex)
                    } else {
                        val songItem = SongItem.createOnlineSong(
                            song.videoId,
                            song.title,
                            song.artist,
                            "",
                            song.durationMs,
                            song.thumbnailUrl,
                            song.artistId
                        )
                        val queue = YouTubeQueue.radio(songItem)
                        playerSharedViewModel.setQueue(queue)
                    }
                }
            }
            is ExploreEvent.ScrollToTop -> {
                onScrollToTop()
            }
            is ExploreEvent.OpenSearch -> {
                onOpenSearch()
            }
            is ExploreEvent.NavigateToSpotifyPlaylist -> {
                onNavigateToSpotifyPlaylist(event.playlistId)
            }
        }
    }

    // 2. LIFECYCLE-SAFE PLAYBACK TRIGGER EVENT CONSUMER
    LaunchedEffect(lifecycleOwner.lifecycle, viewModel, playerSharedViewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.playbackTriggerEvent.collect {
                val song = viewModel.playbackState.value.currentOnlineSong
                val onlineQueue = viewModel.onlineQueue.value
                val currentIndex = viewModel.currentOnlineIndex.value
                if (song != null) {
                    if (onlineQueue.isNotEmpty()) {
                        val queue = withContext(Dispatchers.Default) {
                            val songItems = onlineQueue.map { onlineSong ->
                                SongItem.createOnlineSong(
                                    onlineSong.videoId,
                                    onlineSong.title,
                                    onlineSong.artist,
                                    "",
                                    onlineSong.durationMs,
                                    onlineSong.thumbnailUrl,
                                    onlineSong.artistId
                                )
                            }
                            com.codetrio.spatialflow.player.queue.LocalPlaylistQueue(
                                playlistTitle = "Playlist",
                                songs = songItems,
                                startIndex = currentIndex.coerceIn(0, (songItems.size - 1).coerceAtLeast(0))
                            )
                        }
                        playerSharedViewModel.setQueue(queue, currentIndex)
                    } else {
                        val queue = withContext(Dispatchers.Default) {
                            val songItem = SongItem.createOnlineSong(
                                song.videoId,
                                song.title,
                                song.artist,
                                "",
                                song.durationMs,
                                song.thumbnailUrl,
                                song.artistId
                            )
                            YouTubeQueue.radio(songItem)
                        }
                        playerSharedViewModel.setQueue(queue)
                    }
                }
            }
        }
    }

    // 3. LIFECYCLE-SAFE QUEUE INDEX SYNC CONSUMER
    LaunchedEffect(lifecycleOwner.lifecycle, viewModel, playerSharedViewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                playerSharedViewModel.currentSongIndex.collect { idx ->
                    if (idx >= 0) {
                        val queue = viewModel.onlineQueue.value
                        if (idx < queue.size) {
                            viewModel.updateActiveSongAndIndex(idx, queue[idx])
                        }
                    }
                }
            }
        }
    }
}
