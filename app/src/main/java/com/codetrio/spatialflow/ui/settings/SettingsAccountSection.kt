@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SensorsOff
import com.codetrio.spatialflow.service.live.LivePresenceStatus
import com.codetrio.spatialflow.service.live.SpatialFlowLiveManager
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.innertube.AccountManager
import com.codetrio.spatialflow.data.spotify.SpotifyPrefs
import com.codetrio.spatialflow.viewmodel.AccountViewModel
import com.codetrio.spatialflow.viewmodel.SpotifyAuthState
import com.codetrio.spatialflow.viewmodel.SpotifyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.codetrio.spatialflow.viewmodel.LastFmViewModel

@Composable
fun AccountScreen(
    navController: NavController,
    ytCookies: String?,
    onYtCookiesChange: (String?) -> Unit,
    dataSaver: Boolean,
    onDataSaverChange: (Boolean) -> Unit,
    pauseHistory: Boolean,
    onPauseHistoryChange: (Boolean) -> Unit,
    accountViewModel: AccountViewModel? = null,
    spotifyViewModel: SpotifyViewModel? = null,
    lastFmViewModel: LastFmViewModel? = null
) {
    val context = LocalContext.current
    val ytProfile by (accountViewModel?.userProfile?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    val spotifyAuthState by (spotifyViewModel?.authState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(SpotifyAuthState.NOT_CONNECTED) })
    val spotifyAccountName by (spotifyViewModel?.accountName?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("") })
    val spotifyAvatarUrlState by (spotifyViewModel?.accountAvatarUrl?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) })

    val isYtLoggedIn = ytCookies != null || AccountManager.isLoggedIn(context)
    val ytName = ytProfile?.name ?: AccountManager.getAccountName(context)
    val ytAvatarUrl = ytProfile?.avatarUrl

    val isSpotifyConnected = spotifyAuthState == SpotifyAuthState.CONNECTED || SpotifyPrefs.isConnected(context)
    val spotifyName = spotifyAccountName.ifEmpty { SpotifyPrefs.getAccountName(context).ifEmpty { "Spotify Account" } }
    val spotifyAvatarUrl = spotifyAvatarUrlState ?: SpotifyPrefs.getAccountAvatarUrl(context)

    val lastFmLoggedIn = lastFmViewModel?.isLoggedIn?.collectAsStateWithLifecycle()?.value ?: com.codetrio.spatialflow.data.lastfm.LastFmPrefs.isLoggedIn(context)
    val lastFmUsername = lastFmViewModel?.username?.collectAsStateWithLifecycle()?.value ?: com.codetrio.spatialflow.data.lastfm.LastFmPrefs.getUsername(context)
    val lastFmAvatar = lastFmViewModel?.avatarUrl?.collectAsStateWithLifecycle()?.value ?: com.codetrio.spatialflow.data.lastfm.LastFmPrefs.getAvatarUrl(context)
    val lastFmScrobbleCount = lastFmViewModel?.scrobbleCount?.collectAsStateWithLifecycle()?.value ?: com.codetrio.spatialflow.data.lastfm.LastFmPrefs.getScrobbleCount(context)

    var isLiveEnabled by remember { mutableStateOf(SpatialFlowLiveManager.isPresenceEnabled(context)) }
    val liveStatus by SpatialFlowLiveManager.liveStatus.collectAsStateWithLifecycle()
    var showLiveBottomSheet by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = "Account & Sync",
        onBack = { navController.popBackStack() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsHeader("Connected Accounts")
            SettingsGroupCard(listOf(
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isYtLoggedIn && !ytAvatarUrl.isNullOrEmpty()) {
                            SpatialImage(
                                model = ytAvatarUrl,
                                contentDescription = "YouTube Avatar",
                                size = ImageSemanticSize.AvatarSmall,
                                shape = CircleShape,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_youtube_music),
                                        contentDescription = "YouTube Music",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isYtLoggedIn) ytName else "YouTube Music (Not Linked)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isYtLoggedIn) (ytProfile?.email ?: "YouTube Library & History Synced") else "Tap to sign in to YouTube Music",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isYtLoggedIn) {
                            FilledTonalButton(
                                onClick = {
                                    onYtCookiesChange(null)
                                    accountViewModel?.logout()
                                }
                            ) {
                                Text("Log Out")
                            }
                        } else {
                            Button(
                                onClick = {
                                    navController.navigate("google_signin")
                                }
                            ) {
                                Text("Log In")
                            }
                        }
                    }
                },
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isSpotifyConnected && !spotifyAvatarUrl.isNullOrEmpty()) {
                            SpatialImage(
                                model = spotifyAvatarUrl,
                                contentDescription = "Spotify Avatar",
                                size = ImageSemanticSize.AvatarSmall,
                                shape = CircleShape,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_spotify_logo),
                                        contentDescription = "Spotify Logo",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSpotifyConnected) spotifyName else "Spotify (Not Linked)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isSpotifyConnected) "Spotify Library & Playlists Synced" else "Tap to sign in to Spotify",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSpotifyConnected) {
                            FilledTonalButton(
                                onClick = {
                                    spotifyViewModel?.logout()
                                    SpotifyPrefs.clearAll(context)
                                }
                            ) {
                                Text("Log Out")
                            }
                        } else {
                            Button(
                                onClick = {
                                    navController.navigate("spotify_signin")
                                }
                            ) {
                                Text("Log In")
                            }
                        }
                    }
                },
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (lastFmLoggedIn && !lastFmAvatar.isNullOrEmpty()) {
                            SpatialImage(
                                model = lastFmAvatar,
                                contentDescription = "Last.fm Avatar",
                                size = ImageSemanticSize.AvatarSmall,
                                shape = CircleShape,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_lastfm),
                                        contentDescription = "Last.fm",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            Text(
                                text = if (lastFmLoggedIn) lastFmUsername else "Last.fm (Not Linked)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (lastFmLoggedIn) {
                                    if (lastFmScrobbleCount > 0) "$lastFmScrobbleCount scrobbles • Tap for settings" else "Scrobbler Active • Tap for settings"
                                } else {
                                    "Tap to connect Last.fm scrobbler"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (lastFmLoggedIn) {
                            FilledTonalButton(
                                onClick = { navController.navigate("lastfm_settings") }
                            ) {
                                Text("Settings")
                            }
                        } else {
                            Button(
                                onClick = { navController.navigate("lastfm_signin") }
                            ) {
                                Text("Log In")
                            }
                        }
                    }
                }
            ))

            SettingsHeader("Data Saving")
            SettingsGroupCard(listOf(
                { DataSaverRow(dataSaver, onDataSaverChange) }
            ))

            SettingsHeader("Sync & Privacy")
            SettingsGroupCard(listOf(
                {
                    SpatialFlowLivePrivacyRow(
                        checked = isLiveEnabled,
                        onToggle = { enabled ->
                            isLiveEnabled = enabled
                            SpatialFlowLiveManager.setPresenceEnabled(context, enabled)
                        }
                    )
                },
                {
                    SpatialFlowLiveStatusRow(
                        status = liveStatus,
                        onClick = { showLiveBottomSheet = true }
                    )
                },
                { PauseHistoryRow(pauseHistory, onPauseHistoryChange) },
                { ManualSyncRow() }
            ))
        }
    }

    if (showLiveBottomSheet) {
        SpatialFlowLiveBottomSheet(
            onDismissRequest = { showLiveBottomSheet = false }
        )
    }
}

