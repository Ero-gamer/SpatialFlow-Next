package com.codetrio.spatialflow.ui.spotify

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.spotify.SpotifyHomeItem
import com.codetrio.spatialflow.data.spotify.SpotifyHomeSection
import com.codetrio.spatialflow.viewmodel.SpotifyAuthState
import com.codetrio.spatialflow.viewmodel.SpotifyViewModel
import androidx.compose.runtime.remember

private val SpotifyGreen = Color(0xFF1ED760)
private val SpotifyDarkGreen = Color(0xFF12833B)
private val HTML_TAG_REGEX = Regex("<[^>]*>")

enum class ExploreMusicSource {
    YOUTUBE_MUSIC,
    SPOTIFY,
}

@Composable
fun ExploreSourcePicker(
    selectedSource: ExploreMusicSource,
    onSourceSelected: (ExploreMusicSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourcePickerItem(
            selected = selectedSource == ExploreMusicSource.YOUTUBE_MUSIC,
            onClick = { onSourceSelected(ExploreMusicSource.YOUTUBE_MUSIC) },
            label = "YouTube Music",
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_youtube_music),
                    contentDescription = null,
                    tint = if (selectedSource == ExploreMusicSource.YOUTUBE_MUSIC) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
        SourcePickerItem(
            selected = selectedSource == ExploreMusicSource.SPOTIFY,
            onClick = { onSourceSelected(ExploreMusicSource.SPOTIFY) },
            label = "Spotify",
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_spotify_logo),
                    contentDescription = null,
                    tint = if (selectedSource == ExploreMusicSource.SPOTIFY) Color(0xFF081C15) else SpotifyGreen,
                    modifier = Modifier.size(20.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SourcePickerItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected && label == "Spotify") SpotifyGreen else if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected && label == "Spotify") Color(0xFF081C15) else if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SpotifyExploreHome(
    spotifyViewModel: SpotifyViewModel,
    onConnect: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSourceSelected: (ExploreMusicSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val authState by spotifyViewModel.authState.collectAsStateWithLifecycle()
    val homeFeed by spotifyViewModel.homeFeed.collectAsStateWithLifecycle()
    val homeError by spotifyViewModel.homeError.collectAsStateWithLifecycle()
    val accountName by spotifyViewModel.accountName.collectAsStateWithLifecycle()
    val accountAvatarUrl by spotifyViewModel.accountAvatarUrl.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ExploreSourcePicker(
            selectedSource = ExploreMusicSource.SPOTIFY,
            onSourceSelected = onSourceSelected,
        )
        AnimatedContent(
            targetState = authState,
            transitionSpec = {
                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(initialScale = 0.98f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                    .togetherWith(
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleOut(targetScale = 0.98f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    )
            },
            label = "SpotifyExploreStateTransition"
        ) { state ->
            when (state) {
                SpotifyAuthState.NOT_CONNECTED -> SpotifyExploreConnectCard(onConnect = onConnect)
                SpotifyAuthState.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SpotifyGreen)
                }
                SpotifyAuthState.ERROR -> SpotifyExploreError(
                    message = homeError ?: "Spotify could not be loaded.",
                    onRetry = spotifyViewModel::reload,
                )
                SpotifyAuthState.CONNECTED -> SpotifyHomeContent(
                    greeting = homeFeed?.greeting,
                    sections = homeFeed?.sections.orEmpty(),
                    accountName = accountName,
                    accountAvatarUrl = accountAvatarUrl,
                    errorMessage = homeError,
                    onRefresh = spotifyViewModel::reload,
                    onPlaylistClick = onPlaylistClick,
                )
            }
        }
    }
}

@Composable
internal fun SpotifyExploreConnectCard(
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isWideScreen) 24.dp else 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            SpotifyGreen,
                            Color(0xFF19B34E),
                            SpotifyDarkGreen
                        )
                    )
                )
                .clip(RoundedCornerShape(24.dp))
        ) {
            // Subtle ambient watermark in trailing background
            Icon(
                painter = painterResource(R.drawable.ic_spotify_logo),
                contentDescription = null,
                tint = Color(0xFF081C15).copy(alpha = 0.08f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(if (isWideScreen) 210.dp else 140.dp)
                    .offset(x = if (isWideScreen) 40.dp else 30.dp, y = if (isWideScreen) 0.dp else 15.dp)
            )

            if (isWideScreen) {
                // Wide Screen Adaptive Horizontal Banner Layout (Tablets, Foldables, Landscape)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_spotify_logo),
                                contentDescription = null,
                                tint = Color(0xFF081C15),
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "Spotify",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF081C15).copy(alpha = 0.85f),
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = "Explore your Spotify",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF081C15)
                        )

                        Text(
                            text = "Connect to view Daily Mixes, recommendations, and recently played music.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF081C15).copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.widthIn(max = 520.dp)
                        )
                    }

                    // Branded High-Contrast Pill CTA Button
                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF081C15),
                            contentColor = Color.White
                        ),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_spotify_logo),
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Connect Spotify",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                // Compact Screen Vertical Card Layout (Phone Portrait)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_spotify_logo),
                            contentDescription = null,
                            tint = Color(0xFF081C15),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Spotify",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF081C15).copy(alpha = 0.85f),
                            letterSpacing = 0.5.sp
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Explore your Spotify",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF081C15)
                        )
                        Text(
                            text = "Connect to view Daily Mixes, recommendations, and recently played music.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF081C15).copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF081C15),
                            contentColor = Color.White
                        ),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_spotify_logo),
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Connect Spotify",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifyExploreError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun SpotifyHomeContent(
    greeting: String?,
    sections: List<SpotifyHomeSection>,
    accountName: String,
    accountAvatarUrl: String?,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onPlaylistClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_spotify_logo), null, tint = SpotifyGreen, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(greeting ?: "Good listening", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(accountName.ifBlank { "Spotify" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (accountAvatarUrl != null) {
                    com.codetrio.spatialflow.ui.explore.SpatialImage(
                        model = accountAvatarUrl,
                        contentDescription = null,
                        size = com.codetrio.spatialflow.ui.explore.ImageSemanticSize.AvatarSmall,
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Spotify Home")
                }
            }
        }
        if (errorMessage != null) {
            item {
                Text(
                    errorMessage,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (sections.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(R.drawable.ic_spotify_logo), null, tint = SpotifyGreen, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("Your Spotify Home is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Pull to refresh or try again in a moment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(
            items = sections,
            key = { index, section -> "sp_sec_${index}_${section.uri.ifEmpty { section.title?.hashCode()?.toString() ?: "sec" }}" },
            contentType = { _, _ -> "spotify_section" }
        ) { _, section ->
            SpotifyHomeSectionRow(
                section = section,
                onPlaylistClick = onPlaylistClick
            )
        }
    }
}

@Composable
internal fun SpotifyHomeSectionRow(
    section: SpotifyHomeSection,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAlbumClick: ((String) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
) {
    Column(modifier = modifier.padding(top = 12.dp)) {
        section.title?.takeIf(String::isNotBlank)?.let { title ->
            Text(
                title,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = section.items,
                key = { index: Int, item: SpotifyHomeItem -> "sp_item_${section.title?.hashCode() ?: 0}_${index}_${item.id}" },
                contentType = { _: Int, item: SpotifyHomeItem -> item::class.java.simpleName }
            ) { _, item: SpotifyHomeItem ->
                SpotifyHomeCard(
                    item = item,
                    onPlaylistClick = onPlaylistClick,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick
                )
            }
        }
    }
}

@Composable
private fun SpotifyHomeCard(
    item: SpotifyHomeItem,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAlbumClick: ((String) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
) {
    val isArtist = item is SpotifyHomeItem.Artist
    val imageUrl = when (item) {
        is SpotifyHomeItem.Playlist -> item.imageUrl
        is SpotifyHomeItem.Album -> item.imageUrl
        is SpotifyHomeItem.Artist -> item.imageUrl
    }
    val title = when (item) {
        is SpotifyHomeItem.Playlist -> item.name
        is SpotifyHomeItem.Album -> item.name
        is SpotifyHomeItem.Artist -> item.name
    }
    val subtitle = remember(item) {
        when (item) {
            is SpotifyHomeItem.Playlist -> item.description?.replace(HTML_TAG_REGEX, "")?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")?.trim() ?: item.ownerName ?: "Playlist"
            is SpotifyHomeItem.Album -> item.artists.joinToString { it.name }.ifBlank { "Album" }
            is SpotifyHomeItem.Artist -> "Artist"
        }
    }
    val clickModifier = when (item) {
        is SpotifyHomeItem.Playlist -> Modifier.clickable { onPlaylistClick(item.id) }
        is SpotifyHomeItem.Album -> if (onAlbumClick != null) Modifier.clickable { onAlbumClick(item.id) } else Modifier
        is SpotifyHomeItem.Artist -> if (onArtistClick != null) Modifier.clickable { onArtistClick(item.id) } else Modifier
    }

    Column(modifier = modifier.width(156.dp).then(clickModifier)) {
        if (imageUrl != null) {
            com.codetrio.spatialflow.ui.explore.SpatialImage(
                model = imageUrl,
                contentDescription = null,
                size = com.codetrio.spatialflow.ui.explore.ImageSemanticSize.ThumbnailMedium,
                shape = if (isArtist) CircleShape else RoundedCornerShape(14.dp),
                modifier = Modifier.size(156.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(156.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_spotify_logo), null, tint = SpotifyGreen, modifier = Modifier.size(42.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
