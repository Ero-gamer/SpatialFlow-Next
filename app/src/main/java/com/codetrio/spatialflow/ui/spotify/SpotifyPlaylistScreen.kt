@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.codetrio.spatialflow.ui.spotify

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.components.SwipeToEnqueueContainer
import com.codetrio.spatialflow.ui.dialogs.PlaylistDownloadBottomSheet
import com.codetrio.spatialflow.util.SongDownloader
import kotlinx.coroutines.Dispatchers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.codetrio.spatialflow.data.spotify.SpotifyTrack
import com.codetrio.spatialflow.data.spotify.thumbnailUrl
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.ui.components.ExplicitBadge
import com.codetrio.spatialflow.ui.explore.AdaptiveDetailContainer
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.codetrio.spatialflow.viewmodel.SpotifyPlaylistViewModel
import kotlinx.coroutines.launch

private sealed interface SpotifyScreenState {
    data object Loading : SpotifyScreenState
    data class Error(val message: String) : SpotifyScreenState
    data object Content : SpotifyScreenState
}

/**
 * High-fidelity Spotify Playlist screen aligning directly with YouTube Music playlist styling.
 * Leverages AdaptiveDetailContainer for ambient blur parallax header, 2-pane landscape,
 * and ExpressiveConnectedButtonGroup for interactive Play All / Shuffle controls.
 */
