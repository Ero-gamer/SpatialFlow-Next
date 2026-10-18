@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS",
    "TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS"
)

package com.codetrio.spatialflow.ui.library

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.codetrio.spatialflow.ui.theme.concentricInner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.db.PlaylistEntity
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.model.toSongItem
import com.codetrio.spatialflow.ui.SongActionsBottomSheet
import com.codetrio.spatialflow.ui.dialogs.AddToPlaylistDialog
import com.codetrio.spatialflow.ui.dialogs.CreatePlaylistDialog
import com.codetrio.spatialflow.ui.explore.AccountScreen
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.OnlineSongBottomSheet
import com.codetrio.spatialflow.ui.explore.SongCreditsBottomSheet
import com.codetrio.spatialflow.ui.explore.SongCreditsScreen
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.ui.library.recap.RecapShareSheet
import com.codetrio.spatialflow.ui.library.recap.RecapStoryDialog
import com.codetrio.spatialflow.ui.library.recap.RecapThemeEngine
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.codetrio.spatialflow.ui.spotify.SpotifyLibraryTab
import com.codetrio.spatialflow.viewmodel.AccountViewModel
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.codetrio.spatialflow.viewmodel.SpotifyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder {
    A_Z,
    DATE_ADDED,
    ARTIST,
    DURATION
}

