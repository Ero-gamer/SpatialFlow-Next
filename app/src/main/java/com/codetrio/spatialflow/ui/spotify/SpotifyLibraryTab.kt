@file:OptIn(ExperimentalMaterial3Api::class)

package com.codetrio.spatialflow.ui.spotify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.codetrio.spatialflow.ui.theme.concentricInner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.codetrio.spatialflow.data.spotify.SpotifyPlaylist
import com.codetrio.spatialflow.data.spotify.SpotifyTrack
import com.codetrio.spatialflow.data.spotify.thumbnailUrl
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.codetrio.spatialflow.viewmodel.SpotifyAuthState
import com.codetrio.spatialflow.viewmodel.SpotifyViewModel
import kotlinx.coroutines.launch

@Composable
fun SpotifyLibraryTab(
    navController: NavController,
    spotifyViewModel: SpotifyViewModel,
    playerViewModel: PlayerSharedViewModel,
    modifier: Modifier = Modifier,
) {
    val authState by spotifyViewModel.authState.collectAsStateWithLifecycle()
    val playlists by spotifyViewModel.playlists.collectAsStateWithLifecycle()
    val likedTracks by spotifyViewModel.likedTracks.collectAsStateWithLifecycle()
    val accountName by spotifyViewModel.accountName.collectAsStateWithLifecycle()
    val accountAvatarUrl by spotifyViewModel.accountAvatarUrl.collectAsStateWithLifecycle()
    val errorMessage by spotifyViewModel.errorMessage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }

    when (authState) {
        SpotifyAuthState.NOT_CONNECTED -> SpotifyConnectCard(
            modifier = modifier,
            onConnect = { navController.navigate("spotify_signin") },
        )

        SpotifyAuthState.LOADING -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        SpotifyAuthState.ERROR -> SpotifyErrorCard(
            modifier = modifier,
            message = errorMessage ?: "Spotify could not be connected.",
            onRetry = spotifyViewModel::reload,
            onDismiss = spotifyViewModel::dismissError,
        )

        SpotifyAuthState.CONNECTED -> Column(modifier = modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Playlists", "Liked Songs").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            if (selectedTab == 0) {
                SpotifyPlaylistsGrid(
                    playlists = playlists,
                    onPlaylistClick = { playlist ->
                        navController.navigate("spotify_playlist/${playlist.id}")
                    },
                )
            } else {
                SpotifyTracksList(
                    tracks = likedTracks,
                    resolvingTrackId = resolvingTrackId,
                    onTrackClick = { track ->
                        if (resolvingTrackId != null) return@SpotifyTracksList
                        resolvingTrackId = track.id
                        scope.launch {
                            val song = track.resolveForPlayback()
                            if (song == null) {
                                SnackbarController.showMessage("No YouTube Music match found")
                            } else {
                                playerViewModel.setQueue(
                                    com.codetrio.spatialflow.player.queue.StaticQueue(
                                        currentTitle = "Spotify Liked Songs",
                                        initialItems = listOf(song),
                                        startIndex = 0
                                    )
                                )
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val trackIndex = likedTracks.indexOfFirst { it.id == track.id }
                                    val nextTracks = if (trackIndex >= 0) likedTracks.drop(trackIndex + 1) else likedTracks.filter { it.id != track.id }
                                    val nextResolved = nextTracks.resolveBatchForPlayback(30)
                                    if (nextResolved.isNotEmpty()) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            playerViewModel.addSongsToQueue(nextResolved)
                                        }
                                    }
                                }
                            }
                            resolvingTrackId = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SpotifyConnectCard(modifier: Modifier, onConnect: () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF12833B))))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp),
            )
            Text("Connect Spotify", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Browse your playlists and liked songs, then play them through YouTube Music.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Button(onClick = onConnect) {
                Text("Connect Spotify")
            }
        }
    }
}

@Composable
private fun SpotifyErrorCard(
    modifier: Modifier,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text("Spotify connection failed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("Retry") }
                OutlinedButton(onClick = onDismiss) { Text("Disconnect") }
            }
        }
    }
}

@Composable
private fun SpotifyAccountHeader(
    name: String,
    avatarUrl: String?,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatarUrl != null) {
            SpatialImage(
                model = avatarUrl,
                contentDescription = null,
                size = ImageSemanticSize.AvatarSmall,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = Color(0xFF1DB954)) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Spotify", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh Spotify library")
        }
        IconButton(onClick = onLogout) {
            Icon(Icons.Default.ExitToApp, contentDescription = "Disconnect Spotify")
        }
    }
}

@Composable
private fun SpotifyPlaylistsGrid(
    playlists: List<SpotifyPlaylist>,
    onPlaylistClick: (SpotifyPlaylist) -> Unit,
) {
    if (playlists.isEmpty()) {
        SpotifyEmptyState("No Spotify playlists found")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(playlists, key = { idx, playlist -> "sp_lib_pl_${idx}_${playlist.id}" }) { _, playlist ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                // Concentric Corner: Outer (16.dp) - Padding (10.dp) = 6.dp
                val thumbCorner = 16.dp.concentricInner(10.dp)
                Column(modifier = Modifier.padding(10.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                        SpatialImage(
                            model = playlist.thumbnailUrl(),
                            contentDescription = null,
                            size = ImageSemanticSize.ThumbnailMedium,
                            shape = RoundedCornerShape(thumbCorner),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        // Track count pill on bottom left of thumbnail
                        if ((playlist.tracks?.total ?: 0) > 0) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "${playlist.tracks?.total} tracks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        playlist.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        playlist.owner?.displayName ?: "Spotify playlist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SpotifyTracksList(
    tracks: List<SpotifyTrack>,
    resolvingTrackId: String?,
    onTrackClick: (SpotifyTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        SpotifyEmptyState("No tracks found")
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(tracks.size, key = { idx -> "sp_lib_trk_${idx}_${tracks[idx].id}" }) { index ->
            SpotifyTrackRow(
                track = tracks[index],
                isResolving = resolvingTrackId == tracks[index].id,
                onClick = { onTrackClick(tracks[index]) },
            )
        }
    }
}

@Composable
internal fun SpotifyTrackRow(
    track: SpotifyTrack,
    isResolving: Boolean,
    onClick: () -> Unit,
    index: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isResolving, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(6.dp))
        }

        SpatialImage(
            model = track.thumbnailUrl(),
            contentDescription = null,
            size = ImageSemanticSize.ThumbnailSmall,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(48.dp),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                if (track.explicit) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text("E", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                track.artists.joinToString { it.name }.ifBlank { "Unknown artist" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isResolving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(formatSpotifyDuration(track.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpotifyEmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun formatSpotifyDuration(durationMs: Int): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
