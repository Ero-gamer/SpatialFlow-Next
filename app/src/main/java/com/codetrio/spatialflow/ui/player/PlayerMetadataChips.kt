package com.codetrio.spatialflow.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.metrics.ScreenMetrics
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexDisplay
import com.codetrio.spatialflow.util.SongDownloader

/**
 * Composable rendering the song metadata (title + artist) and the horizontal
 * pill-chip action row (Like/Dislike, Haptics, Lyrics, Save, Share, Download).
 *
 * Corresponds to the [metadataAndChipsContent] lambda previously inlined in [FullPlayer].
 */
@Composable
fun PlayerMetadataChips(
    currentSong: SongItem?,
    isCurrentSongFavorite: Boolean,
    isCurrentSongDisliked: Boolean,
    likesCount: String,
    isHapticsEnabled: Boolean,
    isLyricsModeEnabled: Boolean,
    hasLyrics: Boolean,
    isLyricsLoading: Boolean,
    isCurrentSongDownloaded: Boolean,
    currentSongDownloadProgress: Int?,
    contentColor: Color,
    dynamicAccentColor: Color,
    isDark: Boolean,
    metrics: ScreenMetrics,
    onFavoriteClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onHapticChipClick: () -> Unit,
    onLyricsModeChanged: (Boolean) -> Unit,
    onFetchLyrics: () -> Unit,
    onSaveClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAlbumClick: (String?, String) -> Unit,
    onArtistClick: (String?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PlayerSongInfo(
            currentSong = currentSong,
            contentColor = contentColor,
            metrics = metrics,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick
        )

        Spacer(modifier = Modifier.height(if (metrics.isLandscape) 4.dp else metrics.metadataGap))

        PlayerPillGroup(
            currentSong = currentSong,
            isCurrentSongFavorite = isCurrentSongFavorite,
            isCurrentSongDisliked = isCurrentSongDisliked,
            likesCount = likesCount,
            isHapticsEnabled = isHapticsEnabled,
            isLyricsModeEnabled = isLyricsModeEnabled,
            hasLyrics = hasLyrics,
            isLyricsLoading = isLyricsLoading,
            isCurrentSongDownloaded = isCurrentSongDownloaded,
            currentSongDownloadProgress = currentSongDownloadProgress,
            contentColor = contentColor,
            dynamicAccentColor = dynamicAccentColor,
            isDark = isDark,
            onFavoriteClick = onFavoriteClick,
            onDislikeClick = onDislikeClick,
            onHapticChipClick = onHapticChipClick,
            onLyricsModeChanged = onLyricsModeChanged,
            onFetchLyrics = onFetchLyrics,
            onSaveClick = onSaveClick,
            onDownloadClick = onDownloadClick,
            chipSpacing = metrics.chipSpacing
        )
    }
}

/**
 * Song Title + Artist row with explicit badge and marquee styling.
 */
