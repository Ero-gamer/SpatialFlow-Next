@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)

package com.codetrio.spatialflow.ui.explore

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.codetrio.spatialflow.data.innertube.AlbumPage
import com.codetrio.spatialflow.data.innertube.ArtistPage
import com.codetrio.spatialflow.data.innertube.HomeSection
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.PlaylistPage
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.innertube.toSongItem
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.components.ExplicitBadge
import com.codetrio.spatialflow.ui.components.LiquidGlassActionPill
import com.codetrio.spatialflow.ui.components.LiquidGlassPillIconButton
import com.codetrio.spatialflow.ui.components.SwipeToEnqueueContainer
import com.codetrio.spatialflow.ui.components.rememberThrottledBackdrop
import com.codetrio.spatialflow.ui.components.throttledLayerBackdrop
import com.codetrio.spatialflow.ui.dialogs.PlaylistDownloadBottomSheet
import com.codetrio.spatialflow.ui.player.ExtractedPaletteColors
import com.codetrio.spatialflow.ui.player.PlayerColorExtractor
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlayer
import com.codetrio.spatialflow.ui.player.canvas.DetailCanvasResolver
import com.codetrio.spatialflow.ui.theme.SpatialShapes
import com.codetrio.spatialflow.ui.theme.observeKey
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.MoodDetail
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * Extracts and caches dynamic ambient palette swatches from artwork URL for Detail pages.
 */
@Composable
fun rememberDetailPalette(artworkUrl: String?): ExtractedPaletteColors {
    val context = LocalContext.current
    var palette by remember(artworkUrl) {
        mutableStateOf(PlayerColorExtractor.getCached(artworkUrl) ?: ExtractedPaletteColors.Default)
    }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) return@LaunchedEffect
        val cached = PlayerColorExtractor.getCached(artworkUrl)
        if (cached != null) {
            palette = cached
            return@LaunchedEffect
        }
        try {
            // 1. Fetch lightweight thumbnail bitmap on IO dispatcher
            val bitmap = withContext(Dispatchers.IO) {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl.resizeCDN(96))
                    .size(96, 96)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                loader.execute(request).drawable?.toBitmap()
            } ?: return@LaunchedEffect

            // 2. CPU-heavy palette swatch extraction on Default dispatcher
            val extracted = withContext(Dispatchers.Default) {
                PlayerColorExtractor.extractFromBitmap(artworkUrl, bitmap)
            }
            palette = extracted
        } catch (_: CancellationException) {
            // Standard coroutine cancellation on key change/unmount
        } catch (_: Exception) {
            // Non-fatal bitmap failure
        }
    }
    return palette
}

/**
 * Entity type descriptor for detail views to resolve live motion/canvas artwork.
 */
enum class DetailEntityType {
    ALBUM,
    ARTIST,
    PLAYLIST,
    OTHER
}

/**
 * Minimalist Apple Music Audio Badges line (Year • Track Count • Dolby Atmos • Hi-Res Lossless).
 * Badges only appear dynamically if Dolby Atmos DSP or Lossless Source Pool streaming are active.
 */
@Composable
fun AppleMusicAudioBadges(
    year: Int?,
    songCountText: String?,
    showDolbyAtmos: Boolean = false,
    showLossless: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val metaParts = mutableListOf<String>()
        if (year != null && year > 0) metaParts.add("$year")
        if (!songCountText.isNullOrBlank()) metaParts.add(songCountText)

        if (metaParts.isNotEmpty()) {
            Text(
                text = metaParts.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        // Dolby Atmos Badge (Only when active)
        if (showDolbyAtmos) {
            if (metaParts.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = com.codetrio.spatialflow.R.drawable.ic_dolby),
                    contentDescription = "Dolby Atmos",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Dolby Atmos",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }

        // Lossless Badge (Only when enabled)
        if (showLossless) {
            if (metaParts.isNotEmpty() || showDolbyAtmos) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = com.codetrio.spatialflow.R.drawable.ic_lossless),
                    contentDescription = "Lossless",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Lossless",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * Apple Music Album Hero Action Row:
 * Left Circular Shuffle button (48dp), Center Large Play stadium pill (48x156dp), Right Circular Radio button (48dp).
 */
@Composable
fun AppleMusicAlbumActionRow(
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRadioClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Shuffle (Circular Tonal Button)
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onShuffleClick()
            },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                modifier = Modifier.size(22.dp)
            )
        }

        // 2. Play (Prominent Stadium Pill)
        val playContainer = if (isDark) Color(0xFFF2F2F2) else MaterialTheme.colorScheme.primary
        val playContent = if (isDark) Color(0xFF101010) else MaterialTheme.colorScheme.onPrimary

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPlayClick()
            },
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 156.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = playContainer,
                contentColor = playContent
            ),
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = com.codetrio.spatialflow.R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
            }
        }

        // 3. Radio (Circular Tonal Button)
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRadioClick()
            },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                painter = painterResource(id = com.codetrio.spatialflow.R.drawable.ic_radio),
                contentDescription = "Start Radio",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Apple Music Album Tracklist Row:
 * Clean typography, track numbers or animated equalizer, optional album art thumbnail with active playback overlay,
 * explicit badge, featured collaborator subtitle, and subtle hairline divider.
 */
@Composable
fun AppleMusicAlbumTrackItem(
    song: OnlineSong,
    index: Int,
    isPlaying: Boolean,
    mainArtistName: String?,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAlbumArt: Boolean = true
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? androidx.fragment.app.FragmentActivity }
    val playerSharedViewModel = remember(activity) {
        activity?.let { androidx.lifecycle.ViewModelProvider(it)[PlayerSharedViewModel::class.java] }
    }

    SwipeToEnqueueContainer(
        onPlayNext = {
            val songItem = SongItem.createOnlineSong(
                song.videoId, song.title, song.artist, "", song.durationMs, song.thumbnailUrl, song.artistId
            )
            playerSharedViewModel?.addToQueueNext(songItem)
        },
        onAddToQueue = {
            val songItem = SongItem.createOnlineSong(
                song.videoId, song.title, song.artist, "", song.durationMs, song.thumbnailUrl, song.artistId
            )
            playerSharedViewModel?.addToQueue(songItem)
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onMenuClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (showAlbumArt) 8.dp else 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track Number / Animated Equalizer Bars
                Box(
                    modifier = Modifier.width(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying && !showAlbumArt) {
                        AnimatedEqualizerBars(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "$index",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Album Art thumbnail with active playback scrim & equalizer overlay
                if (showAlbumArt) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        SpatialImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            size = ImageSemanticSize.ThumbnailSmall,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (isPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedEqualizerBars(
                                    modifier = Modifier.size(20.dp).height(14.dp),
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Track Title & Collaborator Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (song.isExplicit) {
                            Spacer(modifier = Modifier.width(6.dp))
                            ExplicitBadge(size = 12.dp)
                        }
                    }

                    val showArtist = if (!mainArtistName.isNullOrBlank()) {
                        song.artist.isNotBlank() && !song.artist.equals(mainArtistName, ignoreCase = true)
                    } else {
                        song.artist.isNotBlank()
                    }

                    if (showArtist) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // More Options
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = if (showAlbumArt) 104.dp else 52.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
            )
        }
    }
}

/**
 * Apple Music Album Footer:
 * Track count, total calculated duration, and release year.
 */
@Composable
fun AppleMusicAlbumFooter(
    trackCount: Int,
    year: Int?,
    songs: List<OnlineSong>,
    isLossless: Boolean = true,
    modifier: Modifier = Modifier
) {
    val totalSeconds = remember(songs) {
        songs.sumOf { song ->
            if (song.durationMs > 0) {
                song.durationMs / 1000
            } else {
                val parts = song.duration?.split(":")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
                when (parts.size) {
                    2 -> parts[0] * 60 + parts[1]
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    else -> 0L
                }
            }
        }
    }

    val durationText = remember(totalSeconds) {
        if (totalSeconds > 0) {
            val minutes = totalSeconds / 60
            if (minutes >= 60) {
                val hours = minutes / 60
                val remMin = minutes % 60
                "$hours hr $remMin min"
            } else {
                "$minutes Minutes"
            }
        } else null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        val summaryLine = buildString {
            append("$trackCount Songs")
            if (durationText != null) {
                append(", $durationText")
            }
            if (year != null && year > 0) {
                append(" • Released $year")
            }
        }
        Text(
            text = summaryLine,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
        )
    }
}

/**
 * Enhanced premium detail view for an Album inspired by Apple Music design language.
 */
@Composable
fun AlbumDetailView(
    albumPage: AlbumPage,
    currentOnlineSong: OnlineSong?,
    isLoadingStream: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit,
    onArtistClick: ((OnlineArtist) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current

    val isLosslessEnabled = remember(context) {
        com.codetrio.spatialflow.util.QualityModeSelector.isLosslessStreamingEnabled(context)
    }
    val isAtmosActive by com.codetrio.spatialflow.audio.dolby.DolbyAtmosManager.isAtmosActive.collectAsState()

    val mainArtist = remember(albumPage.album.artists) { albumPage.album.artists.firstOrNull() }
    val artistSubtitle = remember(albumPage.album.artists) {
        albumPage.album.artists.joinToString { it.name }.takeIf { it.isNotBlank() } ?: "Various Artists"
    }

    var showDownloadSheet by remember { mutableStateOf(false) }

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = albumPage.album.thumbnailUrl,
        title = albumPage.album.title,
        subtitle = artistSubtitle,
        sharedElementKey = albumPage.album.browseId,
        entityType = DetailEntityType.ALBUM,
        artistName = mainArtist?.name,
        entityId = albumPage.album.browseId,
        onSubtitleClick = {
            mainArtist?.let {
                onArtistClick?.invoke(OnlineArtist(browseId = it.id ?: "", title = it.name))
            }
        },
        onDownloadClick = { showDownloadSheet = true },
        onBack = onBack,
        headerActions = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppleMusicAudioBadges(
                    year = albumPage.album.year,
                    songCountText = "${albumPage.songs.size} Songs",
                    showDolbyAtmos = isAtmosActive,
                    showLossless = isLosslessEnabled
                )

                Spacer(modifier = Modifier.height(16.dp))

                AppleMusicAlbumActionRow(
                    onPlayClick = {
                        if (albumPage.songs.isNotEmpty()) {
                            onSongClick(albumPage.songs.first(), albumPage.songs, 0)
                        }
                    },
                    onShuffleClick = {
                        if (albumPage.songs.isNotEmpty()) {
                            onSongClick(albumPage.songs.first(), albumPage.songs.shuffled(), 0)
                        }
                    },
                    onRadioClick = {
                        if (albumPage.songs.isNotEmpty()) {
                            onStartRadioClick(albumPage.songs.first().videoId)
                        }
                    }
                )
            }
        }
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
        }
        itemsIndexed(
            items = albumPage.songs,
            key = { index, song -> "album-song-${index}-${song.videoId}" }
        ) { index, song ->
            val isPlaying = song.videoId == currentOnlineSong?.videoId
            AppleMusicAlbumTrackItem(
                song = song,
                index = index + 1,
                isPlaying = isPlaying,
                mainArtistName = mainArtist?.name,
                onClick = { onSongClick(song, albumPage.songs, index) },
                onMenuClick = { onSongMenuClick(song) }
            )
        }
        item(key = "album-footer") {
            AppleMusicAlbumFooter(
                trackCount = albumPage.songs.size,
                year = albumPage.album.year,
                songs = albumPage.songs,
                isLossless = isLosslessEnabled
            )
        }
    }

    if (showDownloadSheet) {
        val songItems = remember(albumPage.songs) {
            albumPage.songs.map { it.toSongItem() }
        }
        PlaylistDownloadBottomSheet(
            playlistTitle = albumPage.album.title,
            playlistAuthor = mainArtist?.name ?: artistSubtitle,
            thumbnailUrl = albumPage.album.thumbnailUrl,
            songs = songItems,
            onConfirmDownload = { selectedSongs, format ->
                com.codetrio.spatialflow.util.SongDownloader.downloadPlaylist(
                    context = context,
                    playlistTitle = albumPage.album.title,
                    songs = selectedSongs,
                    format = format
                )
                showDownloadSheet = false
            },
            onDismiss = { showDownloadSheet = false }
        )
    }
}