@Composable
fun DataSaverRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Data Saver (Wi-Fi Only)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Restrict streaming and high-res cover art downloads to Wi-Fi only",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun PauseHistoryRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Pause Listening History",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Stop tracking played songs for recommendations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ManualSyncRow() {
    var isSyncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ListItem(
        onClick = {
            if (!isSyncing) {
                isSyncing = true
                scope.launch {
                    delay(2000)
                    isSyncing = false
                }
            }
        },
        content = {
            Column {
                Text(text = "Manual Sync", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Sync playlists and favorites with server", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync Now",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SpatialFlowLivePrivacyRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Sensors,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        content = {
            Column {
                Text(
                    text = "SpatialFlow Live Presence",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Share anonymous playback heartbeat with the global listener community",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SpatialFlowLiveStatusRow(status: LivePresenceStatus, onClick: () -> Unit) {
    val (statusIcon, statusColor, statusText) = when (status) {
        LivePresenceStatus.ACTIVE -> Triple(
            Icons.Rounded.GraphicEq,
            MaterialTheme.colorScheme.primary,
            "Active - Heartbeat sending - Tap to view"
        )
        LivePresenceStatus.IDLE -> Triple(
            Icons.Rounded.Headphones,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Idle - Playback paused - Tap to view"
        )
        LivePresenceStatus.DISABLED -> Triple(
            Icons.Rounded.SensorsOff,
            MaterialTheme.colorScheme.outline,
            "Disabled - Tap to configure"
        )
    }

    ListItem(
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
        },
        content = {
            Column {
                Text(
                    text = "Live Community Status",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = "View Live Community",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
