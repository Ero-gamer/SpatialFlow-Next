@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.codetrio.spatialflow.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CorporateFare
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SongCreditsData
import com.codetrio.spatialflow.data.innertube.SongCreditsResolver
import com.codetrio.spatialflow.ui.theme.SpatialShapes

/**
 * Material 3 Expressive Song Credits Bottom Sheet.
 * Displays authentic, verified song credits (Performers, Writers, Producers, Release & Source)
 * formatted as Segmented Lists with high tactile affordance, continuous squircle geometry,
 * and sufficient bottom clearance so details are never obscured by system bars or persistent playback overlays.
 */
@Composable
fun SongCreditsBottomSheet(
    song: OnlineSong,
    onDismissRequest: () -> Unit,
    onArtistClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Asynchronously resolve authentic credits from InnerTube and music databases
    val creditsData by produceState(
        initialValue = SongCreditsData(
            title = song.title,
            performers = song.artist.split(Regex("[,&]| and | feat\\.? | ft\\.? ", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            album = song.albumName,
            isVerified = false
        ),
        key1 = song.videoId
    ) {
        value = SongCreditsResolver.resolveCredits(song)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = SpatialShapes.SheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Track Header with Artwork, Title, Artist, Verified Badge, and Close Button
            CreditsHeaderRow(
                song = song,
                creditsData = creditsData,
                onClose = onDismissRequest
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Expressive Segmented Lists Feed
            CreditsSegmentedLists(
                credits = creditsData,
                song = song,
                onArtistClick = onArtistClick
            )

            // Extra generous clearance ensuring the last details are never covered by mini player or navigation bar
            Spacer(modifier = Modifier.height(44.dp))
        }
    }
}

/**
 * Backward compatibility alias for any existing full-screen invocations.
 */
@Composable
fun SongCreditsScreen(
    song: OnlineSong,
    onBack: () -> Unit,
    onArtistClick: ((String) -> Unit)? = null
) {
    SongCreditsBottomSheet(
        song = song,
        onDismissRequest = onBack,
        onArtistClick = onArtistClick
    )
}

/**
 * Header row inside the Bottom Sheet showing album cover thumbnail, track info, verified chip, and close button.
 */
@Composable
private fun CreditsHeaderRow(
    song: OnlineSong,
    creditsData: SongCreditsData,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Artwork with subtle squircle shape
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = SpatialShapes.Medium,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
        ) {
            SpatialImage(
                model = song.thumbnailUrl,
                contentDescription = "Cover for ${song.title}",
                size = ImageSemanticSize.ThumbnailMedium,
                shape = SpatialShapes.Medium,
                modifier = Modifier
                    .size(56.dp)
                    .clip(SpatialShapes.Medium)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // Official Verified Badge
                AnimatedVisibility(
                    visible = creditsData.isVerified,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = SpatialShapes.Pill,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = "Verified Metadata",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Official",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Close Icon Button with minimum 48dp touch target
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close Credits",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Segmented Lists representation of Song Credits in Material 3 Expressive styling.
 */
@Composable
private fun CreditsSegmentedLists(
    credits: SongCreditsData,
    song: OnlineSong,
    onArtistClick: ((String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Performed By (Artists) Segmented List
        if (credits.performers.isNotEmpty()) {
            SegmentedCardGroup(
                title = "Performed by",
                icon = Icons.Rounded.Mic,
                accentColor = MaterialTheme.colorScheme.primary,
                count = credits.performers.size
            ) {
                credits.performers.forEachIndexed { index, artistName ->
                    if (index > 0) {
                        SegmentedListDivider()
                    }
                    SegmentedListItem(
                        avatarChar = artistName.firstOrNull()?.uppercaseChar() ?: 'A',
                        title = artistName,
                        subtitle = if (index == 0) "Main Artist" else "Featured Artist",
                        trailingBadge = if (index == 0) "Lead" else null,
                        onClick = onArtistClick?.let { { it(artistName) } }
                    )
                }
            }
        }

        // 2. Written By (Songwriters) Segmented List
        if (credits.writers.isNotEmpty()) {
            SegmentedCardGroup(
                title = "Written by",
                icon = Icons.Rounded.Edit,
                accentColor = MaterialTheme.colorScheme.secondary,
                count = credits.writers.size
            ) {
                credits.writers.forEachIndexed { index, writerName ->
                    if (index > 0) {
                        SegmentedListDivider()
                    }
                    SegmentedListItem(
                        avatarChar = writerName.firstOrNull()?.uppercaseChar() ?: 'W',
                        title = writerName,
                        subtitle = "Songwriter / Composer"
                    )
                }
            }
        }

        // 3. Produced By (Producers) Segmented List
        if (credits.producers.isNotEmpty()) {
            SegmentedCardGroup(
                title = "Produced by",
                icon = Icons.Rounded.GraphicEq,
                accentColor = MaterialTheme.colorScheme.tertiary,
                count = credits.producers.size
            ) {
                credits.producers.forEachIndexed { index, producerName ->
                    if (index > 0) {
                        SegmentedListDivider()
                    }
                    SegmentedListItem(
                        avatarChar = producerName.firstOrNull()?.uppercaseChar() ?: 'P',
                        title = producerName,
                        subtitle = "Producer"
                    )
                }
            }
        }

        // 4. Release & Recording Source Details Segmented List
        val displayAlbum = credits.album ?: song.albumName
        val hasReleaseInfo = !credits.metadataProvider.isNullOrBlank() ||
            !credits.releaseDate.isNullOrBlank() ||
            !displayAlbum.isNullOrBlank() ||
            credits.genres.isNotEmpty()

        if (hasReleaseInfo) {
            SegmentedCardGroup(
                title = "Release & Recording",
                icon = Icons.Rounded.CorporateFare,
                accentColor = MaterialTheme.colorScheme.outline
            ) {
                var needsDivider = false

                // Album Row
                if (!displayAlbum.isNullOrBlank()) {
                    SegmentedListItem(
                        leadingIcon = Icons.Rounded.Album,
                        title = displayAlbum,
                        subtitle = "Album"
                    )
                    needsDivider = true
                }

                // Release Date Row
                if (!credits.releaseDate.isNullOrBlank()) {
                    if (needsDivider) SegmentedListDivider()
                    SegmentedListItem(
                        leadingIcon = Icons.Rounded.CalendarToday,
                        title = credits.releaseDate,
                        subtitle = "Release Date"
                    )
                    needsDivider = true
                }

                // Metadata Provider / Distributor Source Row
                if (!credits.metadataProvider.isNullOrBlank()) {
                    if (needsDivider) SegmentedListDivider()
                    SegmentedListItem(
                        leadingIcon = Icons.Rounded.CorporateFare,
                        title = credits.metadataProvider,
                        subtitle = "Provided to YouTube by"
                    )
                    needsDivider = true
                }

                // Genres Row
                if (credits.genres.isNotEmpty()) {
                    if (needsDivider) SegmentedListDivider()
                    SegmentedListItem(
                        leadingIcon = Icons.Rounded.Category,
                        title = credits.genres.joinToString(", "),
                        subtitle = "Genre"
                    )
                }
            }
        } else if (credits.writers.isEmpty() && credits.producers.isEmpty()) {
            // Informative fallback notice if external databases haven't published detailed liner notes yet
            Surface(
                shape = SpatialShapes.Large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Detailed songwriting and production liner notes have not been published for this release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Segmented Card Group container.
 * Groups items inside a continuous rounded squircle container (SpatialShapes.Large)
 * with an expressive title, tonal icon capsule, and item count badge.
 */
@Composable
private fun SegmentedCardGroup(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.14f),
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (count != null && count > 1) {
                Surface(
                    shape = SpatialShapes.Pill,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Unified Segmented Surface Container
        Surface(
            shape = SpatialShapes.Large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

/**
 * Individual row item inside a Segmented Card Group.
 * Supports leading avatar or icon, title, subtitle role, optional trailing badge, and tap interaction.
 */
@Composable
private fun SegmentedListItem(
    title: String,
    subtitle: String,
    avatarChar: Char? = null,
    leadingIcon: ImageVector? = null,
    trailingBadge: String? = null,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(clickableModifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Leading Avatar or Icon
        if (avatarChar != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = avatarChar.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else if (leadingIcon != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Content Column: Title & Subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Trailing Badge
        if (trailingBadge != null) {
            Surface(
                shape = SpatialShapes.Pill,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
            ) {
                Text(
                    text = trailingBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Trailing Navigation Arrow if Clickable
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "View Artist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Inset divider between items in a segmented list.
 */
@Composable
private fun SegmentedListDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp, end = 14.dp),
        thickness = 0.7.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
    )
}