/**
 * Enhanced premium detail view for a Playlist leveraging Unified Parallax system.
 */
@Composable
fun PlaylistDetailView(
    playlistPage: PlaylistPage,
    currentOnlineSong: OnlineSong?,
    isLoadingStream: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit,
    onLoadMore: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val playlistSubtitle = remember(playlistPage.playlist.author, playlistPage.playlist.songCount, playlistPage.songs.size) {
        val authorName = playlistPage.playlist.author?.name?.takeIf { it.isNotBlank() } ?: "Playlist"
        val rawCount = playlistPage.playlist.songCount?.takeIf { it.isNotBlank() }
        val countText = if (rawCount != null && rawCount.any { it.isDigit() }) {
            rawCount
        } else {
            "${playlistPage.songs.size} tracks"
        }
        "$authorName • $countText"
    }

    val context = LocalContext.current
    var showDownloadSheet by remember { mutableStateOf(false) }

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = playlistPage.playlist.thumbnailUrl,
        title = playlistPage.playlist.title,
        subtitle = playlistSubtitle,
        sharedElementKey = playlistPage.playlist.playlistId,
        entityType = DetailEntityType.PLAYLIST,
        artistName = playlistPage.playlist.author?.name,
        entityId = playlistPage.playlist.playlistId,
        onDownloadClick = { showDownloadSheet = true },
        onBack = onBack,
        headerActions = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppleMusicAudioBadges(
                    year = null,
                    songCountText = "${playlistPage.songs.size} Songs",
                    showDolbyAtmos = false,
                    showLossless = false
                )

                Spacer(modifier = Modifier.height(16.dp))

                AppleMusicAlbumActionRow(
                    onPlayClick = {
                        if (playlistPage.songs.isNotEmpty()) {
                            onSongClick(playlistPage.songs.first(), playlistPage.songs, 0)
                        }
                    },
                    onShuffleClick = {
                        if (playlistPage.songs.isNotEmpty()) {
                            onSongClick(playlistPage.songs.first(), playlistPage.songs.shuffled(), 0)
                        }
                    },
                    onRadioClick = {
                        if (playlistPage.songs.isNotEmpty()) {
                            onStartRadioClick(playlistPage.songs.first().videoId)
                        }
                    }
                )
            }
        }
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
        }
        itemsIndexed(
            items = playlistPage.songs,
            key = { index, song -> "playlist_song_${index}_${song.videoId.ifBlank { "song" }}" },
            contentType = { _, _ -> "online_playlist_song" }
        ) { index, song ->
            if (index >= playlistPage.songs.size - 6 && !playlistPage.continuation.isNullOrBlank()) {
                LaunchedEffect(playlistPage.songs.size) {
                    onLoadMore()
                }
            }
            val isPlaying = song.videoId == currentOnlineSong?.videoId
            AppleMusicAlbumTrackItem(
                song = song,
                index = index + 1,
                isPlaying = isPlaying,
                mainArtistName = null,
                onClick = { onSongClick(song, playlistPage.songs, index) },
                onMenuClick = { onSongMenuClick(song) }
            )
        }
        if (!playlistPage.continuation.isNullOrBlank()) {
            item(key = "playlist-loading-more-indicator") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item(key = "playlist-footer") {
            AppleMusicAlbumFooter(
                trackCount = playlistPage.songs.size,
                year = null,
                songs = playlistPage.songs,
                isLossless = false
            )
        }
    }

    if (showDownloadSheet) {
        val songItems = remember(playlistPage.songs) {
            playlistPage.songs.map { it.toSongItem() }
        }
        PlaylistDownloadBottomSheet(
            playlistTitle = playlistPage.playlist.title,
            playlistAuthor = playlistPage.playlist.author?.name,
            thumbnailUrl = playlistPage.playlist.thumbnailUrl,
            songs = songItems,
            onConfirmDownload = { selectedSongs, format ->
                com.codetrio.spatialflow.util.SongDownloader.downloadPlaylist(
                    context = context,
                    playlistTitle = playlistPage.playlist.title,
                    songs = selectedSongs,
                    format = format
                )
                showDownloadSheet = false
            },
            onDismiss = { showDownloadSheet = false }
        )
    }
}

/**
 * Free-flowing Artist Description with zero container/box, 2-line clamp, and animated Chevron toggle.
 */
@Composable
fun FreeArtistDescription(
    description: String,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ChevronRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { isExpanded = !isExpanded }
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.5.sp,
                lineHeight = 21.5.sp,
                fontWeight = FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isExpanded) "Show less" else "Read more",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Show less" else "Read more",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }
    }
}

/**
 * Dynamic color roles extracted from artist or album artwork.
 */
data class HeroDynamicColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color
)

/**
 * Intelligently extracts and boosts the richest, most saturated, luminous jewel-tone
 * colors from [ExtractedPaletteColors] for showcase hero elements, preventing dull dark grays.
 * Returns dynamic colors (primary, secondary, tertiary, surface) extracted from the artwork or artist thumbnail.
 */
fun extractRichHeroColors(
    palette: ExtractedPaletteColors,
    fallback: Color,
    isDark: Boolean
): HeroDynamicColors {
    val candidates = listOf(
        palette.vibrant,
        palette.lightVibrant,
        palette.accent,
        palette.darkVibrant,
        palette.dominant
    )

    val hsl = FloatArray(3)
    var bestPrimary: Color? = null
    var bestScore = -1f

    for (color in candidates) {
        if (color == Color.Transparent || color == Color.Unspecified) continue
        val argb = color.toArgb()
        // Skip default dark/black placeholders
        if (argb == 0xFF1A1A1A.toInt() || argb == 0xFF121212.toInt() ||
            argb == 0xFF333333.toInt() || argb == 0xFF000000.toInt() ||
            argb == 0xFF141218.toInt()
        ) continue

        ColorUtils.colorToHSL(argb, hsl)
        val sat = hsl[1]
        val light = hsl[2]

        // Score based on saturation and balanced lightness
        if (light in 0.12f..0.88f) {
            val score = sat * 2.0f + (1f - kotlin.math.abs(light - 0.5f))
            if (score > bestScore) {
                bestScore = score
                bestPrimary = color
            }
        }
    }

    val primaryBase = bestPrimary ?: fallback
    val primaryHsl = FloatArray(3)
    ColorUtils.colorToHSL(primaryBase.toArgb(), primaryHsl)

    val hue = primaryHsl[0]
    // Human skin/portrait tones typically range in hue 12°..48° with mild-to-medium saturation.
    // Over-saturating these turns human skin into radioactive, neon carrot orange.
    val isSkinTone = hue in 12f..48f && primaryHsl[1] < 0.65f
    if (isSkinTone) {
        // Preserve warm, elegant natural complexion without garish neon saturation
        primaryHsl[1] = primaryHsl[1].coerceIn(0.35f, 0.58f)
        primaryHsl[2] = if (isDark) primaryHsl[2].coerceIn(0.50f, 0.65f) else primaryHsl[2].coerceIn(0.38f, 0.50f)
    } else {
        // True chromatic jewel tones (vibrant lighting, colorful clothes, graphics)
        primaryHsl[1] = primaryHsl[1].coerceIn(0.55f, 0.86f)
        primaryHsl[2] = if (isDark) primaryHsl[2].coerceIn(0.50f, 0.66f) else primaryHsl[2].coerceIn(0.40f, 0.54f)
    }

    val richPrimary = Color(ColorUtils.HSLToColor(primaryHsl))

    // Derive secondary from lightVibrant or harmonic hue shift (+32 degrees for dual-tone aurora radiance)
    val secondaryHsl = FloatArray(3)
    if (palette.lightVibrant != Color.Transparent && palette.lightVibrant != Color.Unspecified && palette.lightVibrant.toArgb() != 0xFFEEEEEE.toInt()) {
        ColorUtils.colorToHSL(palette.lightVibrant.toArgb(), secondaryHsl)
        secondaryHsl[1] = secondaryHsl[1].coerceIn(0.60f, 0.92f)
        secondaryHsl[2] = if (isDark) secondaryHsl[2].coerceIn(0.50f, 0.68f) else secondaryHsl[2].coerceIn(0.40f, 0.56f)
    } else {
        secondaryHsl[0] = (primaryHsl[0] + 32f) % 360f
        secondaryHsl[1] = (primaryHsl[1] * 0.92f).coerceIn(0.65f, 0.95f)
        secondaryHsl[2] = if (isDark) (primaryHsl[2] * 0.92f).coerceIn(0.46f, 0.62f) else (primaryHsl[2] * 0.95f).coerceIn(0.38f, 0.54f)
    }
    val richSecondary = Color(ColorUtils.HSLToColor(secondaryHsl))

    // Derive tertiary / container from darkVibrant or secondary shifted
    val tertiaryHsl = FloatArray(3)
    if (palette.darkVibrant != Color.Transparent && palette.darkVibrant != Color.Unspecified && palette.darkVibrant.toArgb() != 0xFF1A1A1A.toInt()) {
        ColorUtils.colorToHSL(palette.darkVibrant.toArgb(), tertiaryHsl)
        tertiaryHsl[1] = tertiaryHsl[1].coerceIn(0.50f, 0.85f)
        tertiaryHsl[2] = if (isDark) tertiaryHsl[2].coerceIn(0.25f, 0.45f) else tertiaryHsl[2].coerceIn(0.65f, 0.85f)
    } else {
        tertiaryHsl[0] = (primaryHsl[0] - 25f + 360f) % 360f
        tertiaryHsl[1] = (primaryHsl[1] * 0.85f).coerceIn(0.50f, 0.80f)
        tertiaryHsl[2] = if (isDark) 0.32f else 0.75f
    }
    val richTertiary = Color(ColorUtils.HSLToColor(tertiaryHsl))

    val richSurface = if (isDark) {
        if (palette.darkMuted != Color.Transparent && palette.darkMuted.toArgb() != 0xFF121212.toInt()) {
            Color(palette.darkMuted.toArgb())
        } else {
            Color(0xFF0C0D12)
        }
    } else {
        Color(0xFFF6F6F9)
    }

    return HeroDynamicColors(
        primary = richPrimary,
        secondary = richSecondary,
        tertiary = richTertiary,
        surface = richSurface
    )
}

/**
 * Flagship Deluxe "LATEST RELEASE" Showcase Hero Card on the Artist Detail Page.
 * Features:
 * - Ultra-rich jewel-tone dynamic colors (primary, secondary, tertiary) extracted from the artist thumbnail
 * - Clean minimal header (no dot, no Featured badge)
 * - Full-bleed zoomed & blurred atmospheric album artwork backdrop
 * - 3D Vinyl record peeking out with realistic concentric grooves & colored center label
 * - Crisp floating album cover sleeve with ambient bloom and top gloss highlight
 * - Emphasized typography, category badge, and date capsule
 * - Deluxe "Go to Album" (or "Go to Release") pill button with arrow + companion quick-play button
 */