@Composable
fun PlayerSongInfo(
    currentSong: SongItem?,
    contentColor: Color,
    metrics: ScreenMetrics,
    onAlbumClick: (String?, String) -> Unit,
    onArtistClick: (String?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = MaterialTheme.typography
    val titleStyle = remember(typography, metrics.titleFontSize) {
        typography.headlineMedium.copy(
            fontFamily = GoogleSansFlexDisplay,
            fontSize = metrics.titleFontSize,
            lineHeight = metrics.titleFontSize * 1.25f
        )
    }
    val artistStyle = remember(typography, metrics.artistFontSize) {
        typography.bodyLarge.copy(
            fontSize = metrics.artistFontSize
        )
    }

    val isTitleRtl = remember(currentSong?.title) {
        currentSong?.title?.let { isRtlText(it) } ?: false
    }

    CompositionLocalProvider(LocalLayoutDirection provides (if (isTitleRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.marginHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentSong?.title ?: "Unknown Title",
                    style = titleStyle,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier
                        .basicMarqueeWithFadedEdges()
                        .clickable {
                            if (currentSong != null) {
                                val albumIdStr =
                                    if (currentSong.albumId > 0) currentSong.albumId.toString() else null
                                onAlbumClick(albumIdStr, currentSong.title)
                            }
                        }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.basicMarqueeWithFadedEdges()
                ) {
                    if (currentSong?.isExplicit == true) {
                        com.codetrio.spatialflow.ui.components.ExplicitBadge(
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    ClickableArtists(
                        rawArtist = currentSong?.artist ?: "Unknown Artist",
                        primaryArtistId = currentSong?.artistId,
                        onArtistClick = { artistId, artistName ->
                            onArtistClick(artistId, artistName)
                        },
                        style = artistStyle,
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * Horizontal scrollable Pill Group with Like/Dislike, Haptics, Lyrics, Save, Share, and Download chips.
 */
@Composable
fun PlayerPillGroup(
    currentSong: SongItem?,
    isCurrentSongFavorite: Boolean,
    isCurrentSongDisliked: Boolean,
    likesCount: String,
    isHapticsEnabled: Boolean,
    isLyricsModeEnabled: Boolean,
    hasLyrics: Boolean,
    isLyricsLoading: Boolean,
    isCurrentSongDownloaded: Boolean,
    currentSongDownloadProgress: Int?,
    contentColor: Color,
    dynamicAccentColor: Color,
    isDark: Boolean,
    onFavoriteClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onHapticChipClick: () -> Unit,
    onLyricsModeChanged: (Boolean) -> Unit,
    onFetchLyrics: () -> Unit,
    onSaveClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    chipSpacing: Dp = 8.dp,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showSongShareSheet by remember { mutableStateOf(false) }
    val onShareClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        showSongShareSheet = true
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val pad = 20.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(maxWidth = constraints.maxWidth + 2 * pad)
                    )
                    layout(placeable.width - 2 * pad, placeable.height) {
                        placeable.place(-pad, 0)
                    }
                }
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(12.dp))

            // Like / Dislike split chip
            SplitLikeDislikeChip(
                isLiked = isCurrentSongFavorite,
                isDisliked = isCurrentSongDisliked,
                likesCount = likesCount,
                onLikeClick = onFavoriteClick,
                onDislikeClick = onDislikeClick,
                contentColor = contentColor,
                accentColor = dynamicAccentColor,
                isDark = isDark
            )

            // Music Haptics chip
            PillChip(
                icon = painterResource(id = R.drawable.ic_haptic),
                label = "Music Haptics",
                isSelected = isHapticsEnabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHapticChipClick()
                },
                contentColor = contentColor,
                accentColor = dynamicAccentColor,
                isDark = isDark
            )

            // Lyrics chip
            PillChip(
                icon = painterResource(id = R.drawable.ic_lyrics),
                label = "Lyrics",
                isSelected = isLyricsModeEnabled,
                onClick = {
                    onLyricsModeChanged(!isLyricsModeEnabled)
                    if (currentSong?.id != null && !hasLyrics && !isLyricsLoading) {
                        onFetchLyrics()
                    }
                },
                contentColor = contentColor,
                accentColor = dynamicAccentColor,
                isDark = isDark
            )

            // Save to playlist chip
            PillChip(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = "Save",
                onClick = onSaveClick,
                contentColor = contentColor,
                accentColor = dynamicAccentColor,
                isDark = isDark
            )

            // Share chip
            PillChip(
                icon = painterResource(id = R.drawable.ic_share),
                label = "Share",
                onClick = onShareClick,
                contentColor = contentColor,
                accentColor = dynamicAccentColor,
                isDark = isDark
            )

            // Download chip
            val isDownloading = currentSongDownloadProgress != null
            val downloadLabel = when {
                isCurrentSongDownloaded -> "Downloaded"
                isDownloading -> "Downloading ${currentSongDownloadProgress}%"
                else -> "Download"
            }
            val downloadIcon: Any = if (isCurrentSongDownloaded) {
                painterResource(id = R.drawable.ic_downloaded)
            } else {
                painterResource(id = R.drawable.ic_download)
            }
            PillChip(
                icon = downloadIcon,
                label = downloadLabel,
                isSelected = isCurrentSongDownloaded || isDownloading,
                progress = if (isDownloading) currentSongDownloadProgress / 100f else null,
                onClick = {
                    if (currentSong != null && !isCurrentSongDownloaded && !isDownloading) {
                        SongDownloader.downloadSong(context, currentSong)
                    }
                },
                contentColor = contentColor,
                accentColor = dynamicAccentColor,
                isDark = isDark
            )

            Spacer(modifier = Modifier.width(chipSpacing))
        }
    }

    if (showSongShareSheet && currentSong != null) {
        SongShareBottomSheet(
            song = currentSong,
            onDismiss = { showSongShareSheet = false }
        )
    }
}
