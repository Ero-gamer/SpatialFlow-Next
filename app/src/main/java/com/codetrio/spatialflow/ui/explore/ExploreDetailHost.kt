package com.codetrio.spatialflow.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.viewmodel.ExploreViewModel

/**
 * Isolated Detail navigation host component for Explore.
 * Collects ONLY detailState and playbackState locally to isolate detail rendering.
 */
@Composable
fun ExploreDetailHost(
    screen: String,
    viewModel: ExploreViewModel,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onSubscribeClick: (String) -> Unit,
    onSectionClick: (String, String?, String) -> Unit,
    onGenreClick: (String, String, String?) -> Unit
) {
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val albumDetail = detailState.albumDetail
    val artistDetail = detailState.artistDetail
    val playlistDetail = detailState.playlistDetail
    val sectionDetail = detailState.sectionDetail
    val moodDetail = detailState.moodDetail
    val genresUiModels = detailState.genresUiModels
    val isLoadingDetail = detailState.isLoadingDetail
    val currentRoute = detailState.detailStack.lastOrNull()

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentOnlineSong = playbackState.currentOnlineSong
    val isLoadingStream = playbackState.isLoadingStream

    when (screen) {
        "album" -> {
            if (albumDetail != null) {
                AlbumDetailView(
                    albumPage = albumDetail,
                    currentOnlineSong = currentOnlineSong,
                    isLoadingStream = isLoadingStream,
                    onBack = onBack,
                    onSongClick = onSongClick,
                    onSongMenuClick = onSongMenuClick,
                    onStartRadioClick = onStartRadioClick,
                    onArtistClick = onArtistClick
                )
            } else if (!isLoadingDetail) {
                DetailErrorStateView(
                    title = "Unable to load Album",
                    onBack = onBack,
                    onRetry = {
                        if (currentRoute != null) {
                            viewModel.loadAlbum(currentRoute.id)
                        }
                    }
                )
            } else {
                AlbumGridScreenSkeleton()
            }
        }

        "playlist" -> {
            if (playlistDetail != null) {
                PlaylistDetailView(
                    playlistPage = playlistDetail,
                    currentOnlineSong = currentOnlineSong,
                    isLoadingStream = isLoadingStream,
                    onBack = onBack,
                    onSongClick = onSongClick,
                    onSongMenuClick = onSongMenuClick,
                    onStartRadioClick = onStartRadioClick,
                    onLoadMore = { viewModel.loadMorePlaylistSongs() }
                )
            } else if (!isLoadingDetail) {
                DetailErrorStateView(
                    title = "Unable to load Playlist",
                    onBack = onBack,
                    onRetry = {
                        if (currentRoute != null) {
                            viewModel.loadPlaylist(currentRoute.id)
                        }
                    }
                )
            } else {
                SongListScreenSkeleton()
            }
        }

        "artist" -> {
            if (artistDetail != null) {
                ArtistDetailView(
                    artistPage = artistDetail,
                    currentOnlineSong = currentOnlineSong,
                    isSubscribed = artistDetail.artist.isSubscribed,
                    onBack = onBack,
                    onSongClick = onSongClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                    onSongMenuClick = onSongMenuClick,
                    onSubscribeClick = onSubscribeClick,
                    onStartRadioClick = onStartRadioClick,
                    onSectionClick = onSectionClick
                )
            } else if (!isLoadingDetail) {
                DetailErrorStateView(
                    title = "Unable to load Artist",
                    onBack = onBack,
                    onRetry = {
                        if (currentRoute != null) {
                            viewModel.loadArtist(currentRoute.id)
                        }
                    }
                )
            } else {
                ArtistDetailScreenSkeleton()
            }
        }

        "section" -> {
            if (sectionDetail != null) {
                SectionDetailView(
                    section = sectionDetail,
                    currentOnlineSong = currentOnlineSong,
                    isLoadingStream = isLoadingStream,
                    onBack = onBack,
                    onSongClick = onSongClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                    onSongMenuClick = onSongMenuClick,
                    onStartRadioClick = onStartRadioClick
                )
            } else if (!isLoadingDetail) {
                DetailErrorStateView(
                    title = "Unable to load ${currentRoute?.title ?: "Section"}",
                    onBack = onBack,
                    onRetry = {
                        if (currentRoute != null) {
                            viewModel.loadSectionDetails(currentRoute.id, currentRoute.params, currentRoute.title ?: "Section")
                        }
                    }
                )
            } else {
                SongListScreenSkeleton()
            }
        }

        "genres" -> {
            if (genresUiModels.isNotEmpty()) {
                GenresScreen(
                    genresUiModels = genresUiModels,
                    onBack = onBack,
                    onGenreClick = onGenreClick
                )
            } else if (!isLoadingDetail) {
                DetailErrorStateView(
                    title = "Unable to load Genres",
                    onBack = onBack,
                    onRetry = {
                        viewModel.loadGenres()
                    }
                )
            } else {
                DetailScreenSkeleton()
            }
        }

        "mood" -> {
            if (moodDetail != null) {
                MoodDetailView(
                    moodDetail = moodDetail,
                    currentOnlineSong = currentOnlineSong,
                    isLoadingStream = isLoadingStream,
                    onBack = onBack,
                    onSongClick = onSongClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                    onSongMenuClick = onSongMenuClick,
                    onSectionClick = onSectionClick,
                    onStartRadioClick = onStartRadioClick
                )
            } else if (!isLoadingDetail) {
                DetailErrorStateView(
                    title = "Unable to load ${currentRoute?.title ?: "Mood"}",
                    onBack = onBack,
                    onRetry = {
                        if (currentRoute != null) {
                            viewModel.loadMood(currentRoute.title ?: "Mood", currentRoute.id, currentRoute.params)
                        }
                    }
                )
            } else {
                DetailScreenSkeleton()
            }
        }
    }
}

@Composable
private fun DetailErrorStateView(
    title: String,
    message: String = "Something went wrong while loading this content. Check your network connection and try again.",
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retry")
            }
        }
    }
}