/**
 * Top Search Result style Hero Card for "LATEST RELEASE" on the Artist Detail Page.
 * Styled identically to [TopResultCard] in ExploreComponents:
 * - Card with SpatialShapes.ExtraLarge and 20dp padding
 * - Header row with "LATEST RELEASE" pill badge and release type label
 * - Main content row with 80dp artwork (SpatialShapes.MediumLarge) and Title/Subtitle
 * - Expressive action row with spring-morphing "Go to Album" Button (weight 1f, 48dp)
 *   and companion quick-play FilledTonalIconButton (48dp)
 * - Dynamically themed using colors extracted from the artist's thumbnail
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistLatestReleaseCard(
    title: String,
    releaseDateOrType: String,
    categoryType: String,
    thumbnailUrl: String?,
    artistThumbnailUrl: String? = null,
    artistName: String? = null,
    isExplicit: Boolean = false,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary

    // Dynamic colors extracted from artist thumbnail (with fallback to release thumbnail)
    val colorSourceUrl = artistThumbnailUrl?.takeIf { it.isNotBlank() } ?: thumbnailUrl
    val palette = rememberDetailPalette(colorSourceUrl)
    val dynamicColors = remember(palette, isDark, primaryColor) {
        extractRichHeroColors(palette, primaryColor, isDark)
    }
    val dynamicPrimary = dynamicColors.primary
    val onPrimaryColor = remember(dynamicPrimary) {
        if (ColorUtils.calculateLuminance(dynamicPrimary.toArgb()) > 0.45) {
            Color(0xFF0C0D12)
        } else {
            Color.White
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(SpatialShapes.ExtraLarge)
            .clickable(onClick = onClick),
        shape = SpatialShapes.ExtraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // ── Header / Pill Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "LATEST RELEASE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }

                val typeLabel = when {
                    categoryType.contains("album", ignoreCase = true) -> if (releaseDateOrType.isNotBlank()) "Album • $releaseDateOrType" else "Album"
                    categoryType.contains("single", ignoreCase = true) -> "Single"
                    categoryType.contains("ep", ignoreCase = true) -> "EP"
                    else -> categoryType.substringBefore(" •")
                }

                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Main Content Row (Thumbnail + Details) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Concentric Corner: Outer Card (28.dp) - Padding (20.dp) = 8.dp squircle
                val thumbShape = SpatialShapes.concentricSquircle(28.dp, 20.dp)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(thumbShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (!thumbnailUrl.isNullOrBlank()) {
                        SpatialImage(
                            model = thumbnailUrl,
                            contentDescription = title,
                            size = ImageSemanticSize.ThumbnailMedium,
                            shape = thumbShape,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isExplicit) {
                            ExplicitBadge()
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val subtitle = when {
                        !artistName.isNullOrBlank() && releaseDateOrType.isNotBlank() -> "$artistName • $releaseDateOrType"
                        !artistName.isNullOrBlank() -> artistName
                        else -> categoryType
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Action Row: Hero Action & Secondary Controls ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Hero Action Button: "Go to Album"
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed) 12.dp else 24.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "LatestReleaseActionCorner"
                )

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    interactionSource = interactionSource,
                    shape = SpatialShapes.squircle(cornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dynamicPrimary,
                        contentColor = onPrimaryColor
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Album,
                        contentDescription = "Go to Album",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (categoryType.contains("album", ignoreCase = true)) "Go to Album" else "Go to Release",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                // Secondary Action: Quick Play
                val playInteractionSource = remember { MutableInteractionSource() }
                val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                val playCornerRadius by animateDpAsState(
                    targetValue = if (isPlayPressed) 10.dp else 16.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "LatestReleasePlayCorner"
                )

                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        (onPlayClick ?: onClick).invoke()
                    },
                    modifier = Modifier.size(48.dp),
                    interactionSource = playInteractionSource,
                    shape = SpatialShapes.squircle(playCornerRadius),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = dynamicPrimary
                    )
                ) {
                    Icon(
                        painter = painterResource(id = com.codetrio.spatialflow.R.drawable.ic_play),
                        contentDescription = "Play $title",
                        modifier = Modifier
                            .size(22.dp)
                            .padding(start = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Backward-compatible wrapper for AppleMusicLatestReleaseCard.
 */
@Composable
fun AppleMusicLatestReleaseCard(
    title: String,
    releaseDateOrType: String,
    categoryType: String,
    thumbnailUrl: String?,
    artistThumbnailUrl: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArtistLatestReleaseCard(
        title = title,
        releaseDateOrType = releaseDateOrType,
        categoryType = categoryType,
        thumbnailUrl = thumbnailUrl,
        artistThumbnailUrl = artistThumbnailUrl,
        onClick = onClick,
        onPlayClick = onClick,
        modifier = modifier
    )
}

/**
 * M3 Expressive Modal Bottom Sheet for Artist Options (Share, Copy Name, Don't Recommend).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistOptionsBottomSheet(
    artistName: String,
    artistThumbnailUrl: String?,
    subscriberCount: String?,
    artistBrowseId: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // Header: Thumbnail + Artist Name + Subtitle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    SpatialImage(
                        model = artistThumbnailUrl,
                        contentDescription = null,
                        size = ImageSemanticSize.ThumbnailSmall,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subscriberCount.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subscriberCount,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Material 3 Expressive Segmented List Defaults
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                // Item 0: Share artist
                ListItem(
                    onClick = {
                        val shareUrl = if (!artistBrowseId.isNullOrBlank()) {
                            "https://music.youtube.com/channel/$artistBrowseId"
                        } else "Listen to $artistName on SpatialFlow"
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, artistName)
                            putExtra(Intent.EXTRA_TEXT, "Listen to $artistName on SpatialFlow: $shareUrl")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Artist"))
                        onDismiss()
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    content = {
                        Column {
                            Text(
                                text = "Share artist",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Share artist profile link",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                // Item 1: Copy artist name
                ListItem(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Artist Name", artistName)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied \"$artistName\" to clipboard", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy name",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    content = {
                        Column {
                            Text(
                                text = "Copy artist name",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Copy to clipboard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                // Item 2: Don't recommend this artist
                ListItem(
                    onClick = {
                        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                        val blocked = prefs.getStringSet("blocked_artists", emptySet())?.toMutableSet() ?: mutableSetOf()
                        blocked.add(artistName)
                        prefs.edit().putStringSet("blocked_artists", blocked).apply()
                        Toast.makeText(context, "Won't recommend $artistName anymore", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = "Don't recommend",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    content = {
                        Column {
                            Text(
                                text = "Don't recommend this artist",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Hide from future suggestions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * High-Fidelity Detail view for an Artist with Parallax scaling and Dynamic Content rendering.
 */
