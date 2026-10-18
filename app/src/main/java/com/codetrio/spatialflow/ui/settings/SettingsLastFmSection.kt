@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.viewmodel.LastFmTab
import com.codetrio.spatialflow.viewmodel.LastFmViewModel

@Composable
fun LastFmSettingsScreen(
    navController: NavController,
    viewModel: LastFmViewModel
) {
    val context = LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.avatarUrl.collectAsStateWithLifecycle()
    val scrobbleCount by viewModel.scrobbleCount.collectAsStateWithLifecycle()
    val isScrobblingEnabled by viewModel.isScrobblingEnabled.collectAsStateWithLifecycle()
    val isNowPlayingEnabled by viewModel.isNowPlayingEnabled.collectAsStateWithLifecycle()
    val thresholdPercent by viewModel.thresholdPercent.collectAsStateWithLifecycle()
    val isWifiOnly by viewModel.isWifiOnly.collectAsStateWithLifecycle()
    val isSyncLovedTracks by viewModel.isSyncLovedTracks.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingScrobblesCount.collectAsStateWithLifecycle()

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val topTracksPeriod by viewModel.topTracksPeriod.collectAsStateWithLifecycle()
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val topTracks by viewModel.topTracks.collectAsStateWithLifecycle()
    val lovedTracks by viewModel.lovedTracks.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var showDeveloperKeys by remember { mutableStateOf(false) }
    var customApiKey by remember { mutableStateOf(viewModel.getCustomApiKey()) }
    var customApiSecret by remember { mutableStateOf(viewModel.getCustomApiSecret()) }
    var keysSavedMessage by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.refreshAll()
        }
    }

    SettingsScaffold(
        title = "Last.fm Scrobbler",
        onBack = { navController.popBackStack() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 120.dp)
        ) {
            // Header Hero Profile Card
            if (isLoggedIn) {
                LastFmProfileHeroCard(
                    username = username,
                    avatarUrl = avatarUrl,
                    scrobbleCount = scrobbleCount,
                    pendingCount = pendingCount,
                    lovedCount = lovedTracks.size,
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshAll() },
                    onLogout = { viewModel.logout() },
                    onOpenProfile = {
                        val profileUrl = "https://www.last.fm/user/$username"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
                    }
                )
            } else {
                LastFmConnectHeroCard(
                    onConnectClick = { navController.navigate("lastfm_signin") }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Music Activity & Charts (When Logged In)
            if (isLoggedIn) {
                SettingsHeader(text = "Music Activity")

                // Segmented Activity Selector
                LastFmActivityTabs(
                    selectedTab = selectedTab,
                    onTabSelected = { viewModel.setSelectedTab(it) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(
                            initialOffsetX = { width -> direction * width / 3 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(
                            animationSpec = tween(220)
                        )).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { width -> -direction * width / 3 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeOut(
                                animationSpec = tween(180)
                            )
                        )
                    },
                    label = "LastFmTabContentAnimation"
                ) { tab ->
                    when (tab) {
                        LastFmTab.RECENT -> {
                            if (recentTracks.isNotEmpty()) {
                                SettingsGroupCard(
                                    items = recentTracks.take(15).map { track ->
                                        {
                                            RecentTrackItem(
                                                trackName = track.name,
                                                artistName = track.artist?.displayName ?: "Unknown Artist",
                                                albumName = track.album?.text,
                                                imageUrl = track.getImageUrl(),
                                                isNowPlaying = track.isNowPlaying,
                                                timeAgo = track.date?.uts?.let { formatRelativeTime(it) },
                                                isLoved = track.isLoved,
                                                onToggleLove = {
                                                    val artist = track.artist?.displayName ?: ""
                                                    viewModel.toggleLoveTrack(artist, track.name, track.isLoved)
                                                },
                                                onClick = {
                                                    if (track.url.isNotBlank()) {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.url)))
                                                    }
                                                }
                                            )
                                        }
                                    }
                                )
                            } else {
                                LastFmEmptyStateCard(
                                    icon = Icons.Default.History,
                                    message = if (isRefreshing) "Loading recent scrobbles..." else "No recent scrobbles found. Play a track to start recording!"
                                )
                            }
                        }

                        LastFmTab.TOP_TRACKS -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Period Filter Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PeriodFilterChip(
                                        label = "7 Days",
                                        selected = topTracksPeriod == "7day",
                                        onClick = { viewModel.setTopTracksPeriod("7day") }
                                    )
                                    PeriodFilterChip(
                                        label = "30 Days",
                                        selected = topTracksPeriod == "1month",
                                        onClick = { viewModel.setTopTracksPeriod("1month") }
                                    )
                                    PeriodFilterChip(
                                        label = "All Time",
                                        selected = topTracksPeriod == "overall",
                                        onClick = { viewModel.setTopTracksPeriod("overall") }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (topTracks.isNotEmpty()) {
                                    SettingsGroupCard(
                                        items = topTracks.take(15).mapIndexed { index, track ->
                                            {
                                                TopTrackItem(
                                                    rank = track.attr?.rank?.toIntOrNull() ?: (index + 1),
                                                    trackName = track.name,
                                                    artistName = track.artist?.displayName ?: "Unknown Artist",
                                                    playCount = track.playcount,
                                                    imageUrl = track.getImageUrl(),
                                                    isLoved = track.isLoved,
                                                    onToggleLove = {
                                                        val artist = track.artist?.displayName ?: ""
                                                        viewModel.toggleLoveTrack(artist, track.name, track.isLoved)
                                                    },
                                                    onClick = {
                                                        if (track.url.isNotBlank()) {
                                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.url)))
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    )
                                } else {
                                    LastFmEmptyStateCard(
                                        icon = Icons.Default.Leaderboard,
                                        message = if (isRefreshing) "Loading top charts..." else "No top tracks available for this period."
                                    )
                                }
                            }
                        }

                        LastFmTab.LOVED_TRACKS -> {
                            if (lovedTracks.isNotEmpty()) {
                                SettingsGroupCard(
                                    items = lovedTracks.take(15).map { track ->
                                        {
                                            LovedTrackItem(
                                                trackName = track.name,
                                                artistName = track.artist?.displayName ?: "Unknown Artist",
                                                imageUrl = track.getImageUrl(),
                                                dateLoved = track.date?.uts?.let { formatRelativeTime(it) },
                                                onUnlove = {
                                                    val artist = track.artist?.displayName ?: ""
                                                    viewModel.toggleLoveTrack(artist, track.name, true)
                                                },
                                                onClick = {
                                                    if (track.url.isNotBlank()) {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.url)))
                                                    }
                                                }
                                            )
                                        }
                                    }
                                )
                            } else {
                                LastFmEmptyStateCard(
                                    icon = Icons.Default.Favorite,
                                    message = if (isRefreshing) "Loading loved tracks..." else "You haven't loved any tracks on Last.fm yet."
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Scrobbler Preferences
            SettingsHeader("Scrobbling Preferences")
            SettingsGroupCard(listOf(
                {
                    ListItem(
                        headlineContent = { Text("Enable Scrobbler", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = {
                            Text(
                                "Automatically record songs you listen to on your Last.fm profile",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = isScrobblingEnabled,
                                onCheckedChange = { viewModel.setScrobblingEnabled(it) },
                                enabled = isLoggedIn
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                },
                {
                    ListItem(
                        headlineContent = { Text("Send \"Now Playing\" Status", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = {
                            Text(
                                "Broadcast what track you're currently listening to in real-time",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = isNowPlayingEnabled,
                                onCheckedChange = { viewModel.setNowPlayingEnabled(it) },
                                enabled = isLoggedIn && isScrobblingEnabled
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                },
                {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Scrobble Threshold", style = MaterialTheme.typography.bodyLarge)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "$thresholdPercent%",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Track will scrobble after listening to $thresholdPercent% of its length (or 4 mins max).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = thresholdPercent.toFloat(),
                            onValueChange = { viewModel.setThresholdPercent(it.toInt()) },
                            valueRange = 50f..100f,
                            steps = 9,
                            enabled = isLoggedIn && isScrobblingEnabled
                        )
                    }
                }
            ))

            Spacer(modifier = Modifier.height(24.dp))

            // Sync & Network Settings
            SettingsHeader("Sync & Network")
            SettingsGroupCard(listOf(
                {
                    ListItem(
                        headlineContent = { Text("Scrobble over Wi-Fi Only", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = {
                            Text(
                                "Queue scrobbles on mobile data and flush automatically when on Wi-Fi",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = isWifiOnly,
                                onCheckedChange = { viewModel.setWifiOnly(it) },
                                enabled = isLoggedIn
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                },
                {
                    ListItem(
                        headlineContent = { Text("Sync Loved Tracks", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = {
                            Text(
                                "Liking a song in SpatialFlow automatically adds it to your Loved Tracks on Last.fm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = isSyncLovedTracks,
                                onCheckedChange = { viewModel.setSyncLovedTracks(it) },
                                enabled = isLoggedIn
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            ))

            Spacer(modifier = Modifier.height(24.dp))

            // Offline Cache Queue
            SettingsHeader("Offline Queue")
            SettingsGroupCard(listOf(
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (pendingCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Offline Cache",
                                    tint = if (pendingCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pendingCount == 0) "Queue is Empty" else "$pendingCount Pending Scrobble(s)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (pendingCount == 0) "All tracks are synchronized with Last.fm" else "Cached tracks ready to be submitted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (pendingCount > 0) {
                            Button(
                                onClick = { viewModel.flushPendingScrobbles() }
                            ) {
                                Text("Sync Now")
                            }
                        }
                    }
                }
            ))

            Spacer(modifier = Modifier.height(24.dp))

            // Developer / Custom API Keys (Accordion)
            SettingsGroupCard(listOf(
                {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeveloperKeys = !showDeveloperKeys }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Custom API Credentials (Optional)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                imageVector = if (showDeveloperKeys) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showDeveloperKeys) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = showDeveloperKeys) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "SpatialFlow includes official default Last.fm credentials. If you prefer to use your own personal API Application, specify your API key and secret below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                TextField(
                                    value = customApiKey,
                                    onValueChange = { customApiKey = it },
                                    label = { Text("API Key") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    )
                                )

                                TextField(
                                    value = customApiSecret,
                                    onValueChange = { customApiSecret = it },
                                    label = { Text("API Shared Secret") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.saveCustomApiCredentials(customApiKey, customApiSecret)
                                            keysSavedMessage = true
                                        }
                                    ) {
                                        Text(if (keysSavedMessage) "Saved!" else "Save Credentials")
                                    }
                                }
                            }
                        }
                    }
                }
            ))
        }
    }
}

@Composable
private fun LastFmProfileHeroCard(
    username: String,
    avatarUrl: String?,
    scrobbleCount: Long,
    pendingCount: Int,
    lovedCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // User identity row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar container
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(60.dp)
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        SpatialImage(
                            model = avatarUrl,
                            contentDescription = "Avatar",
                            size = ImageSemanticSize.AvatarSmall,
                            shape = CircleShape,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lastfm),
                                    contentDescription = "Last.fm",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = username.ifBlank { "Last.fm User" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Header actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "spin")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "spinRot"
                    )

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(44.dp)
                            .semantics { contentDescription = "Refresh Profile" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer {
                                if (isRefreshing) rotationZ = rotation
                            }
                        )
                    }

                    IconButton(
                        onClick = onOpenProfile,
                        modifier = Modifier
                            .size(44.dp)
                            .semantics { contentDescription = "View Profile on Last.fm" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Scrobbles",
                    value = formatCount(scrobbleCount),
                    icon = Icons.Default.MusicNote,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Loved",
                    value = lovedCount.toString(),
                    icon = Icons.Default.Favorite,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Queue",
                    value = pendingCount.toString(),
                    icon = Icons.Default.CloudSync,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            FilledTonalButton(
                onClick = onLogout,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log Out from Last.fm")
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LastFmActivityTabs(
    selectedTab: LastFmTab,
    onTabSelected: (LastFmTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val tabs = remember {
        listOf(
            Triple(LastFmTab.RECENT, "Recent", Icons.Default.History),
            Triple(LastFmTab.TOP_TRACKS, "Top", Icons.Default.Leaderboard),
            Triple(LastFmTab.LOVED_TRACKS, "Loved", Icons.Default.Favorite)
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        tabs.forEachIndexed { index, (tab, title, icon) ->
            val checked = tab == selectedTab
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            ToggleButton(
                checked = checked || isPressed,
                onCheckedChange = {
                    if (!checked) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(tab)
                    }
                },
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        border = null
    )
}

@Composable
private fun RecentTrackItem(
    trackName: String,
    artistName: String,
    albumName: String?,
    imageUrl: String?,
    isNowPlaying: Boolean,
    timeAgo: String?,
    isLoved: Boolean,
    onToggleLove: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // High-res album art with fallback
        TrackArtwork(imageUrl = imageUrl, contentDesc = trackName)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trackName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artistName + if (!albumName.isNullOrBlank()) " • $albumName" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isNowPlaying) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Scrobbling Now",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (!timeAgo.isNullOrBlank()) {
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        IconButton(
            onClick = onToggleLove,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = if (isLoved) "Unlike Track" else "Love Track" }
        ) {
            Icon(
                imageVector = if (isLoved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isLoved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TopTrackItem(
    rank: Int,
    trackName: String,
    artistName: String,
    playCount: String,
    imageUrl: String?,
    isLoved: Boolean,
    onToggleLove: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Rank pill
        Surface(
            shape = CircleShape,
            color = if (rank <= 3) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TrackArtwork(imageUrl = imageUrl, contentDesc = trackName)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trackName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$playCount plays",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        IconButton(
            onClick = onToggleLove,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = if (isLoved) "Unlike Track" else "Love Track" }
        ) {
            Icon(
                imageVector = if (isLoved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isLoved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun LovedTrackItem(
    trackName: String,
    artistName: String,
    imageUrl: String?,
    dateLoved: String?,
    onUnlove: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TrackArtwork(imageUrl = imageUrl, contentDesc = trackName)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trackName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!dateLoved.isNullOrBlank()) {
                Text(
                    text = "Loved $dateLoved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        IconButton(
            onClick = onUnlove,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Unlove Track" }
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TrackArtwork(imageUrl: String?, contentDesc: String) {
    if (!imageUrl.isNullOrBlank()) {
        SpatialImage(
            model = imageUrl,
            contentDescription = contentDesc,
            size = ImageSemanticSize.ThumbnailSmall,
            shape = RoundedCornerShape(10.dp),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp)
        )
    } else {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun LastFmEmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LastFmConnectHeroCard(onConnectClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lastfm),
                        contentDescription = "Last.fm",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Connect with Last.fm",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Scrobble your listening history, track your music analytics, and build personalized listening charts across all your devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            )

            Button(
                onClick = onConnectClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect Last.fm Account")
            }
        }
    }
}

private fun formatCount(count: Long): String {
    if (count <= 0) return "0"
    if (count < 1000) return count.toString()
    if (count < 1000000) {
        val whole = count / 1000
        val rem = (count % 1000) / 100
        return if (whole >= 100 || rem == 0L) "${whole}K" else "${whole}.${rem}K"
    }
    val whole = count / 1000000
    val rem = (count % 1000000) / 100000
    return if (whole >= 100 || rem == 0L) "${whole}M" else "${whole}.${rem}M"
}

private fun formatRelativeTime(utsString: String): String {
    val uts = utsString.toLongOrNull() ?: return ""
    val nowSec = System.currentTimeMillis() / 1000L
    val diffSec = nowSec - uts
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        diffSec < 604800 -> "${diffSec / 86400}d ago"
        else -> "${diffSec / 604800}w ago"
    }
}
