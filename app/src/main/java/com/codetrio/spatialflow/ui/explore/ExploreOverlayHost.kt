package com.codetrio.spatialflow.ui.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.data.db.PlaylistEntity
import com.codetrio.spatialflow.data.innertube.AccountManager
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.ui.dialogs.AddToPlaylistDialog
import com.codetrio.spatialflow.ui.dialogs.CreatePlaylistDialog
import com.codetrio.spatialflow.viewmodel.AccountViewModel
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.codetrio.spatialflow.viewmodel.SpotifyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Isolated Overlay host component for Explore.
 * Collects local playlists and speed dial IDs locally to prevent root invalidation.
 */
@Composable
fun ExploreOverlayHost(
    selectedSongForMenu: OnlineSong?,
    showCreditsForSong: OnlineSong?,
    showAddToPlaylistDialog: Boolean,
    showCreatePlaylistDialog: Boolean,
    songToAddPlaylist: SongItem?,
    accountVM: AccountViewModel,
    spotifyViewModel: SpotifyViewModel?,
    playerSharedViewModel: PlayerSharedViewModel,
    exploreViewModel: ExploreViewModel,
    onDismissSongMenu: () -> Unit,
    onDismissCredits: () -> Unit,
    onShowAddToPlaylistDialogChange: (Boolean) -> Unit,
    onShowCreatePlaylistDialogChange: (Boolean) -> Unit,
    onSongToAddPlaylistChange: (SongItem?) -> Unit,
    onPlaylistAddClick: (SongItem) -> Unit,
    onViewCreditsClick: (OnlineSong) -> Unit,
    onNavigateToSignIn: () -> Unit,
    onNavigateToSpotifySignIn: () -> Unit
) {
    // Local DB collection isolated to overlay boundary
    val localPlaylists by playerSharedViewModel.localPlaylistsFlow.collectAsStateWithLifecycle(emptyList())
    val homeState by exploreViewModel.homeState.collectAsStateWithLifecycle()
    val pinnedSpeedDialIds = homeState.pinnedSpeedDialIds

    // Song 3-Dot Menu Bottom Sheet
    selectedSongForMenu?.let { song ->
        val isPinned = pinnedSpeedDialIds.contains(song.videoId)
        OnlineSongBottomSheet(
            song = song,
            isPinned = isPinned,
            onDismissRequest = onDismissSongMenu,
            playerSharedViewModel = playerSharedViewModel,
            exploreViewModel = exploreViewModel,
            onPlaylistAddClick = { onlineSongItem ->
                onSongToAddPlaylistChange(onlineSongItem)
                onShowAddToPlaylistDialogChange(true)
            },
            onViewCreditsClick = onViewCreditsClick
        )
    }

    // Song Credits Bottom Sheet
    showCreditsForSong?.let { song ->
        SongCreditsBottomSheet(
            song = song,
            onDismissRequest = onDismissCredits,
            onArtistClick = { artistName ->
                onDismissCredits()
                exploreViewModel.loadArtistByName(artistName)
            }
        )
    }

    // Add To Playlist & Create Playlist Dialogs
    val exploreUiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val onlinePlaylists = exploreUiState.onlinePlaylists
    val context = LocalContext.current.applicationContext
    val isLoggedIn = remember(context) { AccountManager.isLoggedIn(context) }
    var createIsOnline by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn, showAddToPlaylistDialog) {
        if (isLoggedIn && showAddToPlaylistDialog) {
            exploreViewModel.loadOnlinePlaylists()
        }
    }

    if (showAddToPlaylistDialog && songToAddPlaylist != null) {
        AddToPlaylistDialog(
            song = songToAddPlaylist,
            localPlaylists = localPlaylists,
            onlinePlaylists = onlinePlaylists,
            isLoggedIn = isLoggedIn,
            onCreateNewLocal = {
                createIsOnline = false
                onShowCreatePlaylistDialogChange(true)
                onShowAddToPlaylistDialogChange(false)
            },
            onCreateNewOnline = {
                createIsOnline = true
                onShowCreatePlaylistDialogChange(true)
                onShowAddToPlaylistDialogChange(false)
            },
            onSelectLocalPlaylist = { playlist ->
                playerSharedViewModel.addSongToLocalPlaylist(playlist.id, songToAddPlaylist)
                onShowAddToPlaylistDialogChange(false)
                onSongToAddPlaylistChange(null)
                SnackbarController.showMessage("Added to playlist: ${playlist.title}")
            },
            onSelectOnlinePlaylist = { onlinePlaylist ->
                val s = songToAddPlaylist
                if (s != null) {
                    SnackbarController.showMessage("Adding to YouTube Music...")
                    playerSharedViewModel.addSongToYouTubePlaylist(onlinePlaylist.playlistId, s) { _, msg ->
                        SnackbarController.showMessage(msg)
                    }
                }
                onShowAddToPlaylistDialogChange(false)
                onSongToAddPlaylistChange(null)
            },
            onDismiss = {
                onShowAddToPlaylistDialogChange(false)
                onSongToAddPlaylistChange(null)
            }
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            isOnline = createIsOnline,
            onConfirm = { name ->
                if (!createIsOnline) {
                    playerSharedViewModel.createLocalPlaylist(name)
                    SnackbarController.showMessage("Created local playlist '$name'")
                } else {
                    SnackbarController.showMessage("Creating YouTube Music playlist...")
                    playerSharedViewModel.createYouTubePlaylist(name, songToAddPlaylist) { success, _ ->
                        if (success) {
                            SnackbarController.showMessage("Created YouTube playlist '$name'")
                            exploreViewModel.loadOnlinePlaylists(forceRefresh = true)
                        } else {
                            SnackbarController.showMessage("Failed to create YouTube playlist")
                        }
                    }
                }
                onShowCreatePlaylistDialogChange(false)
                onShowAddToPlaylistDialogChange(true)
            },
            onDismiss = {
                onShowCreatePlaylistDialogChange(false)
                onShowAddToPlaylistDialogChange(true)
            }
        )
    }
}