@Composable
fun ArtistDetailView(
    artistPage: ArtistPage,
    currentOnlineSong: OnlineSong?,
    isSubscribed: Boolean = false,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSubscribeClick: ((String) -> Unit)? = null,
    onStartRadioClick: ((String) -> Unit)? = null,
    onSectionClick: ((String, String?, String) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val formattedSubtitle = remember(artistPage.artist.subscriberCount) {
        val raw = artistPage.artist.subscriberCount ?: ""
        if (raw.isBlank()) return@remember ""
        val parts = raw.split(Regex(pattern = " • | •|• |•| · |·|,"))
        parts.mapNotNull { part ->
            var trimmed = part.trim()
            trimmed = trimmed.replace("Spotify", "", ignoreCase = true).trim()
            if (trimmed.isBlank()) return@mapNotNull null

            val lower = trimmed.lowercase()
            when {
                lower.contains("subscriber") || lower.contains("subscribers") || lower.contains("subs") -> {
                    trimmed.replace("subscribers", "Subscribers", ignoreCase = true)
                        .replace("subscriber", "Subscriber", ignoreCase = true)
                        .replace("subs", "Subscribers", ignoreCase = true)
                }

                lower.contains("listener") || lower.contains("listeners") || lower.contains("audience") -> {
                    var clean = trimmed.replace(Regex("(?i)\\b(spotify monthly listeners|monthly listeners|monthly listener|listeners|listener)\\b"), "Monthly Listeners")
                    clean = clean.replace(Regex("(?i)\\bmonthly monthly listeners\\b"), "Monthly Listeners")
                    if (!clean.endsWith("Monthly Listeners", ignoreCase = true) && !clean.endsWith("Listeners", ignoreCase = true)) {
                        clean = "$clean Monthly Listeners"
                    }
                    clean
                }

                else -> {
                    if (trimmed.endsWith("Subscribers", ignoreCase = true) || trimmed.endsWith("Listeners", ignoreCase = true)) {
                        trimmed
                    } else {
                        "$trimmed Subscribers"
                    }
                }
            }.replace("Monthly Monthly Listeners", "Monthly Listeners", ignoreCase = true)
             .replace("Spotify", "", ignoreCase = true)
             .trim()
        }.filter { it.isNotBlank() }.distinct().joinToString(" • ")
    }

    val latestReleaseItem = remember(artistPage.sections) {
        // 1. Look for explicit "Latest release" or "New release" section
        val latestSection = artistPage.sections.firstOrNull {
            it.title.contains("latest", ignoreCase = true) || it.title.contains("new release", ignoreCase = true)
        }
        val itemFromLatest = latestSection?.items?.firstOrNull()
        if (itemFromLatest != null) return@remember itemFromLatest

        // 2. Look for first item in Albums section or Singles section
        val albumOrSingleSection = artistPage.sections.firstOrNull {
            it.title.contains("album", ignoreCase = true) || it.title.contains("single", ignoreCase = true)
        }
        albumOrSingleSection?.items?.firstOrNull()
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val artistPalette = rememberDetailPalette(artistPage.artist.thumbnailUrl)
    val artistDynamicColors = remember(artistPalette, isDark, primaryColor) {
        extractRichHeroColors(artistPalette, primaryColor, isDark)
    }

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = artistPage.artist.thumbnailUrl,
        title = artistPage.artist.title,
        subtitle = formattedSubtitle,
        isCircular = false,
        sharedElementKey = artistPage.artist.browseId,
        entityType = DetailEntityType.ARTIST,
        artistName = artistPage.artist.title,
        entityId = artistPage.artist.browseId,
        onBack = onBack,
        headerActions = {
            val allArtistSongs = remember(artistPage.sections) {
                artistPage.sections.flatMap { it.items }
                    .filterIsInstance<SearchItem.Song>().map { it.song }
            }

            val primaryColor = artistDynamicColors.primary
            val onPrimaryColor = remember(primaryColor) {
                if (ColorUtils.calculateLuminance(primaryColor.toArgb()) > 0.45f) {
                    Color(0xFF0C0D12)
                } else {
                    Color.White
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // Follow / Subscribe Pill Chip (Apple Music Style)
                if (onSubscribeClick != null) {
                    val isSub = isSubscribed
                    Surface(
                        onClick = { onSubscribeClick(artistPage.artist.browseId) },
                        shape = CircleShape,
                        color = if (isSub) {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.90f)
                        } else {
                            primaryColor.copy(alpha = 0.18f)
                        },
                        contentColor = if (isSub) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            primaryColor
                        },
                        modifier = Modifier
                            .align(Alignment.Start)
                            .height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (isSub) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isSub) "Following" else "+ Follow",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ── MATERIAL 3 EXPRESSIVE CONNECTED BUTTONS (Center Bottom) ──
                ArtistExpressiveConnectedButtons(
                    onPlayClick = {
                        if (allArtistSongs.isNotEmpty()) {
                            onSongClick(allArtistSongs.first(), allArtistSongs, 0)
                        }
                    },
                    onRadioClick = {
                        if (allArtistSongs.isNotEmpty()) {
                            onStartRadioClick?.invoke(allArtistSongs.random().videoId)
                        }
                    },
                    primaryColor = primaryColor,
                    onPrimaryColor = onPrimaryColor
                )
            }
        }
    ) {
        // ── Free-flowing Artist Biography / Description (Zero Container / Pure Clean Text) ──
        if (!artistPage.description.isNullOrBlank()) {
            item(key = "artist-biography-description") {
                FreeArtistDescription(
                    description = artistPage.description,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // ── Hero "LATEST RELEASE" Highlight Card ──
        if (latestReleaseItem != null) {
            item(key = "artist-latest-release-card") {
                when (latestReleaseItem) {
                    is SearchItem.Album -> {
                        ArtistLatestReleaseCard(
                            title = latestReleaseItem.album.title,
                            releaseDateOrType = latestReleaseItem.album.year?.toString() ?: "Latest",
                            categoryType = if (latestReleaseItem.album.songCount != null) "Album • ${latestReleaseItem.album.songCount}" else "Album",
                            thumbnailUrl = latestReleaseItem.album.thumbnailUrl,
                            artistThumbnailUrl = artistPage.artist.thumbnailUrl,
                            artistName = artistPage.artist.title,
                            isExplicit = false,
                            onClick = { onAlbumClick(latestReleaseItem.album) },
                            onPlayClick = { onAlbumClick(latestReleaseItem.album) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is SearchItem.Song -> {
                        ArtistLatestReleaseCard(
                            title = latestReleaseItem.song.title,
                            releaseDateOrType = "Latest Single",
                            categoryType = "Single",
                            thumbnailUrl = latestReleaseItem.song.thumbnailUrl,
                            artistThumbnailUrl = artistPage.artist.thumbnailUrl,
                            artistName = latestReleaseItem.song.artist.ifBlank { artistPage.artist.title },
                            isExplicit = latestReleaseItem.song.isExplicit,
                            onClick = { onSongClick(latestReleaseItem.song, listOf(latestReleaseItem.song), 0) },
                            onPlayClick = { onSongClick(latestReleaseItem.song, listOf(latestReleaseItem.song), 0) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {}
                }
            }
        }

        artistPage.sections.forEachIndexed { secIdx, section ->
            val isTopSongs = section.title.contains("song", ignoreCase = true)
            if (isTopSongs) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Top songs", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (section.browseEndpoint != null) {
                                    onSectionClick?.invoke(section.browseEndpoint, section.params, "Top songs")
                                } else {
                                    val songs = section.items.filterIsInstance<SearchItem.Song>().map { it.song }
                                    if (songs.isNotEmpty()) onSongClick(songs.first(), songs, 0)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "See all",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, "See all",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                val songs = section.items.filterIsInstance<SearchItem.Song>()
                val columns = if (isLandscape) 2 else 1
                val songChunks = songs.take(5).chunked(columns)

                songChunks.forEachIndexed { chunkIdx, chunk ->
                    item(key = "artist_sec_${secIdx}_top_songs_$chunkIdx") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            chunk.forEachIndexed { colIdx, item ->
                                val song = item.song
                                val isPlaying = song.videoId == currentOnlineSong?.videoId
                                val index = chunkIdx * columns + colIdx
                                Box(modifier = Modifier.weight(1f)) {
                                    ArtistTopSongItem(
                                        song = song,
                                        isPlaying = isPlaying,
                                        index = index + 1,
                                        onClick = { onSongClick(song, songs.map { it.song }, index) },
                                        onMenuClick = { onSongMenuClick(song) }
                                    )
                                }
                            }
                            if (chunk.size < columns) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                item {
                    HomeSectionRow(
                        section = section,
                        currentSongId = currentOnlineSong?.videoId,
                        onSongClick = onSongClick,
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                        onPlaylistClick = onPlaylistClick,
                        onSongMenuClick = onSongMenuClick,
                        onSectionClick = { browseId, params, title ->
                            onSectionClick?.invoke(browseId, params, title)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Adaptive container that hosts both optimized 2-pane landscape layout and hero parallax portrait layout.
 */
@Composable
fun AdaptiveDetailContainer(
    isLandscape: Boolean,
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    isCircular: Boolean = false,
    sharedElementKey: String? = null,
    entityType: DetailEntityType = DetailEntityType.OTHER,
    artistName: String? = null,
    entityId: String? = null,
    headerActions: @Composable (() -> Unit)? = null,
    onSubtitleClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? androidx.fragment.app.FragmentActivity }
    val playerSharedViewModel = remember(activity) {
        activity?.let { androidx.lifecycle.ViewModelProvider(it)[PlayerSharedViewModel::class.java] }
    }
    val exploreViewModel: ExploreViewModel? = remember(activity) {
        activity?.let { androidx.lifecycle.ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val detailKey = remember(sharedElementKey, title) { sharedElementKey ?: title }

    val savedScrollPosition = remember(detailKey, exploreViewModel) {
        if (!detailKey.isNullOrBlank() && exploreViewModel != null) {
            exploreViewModel.getScrollPosition(detailKey)
        } else null
    }

    val lazyListState = remember(detailKey) {
        androidx.compose.foundation.lazy.LazyListState(
            firstVisibleItemIndex = savedScrollPosition?.first ?: 0,
            firstVisibleItemScrollOffset = savedScrollPosition?.second ?: 0
        )
    }

    LaunchedEffect(detailKey, lazyListState, exploreViewModel) {
        if (!detailKey.isNullOrBlank() && exploreViewModel != null) {
            try {
                snapshotFlow {
                    lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
                }
                .distinctUntilChanged()
                .collect { (index, offset) ->
                    exploreViewModel.saveScrollPosition(detailKey, index, offset)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    val isPlayerExpanded = playerSharedViewModel?.isPlayerExpanded?.collectAsStateWithLifecycle()?.value ?: false

    val prefs = remember(context) { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
    val showAnimatedArt by prefs.observeKey("show_animated_art", true)

    var canvasArtwork by remember(title, subtitle, entityType, entityId) { mutableStateOf<CanvasArtwork?>(null) }

    LaunchedEffect(title, subtitle, entityType, entityId, showAnimatedArt) {
        if (!showAnimatedArt) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val resolved = when (entityType) {
                DetailEntityType.ALBUM -> DetailCanvasResolver.resolveAlbumCanvas(
                    albumTitle = title,
                    artistName = artistName ?: subtitle.substringBefore("•").trim(),
                    albumId = entityId
                )
                DetailEntityType.ARTIST -> DetailCanvasResolver.resolveArtistCanvas(
                    artistName = title,
                    artistId = entityId
                )
                DetailEntityType.PLAYLIST -> DetailCanvasResolver.resolvePlaylistCanvas(
                    playlistTitle = title,
                    curatorName = artistName ?: subtitle.substringBefore("•").trim(),
                    playlistId = entityId
                )
                DetailEntityType.OTHER -> null
            }
            if (resolved != null) {
                withContext(Dispatchers.Main) {
                    canvasArtwork = resolved
                }
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = !isPlayerExpanded) {
        onBack()
    }

    val effectiveArtUrl = remember(canvasArtwork?.static, thumbnailUrl) {
        canvasArtwork?.static?.takeIf { it.isNotBlank() } ?: thumbnailUrl
    }
    val palette = rememberDetailPalette(effectiveArtUrl)

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val amoledEnabled by prefs.observeKey("amoled_black", false)
    val baseDarkBg = if (amoledEnabled) Color.Black else Color(0xFF0E1014)
    val baseLightBg = MaterialTheme.colorScheme.surface
    val baseLightSecondaryBg = MaterialTheme.colorScheme.surfaceContainerLow

    val isArtist = entityType == DetailEntityType.ARTIST

    // Material 3 Expressive Dynamic Tonal Roles from Artwork Palette
    val dynamicSurface = remember(palette.dominant, palette.vibrant, palette.darkMuted, palette.darkVibrant, baseDarkBg, baseLightBg, isDark, amoledEnabled, isArtist) {
        if (isDark) {
            if (amoledEnabled) Color.Black
            else {
                // For artists, prioritize muted dark swatches to avoid face/skin sludge
                val seed = when {
                    isArtist && palette.darkMuted != Color.Black -> palette.darkMuted
                    isArtist && palette.darkVibrant != Color.Black -> palette.darkVibrant
                    palette.dominant != Color.Black -> palette.dominant
                    palette.vibrant != Color.Black -> palette.vibrant
                    else -> Color.Black
                }
                if (seed != Color.Black) {
                    val lerpFactor = if (isArtist) 0.12f else 0.24f
                    androidx.compose.ui.graphics.lerp(baseDarkBg, seed, lerpFactor)
                } else baseDarkBg
            }
        } else {
            val seed = if (palette.dominant != Color.Black) palette.dominant else palette.vibrant
            if (seed != Color.Black) {
                val lerpFactor = if (isArtist) 0.05f else 0.08f
                androidx.compose.ui.graphics.lerp(baseLightBg, seed, lerpFactor)
            } else baseLightBg
        }
    }

    val dynamicSecondarySurface = remember(palette.darkMuted, palette.vibrant, dynamicSurface, baseDarkBg, baseLightSecondaryBg, isDark, amoledEnabled, isArtist) {
        if (isDark) {
            if (amoledEnabled) Color.Black
            else {
                val seed = if (palette.darkMuted != Color.Black) palette.darkMuted else palette.vibrant
                if (seed != Color.Black) {
                    val lerpFactor = if (isArtist) 0.08f else 0.18f
                    androidx.compose.ui.graphics.lerp(dynamicSurface, seed, lerpFactor)
                } else dynamicSurface
            }
        } else {
            val seed = if (palette.darkMuted != Color.Black) palette.darkMuted else palette.vibrant
            if (seed != Color.Black) {
                val lerpFactor = if (isArtist) 0.04f else 0.06f
                androidx.compose.ui.graphics.lerp(dynamicSurface, seed, lerpFactor)
            } else baseLightSecondaryBg
        }
    }

    val pageBgBrush = remember(dynamicSurface, dynamicSecondarySurface, isDark, amoledEnabled) {
        if (isDark && amoledEnabled) {
            Brush.verticalGradient(listOf(Color.Black, Color.Black))
        } else {
            Brush.verticalGradient(
                listOf(
                    dynamicSurface,
                    dynamicSecondarySurface
                )
            )
        }
    }

    val detailBackdrop = rememberThrottledBackdrop(dynamicSurface)

    val isCanvasPlayable by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && !isPlayerExpanded
        }
    }

    var showOptionsSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLandscape) {
            // --- LANDSCAPE TWO-PANE ---
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .throttledLayerBackdrop(detailBackdrop)
                        .drawBehind { drawRect(brush = pageBgBrush) }
                ) {
                    // Ambient glass backdrop in landscape mode
                    if (!thumbnailUrl.isNullOrBlank()) {
                        SpatialImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            size = ImageSemanticSize.Cover,
                            innerBorder = null,
                            shape = RectangleShape,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 0.25f }
                                .then(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        Modifier.blur(48.dp)
                                    } else Modifier
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(start = 16.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Left Pane: Artwork & Metadata
                        Column(
                            modifier = Modifier
                                .weight(0.40f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(if (isCircular) CircleShape else RoundedCornerShape(20.dp))
                            ) {
                                if (!thumbnailUrl.isNullOrBlank()) {
                                    SpatialImage(
                                        model = thumbnailUrl,
                                        contentDescription = null,
                                        size = ImageSemanticSize.Cover,
                                        shape = if (isCircular) CircleShape else RoundedCornerShape(20.dp),
                                        crossfade = true,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                if (showAnimatedArt && !isPlayerExpanded && canvasArtwork != null) {
                                    CanvasArtworkPlayer(
                                        primaryUrl = canvasArtwork?.preferredAnimationUrl,
                                        fallbackUrl = canvasArtwork?.preferredVerticalAnimationUrl,
                                        isPlaying = isCanvasPlayable,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) Color.White.copy(alpha = 0.70f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier
                                )
                            }
                            if (headerActions != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    headerActions()
                                }
                            }
                        }

                        // Right Pane: Content List
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .weight(0.60f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            content()
                        }
                    }
                }

                // Floating Liquid Glass Action Controls (Landscape)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassActionPill(backdrop = detailBackdrop) {
                        LiquidGlassPillIconButton(
                            onClick = onBack,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    val hasEndAction = entityType == DetailEntityType.ARTIST || ((entityType == DetailEntityType.PLAYLIST || entityType == DetailEntityType.ALBUM) && !entityId.isNullOrBlank())
                    if (hasEndAction) {
                        LiquidGlassActionPill(backdrop = detailBackdrop) {
                            if (entityType == DetailEntityType.ARTIST) {
                                LiquidGlassPillIconButton(
                                    onClick = { showOptionsSheet = true },
                                    icon = Icons.Default.MoreVert,
                                    contentDescription = "More Options"
                                )
                            } else if ((entityType == DetailEntityType.PLAYLIST || entityType == DetailEntityType.ALBUM) && !entityId.isNullOrBlank()) {
                                if (onDownloadClick != null) {
                                    val downloadLabel = if (entityType == DetailEntityType.ALBUM) "Download Album" else "Download Playlist"
                                    LiquidGlassPillIconButton(
                                        onClick = onDownloadClick,
                                        icon = Icons.Rounded.Download,
                                        contentDescription = downloadLabel
                                    )
                                }
                                val shareTitle = if (entityType == DetailEntityType.ALBUM) "Share Album" else "Share Playlist"
                                LiquidGlassPillIconButton(
                                    onClick = {
                                        val pid = entityId.removePrefix("VL")
                                        val shareUrl = if (entityType == DetailEntityType.ALBUM && !pid.startsWith("MPREb_") && !pid.startsWith("OLAK5uy_")) {
                                            "https://music.youtube.com/browse/$pid"
                                        } else {
                                            "https://music.youtube.com/playlist?list=$pid"
                                        }
                                        val shareText = "Listen to \"$title\" on SpatialFlow:\n$shareUrl"
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, shareTitle)
                                        context.startActivity(shareIntent)
                                    },
                                    icon = Icons.Rounded.Share,
                                    contentDescription = shareTitle
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // --- PORTRAIT: APPLE MUSIC PROGRESSIVE BLUR DIFFUSION HERO WITH FLOATING LIQUID GLASS CONTROLS ---
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val screenHeightDp = configuration.screenHeightDp.dp

            val hasMotion = showAnimatedArt && !isPlayerExpanded && canvasArtwork != null
            val hasVerticalMotion = hasMotion && !canvasArtwork?.preferredVerticalAnimationUrl.isNullOrBlank()
            val motionUrl = canvasArtwork?.preferredVerticalAnimationUrl ?: canvasArtwork?.preferredAnimationUrl

            val isArtist = entityType == DetailEntityType.ARTIST
            val isAlbum = entityType == DetailEntityType.ALBUM

            val artistHeroCardHeight = remember(configuration.screenWidthDp) {
                (configuration.screenWidthDp.dp * (4f / 3f)).coerceAtLeast(460.dp)
            }
            val artistTotalHeroHeight = artistHeroCardHeight

            // Dynamic complete canvas viewport height
            val targetHeroHeight = remember(screenHeightDp, hasVerticalMotion, isArtist, isAlbum, artistTotalHeroHeight) {
                if (isArtist) {
                    artistTotalHeroHeight
                } else if (isAlbum) {
                    (screenHeightDp * 0.50f).coerceIn(440.dp, 540.dp)
                } else if (hasVerticalMotion) {
                    (screenHeightDp * 0.56f).coerceIn(460.dp, 560.dp)
                } else {
                    (screenHeightDp * 0.45f).coerceIn(380.dp, 440.dp)
                }
            }

            val heroHeightDp by animateDpAsState(
                targetValue = targetHeroHeight,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "HeroHeightAnimation"
            )
            val heroHeightPx = with(density) { heroHeightDp.toPx() }

            // Content layer with background brush (recorded by detailBackdrop)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .throttledLayerBackdrop(detailBackdrop)
                    .drawBehind {
                        drawRect(brush = pageBgBrush)
                    }
            ) {
                // ── 1. Full-Bleed / Inset Parallax Backdrop (Zero Cuts / Zero Seams) ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeightDp)
                        .clipToBounds()
                        .graphicsLayer {
                            val scrollOffset = if (lazyListState.firstVisibleItemIndex == 0) {
                                -lazyListState.firstVisibleItemScrollOffset.toFloat()
                            } else {
                                -heroHeightPx
                            }
                            val scrollProgress = (-scrollOffset / heroHeightPx).coerceIn(0f, 1f)
                            translationY = scrollOffset * 0.42f
                            alpha = (1f - scrollProgress * 1.35f).coerceIn(0f, 1f)
                            if (scrollOffset > 0) {
                                scaleX = 1f + scrollOffset * 0.0012f
                                scaleY = 1f + scrollOffset * 0.0012f
                            }
                        }
                ) {
                    if (isArtist) {
                        // ── ARTIST 3:4 FULL-IMMERSIVE HERO ART (Edge-to-Edge, Rounded Bottom Only) ──
                        val artistBottomRoundedShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(artistHeroCardHeight)
                                .clip(artistBottomRoundedShape)
                        ) {
                            if (!thumbnailUrl.isNullOrBlank()) {
                                SpatialImage(
                                    model = thumbnailUrl,
                                    contentDescription = "$title artwork",
                                    size = ImageSemanticSize.Cover,
                                    innerBorder = null,
                                    shape = artistBottomRoundedShape,
                                    crossfade = true,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(artistBottomRoundedShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            // Live Motion Loop (Apple Music / Spotify Canvas)
                            if (hasMotion && !motionUrl.isNullOrBlank()) {
                                CanvasArtworkPlayer(
                                    primaryUrl = motionUrl,
                                    fallbackUrl = canvasArtwork?.preferredAnimationUrl,
                                    isPlaying = isCanvasPlayable,
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(artistBottomRoundedShape)
                                )
                            }
                        }
                    } else {
                        // Static Backdrop (Albums & Playlists)
                        if (!thumbnailUrl.isNullOrBlank()) {
                            SpatialImage(
                                model = thumbnailUrl,
                                contentDescription = null,
                                size = ImageSemanticSize.Cover,
                                innerBorder = null,
                                shape = RectangleShape,
                                crossfade = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Live Motion Loop (Apple Music / Spotify Canvas)
                        if (hasMotion && !motionUrl.isNullOrBlank()) {
                            CanvasArtworkPlayer(
                                primaryUrl = motionUrl,
                                fallbackUrl = canvasArtwork?.preferredAnimationUrl,
                                isPlaying = isCanvasPlayable,
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                            )
                        }

                        // Apple Music Progressive Diffusion Scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        *(if (isAlbum) {
                                            arrayOf(
                                                0.00f to Color.Transparent,
                                                0.18f to Color.Transparent,
                                                0.48f to dynamicSurface.copy(alpha = 0.35f),
                                                0.76f to dynamicSurface.copy(alpha = 0.82f),
                                                1.00f to dynamicSurface
                                            )
                                        } else {
                                            arrayOf(
                                                0.00f to Color.Transparent,
                                                0.30f to Color.Transparent,
                                                0.60f to dynamicSurface.copy(alpha = 0.50f),
                                                0.82f to dynamicSurface.copy(alpha = 0.85f),
                                                1.00f to dynamicSurface
                                            )
                                        })
                                    )
                                )
                        )
                    }
                }

                // ── 2. Unified Scroll Content (Zero Overlap Collision) ──
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Item 0: Hero Header containing Title, Subtitle, and Action Buttons (or Spacer for Artist)
                    item(key = "detail-hero-header") {
                        if (isArtist) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroHeightDp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroHeightDp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                                    horizontalAlignment = if (isAlbum) Alignment.CenterHorizontally else Alignment.Start
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontSize = if (isAlbum) 26.sp else 36.sp,
                                            lineHeight = if (isAlbum) 30.sp else 40.sp,
                                            letterSpacing = (-0.6).sp
                                        ),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = if (isAlbum) TextAlign.Center else TextAlign.Start,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = (if (isAlbum) Modifier.fillMaxWidth() else Modifier)
                                            .then(if (sharedElementKey != null) Modifier.sharedElementIfAvailable("title-$sharedElementKey") else Modifier)
                                    )
                                    if (subtitle.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = if (isAlbum) 18.sp else 16.sp
                                            ),
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isAlbum) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = if (isAlbum) TextAlign.Center else TextAlign.Start,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = (if (isAlbum) Modifier.fillMaxWidth() else Modifier)
                                                .then(
                                                    if (onSubtitleClick != null) {
                                                        Modifier.clickable(
                                                            indication = null,
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            onClick = onSubtitleClick
                                                        )
                                                    } else Modifier
                                                )
                                        )
                                    }
                                    if (headerActions != null) {
                                        Spacer(modifier = Modifier.height(if (isAlbum) 16.dp else 14.dp))
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = if (isAlbum) Alignment.Center else Alignment.CenterStart
                                        ) {
                                            headerActions()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── For Artist: Metadata and Actions Cleanly Below 3:4 Hero Art ──
                    if (isArtist) {
                        item(key = "artist-header-metadata") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 16.dp, bottom = 8.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontSize = 28.sp,
                                        lineHeight = 34.sp,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Start,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (sharedElementKey != null) Modifier.sharedElementIfAvailable("title-$sharedElementKey") else Modifier
                                )

                                if (subtitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Start,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (headerActions != null) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    headerActions()
                                }
                            }
                        }
                    }

                    // Items 1..N: Songs / Sections
                    content()
                }
            }

            // ── 3. Floating Dual Liquid Glass Action Controls (Portrait - Sibling outside content Box) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassActionPill(backdrop = detailBackdrop) {
                    LiquidGlassPillIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                val hasEndAction = entityType == DetailEntityType.ARTIST || ((entityType == DetailEntityType.PLAYLIST || entityType == DetailEntityType.ALBUM) && !entityId.isNullOrBlank())
                if (hasEndAction) {
                    LiquidGlassActionPill(backdrop = detailBackdrop) {
                        if (entityType == DetailEntityType.ARTIST) {
                            LiquidGlassPillIconButton(
                                onClick = { showOptionsSheet = true },
                                icon = Icons.Default.MoreVert,
                                contentDescription = "More Options"
                            )
                        } else if ((entityType == DetailEntityType.PLAYLIST || entityType == DetailEntityType.ALBUM) && !entityId.isNullOrBlank()) {
                            if (onDownloadClick != null) {
                                val downloadLabel = if (entityType == DetailEntityType.ALBUM) "Download Album" else "Download Playlist"
                                LiquidGlassPillIconButton(
                                    onClick = onDownloadClick,
                                    icon = Icons.Rounded.Download,
                                    contentDescription = downloadLabel
                                )
                            }
                            val shareTitle = if (entityType == DetailEntityType.ALBUM) "Share Album" else "Share Playlist"
                            LiquidGlassPillIconButton(
                                onClick = {
                                    val pid = entityId.removePrefix("VL")
                                    val shareUrl = if (entityType == DetailEntityType.ALBUM && !pid.startsWith("MPREb_") && !pid.startsWith("OLAK5uy_")) {
                                        "https://music.youtube.com/browse/$pid"
                                    } else {
                                        "https://music.youtube.com/playlist?list=$pid"
                                    }
                                    val shareText = "Listen to \"$title\" on SpatialFlow:\n$shareUrl"
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, shareTitle)
                                    context.startActivity(shareIntent)
                                },
                                icon = Icons.Rounded.Share,
                                contentDescription = shareTitle
                            )
                        }
                    }
                }
            }
        }

        // Artist Options Modal Bottom Sheet
        if (showOptionsSheet) {
            ArtistOptionsBottomSheet(
                artistName = title,
                artistThumbnailUrl = thumbnailUrl,
                subscriberCount = subtitle.takeIf { it.isNotBlank() },
                artistBrowseId = entityId,
                onDismiss = { showOptionsSheet = false }
            )
        }
    }
}

@Composable
fun ArtistTopSongItem(
    song: OnlineSong,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMenuClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Apple Music Ranked Track Number
        if (index != null) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    AnimatedEqualizerBars(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(modifier = Modifier.size(52.dp)) {
            SpatialImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                size = ImageSemanticSize.ThumbnailSmall,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isPlaying && index == null) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizerBars(
                        modifier = Modifier.size(24.dp).height(16.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist}${song.duration?.let { " • $it" } ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Animated skeleton shown while waiting for Detail content to load.
 */
@Composable
fun DetailScreenSkeleton() {
    UnifiedShimmerProvider {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShimmerModifier(width = 140.dp, height = 140.dp, useMorphingLoadingShape = true)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    ShimmerModifier(width = 160.dp, height = 24.dp, shape = RoundedCornerShape(8.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerModifier(width = 100.dp, height = 16.dp, shape = RoundedCornerShape(6.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShimmerModifier(width = 90.dp, height = 36.dp, shape = RoundedCornerShape(18.dp))
                        ShimmerModifier(width = 90.dp, height = 36.dp, shape = RoundedCornerShape(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(6) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ShimmerModifier(width = 48.dp, height = 48.dp, shape = RoundedCornerShape(6.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            ShimmerModifier(width = 160.dp, height = 16.dp, shape = RoundedCornerShape(8.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            ShimmerModifier(width = 100.dp, height = 12.dp, shape = RoundedCornerShape(6.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated high-fidelity skeleton for Artist Detail pages matching exact UI layout
 * (Hero Header, Title, Subscriber Count, Subscribe Pill, Radio & Play action buttons,
 * Albums horizontal row, Singles & EPs horizontal row) with M3 Expressive Shape Morphing.
 */
@Composable
fun ArtistDetailScreenSkeleton() {
    UnifiedShimmerProvider {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Hero Header Container ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                // Top Navigation Bar Placeholder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerModifier(
                        width = 40.dp,
                        height = 40.dp,
                        shape = CircleShape
                    )
                }

                // Hero Content at Bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Artist Title Placeholder
                    ShimmerModifier(
                        width = 220.dp,
                        height = 36.dp,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Subscriber Count Subtitle Placeholder
                    ShimmerModifier(
                        width = 140.dp,
                        height = 18.dp,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Button Row (Subscribe Pill, Radio Button, Play Button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Subscribe Pill Button
                        ShimmerModifier(
                            width = 110.dp,
                            height = 40.dp,
                            shape = CircleShape
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Radio Button
                        ShimmerModifier(
                            width = 42.dp,
                            height = 42.dp,
                            shape = CircleShape,
                            useMorphingLoadingShape = true
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Large Play Button
                        ShimmerModifier(
                            width = 56.dp,
                            height = 56.dp,
                            shape = CircleShape,
                            useMorphingLoadingShape = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Section 1: Albums ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerModifier(
                        width = 90.dp,
                        height = 24.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Horizontal Album Card Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(3) {
                        Column(
                            modifier = Modifier.width(160.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShimmerModifier(
                                width = 160.dp,
                                height = 160.dp,
                                shape = RoundedCornerShape(16.dp),
                                useMorphingLoadingShape = true
                            )
                            ShimmerModifier(
                                width = 140.dp,
                                height = 16.dp,
                                shape = RoundedCornerShape(6.dp)
                            )
                            ShimmerModifier(
                                width = 100.dp,
                                height = 14.dp,
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Section 2: Singles & EPs ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerModifier(
                        width = 130.dp,
                        height = 24.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Horizontal Single Card Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(3) {
                        Column(
                            modifier = Modifier.width(160.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShimmerModifier(
                                width = 160.dp,
                                height = 160.dp,
                                shape = RoundedCornerShape(16.dp),
                                useMorphingLoadingShape = true
                            )
                            ShimmerModifier(
                                width = 140.dp,
                                height = 16.dp,
                                shape = RoundedCornerShape(6.dp)
                            )
                            ShimmerModifier(
                                width = 90.dp,
                                height = 14.dp,
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * High-fidelity skeleton for Albums Collection grid pages matching screenshot 1
 * (Back Arrow, Title "Albums", Subtitle "Collection • 100 items", 2-Column Vertical Grid of Cards).
 */
@Composable
fun AlbumGridScreenSkeleton() {
    UnifiedShimmerProvider {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerModifier(
                    width = 40.dp,
                    height = 40.dp,
                    shape = CircleShape
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Large Title Placeholder ("Albums")
            ShimmerModifier(
                width = 180.dp,
                height = 36.dp,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle Placeholder ("Collection • 100 items")
            ShimmerModifier(
                width = 130.dp,
                height = 16.dp,
                shape = RoundedCornerShape(6.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2-Column Vertical Grid of Album Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                repeat(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        repeat(2) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ShimmerModifier(
                                    width = 160.dp,
                                    height = 160.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    useMorphingLoadingShape = true
                                )
                                ShimmerModifier(
                                    width = 140.dp,
                                    height = 16.dp,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                ShimmerModifier(
                                    width = 90.dp,
                                    height = 14.dp,
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * High-fidelity skeleton for Songs / Videos section list pages matching screenshot 2
 * (Back Arrow, Title "Videos/Songs", Subtitle "Songs • 15 items", Shuffle & Radio Pill Buttons, Vertical List with 3-Dots Menu).
 */
@Composable
fun SongListScreenSkeleton() {
    UnifiedShimmerProvider {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerModifier(
                    width = 40.dp,
                    height = 40.dp,
                    shape = CircleShape
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Large Title Placeholder ("Videos")
            ShimmerModifier(
                width = 160.dp,
                height = 36.dp,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle Placeholder ("Songs • 15 items")
            ShimmerModifier(
                width = 120.dp,
                height = 16.dp,
                shape = RoundedCornerShape(6.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Row: Shuffle & Radio Pill Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShimmerModifier(
                    width = 140.dp,
                    height = 44.dp,
                    shape = RoundedCornerShape(22.dp)
                )
                ShimmerModifier(
                    width = 140.dp,
                    height = 44.dp,
                    shape = RoundedCornerShape(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Vertical List of Song Items with 3-Dots Menu
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(8) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Song Thumbnail
                        ShimmerModifier(
                            width = 56.dp,
                            height = 56.dp,
                            shape = RoundedCornerShape(10.dp),
                            useMorphingLoadingShape = true
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        // Title & Subtitle Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ShimmerModifier(
                                width = 180.dp,
                                height = 16.dp,
                                shape = RoundedCornerShape(6.dp)
                            )
                            ShimmerModifier(
                                width = 110.dp,
                                height = 12.dp,
                                shape = RoundedCornerShape(6.dp)
                            )
                        }

                        // 3-Dots Vertical Menu Placeholder
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Material 3 Expressive Connected Button Group for Artist Pages.
 * Asymmetric connected pills (Play hero with high weight + Radio companion with icon-only).
 * Features 2dp ConnectedSpaceBetween, tonal elevation, and tactile spring feedback.
 */
@Composable
fun ArtistExpressiveConnectedButtons(
    onPlayClick: () -> Unit,
    onRadioClick: () -> Unit,
    primaryColor: Color,
    onPrimaryColor: Color,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val playInteraction = remember { MutableInteractionSource() }
        val playPressed by playInteraction.collectIsPressedAsState()

        // ── 1. Hero PLAY Button (High Weight: 1.85f, Play icon + "Play" label) ──
        ToggleButton(
            checked = playPressed,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPlayClick()
            },
            interactionSource = playInteraction,
            modifier = Modifier
                .weight(1.85f)
                .height(52.dp),
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = primaryColor,
                contentColor = onPrimaryColor,
                checkedContainerColor = primaryColor.copy(alpha = 0.88f),
                checkedContentColor = onPrimaryColor
            ),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = com.codetrio.spatialflow.R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                )
            }
        }

        val radioInteraction = remember { MutableInteractionSource() }
        val radioPressed by radioInteraction.collectIsPressedAsState()

        // ── 2. RADIO Companion Button (Less Weight: 0.75f, Icon-Only) ──
        ToggleButton(
            checked = radioPressed,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRadioClick()
            },
            interactionSource = radioInteraction,
            modifier = Modifier
                .weight(0.75f)
                .height(52.dp),
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                checkedContentColor = MaterialTheme.colorScheme.primary
            ),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Icon(
                painter = painterResource(id = com.codetrio.spatialflow.R.drawable.ic_radio),
                contentDescription = "Start Radio",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Reusable Apple Music Style Action Buttons (Shuffle / Radio).
 * Renders high-emphasis filled Shuffle stadium pill + tonal Radio stadium pill.
 */
@Composable
fun ExpressiveConnectedButtonGroup(
    onShuffleClick: () -> Unit,
    onRadioClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shuffleInteraction = remember { MutableInteractionSource() }
        val shufflePressed by shuffleInteraction.collectIsPressedAsState()

        // 1. Shuffle (Connected Leading Button)
        ToggleButton(
            checked = shufflePressed,
            onCheckedChange = { onShuffleClick() },
            interactionSource = shuffleInteraction,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Shuffle",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val radioInteraction = remember { MutableInteractionSource() }
        val radioPressed by radioInteraction.collectIsPressedAsState()

        // 2. Radio (Connected Trailing Button)
        ToggleButton(
            checked = radioPressed,
            onCheckedChange = { onRadioClick() },
            interactionSource = radioInteraction,
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
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Radio",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Generic detail view for any section type (Albums, Artists, Playlists, Songs)
 */
@Composable
fun SectionDetailView(
    section: HomeSection,
    currentOnlineSong: OnlineSong?,
    isLoadingStream: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val hasSongsOnly = remember(section.items) {
        section.items.isNotEmpty() && section.items.all { it is SearchItem.Song }
    }

    if (hasSongsOnly) {
        val songs = remember(section.items) {
            section.items.filterIsInstance<SearchItem.Song>().map { it.song }
        }
        AdaptiveDetailContainer(
            isLandscape = isLandscape,
            thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
            title = section.title,
            subtitle = "Songs • ${songs.size} items",
            sharedElementKey = section.title,
            onBack = onBack,
            headerActions = {
                ExpressiveConnectedButtonGroup(
                    onShuffleClick = { if (songs.isNotEmpty()) onSongClick(songs.first(), songs.shuffled(), 0) },
                    onRadioClick = { if (songs.isNotEmpty()) onStartRadioClick(songs.random().videoId) },
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
            itemsIndexed(
                items = songs,
                key = { index, song -> "section-song-${index}-${song.videoId}" }
            ) { index, song ->
                SearchResultItem(
                    item = SearchItem.Song(song),
                    isCurrentlyPlaying = song.videoId == currentOnlineSong?.videoId,
                    isLoading = isLoadingStream && song.videoId == currentOnlineSong?.videoId,
                    onSongMenuClick = onSongMenuClick,
                    onClick = { onSongClick(song, songs, index) }
                )
            }
        }
    } else {
        val firstItemThumb = remember(section.items) {
            when (val item = section.items.firstOrNull()) {
                is SearchItem.Song -> item.song.thumbnailUrl
                is SearchItem.Album -> item.album.thumbnailUrl
                is SearchItem.Artist -> item.artist.thumbnailUrl
                is SearchItem.Playlist -> item.playlist.thumbnailUrl
                is SearchItem.TopResult -> item.thumbnailUrl
                is SearchItem.Header -> null
                null -> null
            }
        }
        val columns = if (isLandscape) 4 else 2
        val chunked = remember(section.items, columns) {
            section.items.chunked(columns)
        }

        AdaptiveDetailContainer(
            isLandscape = isLandscape,
            thumbnailUrl = firstItemThumb,
            title = section.title,
            subtitle = "Collection • ${section.items.size} items",
            sharedElementKey = section.title,
            onBack = onBack,
            headerActions = null
        ) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }

            itemsIndexed(
                chunked,
                key = { rowIndex, rowItems ->
                    "grid_row_${rowIndex}_" + rowItems.joinToString("-") { item ->
                        when (item) {
                            is SearchItem.Song -> item.song.videoId
                            is SearchItem.Album -> item.album.browseId
                            is SearchItem.Artist -> item.artist.browseId
                            is SearchItem.Playlist -> item.playlist.playlistId
                            is SearchItem.TopResult -> item.title
                            is SearchItem.Header -> item.title
                        }
                    }
                }
            ) { _, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            HomeCarouselItem(
                                item = item,
                                isPlaying = when (item) {
                                    is SearchItem.Song -> item.song.videoId == currentOnlineSong?.videoId
                                    else -> false
                                },
                                onClick = {
                                    when (item) {
                                        is SearchItem.Song -> onSongClick(item.song, listOf(item.song), 0)
                                        is SearchItem.Album -> onAlbumClick(item.album)
                                        is SearchItem.Artist -> onArtistClick(item.artist)
                                        is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                        is SearchItem.TopResult, is SearchItem.Header -> {}
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (rowItems.size < columns) {
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoodDetailView(
    moodDetail: MoodDetail,
    currentOnlineSong: OnlineSong?,
    isLoadingStream: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSectionClick: (String, String?, String) -> Unit,
    onStartRadioClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val (_, fallbackImageUrl) = getMoodVisuals(moodDetail.moodName)

    val firstItemThumb = moodDetail.sections.firstOrNull()?.items?.firstOrNull()?.let {
        when (it) {
            is SearchItem.Song -> it.song.thumbnailUrl
            is SearchItem.Album -> it.album.thumbnailUrl
            is SearchItem.Artist -> it.artist.thumbnailUrl
            is SearchItem.Playlist -> it.playlist.thumbnailUrl
            is SearchItem.TopResult -> it.thumbnailUrl
            is SearchItem.Header -> null
        }
    } ?: fallbackImageUrl

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = firstItemThumb,
        title = moodDetail.moodName,
        subtitle = "Mood Hub • ${moodDetail.sections.size} categories",
        sharedElementKey = "mood-${moodDetail.moodName}",
        onBack = onBack,
        headerActions = {
            val allSongs = remember(moodDetail.sections) {
                moodDetail.sections.flatMap { it.items }
                    .filterIsInstance<SearchItem.Song>().map { it.song }
            }
            if (allSongs.isNotEmpty()) {
                ExpressiveConnectedButtonGroup(
                    onShuffleClick = { onSongClick(allSongs.first(), allSongs.shuffled(), 0) },
                    onRadioClick = { onStartRadioClick(allSongs.random().videoId) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        item {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }

        moodDetail.sections.forEachIndexed { index, section ->
            item(key = "mood-section-${index}-${section.title}") {
                HomeSectionRow(
                    section = section,
                    currentSongId = currentOnlineSong?.videoId,
                    onSongClick = onSongClick,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick,
                    onSongMenuClick = onSongMenuClick,
                    onSectionClick = { browseId, params, title ->
                        onSectionClick(browseId, params, title)
                    }
                )
            }
        }
    }
}

// ── Pre-allocated Mood Visual Constants ──
private val VisualChill = MoodVisuals(listOf(Color(0xFF0B1E36), Color(0xFF1E3C72)), "https://yt3.googleusercontent.com/5Wbm5UXl8JWjFxHXdnhhbFPT86rn-7KP0QEprpDUR2orURbk0VXsXWnEJ8drqQKzSdUd-LIJgScxHxF7uw=w120-h120-l90-rj")
private val VisualCommute = MoodVisuals(listOf(Color(0xFF2C0F2F), Color(0xFF4C1E4F)), "https://yt3.googleusercontent.com/lEJ45zST5h0zYX0ZzFrIPf0OUunLaY7Gz83K93DQMTKCILUkA3Y6kLrrKuaREKZkmwpMlEVwf1eMD2QYKg=w544-h544-l90-rj")
private val VisualEnergize = MoodVisuals(listOf(Color(0xFF4A0E17), Color(0xFF2B0208)), "https://i.ytimg.com/vi/iGvcyYwNV7E/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3lKGLBfK6iCjZPvJVqAl4BvHZpOrg")
private val VisualFeelGood = MoodVisuals(listOf(Color(0xFF4A340E), Color(0xFF2D1E04)), "https://yt3.googleusercontent.com/AT2wfk1PvKjnHO7BzH2i2NkAvqRy_oZNEq48QGBlLJb51AuguCiR3mzp99X1uFdZezAkoSJ2WGjUU-HXZg=w120-h120-l90-rj")
private val VisualFocus = MoodVisuals(listOf(Color(0xFF0B2536), Color(0xFF173D54)), "https://yt3.googleusercontent.com/Aen64Gcp92yGdBM6LLWFk8vXDF3l6fzWHnyRJJFmjOcqZovwI9ddtd0ONGGaLUYRiRCswTDhcY8DFM1h=w544-h544-l90-rj")
private val VisualGaming = MoodVisuals(listOf(Color(0xFF1C0E36), Color(0xFF381D66)), "https://yt3.googleusercontent.com/oV0yx_UYlchhRaC5R4sKpDQGbEw1ZzxvvDYESz3shjne70pJwFotUmQc7kGayhY7EXIoTrZxsMsUBeE=w120-h120-l90-rj")
private val VisualParty = MoodVisuals(listOf(Color(0xFF2D0F38), Color(0xFF561D6B)), "https://yt3.googleusercontent.com/voQriSjwTDvhYfOsDX3TqTfNOVtVoZyY3TIlDjem4209T6nU839S6NoMERGoN7UisKAFSsViy4Qv6gRI=w544-h544-l90-rj")
private val VisualRomance = MoodVisuals(listOf(Color(0xFF3D0911), Color(0xFF1A0205)), "https://yt3.googleusercontent.com/kIN6MHu8YyLF7_FOxwoC0LSbZM4zMMiDLvR5dlnjR8d17Bnt_eLsSvCddxrFm47b9gLNpbd0v6tLvHc=w120-h120-l90-rj")
private val VisualSad = MoodVisuals(listOf(Color(0xFF0D1E36), Color(0xFF162536)), "https://i.ytimg.com/vi/q48Y5Hf83Bk/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3kotH-DjBWGPNSCY-2wJM_ZN3ASdw")
private val VisualSleep = MoodVisuals(listOf(Color(0xFF0E0B25), Color(0xFF1C1740)), "https://yt3.googleusercontent.com/Rh31BmBmpm5nP3lM-XO-q-546S1cUAoCSFmqWSSiS01jekVhS7fQqmeFuiK-gYKPDrY9QOHfa9G_2iI-=w120-h120-l90-rj")
private val VisualWorkout = MoodVisuals(listOf(Color(0xFF0E3823), Color(0xFF1E5C40)), "https://yt3.googleusercontent.com/6gEZkZYsLbktcQLbZhPzVj4KGD-WI69Y_2TA3D1XglIkivE0VXiN1byza_D8LFhIACBVTf839kflhzGo=w120-h120-l90-rj")

// ── Pre-allocated Genre Visual Constants (Regional / Language) ──
private val VisualAfrican = MoodVisuals(listOf(Color(0xFF47210E), Color(0xFF240E04)), "https://lh3.googleusercontent.com/_QE_u2AbzzmENeNq9ixrSpB_dZHCgX1sX9_4NAzGIWTR8Gd6jHpGcUZlmG4ZjA97F7pHrErkFOdNfw=w120-h120-p-l90-rj")
private val VisualArabic = MoodVisuals(listOf(Color(0xFF3E1E4F), Color(0xFF240E30)), "https://i.ytimg.com/vi/y0ua77o3rJI/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3l2sZyORL_jC9GB3-KnnTVOlbAI_w")
private val VisualBengali = MoodVisuals(listOf(Color(0xFF0E4F32), Color(0xFF052B1A)), "https://yt3.googleusercontent.com/PM75ZJhqaLfpFMqx_rJdOnshUdE5IPfko7RYkIXe__rRZlwxKXfAA5P_Zifqx_uu8V-WBSJSCpInAFpa=w544-h544-l90-rj")
private val VisualBhojpuri = MoodVisuals(listOf(Color(0xFF4F3B0E), Color(0xFF2B1F04)), "https://yt3.googleusercontent.com/DL2RONGBWdojmekrXzdYa6o0_uAcJ2F_rJbqBsfgJ2_tyxJDl3z9ItBF5t13PuZc6uIVXcUU-G1hyySY=w120-h120-l90-rj")
private val VisualCarnatic = MoodVisuals(listOf(Color(0xFF4F0E13), Color(0xFF2B0407)), "https://i.ytimg.com/vi/Go-mAJpH6_w/sddefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCghqEJQEGHgg6AJIWg&rs=AMzJL3l9U87DpC3SxCi9IUtuP6XTT0BvZg")
private val VisualGujarati = MoodVisuals(listOf(Color(0xFF4F2B0E), Color(0xFF2B1404)), "https://i.ytimg.com/vi/MRWHOvPSIi4/sddefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCghqEJQEGHgg6AJIWg&rs=AMzJL3kWFdDEPqjyHY4zyTDmn8I-vI4OZA")
private val VisualHaryanvi = MoodVisuals(listOf(Color(0xFF0E434F), Color(0xFF06232B)), "https://yt3.googleusercontent.com/9b6smz_a_wDIELlfuNtf8Py4dxQvkYhrY0JTpbiJ1nEBXXigtYMHokGkUySMJfw2We7fJhjz-x_u2MrT=w120-h120-l90-rj")
private val VisualHindi = MoodVisuals(listOf(Color(0xFF4F1A0E), Color(0xFF2B0B04)), "https://lh3.googleusercontent.com/bbR8znm7CX07mCGQH-M484ckFRaKkSmTjwrwuFZxQUBy7Uc5gQcintkpqDXCuSX0DdLLg2aPskZhC2s=w120-h120-p-l90-rj")
private val VisualHindustani = MoodVisuals(listOf(Color(0xFF380E4F), Color(0xFF1C042B)), "https://yt3.googleusercontent.com/5tDBQm5emPRocnpNzBKKra2IVSEFAVodBuq6X9tbMc0PVNR9J7YHwKNgvVe1V_mDAsUPRl7F_Az-=s1200")
private val VisualKannada = MoodVisuals(listOf(Color(0xFF4F0E34), Color(0xFF2B041B)), "https://yt3.googleusercontent.com/zSzuaIP-OMj_kns5Q4erv8rW0-oMS5fIbANYcUt_2cnyKQ_3ThEFP4AvjH_D7DeWlCtRQ60ULA=w120-h120-l90-rj-dcpUWACyIJ")
private val VisualMalayalam = MoodVisuals(listOf(Color(0xFF0E384F), Color(0xFF041F2B)), "https://yt3.googleusercontent.com/Q3SvVNKE33VIDryHPfRmw8TFI5S7N_cf37OIXSoALVSHCpswOLO8rWFIihJA-Z43d_q59PoTNlvnrUVF=w120-h120-l90-rj")
private val VisualMarathi = MoodVisuals(listOf(Color(0xFF4F1D0E), Color(0xFF2B0D04)), "https://i.ytimg.com/vi/SRKqCi9-aeg/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3lg_pIgHLVg514QuZpqDWFs03DHXw")
private val VisualPunjabi = MoodVisuals(listOf(Color(0xFF4F0F27), Color(0xFF2B0412)), "https://i.ytimg.com/vi/JQ922u24pH8/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3k55rXpOx8UtU7xpry42BhYfR2EZw")
private val VisualTamil = MoodVisuals(listOf(Color(0xFF0E4F28), Color(0xFF042B14)), "https://lh3.googleusercontent.com/wBG4jypwBcEGHd-qSbM2_4B46WPEhlOCjusCOEkxdnsoIC4WLS9LmFARZsE854pB-vAEYlsp4x2yiHE=w120-h120-p-l90-rj")
private val VisualTelugu = MoodVisuals(listOf(Color(0xFF0F264F), Color(0xFF06132B)), "https://yt3.googleusercontent.com/CdnTrpYIv-ZumLEqKXb502AOOd5ec3kp91VgMpfl5lnOCWrQg0twoJDiiQctBQ6KpUpTd0eM7bdBnds=w120-h120-l90-rj")

// ── Pre-allocated Genre Visual Constants (Indian Indie / Desi) ──
private val VisualDesiHipHop = MoodVisuals(listOf(Color(0xFF43320B), Color(0xFF241A04)), "https://yt3.googleusercontent.com/cD8eFPDqoVhg5AXB7GN8fONZVV6LIxqCLww1V754H2djjQdLLmhth1ucWwHuLMhsUH4kJ_2YivwTrAk=w544-h544-l90-rj")
private val VisualDevotional = MoodVisuals(listOf(Color(0xFF4E2C0F), Color(0xFF291404)), "https://yt3.googleusercontent.com/CXTdaEBZHvJREVPpI7epDp4Sqv8oy2eHguv1-rH2Pjy6JAHZNr60BEr4Z5actaBr7UhkhlVamPrFOnjZ=w120-h120-l90-rj")
private val VisualFamily = MoodVisuals(listOf(Color(0xFF0E4F38), Color(0xFF042B1E)), "https://yt3.googleusercontent.com/dDwLaiGIjQwZTku4-Zj0moP-LnYXoAlDvbrNKVCVs2kpV2SzdcCySUAwlfOrjMLMEq6ubyYHR7QnH3I=w120-h120-l90-rj")
private val VisualGhazal = MoodVisuals(listOf(Color(0xFF2F0E4F), Color(0xFF1A042E)), "https://yt3.googleusercontent.com/ovcYuUOnxjVXIApysRqshE_laShvabvSbNa4eXu_QBFjP7j3_SGhp0L1rRvAiol1MyIYOid0rLpXrXXW=w544-h544-l90-rj")
private val VisualIndianIndie = MoodVisuals(listOf(Color(0xFF4F1B0E), Color(0xFF2B0C04)), "https://yt3.googleusercontent.com/-O9wZ1lc0XI9mEzJaNrQbxf8gaapV0nz-iisPVgXUl0u59vGuGgmlb8hfXgnyhK451TR6X6Lbg=s1200")
private val VisualIndianPop = MoodVisuals(listOf(Color(0xFF4F0E2F), Color(0xFF2B0419)), "https://yt3.googleusercontent.com/k8T1iodf5nMMksDlIjS7z0T4uUGi0IWvfzz6H2QgFHs1lv9kjgd5gXRs05lC7TBP_Csv4dsu0K8nr3yqRA=w544-h544-l90-rj")
private val VisualMonsoon = MoodVisuals(listOf(Color(0xFF0E2538), Color(0xFF06131F)), "https://yt3.googleusercontent.com/vrtDD6cpMCYeP7f7SImfbNrfiSjGPT6hRH8cEB2ayKGeUwvOpckqM3Z6CT6V8if79vPxt3p2tnw3zfti=w544-h544-l90-rj")

// ── Pre-allocated Genre Visual Constants (Western / International) ──
private val VisualClassical = MoodVisuals(listOf(Color(0xFF0E2538), Color(0xFF071521)), "https://lh3.googleusercontent.com/Z_MIQT_NQBhEP5PsULMz829VblbsqWgibZXRSgwWhYImkYJYq6DUwUvSkg4-0RJImuZFbrQqvZXetg=w120-h120-p-l90-rj")
private val VisualCountry = MoodVisuals(listOf(Color(0xFF38290E), Color(0xFF1F1505)), "https://yt3.googleusercontent.com/JHAV9fESXYj_ewEC9HYdceX_9E0LQIgjixhvxxRFDO4S_aAqq1ySb8pCxthYSHy2-11EKJLqipLEVfk=w120-h120-l90-rj")
private val VisualDance = MoodVisuals(listOf(Color(0xFF0B333B), Color(0xFF04181F)), "https://yt3.googleusercontent.com/VmcOQDfSnyBT2ObpVAQv7A2IZI0U41HnHIrplWW3hRbyLAfCxo5Nzu0uy2U2x5pOMCo7P_vaA8qzDXji=w544-h544-l90-rj")
private val VisualDecades = MoodVisuals(listOf(Color(0xFF0E264F), Color(0xFF04132B)), "https://yt3.googleusercontent.com/D8B3OUAetDwJxEsbPPo1n-uipZZeYjnhRdr0tV_ldP-T5Mujn7enMg_swnuvCRozQsPYzO2PzIPeU884=w544-h544-l90-rj")
private val VisualFolk = MoodVisuals(listOf(Color(0xFF2C2F36), Color(0xFF14161B)), "https://i.ytimg.com/vi/8UjJBh_GO2k/sddefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCghqEJQEGHgg6AJIWg&rs=AMzJL3mqEOs6DltpzTfq2yZX69QsJ2pUbA")
private val VisualHipHop = MoodVisuals(listOf(Color(0xFF4F2B0E), Color(0xFF2B1404)), "https://yt3.googleusercontent.com/zOaDpfj2eyVVe1WX6_hRirEI0DZxghCjpg-wVOebZK3FS_Q7MJKddQ2z5MGLqcUlcWN9RlJME34xpRuA=w120-h120-l90-rj")
private val VisualIndie = MoodVisuals(listOf(Color(0xFF0B2D3B), Color(0xFF041924)), "https://i.ytimg.com/vi/ocJWc7DgGp8/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3nrVm8fTxDaqW7cqO-pN9l156g9TQ")
private val VisualJPop = MoodVisuals(listOf(Color(0xFF4F1E4A), Color(0xFF2B0E27)), "https://yt3.googleusercontent.com/TdUFj3N68xAQWNhlHyX-7Z9roXUQ0ouJlv3_dHJfKbkzLmmRNIfxzqlUMeND2Z_wLeg333yvsg=w120-h120-l90-rj")
private val VisualJazz = MoodVisuals(listOf(Color(0xFF4F2C32), Color(0xFF2B141B)), "https://yt3.googleusercontent.com/ZKgKcRJr0BOAJAiKt3t2-a1f63LoI7FpKt1lqKoFH9uCR3Nl4mmZRIxRxqK7EzOJ5dK_uPaCMQgQCRCS=w120-h120-l90-rj")
private val VisualKPop = MoodVisuals(listOf(Color(0xFF381F4F), Color(0xFF1E0E2E)), "https://yt3.googleusercontent.com/c7rpiHR2uLqq12hGkd3O_lnUaJav1pEw3_gLxLRsaii7rvD_N_JY5yiSw7Dxalxbe6ZIiwcE5k0=s1200")
private val VisualLatin = MoodVisuals(listOf(Color(0xFF4F101A), Color(0xFF2B040B)), "https://yt3.googleusercontent.com/ploU_4iWpDoJlX3FOlwwd_yQcex0I8A0_665lePXAEBbNp1zn5g42eNwg5Q7lvYc2mG2--UNIYcIhww=w120-h120-p-l90-rj")
private val VisualMetal = MoodVisuals(listOf(Color(0xFF1E1E1E), Color(0xFF0F0F0F)), "https://i.ytimg.com/pl_c/PLmXxqSJJq-yUwqtbp8MHBoTDoDULMoViq/studio_square_thumbnail.jpg?sqp=CJ3rw9IG-oaymwEKCMAWENQMIABIWqLzl_8DBgiOva_ABg&rs=AMzJL3n7cg8KNd-aBmA4zWF5X6PjiJPHIA")
private val VisualPop = MoodVisuals(listOf(Color(0xFF4F0E2A), Color(0xFF2B0414)), "https://yt3.googleusercontent.com/wxE4sWHvCcQiK92dPmiqZKLtp1yFwCm3dJb0Fv1kPNSAceFYfZxkF0au_UFSQLznSlsDIYvE=w120-h120-l90-rj")
private val VisualRnB = MoodVisuals(listOf(Color(0xFF2F173A), Color(0xFF160A1C)), "https://i.ytimg.com/vi/nftGJDj8ho0/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3nGsZ1n-T0KIqDZeM_65QkVX6532Q")
private val VisualReggae = MoodVisuals(listOf(Color(0xFF0E3A33), Color(0xFF041F1A)), "https://i.ytimg.com/vi/oFWwJJhUAxM/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3nbZKER-Hy4_po0ON-pwiZDW4yW6w")
private val VisualRock = MoodVisuals(listOf(Color(0xFF26104F), Color(0xFF11042B)), "https://yt3.googleusercontent.com/QUGEA4FQc0BoAlHXCe71o85DIpcjDplPP_imjTkxsxZef4eAoyo4QIKTWHoU6jM0pCB5H1Fe3Uc=s1200")

// ── Pre-allocated Fallback Gradients ──
private val FallbackGradients = listOf(
    listOf(Color(0xFF3A1E17), Color(0xFF24100C)),
    listOf(Color(0xFF17243A), Color(0xFF0C1424)),
    listOf(Color(0xFF173A2A), Color(0xFF0C2419)),
    listOf(Color(0xFF3A3117), Color(0xFF241E0C)),
    listOf(Color(0xFF2C173A), Color(0xFF1B0C24)),
    listOf(Color(0xFF17383A), Color(0xFF0C2224)),
    listOf(Color(0xFF3A172B), Color(0xFF240C1A)),
    listOf(Color(0xFF1F1F1F), Color(0xFF121212))
)

private val DefaultBaseGradient = listOf(Color(0xFF0B1E36), Color(0xFF1E3C72))

// Bounded LRU cache for deterministic, rapid mood visuals resolution
private val moodVisualsCache = android.util.LruCache<String, MoodVisuals>(128)

fun getMoodVisuals(name: String): MoodVisuals {
    moodVisualsCache.get(name)?.let { return it }

    val lower = name.lowercase()
    val result = when {
        // ── Moods ──
        lower.contains("chill") -> VisualChill
        lower.contains("commute") || lower.contains("travel") -> VisualCommute
        lower.contains("energize") || lower.contains("energy") -> VisualEnergize
        lower.contains("feel good") || lower.contains("happy") || lower.contains("feelgood") -> VisualFeelGood
        lower.contains("focus") || lower.contains("study") -> VisualFocus
        lower.contains("gaming") -> VisualGaming
        lower.contains("party") -> VisualParty
        lower.contains("romance") || lower.contains("love") -> VisualRomance
        lower.contains("sad") || lower.contains("cry") -> VisualSad
        lower.contains("sleep") || lower.contains("relax") -> VisualSleep
        lower.contains("workout") || lower.contains("gym") || lower.contains("fitness") -> VisualWorkout

        // ── Genres: Regional / Language ──
        lower.contains("african") -> VisualAfrican
        lower.contains("arabic") -> VisualArabic
        lower.contains("bengali") -> VisualBengali
        lower.contains("bhojpuri") -> VisualBhojpuri
        lower.contains("carnatic") -> VisualCarnatic
        lower.contains("gujarati") -> VisualGujarati
        lower.contains("haryanvi") -> VisualHaryanvi
        lower.contains("hindi") && !lower.contains("hindustani") -> VisualHindi
        lower.contains("hindustani") -> VisualHindustani
        lower.contains("kannada") -> VisualKannada
        lower.contains("malayalam") -> VisualMalayalam
        lower.contains("marathi") -> VisualMarathi
        lower.contains("punjabi") -> VisualPunjabi
        lower.contains("tamil") -> VisualTamil
        lower.contains("telugu") -> VisualTelugu

        // ── Genres: Indian Indie / Desi ──
        lower.contains("desi hip-hop") || lower.contains("desi hip hop") -> VisualDesiHipHop
        lower.contains("devotional") -> VisualDevotional
        lower.contains("family") -> VisualFamily
        lower.contains("ghazal") || lower.contains("sufi") -> VisualGhazal
        lower.contains("indian indie") -> VisualIndianIndie
        lower.contains("indian pop") -> VisualIndianPop
        lower.contains("monsoon") -> VisualMonsoon

        // ── Genres: Western / International ──
        lower.contains("classical") -> VisualClassical
        lower.contains("country") || lower.contains("americana") -> VisualCountry
        lower.contains("dance") || lower.contains("electronic") || lower.contains("edm") -> VisualDance
        lower.contains("decades") -> VisualDecades
        lower.contains("folk") || lower.contains("acoustic") -> VisualFolk
        lower.contains("hip-hop") || lower.contains("hip hop") || lower.contains("rap") -> VisualHipHop
        lower.contains("indie") || lower.contains("alternative") -> VisualIndie
        lower.contains("j-pop") || lower.contains("jpop") -> VisualJPop
        lower.contains("jazz") || lower.contains("blues") -> VisualJazz
        lower.contains("k-pop") || lower.contains("kpop") -> VisualKPop
        lower.contains("latin") -> VisualLatin
        lower.contains("metal") -> VisualMetal
        lower.contains("pop") -> VisualPop
        lower.contains("r&b") || lower.contains("soul") || lower.contains("rnb") -> VisualRnB
        lower.contains("reggae") || lower.contains("caribbean") -> VisualReggae
        lower.contains("rock") -> VisualRock

        // ── Hash-based fallback for unknown categories (local gradient, no remote network payload) ──
        else -> {
            val hash = name.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
            val gradientColors = FallbackGradients[hash % FallbackGradients.size]
            MoodVisuals(gradientColors, "")
        }
    }

    moodVisualsCache.put(name, result)
    return result
}

fun createBrightGradientFromBase(baseColor: Color): List<Color> {
    if (baseColor == Color.Black || baseColor == Color.Transparent || (baseColor.red < 0.1f && baseColor.green < 0.1f && baseColor.blue < 0.1f)) {
        return DefaultBaseGradient
    }

    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(
        android.graphics.Color.argb(
            (baseColor.alpha * 255).toInt(),
            (baseColor.red * 255).toInt(),
            (baseColor.green * 255).toInt(),
            (baseColor.blue * 255).toInt()
        ),
        hsl
    )
    
    // Coerce HSL for a very sleek, rich, dark-mode gradient (instead of harsh neon colors)
    val brightHsl1 = floatArrayOf(
        hsl[0],
        hsl[1].coerceIn(0.35f, 0.60f), // pleasant saturation, not neon
        hsl[2].coerceIn(0.15f, 0.32f)  // deep/dark base
    )
    
    val brightHsl2 = floatArrayOf(
        (hsl[0] + 25f) % 360f,          // subtle shifting tone
        hsl[1].coerceIn(0.30f, 0.55f),
        hsl[2].coerceIn(0.10f, 0.24f)  // even darker accent
    )
    
    val color1 = Color(ColorUtils.HSLToColor(brightHsl1))
    val color2 = Color(ColorUtils.HSLToColor(brightHsl2))
    
    return listOf(color1, color2)
}