@Composable
fun SpotifyPlaylistScreen(
    playlistId: String,
    navController: NavController,
    playerViewModel: PlayerSharedViewModel,
    modifier: Modifier = Modifier,
    spotifyPlaylistViewModel: SpotifyPlaylistViewModel = viewModel(),
) {
    val playlist by spotifyPlaylistViewModel.playlist.collectAsStateWithLifecycle()
    val tracks by spotifyPlaylistViewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by spotifyPlaylistViewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by spotifyPlaylistViewModel.errorMessage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }
    var isResolvingAll by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(playlistId) {
        spotifyPlaylistViewModel.load(playlistId)
    }

    LaunchedEffect(tracks) {
        if (tracks.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                tracks.resolveBatchForPlayback(6)
            }
        }
    }

    val screenState = when {
        isLoading && playlist == null -> SpotifyScreenState.Loading
        errorMessage != null && playlist == null -> SpotifyScreenState.Error(errorMessage ?: "Unable to load playlist")
        else -> SpotifyScreenState.Content
    }

    AnimatedContent(
        targetState = screenState,
        transitionSpec = {
            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(initialScale = 0.98f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                .togetherWith(
                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            scaleOut(targetScale = 0.98f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                )
        },
        label = "SpotifyPlaylistStateTransition"
    ) { state ->
        when (state) {
            is SpotifyScreenState.Loading -> {
                Box(
                    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }

            is SpotifyScreenState.Error -> {
                Box(
                    modifier = modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = spotifyPlaylistViewModel::reload,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            }

            is SpotifyScreenState.Content -> {
                val title = playlist?.name ?: "Spotify playlist"
                val totalTracks = playlist?.tracks?.total ?: tracks.size
                val owner = playlist?.owner?.displayName
                val subtitle = if (!owner.isNullOrBlank()) {
                    "Spotify Playlist • $owner • $totalTracks tracks"
                } else {
                    "Spotify Playlist • $totalTracks tracks"
                }

                var showDownloadSheet by remember { mutableStateOf(false) }

                AdaptiveDetailContainer(
                    isLandscape = isLandscape,
                    thumbnailUrl = playlist?.thumbnailUrl(),
                    title = title,
                    subtitle = subtitle,
                    sharedElementKey = playlist?.id,
                    entityType = com.codetrio.spatialflow.ui.explore.DetailEntityType.PLAYLIST,
                    artistName = owner,
                    entityId = playlist?.id,
                    onDownloadClick = { showDownloadSheet = true },
                    onBack = { navController.navigateUp() },
                    headerActions = {
                            
                        SpotifyExpressiveButtonGroup(
                            isResolvingAll = isResolvingAll,
                            hasTracks = tracks.isNotEmpty(),
                            onPlayAllClick = {
                                if (isResolvingAll || tracks.isEmpty()) return@SpotifyExpressiveButtonGroup
                                isResolvingAll = true
                                scope.launch {
                                    val firstTrack = tracks.firstOrNull()
                                    val firstSong = firstTrack?.resolveForPlayback()
                                    if (firstSong == null) {
                                        SnackbarController.showMessage("No YouTube Music match found for playlist")
                                    } else {
                                        playerViewModel.setQueue(
                                            com.codetrio.spatialflow.player.queue.StaticQueue(
                                                currentTitle = title,
                                                initialItems = listOf(firstSong),
                                                startIndex = 0
                                            )
                                        )
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val nextResolved = tracks.drop(1).resolveBatchForPlayback(30)
                                            if (nextResolved.isNotEmpty()) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    playerViewModel.addSongsToQueue(nextResolved)
                                                }
                                            }
                                        }
                                    }
                                    isResolvingAll = false
                                }
                            },
                            onShuffleClick = {
                                if (isResolvingAll || tracks.isEmpty()) return@SpotifyExpressiveButtonGroup
                                isResolvingAll = true
                                scope.launch {
                                    val shuffled = tracks.shuffled()
                                    val firstTrack = shuffled.firstOrNull()
                                    val firstSong = firstTrack?.resolveForPlayback()
                                    if (firstSong == null) {
                                        SnackbarController.showMessage("No YouTube Music match found for playlist")
                                    } else {
                                        playerViewModel.setQueue(
                                            com.codetrio.spatialflow.player.queue.StaticQueue(
                                                currentTitle = "$title (Shuffled)",
                                                initialItems = listOf(firstSong),
                                                startIndex = 0
                                            )
                                        )
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val nextResolved = shuffled.drop(1).resolveBatchForPlayback(30)
                                            if (nextResolved.isNotEmpty()) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    playerViewModel.addSongsToQueue(nextResolved)
                                                }
                                            }
                                        }
                                    }
                                    isResolvingAll = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                ) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }

                if (errorMessage != null && tracks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errorMessage ?: "Partial playlist loaded",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = spotifyPlaylistViewModel::reload,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Retry remaining", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = tracks,
                    key = { index: Int, track: SpotifyTrack -> "sp_trk_${index}_${track.id}" },
                    contentType = { _: Int, _: SpotifyTrack -> "spotify_track" }
                ) { index: Int, track: SpotifyTrack ->
                    SwipeToEnqueueContainer(
                        onPlayNext = {
                            scope.launch {
                                val song = track.resolveForPlayback()
                                if (song != null) {
                                    playerViewModel.addToQueueNext(song)
                                } else {
                                    SnackbarController.showMessage("Could not match \"${track.name}\"")
                                }
                            }
                        },
                        onAddToQueue = {
                            scope.launch {
                                val song = track.resolveForPlayback()
                                if (song != null) {
                                    playerViewModel.addToQueue(song)
                                } else {
                                    SnackbarController.showMessage("Could not match \"${track.name}\"")
                                }
                            }
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    ) {
                        SpotifyTrackDetailItem(
                            track = track,
                            index = index + 1,
                            isResolving = resolvingTrackId == track.id,
                            onClick = {
                                if (resolvingTrackId != null || isResolvingAll) return@SpotifyTrackDetailItem
                                resolvingTrackId = track.id
                                scope.launch {
                                    val song = track.resolveForPlayback()
                                    if (song == null) {
                                        SnackbarController.showMessage("No YouTube Music match found for \"${track.name}\"")
                                    } else {
                                        playerViewModel.setQueue(
                                            com.codetrio.spatialflow.player.queue.StaticQueue(
                                                currentTitle = title,
                                                initialItems = listOf(song),
                                                startIndex = 0
                                            )
                                        )
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val nextResolved = tracks.drop(index + 1).resolveBatchForPlayback(30)
                                            if (nextResolved.isNotEmpty()) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    playerViewModel.addSongsToQueue(nextResolved)
                                                }
                                            }
                                        }
                                    }
                                    resolvingTrackId = null
                                }
                            }
                        )
                    }
                }
            }

            if (showDownloadSheet) {
                val placeholderSongs = remember(tracks) {
                    tracks.map { track ->
                        SongItem(
                            id = track.id.hashCode().toLong(),
                            rawTitle = track.name,
                            rawArtist = track.artists.joinToString { it.name }.ifEmpty { "Unknown Artist" },
                            albumId = 0L,
                            path = null,
                            duration = track.durationMs.toLong(),
                            dateAdded = System.currentTimeMillis()
                        ).apply {
                            thumbnailUrl = track.album?.images?.firstOrNull()?.url
                            albumName = track.album?.name
                        }
                    }
                }
                PlaylistDownloadBottomSheet(
                    playlistTitle = title,
                    playlistAuthor = owner,
                    thumbnailUrl = playlist?.thumbnailUrl(),
                    songs = placeholderSongs,
                    onConfirmDownload = { selectedSongs, format ->
                        showDownloadSheet = false
                        SnackbarController.showMessage("Queuing ${selectedSongs.size} tracks for download...")
                        scope.launch(Dispatchers.IO) {
                            val cleanPlaylistName = title.replace(Regex("[^a-zA-Z0-9._\\s-]"), "_").trim()
                            val subFolder = "SpatialFlow/Playlists/$cleanPlaylistName"
                            selectedSongs.forEach { item ->
                                val track = tracks.firstOrNull { it.id.hashCode().toLong() == item.id || it.name == item.title }
                                val resolved = track?.resolveForPlayback() ?: item
                                if (!resolved.videoId.isNullOrEmpty()) {
                                    SongDownloader.downloadSong(
                                        context = context,
                                        song = resolved,
                                        customSubFolder = subFolder,
                                        overrideFormat = format
                                    )
                                }
                            }
                        }
                    },
                    onDismiss = { showDownloadSheet = false }
                )
            }
        }
    }
}
}

