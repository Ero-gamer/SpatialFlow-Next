package com.codetrio.spatialflow.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.util.SongDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class FormatInfo(
    val format: String,
    val label: String,
    val description: String
)

private val AVAILABLE_FORMATS = listOf(
    FormatInfo("OPUS", "OPUS", "Original stream • ~160 kbps (High efficiency)"),
    FormatInfo("MP3", "MP3", "Universal audio • 320 kbps (High compatibility)"),
    FormatInfo("FLAC", "FLAC", "Lossless audio where available")
)

/**
 * Material 3 Expressive Modal Bottom Sheet for downloading a playlist.
 *
 * Designed for instant zero-lag opening:
 * - Offline song detection runs asynchronously in Dispatchers.IO to never block the UI thread.
 * - Format selection uses native [SingleChoiceSegmentedButtonRow] with official M3 animations.
 * - Song rows use native M3 [Surface] and [Checkbox] without per-item animation allocations.
 * - Sticky bottom CTA uses native M3 [Button] with system ripple and physics.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistDownloadBottomSheet(
    playlistTitle: String,
    playlistAuthor: String? = null,
    thumbnailUrl: String? = null,
    songs: List<SongItem>,
    initialFormat: String? = null,
    onConfirmDownload: (selectedSongs: List<SongItem>, format: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Format selection state (OPUS, MP3, FLAC)
    var selectedFormat by remember {
        val prefs = context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        val defaultFormat = prefs.getString("download_audio_format", "OPUS")?.uppercase() ?: "OPUS"
        mutableStateOf(initialFormat?.uppercase() ?: defaultFormat)
    }

    // Map each song to its key for selection tracking
    val songKeys = remember(songs) {
        songs.map { it.videoId ?: it.id.toString() }
    }

    // Multi-selection state: all songs selected by default as requested
    var selectedKeys by remember(songs) {
        mutableStateOf(songKeys.toSet())
    }

    // Check which songs are already downloaded ASYNCHRONOUSLY on IO thread (Prevents UI freeze)
    var downloadedKeys by remember(songs) {
        mutableStateOf<Set<String>>(emptySet())
    }

    LaunchedEffect(songs) {
        withContext(Dispatchers.IO) {
            val downloaded = songs.filter { SongDownloader.isSongDownloaded(context, it) }
                .map { it.videoId ?: it.id.toString() }
                .toSet()
            downloadedKeys = downloaded
        }
    }

    val allSelected = selectedKeys.size == songs.size && songs.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // ── 1. HEADER ROW ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    SpatialImage(
                        model = thumbnailUrl,
                        contentDescription = "Playlist Cover",
                        size = ImageSemanticSize.ThumbnailMedium,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val authorText = playlistAuthor?.takeIf { it.isNotBlank() }
                    val subtitle = if (authorText != null) {
                        "$playlistTitle • $authorText"
                    } else {
                        "$playlistTitle • ${songs.size} tracks"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // ── 2. SCROLLABLE CONTENT (FORMATS + SONG LIST) ───────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Audio Format Selection using Native M3 Expressive Segmented Buttons
                item(key = "format-section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "Audio Format",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AVAILABLE_FORMATS.forEachIndexed { index, item ->
                                SegmentedButton(
                                    selected = selectedFormat == item.format,
                                    onClick = { selectedFormat = item.format },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AVAILABLE_FORMATS.size
                                    ),
                                    icon = {
                                        SegmentedButtonDefaults.Icon(active = selectedFormat == item.format)
                                    }
                                ) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selectedFormat == item.format) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        val currentInfo = AVAILABLE_FORMATS.firstOrNull { it.format == selectedFormat }
                            ?: AVAILABLE_FORMATS.first()
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentInfo.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }

                item(key = "divider-middle") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }

                // Songs Multi-Selection Header Toolbar
                item(key = "selection-toolbar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Select Tracks",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedKeys.size} of ${songs.size} selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Skip Downloaded Chip (appears once background scan finishes if any offline tracks exist)
                            if (downloadedKeys.isNotEmpty()) {
                                val allDownloadedExcluded = downloadedKeys.none { it in selectedKeys }
                                FilterChip(
                                    selected = allDownloadedExcluded,
                                    onClick = {
                                        selectedKeys = if (allDownloadedExcluded) {
                                            selectedKeys + downloadedKeys
                                        } else {
                                            selectedKeys - downloadedKeys
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = if (allDownloadedExcluded) "Include Offline" else "Skip Offline",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }

                            // Select All / Deselect All Toggle Chip
                            FilterChip(
                                selected = allSelected,
                                onClick = {
                                    selectedKeys = if (allSelected) {
                                        emptySet()
                                    } else {
                                        songKeys.toSet()
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (allSelected) "Deselect All" else "Select All",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (allSelected) Icons.Rounded.Check else Icons.Rounded.SelectAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // Songs List Items
                itemsIndexed(
                    items = songs,
                    key = { index, song -> "dl_song_${index}_${song.videoId ?: song.id}" }
                ) { index, song ->
                    val key = song.videoId ?: song.id.toString()
                    val isSelected = key in selectedKeys
                    val isDownloaded = key in downloadedKeys

                    SongSelectionRow(
                        song = song,
                        index = index + 1,
                        isSelected = isSelected,
                        isDownloaded = isDownloaded,
                        onToggle = {
                            selectedKeys = if (isSelected) {
                                selectedKeys - key
                            } else {
                                selectedKeys + key
                            }
                        }
                    )
                }
            }

            // ── 3. STICKY BOTTOM ACTION CTA ──────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    val isEnabled = selectedKeys.isNotEmpty()

                    Button(
                        onClick = {
                            val selectedSongItems = songs.filter { (it.videoId ?: it.id.toString()) in selectedKeys }
                            onConfirmDownload(selectedSongItems, selectedFormat)
                        },
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedKeys.isEmpty()) {
                                "Select tracks to download"
                            } else {
                                "Download ${selectedKeys.size} ${if (selectedKeys.size == 1) "Track" else "Tracks"} • $selectedFormat"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean, lightweight selectable song row.
 * Uses native Surface with standard ripple and zero per-item animation allocations.
 */
@Composable
private fun SongSelectionRow(
    song: SongItem,
    index: Int,
    isSelected: Boolean,
    isDownloaded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Thumbnail or index fallback
            val thumb = song.thumbnailUrl ?: song.getAlbumArtUri()?.toString()
            if (!thumb.isNullOrBlank()) {
                SpatialImage(
                    model = thumb,
                    contentDescription = null,
                    size = ImageSemanticSize.ThumbnailSmall,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isDownloaded) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Offline",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