@Stable
sealed class DeviceSongEntry {
    @Stable
    data class Header(val title: String) : DeviceSongEntry()
    @Stable
    data class Song(
        val song: SongItem,
        val localIndex: Int,
        val localCount: Int
    ) : DeviceSongEntry()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: PlayerSharedViewModel,
    navController: NavController,
    spotifyViewModel: SpotifyViewModel,
    onEditSong: (SongItem) -> Unit = {},
    onNavigateToExplore: () -> Unit = {}
) {
    val context = LocalContext.current
    val mainActivity = remember(context) { getActivityFromContext(context) as? MainActivity }
    val fragmentActivity = remember(context) { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }
    val fragmentManager = remember(fragmentActivity) { fragmentActivity?.supportFragmentManager }

    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val accountViewModel = remember(fragmentActivity) {
        fragmentActivity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(fragmentActivity) {
        fragmentActivity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val userProfile by accountViewModel?.userProfile?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val isLoggedIn = remember(userProfile) { com.codetrio.spatialflow.data.innertube.AccountManager.isLoggedIn(context) }

    val appPrefs = remember { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
    val defaultTab = remember { appPrefs.getString("default_library_tab", "") ?: "" }

    val availableTabs = remember(isLoggedIn) {
        if (isLoggedIn) {
            listOf(
                "Playlists",
                "Spotify",
                "Recap",
                "Podcasts",
                "Songs",
                "Albums",
                "Artists",
                "Device Files"
            )
        } else {
            listOf(
                "Playlists",
                "Spotify",
                "Recap",
                "Device Files"
            )
        }
    }

    val initialTab = remember(isLoggedIn, defaultTab) {
        if (defaultTab.isNotEmpty() && availableTabs.contains(defaultTab)) defaultTab else ""
    }

    var activeTab by remember(isLoggedIn, initialTab) { mutableStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.A_Z) }
    var isRefreshing by remember { mutableStateOf(false) }

    var showAccountScreen by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }

    val history by accountViewModel?.history?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val currentOnlineSong by exploreViewModel?.currentOnlineSong?.collectAsStateWithLifecycle(null) ?: remember { mutableStateOf(null) }
    val isLoadingStream by exploreViewModel?.isLoadingStream?.collectAsStateWithLifecycle(false) ?: remember { mutableStateOf(false) }

    val localSongs by viewModel.localSongs.collectAsStateWithLifecycle(emptyList())
    val favoriteIds by viewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()

    var selectedSongForMenu by remember { mutableStateOf<OnlineSong?>(null) }
    var songToAddPlaylist by remember { mutableStateOf<SongItem?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCreditsForSong by remember { mutableStateOf<OnlineSong?>(null) }
    val localPlaylists by viewModel.localPlaylistsFlow.collectAsStateWithLifecycle(emptyList())

    // Trigger local files scan on first composition
    LaunchedEffect(Unit) {
        if (localSongs.isEmpty()) {
            scanLocalFiles(context, viewModel)
        }
    }

    val subscriptionChanged by exploreViewModel?.subscriptionChanged?.collectAsStateWithLifecycle(false) ?: remember { mutableStateOf(false) }
    LaunchedEffect(subscriptionChanged) {
        if (subscriptionChanged) {
            accountViewModel?.loadLibrary()
            exploreViewModel?.consumeSubscriptionChanged()
        }
    }

    val nestedScrollConnection = remember(mainActivity) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (mainActivity != null) {
                    if (consumed.y < -10f) {
                        mainActivity.hideBottomNavWithAnimation()
                    } else if (consumed.y > 10f) {
                        mainActivity.showBottomNavWithAnimation()
                    }
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    Scaffold(
        // snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, // Replaced by global SnackbarController
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Row (Hides in Device Files)
                AnimatedVisibility(
                    visible = activeTab != "Device Files",
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = { showHistoryScreen = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_history),
                                    contentDescription = "Listening History",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            val activeAvatarUrl = if (!userProfile?.avatarUrl.isNullOrEmpty()) {
                                userProfile?.avatarUrl
                            } else {
                                com.codetrio.spatialflow.data.spotify.SpotifyPrefs.getAccountAvatarUrl(LocalContext.current)
                            }

                            if (!activeAvatarUrl.isNullOrEmpty()) {
                                SpatialImage(
                                    model = activeAvatarUrl,
                                    contentDescription = "Account Settings",
                                    size = ImageSemanticSize.AvatarSmall,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable { showAccountScreen = true },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                IconButton(onClick = { showAccountScreen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Account Settings",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Horizontal scroll chips row
            val tabs = availableTabs

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val spotifyAvatarUrlState = com.codetrio.spatialflow.data.spotify.SpotifyPrefs.getAccountAvatarUrl(LocalContext.current)
                val isSpotifyConnected = com.codetrio.spatialflow.data.spotify.SpotifyPrefs.isConnected(LocalContext.current)

                tabs.forEach { tabName ->
                    val isSelected = activeTab == tabName
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            activeTab = if (isSelected) "" else tabName
                        },
                        label = { Text(tabName, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = if (tabName == "Spotify") {
                            {
                                if (isSpotifyConnected && !spotifyAvatarUrlState.isNullOrEmpty()) {
                                    SpatialImage(
                                        model = spotifyAvatarUrlState,
                                        contentDescription = "Spotify Avatar",
                                        size = ImageSemanticSize.AvatarSmall,
                                        shape = CircleShape,
                                        modifier = Modifier.size(20.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_spotify_logo),
                                        contentDescription = "Spotify Logo",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF1DB954)
                                    )
                                }
                            }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
            ) {
                when (activeTab) {
                    "Playlists" -> PlaylistsTabContent(nestedScrollConnection, viewModel, isLoggedIn)
                    "Podcasts" -> PodcastsTabContent(nestedScrollConnection)
                    "Songs" -> SongsTabContent(nestedScrollConnection, viewModel)
                    "Albums" -> AlbumsTabContent(nestedScrollConnection)
                    "Artists" -> ArtistsTabContent(nestedScrollConnection)
                    "Recap" -> RecapTabContent(nestedScrollConnection, viewModel, onNavigateToExplore)
                    "Spotify" -> SpotifyLibraryTab(
                        navController = navController,
                        spotifyViewModel = spotifyViewModel,
                        playerViewModel = viewModel,
                    )
                    "Device Files" -> {
                        val onRefreshAction = {
                            scope.launch {
                                isRefreshing = true
                                scanLocalFiles(context, viewModel)
                                isRefreshing = false
                            }
                        }

                        val pullToRefreshState = rememberPullToRefreshState()
                        var deviceSubTab by remember { mutableStateOf("Songs") } // "Songs", "Albums", "Artists", "Folders"
                        var isGridView by remember { mutableStateOf(false) }
                        var sortAscending by remember { mutableStateOf(true) }
                        var deviceSortKey by remember { mutableStateOf("A_Z") }
                        var selectedGroupFilter by remember { mutableStateOf<Pair<String, List<SongItem>>?>(null) }
                        var showSortMenu by remember { mutableStateOf(false) }
                        var storageFilter by remember { mutableStateOf("PHONE") } // "PHONE", "SD_CARD"

                        // Intercept system back button / back gesture when drilled-down into an Album, Artist, or Folder
                        BackHandler(enabled = selectedGroupFilter != null) {
                            selectedGroupFilter = null
                        }

                        val hasSdCard = remember(context, localSongs) {
                            val externalDirs = androidx.core.content.ContextCompat.getExternalFilesDirs(context, null)
                            val hasRemovableVolume = externalDirs.any { dir ->
                                dir != null && android.os.Environment.isExternalStorageRemovable(dir)
                            }
                            val hasSdSongs = localSongs.any { song ->
                                val p = song.path ?: song.data ?: ""
                                p.startsWith("/storage/") && !p.startsWith("/storage/emulated")
                            }
                            hasRemovableVolume || hasSdSongs
                        }

                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { onRefreshAction() },
                            state = pullToRefreshState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Search Bar
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search local ${deviceSubTab.lowercase()}...") },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Clear, null)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = CircleShape,
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )

                                // Category Sub-Tabs Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedGroupFilter != null) {
                                        FilterChip(
                                            selected = true,
                                            onClick = { selectedGroupFilter = null },
                                            label = { Text("← ${selectedGroupFilter!!.first}") },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        )
                                    } else {
                                        listOf("Songs", "Albums", "Artists", "Folders").forEach { subTab ->
                                            val isSelected = deviceSubTab == subTab
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { deviceSubTab = subTab },
                                                label = { Text(subTab.uppercase(), fontWeight = FontWeight.Bold) },
                                                shape = RoundedCornerShape(20.dp),
                                                border = null,
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        }
                                    }
                                }

                                // Sub-Bar Action Controls: Shuffle Button (Left) + Leading/Trailing Action Pill (Right)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Shuffle Button (Left)
                                    Surface(
                                        onClick = {
                                            val shuffled = localSongs.shuffled()
                                            if (shuffled.isNotEmpty()) {
                                                viewModel.setSongList(shuffled)
                                                viewModel.playSong(shuffled.first())
                                            }
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Shuffle,
                                                contentDescription = "Shuffle",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Shuffle",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    // Right Segmented Action Buttons (Leading: Storage Switch, Trailing: Tune/Sort Sheet)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Leading Button: Storage Switch (Phone / SD Card)
                                            IconButton(
                                                onClick = {
                                                    if (hasSdCard) {
                                                        storageFilter = if (storageFilter == "PHONE") "SD_CARD" else "PHONE"
                                                        val count = localSongs.count { s ->
                                                            val p = s.path ?: s.data ?: ""
                                                            if (storageFilter == "SD_CARD") p.startsWith("/storage/") && !p.startsWith("/storage/emulated")
                                                            else !p.startsWith("/storage/") || p.startsWith("/storage/emulated")
                                                        }
                                                        val storageName = if (storageFilter == "SD_CARD") "SD Card" else "Internal Phone"
                                                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Switched to $storageName Storage ($count songs)")
                                                    } else {
                                                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("No SD Card detected on this device")
                                                    }
                                                },
                                                enabled = hasSdCard,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                val icon = if (storageFilter == "SD_CARD") Icons.Rounded.SdCard else Icons.Rounded.Smartphone
                                                val tintColor = if (!hasSdCard) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                                else if (storageFilter == "SD_CARD") MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant

                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = if (storageFilter == "SD_CARD") "SD Card Storage" else "Internal Phone Storage",
                                                    tint = tintColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // Trailing Button: Sort & View Options
                                            IconButton(
                                                onClick = { showSortMenu = true },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Tune,
                                                    contentDescription = "Sort and View Options",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (showSortMenu) {
                                    DeviceFilesSortBottomSheet(
                                        deviceSubTab = deviceSubTab,
                                        deviceSortKey = deviceSortKey,
                                        sortAscending = sortAscending,
                                        isGridView = isGridView,
                                        onSortKeySelected = { key -> deviceSortKey = key },
                                        onSortAscendingToggle = { sortAscending = !sortAscending },
                                        onViewToggle = { grid -> isGridView = grid },
                                        onDismiss = { showSortMenu = false }
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Filter local songs by Storage Location & Search Query
                                val storageFilteredSongs = remember(localSongs, storageFilter, hasSdCard) {
                                    if (!hasSdCard) localSongs
                                    else when (storageFilter) {
                                        "SD_CARD" -> localSongs.filter { song ->
                                            val p = song.path ?: song.data ?: ""
                                            p.startsWith("/storage/") && !p.startsWith("/storage/emulated")
                                        }
                                        "PHONE" -> localSongs.filter { song ->
                                            val p = song.path ?: song.data ?: ""
                                            !p.startsWith("/storage/") || p.startsWith("/storage/emulated")
                                        }
                                        else -> localSongs
                                    }
                                }

                                val filteredSongs = remember(storageFilteredSongs, searchQuery) {
                                    if (searchQuery.isBlank()) storageFilteredSongs
                                    else storageFilteredSongs.filter {
                                        it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true)
                                    }
                                }

                                if (selectedGroupFilter != null) {
                                    // Render Drilled-down songs list for selected Album/Artist/Folder
                                    val groupSongs = selectedGroupFilter!!.second
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize().weight(1f),
                                        contentPadding = PaddingValues(bottom = 160.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        itemsIndexed(groupSongs, key = { idx, song -> "grp_song_${idx}_${song.id}" }) { idx, song ->
                                            val isPlaying = currentSong?.id == song.id
                                            val isFav = favoriteIds.contains(song.id)
                                            SongListItem(
                                                song = song,
                                                isPlaying = isPlaying,
                                                isFavorite = isFav,
                                                localIndex = idx,
                                                localCount = groupSongs.size,
                                                onClick = {
                                                    viewModel.setSongList(groupSongs)
                                                    viewModel.playSong(song)
                                                },
                                                onLongClick = {
                                                    val bs = SongActionsBottomSheet.newInstance(song)
                                                    fragmentManager?.let { bs.show(it, "SongActionsBottomSheet") }
                                                }
                                            )
                                        }
                                    }
                                } else if (deviceSubTab == "Songs") {
                                    // ── SONGS SUB-TAB ──────────────────────────────────────
                                    val sortedSongs = remember(filteredSongs, deviceSortKey, sortAscending) {
                                        val list = when (deviceSortKey) {
                                            "DATE_ADDED" -> filteredSongs.sortedByDescending { it.dateAdded }
                                            "ARTIST" -> filteredSongs.sortedBy { it.artist.lowercase() }
                                            "DURATION" -> filteredSongs.sortedByDescending { it.duration }
                                            else -> filteredSongs.sortedBy { it.title.lowercase() }
                                        }
                                        if (sortAscending) list else list.reversed()
                                    }

                                    if (sortedSongs.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                                            Text(if (searchQuery.isNotEmpty()) "No matching songs found" else "No local songs found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else if (isGridView) {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(2),
                                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxSize().weight(1f)
                                        ) {
                                            items(sortedSongs.size, key = { idx -> "sp_grid_${idx}_${sortedSongs[idx].id}" }) { idx ->
                                                val song = sortedSongs[idx]
                                                DeviceSongGridCard(
                                                    song = song,
                                                    isPlaying = currentSong?.id == song.id,
                                                    onClick = {
                                                        viewModel.setSongList(sortedSongs)
                                                        viewModel.playSong(song)
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize().weight(1f),
                                            contentPadding = PaddingValues(bottom = 160.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            itemsIndexed(sortedSongs, key = { idx, song -> "sp_lst_${idx}_${song.id}" }) { idx, song ->
                                                SongListItem(
                                                    song = song,
                                                    isPlaying = currentSong?.id == song.id,
                                                    isFavorite = favoriteIds.contains(song.id),
                                                    localIndex = idx,
                                                    localCount = sortedSongs.size,
                                                    onClick = {
                                                        viewModel.setSongList(sortedSongs)
                                                        viewModel.playSong(song)
                                                    },
                                                    onLongClick = {
                                                        val bs = SongActionsBottomSheet.newInstance(song)
                                                        fragmentManager?.let { bs.show(it, "SongActionsBottomSheet") }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // ── ALBUMS / ARTISTS / FOLDERS SUB-TABS ─────────────────
                                    val groupedItems = remember(filteredSongs, deviceSubTab, deviceSortKey, sortAscending) {
                                        val map = LinkedHashMap<String, MutableList<SongItem>>()
                                        when (deviceSubTab) {
                                            "Albums" -> {
                                                for (s in filteredSongs) {
                                                    val key = s.albumName?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                                                        ?: "Unknown Album"
                                                    map.getOrPut(key) { mutableListOf() }.add(s)
                                                }
                                            }
                                            "Artists" -> {
                                                for (s in filteredSongs) {
                                                    val key = s.artist.ifBlank { "Unknown Artist" }
                                                    map.getOrPut(key) { mutableListOf() }.add(s)
                                                }
                                            }
                                            else -> { // Folders
                                                for (s in filteredSongs) {
                                                    val path = s.path ?: s.data ?: ""
                                                    val parent = if (path.contains("/")) path.substringBeforeLast("/") else "Storage"
                                                    val key = if (parent.contains("/")) parent.substringAfterLast("/") else parent
                                                    map.getOrPut(key.ifBlank { "Storage" }) { mutableListOf() }.add(s)
                                                }
                                            }
                                        }

                                        val list = map.map { (name, songs) ->
                                            val firstSong = songs.firstOrNull()
                                            val art = firstSong?.getAlbumArtUri()
                                            val subtitle = "${songs.size} ${if (songs.size == 1) "song" else "songs"}"
                                            DeviceGroupItem(name, subtitle, songs, art, isCircle = deviceSubTab == "Artists")
                                        }

                                        val sortedList = when (deviceSortKey) {
                                            "COUNT" -> list.sortedByDescending { it.songs.size }
                                            "ARTIST" -> list.sortedBy { it.songs.firstOrNull()?.artist?.lowercase() ?: "" }
                                            "PATH" -> list.sortedBy { it.title.lowercase() }
                                            else -> list.sortedBy { it.title.lowercase() }
                                        }
                                        if (sortAscending) sortedList else sortedList.reversed()
                                    }

                                    if (groupedItems.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                                            Text("No ${deviceSubTab.lowercase()} found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else if (isGridView) {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(2),
                                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp),
                                            modifier = Modifier.fillMaxSize().weight(1f)
                                        ) {
                                            items(groupedItems.size, key = { idx -> "grp_grid_${idx}_${groupedItems[idx].title}" }) { idx ->
                                                val item = groupedItems[idx]
                                                DeviceGroupGridCard(
                                                    item = item,
                                                    onClick = {
                                                        selectedGroupFilter = Pair(item.title, item.songs)
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize().weight(1f),
                                            contentPadding = PaddingValues(bottom = 160.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            items(groupedItems.size, key = { idx -> "grp_lst_${idx}_${groupedItems[idx].title}" }) { idx ->
                                                val item = groupedItems[idx]
                                                ListItem(
                                                    headlineContent = {
                                                        Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    },
                                                    supportingContent = {
                                                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    },
                                                    leadingContent = {
                                                        if (item.artModel != null) {
                                                            SpatialImage(
                                                                model = item.artModel,
                                                                contentDescription = null,
                                                                size = ImageSemanticSize.ThumbnailSmall,
                                                                shape = if (item.isCircle) CircleShape else RoundedCornerShape(10.dp),
                                                                modifier = Modifier.size(48.dp),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Box(
                                                                modifier = Modifier.size(48.dp).clip(if (item.isCircle) CircleShape else RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (deviceSubTab == "Folders") Icons.Rounded.Folder else if (deviceSubTab == "Artists") Icons.Rounded.Person else Icons.Rounded.Album,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    },
                                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            selectedGroupFilter = Pair(item.title, item.songs)
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "" -> UnifiedLibraryContent(
                        nestedScrollConnection = nestedScrollConnection,
                        isLoggedIn = isLoggedIn,
                        onSignInClick = { mainActivity?.navigateToGoogleSignIn() },
                        viewModel = viewModel
                    )
                }
            }
        }

        // Overlay AccountScreen
        AnimatedVisibility(
            visible = showAccountScreen && accountViewModel != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (accountViewModel != null) {
                AccountScreen(
                    viewModel = accountViewModel,
                    spotifyViewModel = spotifyViewModel,
                    onBack = { showAccountScreen = false },
                    onSongClick = { song, queue, index ->
                        val songItems = queue.map { os ->
                            SongItem.createOnlineSong(
                                os.videoId,
                                os.title,
                                os.artist,
                                "",
                                os.durationMs,
                                os.thumbnailUrl,
                                os.artistId
                            )
                        }
                        val playlistQueue = com.codetrio.spatialflow.player.queue.LocalPlaylistQueue(
                            playlistTitle = "Account",
                            songs = songItems,
                            startIndex = index.coerceIn(0, (songItems.size - 1).coerceAtLeast(0))
                        )
                        viewModel.setQueue(playlistQueue, index)
                    },
                    onNavigateToSignIn = {
                        showAccountScreen = false
                        mainActivity?.navigateToGoogleSignIn()
                    },
                    onNavigateToSpotifySignIn = {
                        showAccountScreen = false
                        mainActivity?.navigateToSpotifySignIn()
                    }
                )
            }
        }

        // Overlay HistoryScreen
        AnimatedVisibility(
            visible = showHistoryScreen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            HistoryScreen(
                history = history,
                currentOnlineSong = currentOnlineSong,
                isLoadingStream = isLoadingStream,
                isRefreshing = accountViewModel?.isLoading?.collectAsStateWithLifecycle()?.value == true,
                onRefresh = { accountViewModel?.loadLibrary() },
                onBack = { showHistoryScreen = false },
                onSongClick = { song, queue, index ->
                    val songItems = queue.map { os ->
                        SongItem.createOnlineSong(
                            os.videoId,
                            os.title,
                            os.artist,
                            "",
                            os.durationMs,
                            os.thumbnailUrl,
                            os.artistId
                        )
                    }
                    val playlistQueue = com.codetrio.spatialflow.player.queue.LocalPlaylistQueue(
                        playlistTitle = "History",
                        songs = songItems,
                        startIndex = index.coerceIn(0, (songItems.size - 1).coerceAtLeast(0))
                    )
                    viewModel.setQueue(playlistQueue, index)
                },
                onSongMenuClick = { song ->
                    selectedSongForMenu = song
                }
            )
        }

        // Song 3-Dot Menu Bottom Sheet
        selectedSongForMenu?.let { song ->
            val isPinned = exploreViewModel?.uiState?.collectAsState()?.value?.pinnedSpeedDialIds?.contains(song.videoId) == true
            if (exploreViewModel != null) {
                OnlineSongBottomSheet(
                    song = song,
                    isPinned = isPinned,
                    onDismissRequest = { selectedSongForMenu = null },
                    playerSharedViewModel = viewModel,
                    exploreViewModel = exploreViewModel,
                    onPlaylistAddClick = { onlineSongItem ->
                        songToAddPlaylist = onlineSongItem
                        showAddToPlaylistDialog = true
                    },
                    onViewCreditsClick = { s ->
                        showCreditsForSong = s
                    }
                )
            }
        }

        var onlinePlaylists by remember { mutableStateOf<List<OnlinePlaylist>>(emptyList()) }
        val isLoggedIn = remember(context) { com.codetrio.spatialflow.data.innertube.AccountManager.isLoggedIn(context) }
        var createIsOnline by remember { mutableStateOf(false) }

        LaunchedEffect(isLoggedIn, showAddToPlaylistDialog) {
            if (isLoggedIn && showAddToPlaylistDialog) {
                withContext(Dispatchers.IO) {
                    com.codetrio.spatialflow.data.innertube.YouTubeMusic.libraryPlaylists().onSuccess { playlists ->
                        onlinePlaylists = playlists
                    }
                }
            }
        }

        if (showAddToPlaylistDialog && songToAddPlaylist != null) {
            AddToPlaylistDialog(
                song = songToAddPlaylist,
                localPlaylists = localPlaylists,
                onlinePlaylists = onlinePlaylists,
                isLoggedIn = isLoggedIn,
                onCreateNewLocal = {
                    createIsOnline = false
                    showCreatePlaylistDialog = true
                    showAddToPlaylistDialog = false
                },
                onCreateNewOnline = {
                    createIsOnline = true
                    showCreatePlaylistDialog = true
                    showAddToPlaylistDialog = false
                },
                onSelectLocalPlaylist = { playlist ->
                    viewModel.addSongToLocalPlaylist(playlist.id, songToAddPlaylist!!)
                    showAddToPlaylistDialog = false
                    songToAddPlaylist = null
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Added to playlist: ${playlist.title}")
                },
                onSelectOnlinePlaylist = { onlinePlaylist ->
                    val s = songToAddPlaylist
                    if (s != null) {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Adding to YouTube Music...")
                        viewModel.addSongToYouTubePlaylist(onlinePlaylist.playlistId, s) { success, msg ->
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                        }
                    }
                    showAddToPlaylistDialog = false
                    songToAddPlaylist = null
                },
                onDeleteLocalPlaylist = { playlist ->
                    viewModel.deleteLocalPlaylist(playlist.id)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Deleted playlist '${playlist.title}'")
                },
                onRenameLocalPlaylist = { playlist, newName ->
                    viewModel.renameLocalPlaylist(playlist.id, newName)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Renamed playlist to '$newName'")
                },
                onDeleteOnlinePlaylist = { onlinePlaylist ->
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Deleting YouTube playlist...")
                    viewModel.deleteYouTubePlaylist(onlinePlaylist.playlistId) { success, msg ->
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                        if (success) {
                            onlinePlaylists = onlinePlaylists.filter { it.playlistId != onlinePlaylist.playlistId }
                        }
                    }
                },
                onRenameOnlinePlaylist = { onlinePlaylist, newName ->
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Renaming YouTube playlist...")
                    viewModel.renameYouTubePlaylist(onlinePlaylist.playlistId, newName) { success, msg ->
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                    }
                },
                onDismiss = {
                    showAddToPlaylistDialog = false
                    songToAddPlaylist = null
                }
            )
        }

        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                isOnline = createIsOnline,
                onConfirm = { name ->
                    if (!createIsOnline) {
                        viewModel.createLocalPlaylist(name)
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Created local playlist '$name'")
                    } else {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Creating YouTube Music playlist...")
                        viewModel.createYouTubePlaylist(name, songToAddPlaylist) { success, _ ->
                            if (success) {
                                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Created YouTube playlist '$name'")
                            } else {
                                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to create YouTube playlist")
                            }
                        }
                    }
                    showCreatePlaylistDialog = false
                    showAddToPlaylistDialog = true
                },
                onDismiss = {
                    showCreatePlaylistDialog = false
                    showAddToPlaylistDialog = true
                }
            )
        }

        // Song Credits Bottom Sheet
        showCreditsForSong?.let { song ->
            SongCreditsBottomSheet(
                song = song,
                onDismissRequest = { showCreditsForSong = null }
            )
        }
    }
}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongListItem(
    song: SongItem,
    isPlaying: Boolean,
    isFavorite: Boolean,
    localIndex: Int,
    localCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shapes = ListItemDefaults.segmentedShapes(index = localIndex, count = localCount)
    val defaultBg = MaterialTheme.colorScheme.surfaceContainer
    val playingBg = MaterialTheme.colorScheme.primaryContainer

    val contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary

    ListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = shapes,
        colors = ListItemDefaults.colors(
            containerColor = if (isPlaying) playingBg else defaultBg,
            headlineColor = contentColor,
            supportingColor = subTextColor,
            leadingIconColor = accentColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp),
        leadingContent = {
            val artModel = remember(song.id, song.path) { song.getAlbumArtUri() ?: R.drawable.default_album_art }
            SpatialImage(
                model = artModel,
                contentDescription = "Album Art",
                size = ImageSemanticSize.ThumbnailSmall,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(54.dp),
                contentScale = ContentScale.Crop
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Now Playing",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        content = {
            Column {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (song.isExplicit) {
                        com.codetrio.spatialflow.ui.components.ExplicitBadge(modifier = Modifier.padding(end = 6.dp))
                    }
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = subTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    )
}

@Composable
private fun UnifiedLibraryContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    isLoggedIn: Boolean,
    onSignInClick: () -> Unit,
    viewModel: PlayerSharedViewModel
) {
    if (!isLoggedIn) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Access YouTube Music",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sign in to access your online playlists, saved songs, listening history, and personalized recommendations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onSignInClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Sign In", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }
    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlinePlaylists by accountViewModel?.playlists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlineAlbums by accountViewModel?.albums?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlineArtists by accountViewModel?.artists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlinePodcasts by accountViewModel?.podcasts?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val onlineSongs by accountViewModel?.songs?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    var longPressedOnlinePlaylist by remember { mutableStateOf<OnlinePlaylist?>(null) }
    var playlistToRenameOnline by remember { mutableStateOf<OnlinePlaylist?>(null) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    longPressedOnlinePlaylist?.let { playlist ->
        var onlineSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
        LaunchedEffect(playlist.playlistId) {
            viewModel.fetchYouTubePlaylistSongs(playlist.playlistId) { loadedSongs: List<SongItem> ->
                onlineSongs = loadedSongs
            }
        }

        com.codetrio.spatialflow.ui.dialogs.PlaylistOptionsBottomSheet(
            title = playlist.title,
            author = playlist.author?.name ?: "YouTube Music",
            trackCountText = playlist.songCount ?: "${onlineSongs.size} tracks",
            onPlayNext = { viewModel.addSongsToQueueNext(onlineSongs) },
            onShufflePlay = { viewModel.playSongsShuffled(onlineSongs) },
            onStartMix = {
                val videoId = onlineSongs.firstOrNull()?.videoId
                if (!videoId.isNullOrEmpty()) {
                    exploreViewModel?.startRadio(videoId)
                    navigateToExplore(activity)
                } else {
                    viewModel.playSongsShuffled(onlineSongs)
                }
            },
            onEditPlaylist = { playlistToRenameOnline = playlist },
            onAddToQueue = { viewModel.addSongsToQueueEnd(onlineSongs) },
            onDownload = {
                com.codetrio.spatialflow.util.SongDownloader.downloadPlaylist(
                    context = context,
                    playlistTitle = playlist.title,
                    songs = onlineSongs,
                    format = "OPUS"
                )
            },
            onDeletePlaylist = {
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Deleting YouTube playlist...")
                viewModel.deleteYouTubePlaylist(playlist.playlistId) { success: Boolean, msg: String ->
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                }
            },
            onDismiss = { longPressedOnlinePlaylist = null }
        )
    }

    playlistToRenameOnline?.let { playlist ->
        com.codetrio.spatialflow.ui.dialogs.RenamePlaylistDialog(
            initialTitle = playlist.title,
            isOnline = true,
            onConfirm = { newTitle ->
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Renaming YouTube playlist...")
                viewModel.renameYouTubePlaylist(playlist.playlistId, newTitle) { success: Boolean, msg: String ->
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                }
                playlistToRenameOnline = null
            },
            onDismiss = { playlistToRenameOnline = null }
        )
    }

    val unifiedItems = remember(onlinePlaylists, onlineAlbums, onlineArtists, onlinePodcasts, onlineSongs) {
        val list = mutableListOf<UnifiedLibraryItem>()

        if (onlineSongs.isNotEmpty()) {
            list.add(object : UnifiedLibraryItem {
                override val title = "Liked Songs"
                override val thumbnailUrl = onlineSongs.firstOrNull()?.thumbnailUrl
                override val subtitle = "Auto playlist • ${onlineSongs.size} songs"
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadPlaylist("LM")
                    navigateToExplore(activity)
                }
                override val isCircle = false
            })
        }

        onlinePlaylists.forEach { playlist ->
            list.add(object : UnifiedLibraryItem {
                override val title = playlist.title
                override val thumbnailUrl = playlist.thumbnailUrl
                override val subtitle = "Playlist" + (if (playlist.songCount?.isNotEmpty() == true) " • ${playlist.songCount}" else "")
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadPlaylist(playlist.playlistId)
                    navigateToExplore(activity)
                }
                override val onLongClick = {
                    longPressedOnlinePlaylist = playlist
                }
                override val isCircle = false
            })
        }

        onlineAlbums.forEach { album ->
            list.add(object : UnifiedLibraryItem {
                override val title = album.title
                override val thumbnailUrl = album.thumbnailUrl
                override val subtitle = "Album" + (if (album.artists.isNotEmpty()) " • ${album.artists.firstOrNull()?.name}" else "")
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadAlbum(album.browseId)
                    navigateToExplore(activity)
                }
                override val isCircle = false
            })
        }

        onlineArtists.forEach { artist ->
            list.add(object : UnifiedLibraryItem {
                override val title = artist.title
                override val thumbnailUrl = artist.thumbnailUrl
                override val subtitle = "Artist"
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadArtist(artist.browseId, artist.thumbnailUrl)
                    navigateToExplore(activity)
                }
                override val isCircle = true
            })
        }

        onlinePodcasts.forEach { podcast ->
            list.add(object : UnifiedLibraryItem {
                override val title = podcast.title
                override val thumbnailUrl = podcast.thumbnailUrl
                override val subtitle = "Podcast"
                override val onClick = {
                    exploreViewModel?.cameFromLibrary = true
                    exploreViewModel?.loadPlaylist(podcast.playlistId)
                    navigateToExplore(activity)
                }
                override val isCircle = false
            })
        }

        list.sortBy { it.title.lowercase() }
        list
    }

    if (unifiedItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No items found in your library", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            items(unifiedItems) { item ->
                UnifiedLibraryCard(item)
            }
        }
    }
}

private interface UnifiedLibraryItem {
    val title: String
    val thumbnailUrl: String?
    val subtitle: String
    val onClick: () -> Unit
    val onLongClick: (() -> Unit)? get() = null
    val isCircle: Boolean
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnifiedLibraryCard(item: UnifiedLibraryItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { item.onClick() },
                onLongClick = item.onLongClick
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Concentric Corner: Outer (16.dp) - Padding (8.dp) = 8.dp
        val innerCorner = 16.dp.concentricInner(8.dp)
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(if (item.isCircle) CircleShape else RoundedCornerShape(innerCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (item.thumbnailUrl?.isNotEmpty() == true) {
                SpatialImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    size = ImageSemanticSize.ThumbnailMedium,
                    shape = if (item.isCircle) CircleShape else RoundedCornerShape(innerCorner),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isCircle) Icons.Default.Person else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            item.title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            item.subtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistsTabContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    viewModel: PlayerSharedViewModel,
    isLoggedIn: Boolean
) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlinePlaylists by accountViewModel?.playlists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val localPlaylists by viewModel.localPlaylistsFlow.collectAsStateWithLifecycle(emptyList())

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedLocalPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var longPressedLocalPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var longPressedOnlinePlaylist by remember { mutableStateOf<OnlinePlaylist?>(null) }
    var playlistToRenameLocal by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToRenameOnline by remember { mutableStateOf<OnlinePlaylist?>(null) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            accountViewModel?.refreshAll()
        }
    }

    longPressedLocalPlaylist?.let { playlist ->
        val songsEntity by viewModel.getSongsForLocalPlaylist(playlist.id).collectAsStateWithLifecycle(emptyList())
        val songs = remember(songsEntity) { songsEntity.map { it.toSongItem() } }

        com.codetrio.spatialflow.ui.dialogs.PlaylistOptionsBottomSheet(
            title = playlist.title,
            author = "Local Playlist",
            trackCountText = "${songs.size} tracks",
            onPlayNext = { viewModel.addSongsToQueueNext(songs) },
            onShufflePlay = { viewModel.playSongsShuffled(songs) },
            onStartMix = { viewModel.playSongsShuffled(songs) },
            onEditPlaylist = { playlistToRenameLocal = playlist },
            onAddToQueue = { viewModel.addSongsToQueueEnd(songs) },
            onDownload = {
                com.codetrio.spatialflow.util.SongDownloader.downloadPlaylist(
                    context = context,
                    playlistTitle = playlist.title,
                    songs = songs,
                    format = "OPUS"
                )
            },
            onDeletePlaylist = {
                viewModel.deleteLocalPlaylist(playlist.id)
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Deleted playlist '${playlist.title}'")
            },
            onDismiss = { longPressedLocalPlaylist = null }
        )
    }

    longPressedOnlinePlaylist?.let { playlist ->
        var onlineSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
        LaunchedEffect(playlist.playlistId) {
            viewModel.fetchYouTubePlaylistSongs(playlist.playlistId) { loadedSongs: List<SongItem> ->
                onlineSongs = loadedSongs
            }
        }

        com.codetrio.spatialflow.ui.dialogs.PlaylistOptionsBottomSheet(
            title = playlist.title,
            author = playlist.author?.name ?: "YouTube Music",
            trackCountText = playlist.songCount ?: "${onlineSongs.size} tracks",
            onPlayNext = { viewModel.addSongsToQueueNext(onlineSongs) },
            onShufflePlay = { viewModel.playSongsShuffled(onlineSongs) },
            onStartMix = {
                val videoId = onlineSongs.firstOrNull()?.videoId
                if (!videoId.isNullOrEmpty()) {
                    exploreViewModel?.startRadio(videoId)
                    navigateToExplore(activity)
                } else {
                    viewModel.playSongsShuffled(onlineSongs)
                }
            },
            onEditPlaylist = { playlistToRenameOnline = playlist },
            onAddToQueue = { viewModel.addSongsToQueueEnd(onlineSongs) },
            onDownload = {
                com.codetrio.spatialflow.util.SongDownloader.downloadPlaylist(
                    context = context,
                    playlistTitle = playlist.title,
                    songs = onlineSongs,
                    format = "OPUS"
                )
            },
            onDeletePlaylist = {
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Deleting YouTube playlist...")
                viewModel.deleteYouTubePlaylist(playlist.playlistId) { success: Boolean, msg: String ->
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                }
            },
            onDismiss = { longPressedOnlinePlaylist = null }
        )
    }

    playlistToRenameLocal?.let { playlist ->
        com.codetrio.spatialflow.ui.dialogs.RenamePlaylistDialog(
            initialTitle = playlist.title,
            isOnline = false,
            onConfirm = { newTitle ->
                viewModel.renameLocalPlaylist(playlist.id, newTitle)
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Renamed playlist to '$newTitle'")
                playlistToRenameLocal = null
            },
            onDismiss = { playlistToRenameLocal = null }
        )
    }

    playlistToRenameOnline?.let { playlist ->
        com.codetrio.spatialflow.ui.dialogs.RenamePlaylistDialog(
            initialTitle = playlist.title,
            isOnline = true,
            onConfirm = { newTitle ->
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Renaming YouTube playlist...")
                viewModel.renameYouTubePlaylist(playlist.playlistId, newTitle) { success: Boolean, msg: String ->
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                }
                playlistToRenameOnline = null
            },
            onDismiss = { playlistToRenameOnline = null }
        )
    }

    if (showCreatePlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.createLocalPlaylist(name)
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedLocalPlaylist?.let { playlist ->
        LocalPlaylistDetailsBottomSheet(
            playlist = playlist,
            viewModel = viewModel,
            onDismiss = { selectedLocalPlaylist = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // My Playlists Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "My Playlists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = { showCreatePlaylistDialog = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create Playlist",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Local Playlists horizontal list
        if (localPlaylists.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clickable { showCreatePlaylistDialog = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "+ Create a playlist to get started",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(localPlaylists) { playlist ->
                    Column(
                        modifier = Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { selectedLocalPlaylist = playlist },
                                onLongClick = { longPressedLocalPlaylist = playlist }
                            )
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            playlist.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (isLoggedIn) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "YT Music Playlists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (onlinePlaylists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No online playlists found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection)
                ) {
                    items(onlinePlaylists) { playlist ->
                        OnlinePlaylistCard(
                            playlist = playlist,
                            onClick = {
                                exploreViewModel?.cameFromLibrary = true
                                exploreViewModel?.loadPlaylist(playlist.playlistId)
                                navigateToExplore(activity)
                            },
                            onLongClick = {
                                longPressedOnlinePlaylist = playlist
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalPlaylistDetailsBottomSheet(
    playlist: PlaylistEntity,
    viewModel: PlayerSharedViewModel,
    onDismiss: () -> Unit
) {
    val songsEntity by viewModel.getSongsForLocalPlaylist(playlist.id).collectAsStateWithLifecycle(emptyList())
    val songs = remember(songsEntity) { songsEntity.map { it.toSongItem() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${songs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = LocalContext.current
                    // Share playlist button
                    IconButton(
                        onClick = {
                            val shareText = buildString {
                                appendLine("Playlist: ${playlist.title}")
                                if (songs.isNotEmpty()) {
                                    appendLine()
                                    appendLine("Tracks (${songs.size}):")
                                    songs.take(15).forEachIndexed { i, s ->
                                        appendLine("${i + 1}. ${s.title} - ${s.artist}")
                                    }
                                    if (songs.size > 15) {
                                        appendLine("...and ${songs.size - 15} more tracks")
                                    }
                                }
                                appendLine()
                                appendLine("Shared from SpatialFlow")
                            }
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Playlist")
                            context.startActivity(shareIntent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share Playlist",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Delete entire playlist button
                    IconButton(
                        onClick = {
                            viewModel.deleteLocalPlaylist(playlist.id)
                            onDismiss()
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Playlist",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons (Play & Shuffle)
            if (songs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.setSongList(songs)
                            if (songs.isNotEmpty()) {
                                viewModel.playSong(songs.first())
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play All")
                    }

                    OutlinedButton(
                        onClick = {
                            val shuffled = songs.shuffled()
                            viewModel.setSongList(shuffled)
                            if (shuffled.isNotEmpty()) {
                                viewModel.playSong(shuffled.first())
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shuffle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Songs List
            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "This playlist is empty",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(songs) { song ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSongList(songs)
                                        viewModel.playSong(song)
                                        onDismiss()
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mini Thumbnail with Concentric Corner: Outer Surface (12.dp) - Padding (8.dp) = 4.dp
                                val miniCorner = 12.dp.concentricInner(8.dp)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(miniCorner))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (song.thumbnailUrl?.isNotEmpty() == true) {
                                        SpatialImage(
                                            model = song.thumbnailUrl,
                                            contentDescription = null,
                                            size = ImageSemanticSize.ThumbnailSmall,
                                            shape = RoundedCornerShape(miniCorner),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Delete single song from playlist button
                                IconButton(
                                    onClick = {
                                        viewModel.removeSongFromLocalPlaylist(playlist.id, song.videoId ?: song.id.toString())
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove song",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnlinePlaylistCard(
    playlist: OnlinePlaylist,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (playlist.thumbnailUrl?.isNotEmpty() == true) {
                    SpatialImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = null,
                        size = ImageSemanticSize.ThumbnailMedium,
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    playlist.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    playlist.songCount ?: "Playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PodcastsTabContent(nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlinePodcasts by accountViewModel?.podcasts?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Your Subscribed Podcasts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlinePodcasts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Podcasts,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .alpha(0.6f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No podcasts found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(onlinePodcasts) { podcast ->
                    OnlinePlaylistCard(podcast, onClick = {
                        exploreViewModel?.cameFromLibrary = true
                        exploreViewModel?.loadPlaylist(podcast.playlistId)
                        navigateToExplore(activity)
                    })
                }
            }
        }
    }
}

@Composable
private fun SongsTabContent(nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection, viewModel: PlayerSharedViewModel) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }

    val onlineSongs by accountViewModel?.songs?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Liked Songs (YT Music)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlineSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No online songs found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(onlineSongs) { song ->
                    ListItem(
                        headlineContent = { Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            if (song.thumbnailUrl?.isNotEmpty() == true) {
                                SpatialImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = null,
                                    size = ImageSemanticSize.ThumbnailSmall,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(48.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(48.dp))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val list = onlineSongs.map { s ->
                                    SongItem.createOnlineSong(
                                        s.videoId,
                                        s.title,
                                        s.artist,
                                        "",
                                        s.durationMs,
                                        s.thumbnailUrl,
                                        s.artistId
                                    )
                                }
                                viewModel.setSongList(list)
                                val idx = list.indexOfFirst { it.videoId == song.videoId }
                                if (idx != -1) {
                                    viewModel.playSongAtIndex(idx)
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsTabContent(nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlineAlbums by accountViewModel?.albums?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "YT Music Albums",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlineAlbums.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No online albums found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(onlineAlbums) { album ->
                    OnlineAlbumCard(album, onClick = {
                        exploreViewModel?.cameFromLibrary = true
                        exploreViewModel?.loadAlbum(album.browseId)
                        navigateToExplore(activity)
                    })
                }
            }
        }
    }
}

@Composable
private fun OnlineAlbumCard(
    album: OnlineAlbum,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (album.thumbnailUrl?.isNotEmpty() == true) {
                    SpatialImage(
                        model = album.thumbnailUrl,
                        contentDescription = null,
                        size = ImageSemanticSize.ThumbnailMedium,
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Album,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    album.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    album.artists.firstOrNull()?.name ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as? androidx.fragment.app.FragmentActivity }

    val accountViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[AccountViewModel::class.java] }
    }
    val exploreViewModel = remember(activity) {
        activity?.let { ViewModelProvider(it)[ExploreViewModel::class.java] }
    }

    val onlineArtists by accountViewModel?.artists?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(Unit) {
        accountViewModel?.refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Followed Artists (YT Music)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlineArtists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No online artists found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection)
            ) {
                items(onlineArtists) { artist ->
                    OnlineArtistCard(artist, onClick = {
                        exploreViewModel?.cameFromLibrary = true
                        exploreViewModel?.loadArtist(artist.browseId, artist.thumbnailUrl)
                        navigateToExplore(activity)
                    })
                }
            }
        }
    }
}

@Composable
private fun OnlineArtistCard(
    artist: OnlineArtist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (artist.thumbnailUrl?.isNotEmpty() == true) {
                SpatialImage(
                    model = artist.thumbnailUrl,
                    contentDescription = null,
                    size = ImageSemanticSize.ThumbnailMedium,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            artist.title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Artist",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecapTabContent(
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    viewModel: PlayerSharedViewModel,
    onNavigateToExplore: () -> Unit
) {
    val context = LocalContext.current
    val recap by viewModel.listeningRecapFlow.collectAsStateWithLifecycle(null)
    val isRecapLoading by viewModel.isRecapLoading.collectAsStateWithLifecycle()
    val selectedTimeRange by viewModel.recapTimeRange.collectAsStateWithLifecycle()

    var showStoryDialog by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    if (recap == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Time-Range Selector Row
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                PlayerSharedViewModel.RecapTimeRange.entries.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = selectedTimeRange == range,
                        onClick = { viewModel.setRecapTimeRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PlayerSharedViewModel.RecapTimeRange.entries.size)
                    ) {
                        Text(range.label)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isRecapLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LoadingIndicator()
                        Text(
                            text = "Analyzing your listening flow...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Crunching highlights for ${selectedTimeRange.label.lowercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "No Stats",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Your Flow is warming up",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "No listening history for ${selectedTimeRange.label.lowercase()}. Start playing your favorite tracks, and your flow highlights will appear here!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }
    } else {
        val data = recap ?: return

        LaunchedEffect(data.topArtists) {
            data.topArtists.forEach { item ->
                if (!item.thumbnailUrl.isNullOrEmpty() && viewModel.artistProfileMap[item.artist] == null) {
                    viewModel.artistProfileMap[item.artist] = item.thumbnailUrl
                }
                viewModel.resolveArtistProfileImage(item.artist)
            }
        }

        val heroSong = remember(data.topSongs) { data.topSongs.firstOrNull() }
        val heroArtist = remember(data.topArtists) { data.topArtists.firstOrNull() }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            // Time Range Filter Row
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    PlayerSharedViewModel.RecapTimeRange.entries.forEachIndexed { index, range ->
                        SegmentedButton(
                            selected = selectedTimeRange == range,
                            onClick = { viewModel.setRecapTimeRange(range) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = PlayerSharedViewModel.RecapTimeRange.entries.size)
                        ) {
                            Text(range.label)
                        }
                    }
                }
            }

            // Solid Recap Story Launch Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${selectedTimeRange.label.uppercase()} RECAP",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_applogo),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "SpatialFlow",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Your Music Flow is Ready",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Play your personalized story to reveal your full listening statistics, moods, and passport.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Quick Teaser Stats Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = viewModel.formatListeningTime(data.totalMinutes),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Stream Time",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (heroArtist != null) {
                                val heroArtistImg = viewModel.artistProfileMap[heroArtist.artist]
                                    ?: heroArtist.thumbnailUrl
                                    ?: data.topSongs.firstOrNull {
                                        it.artist.equals(heroArtist.artist, ignoreCase = true) ||
                                        it.artist.contains(heroArtist.artist, ignoreCase = true) ||
                                        heroArtist.artist.contains(it.artist, ignoreCase = true)
                                    }?.thumbnailUrl

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!heroArtistImg.isNullOrEmpty()) {
                                                SpatialImage(
                                                    model = heroArtistImg,
                                                    contentDescription = "Top Artist",
                                                    size = ImageSemanticSize.AvatarSmall,
                                                    shape = CircleShape,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = heroArtist.artist,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Top Artist",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Action Button: Only button to show Recap
                        Button(
                            onClick = { showStoryDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Show Recap",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Top Songs Quick Shelf
            if (data.topSongs.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Played Songs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp)
                    )
                }

                items(data.topSongs.take(5)) { song ->
                    val rank = data.topSongs.indexOf(song) + 1
                    ListItem(
                        headlineContent = {
                            Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "$rank",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(20.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!song.thumbnailUrl.isNullOrEmpty()) {
                                        SpatialImage(
                                            model = song.thumbnailUrl,
                                            contentDescription = "Cover",
                                            size = ImageSemanticSize.ThumbnailSmall,
                                            shape = RoundedCornerShape(8.dp),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.MusicNote, null)
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            Text(
                                text = "${song.count} plays",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val songItem = if (song.songId.toLongOrNull() != null && !song.thumbnailUrl.isNullOrEmpty() && !song.thumbnailUrl.startsWith("http")) {
                                    SongItem(
                                        song.songId.toLong(),
                                        song.title,
                                        song.artist,
                                        -1L,
                                        song.thumbnailUrl,
                                        0L,
                                        System.currentTimeMillis()
                                    )
                                } else {
                                    SongItem.createOnlineSong(
                                        videoId = song.songId,
                                        title = song.title,
                                        artist = song.artist,
                                        streamUrl = null,
                                        durationMs = 0L,
                                        thumbnailUrl = song.thumbnailUrl
                                    )
                                }
                                viewModel.playSong(songItem)
                            }
                    )
                }
            }

            // Top Artists Quick Shelf
            if (data.topArtists.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Artists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp)
                    )
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(data.topArtists) { item ->
                            val imageUrl = viewModel.artistProfileMap[item.artist]
                                ?: item.thumbnailUrl
                                ?: data.topSongs.firstOrNull {
                                    it.artist.equals(item.artist, ignoreCase = true) ||
                                    it.artist.contains(item.artist, ignoreCase = true) ||
                                    item.artist.contains(it.artist, ignoreCase = true)
                                }?.thumbnailUrl
                            Column(
                                modifier = Modifier
                                    .width(88.dp)
                                    .clickable { onNavigateToExplore() },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!imageUrl.isNullOrEmpty()) {
                                        SpatialImage(
                                            model = imageUrl,
                                            contentDescription = "Artist picture",
                                            size = ImageSemanticSize.ThumbnailSmall,
                                            shape = CircleShape,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, null, modifier = Modifier.size(32.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "${item.count} plays",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showStoryDialog) {
            RecapStoryDialog(
                recap = data,
                timeRangeLabel = selectedTimeRange.label,
                viewModel = viewModel,
                onDismissRequest = { showStoryDialog = false }
            )
        }

        if (showShareSheet) {
            RecapShareSheet(
                recap = data,
                timeRangeLabel = selectedTimeRange.label,
                viewModel = viewModel,
                onDismissRequest = { showShareSheet = false }
            )
        }
    }
}

// Background scanner helper
private suspend fun scanLocalFiles(context: Context, viewModel: PlayerSharedViewModel) {
    viewModel.rescanLocalFiles()
}

private fun getActivityFromContext(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun navigateToExplore(activity: Activity?) {
    (activity as? MainActivity)?.showArtistPage(null, null)
}

// ─── Device Files Helpers & Segmented View Toggle ──────────────────────────────────────

private data class DeviceGroupItem(
    val title: String,
    val subtitle: String,
    val songs: List<SongItem>,
    val artModel: Any?,
    val isCircle: Boolean = false
)

@Composable
private fun SegmentedViewToggle(
    isGridView: Boolean,
    onViewToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        val toggleCorner = 18.dp.concentricInner(2.dp)
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(toggleCorner))
                    .background(if (!isGridView) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onViewToggle(false) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FormatListBulleted,
                    contentDescription = "List View",
                    tint = if (!isGridView) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(toggleCorner))
                    .background(if (isGridView) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onViewToggle(true) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GridView,
                    contentDescription = "Grid View",
                    tint = if (isGridView) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceSongGridCard(
    song: SongItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val artModel = remember(song.id, song.path) { song.getAlbumArtUri() ?: R.drawable.default_album_art }
            SpatialImage(
                model = artModel,
                contentDescription = null,
                size = ImageSemanticSize.ThumbnailMedium,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Gradient Overlay at the bottom half of the cover art (Matching Screenshot 1!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 120f
                        )
                    )
            )

            // Text overlay at bottom left
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            if (isPlaying) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Playing",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp).size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceGroupGridCard(
    item: DeviceGroupItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.artModel != null) {
                SpatialImage(
                    model = item.artModel,
                    contentDescription = null,
                    size = ImageSemanticSize.ThumbnailMedium,
                    shape = if (item.isCircle) CircleShape else RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isCircle) Icons.Rounded.Person else Icons.Rounded.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Dark gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 120f
                        )
                    )
            )

            // Text overlay at bottom left (Matching Screenshot 1!)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = item.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

// ─── Device Files "Sort by" Modal Bottom Sheet (Matching Screenshot 2!) ──────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceFilesSortBottomSheet(
    deviceSubTab: String,
    deviceSortKey: String,
    sortAscending: Boolean,
    isGridView: Boolean,
    onSortKeySelected: (String) -> Unit,
    onSortAscendingToggle: () -> Unit,
    onViewToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            // Order Toggle Button (Ascending / Descending)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSortAscendingToggle() },
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (sortAscending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Order",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (sortAscending) "Ascending" else "Descending",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Sort options radio list
            val sortOptions = when (deviceSubTab) {
                "Songs" -> listOf("A_Z" to "Title", "ARTIST" to "Artist", "DATE_ADDED" to "Date Added", "DURATION" to "Duration")
                "Albums" -> listOf("A_Z" to "Title", "ARTIST" to "Artist", "COUNT" to "Song Count")
                "Artists" -> listOf("A_Z" to "Artist Name", "COUNT" to "Song Count")
                else -> listOf("A_Z" to "Title", "COUNT" to "Song Count", "PATH" to "Folder Path")
            }

            sortOptions.forEach { (key, label) ->
                val isSelected = deviceSortKey == key
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSortKeySelected(key) },
                    color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSortKeySelected(key) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // View Section
            Text(
                text = "View",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onViewToggle(true) },
                    color = if (isGridView) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = null,
                            tint = if (isGridView) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Grid",
                            fontWeight = FontWeight.Bold,
                            color = if (isGridView) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onViewToggle(false) },
                    color = if (!isGridView) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FormatListBulleted,
                            contentDescription = null,
                            tint = if (!isGridView) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "List",
                            fontWeight = FontWeight.Bold,
                            color = if (!isGridView) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