/**
 * Expressive connected button group with Play All and Shuffle actions matching YouTube Music styling.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpotifyExpressiveButtonGroup(
    isResolvingAll: Boolean,
    hasTracks: Boolean,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val playInteraction = remember { MutableInteractionSource() }
        val playPressed by playInteraction.collectIsPressedAsState()

        // 1. Play All (Connected Leading Button)
        ToggleButton(
            checked = playPressed,
            onCheckedChange = {
                if (hasTracks && !isResolvingAll) {
                    onPlayAllClick()
                }
            },
            enabled = hasTracks && !isResolvingAll,
            interactionSource = playInteraction,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            AnimatedContent(
                targetState = isResolvingAll,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            scaleIn(initialScale = 0.85f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                    scaleOut(targetScale = 0.85f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        )
                },
                label = "PlayAllButtonContent"
            ) { resolving ->
                if (resolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play All",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val shuffleInteraction = remember { MutableInteractionSource() }
        val shufflePressed by shuffleInteraction.collectIsPressedAsState()

        // 2. Shuffle (Connected Trailing Button)
        ToggleButton(
            checked = shufflePressed,
            onCheckedChange = {
                if (hasTracks && !isResolvingAll) {
                    onShuffleClick()
                }
            },
            enabled = hasTracks && !isResolvingAll,
            interactionSource = shuffleInteraction,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                checkedContainerColor = MaterialTheme.colorScheme.secondary,
                checkedContentColor = MaterialTheme.colorScheme.onSecondary
            ),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Shuffle",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Playlist track row styled exactly like YouTube Music's SearchResultItem with index, cover art, badges, and duration.
 */
@Composable
private fun SpotifyTrackDetailItem(
    track: SpotifyTrack,
    index: Int,
    isResolving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !isResolving, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index Number
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Thumbnail Image with 4dp rounded corners (YT Music style)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            SpatialImage(
                model = track.thumbnailUrl(),
                contentDescription = null,
                size = ImageSemanticSize.ThumbnailSmall,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Titles and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.explicit) {
                    ExplicitBadge(modifier = Modifier.padding(end = 6.dp))
                }
                val artistText = track.artists.joinToString { it.name }.ifBlank { "Unknown Artist" }
                val formattedDur = formatSpotifyDuration(track.durationMs)
                Text(
                    text = if (formattedDur.isNotBlank()) "$artistText • $formattedDur" else artistText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing status indicator
        AnimatedContent(
            targetState = isResolving,
            transitionSpec = {
                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(initialScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                    .togetherWith(
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleOut(targetScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    )
            },
            label = "TrackResolvingIndicator"
        ) { resolving ->
            if (resolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(modifier = Modifier.size(0.dp))
            }
        }
    }
}
