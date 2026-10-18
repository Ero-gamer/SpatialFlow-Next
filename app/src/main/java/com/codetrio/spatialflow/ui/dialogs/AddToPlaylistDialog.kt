package com.codetrio.spatialflow.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.codetrio.spatialflow.ui.theme.concentricInner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.db.PlaylistEntity
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage

enum class PlaylistDestinationTab { LOCAL, YOUTUBE }

/**
 * Universal Modal Bottom Sheet for adding songs to Local & YouTube Music playlists,
 * including options to Rename and Delete existing playlists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddToPlaylistBottomSheet(
    song: SongItem? = null,
    songs: List<SongItem>? = null,
    songsFromCurrentIndex: List<SongItem>? = null,
    localPlaylists: List<PlaylistEntity>,
    onlinePlaylists: List<OnlinePlaylist>,
    isLoggedIn: Boolean,
    onCreateNewLocal: () -> Unit = {},
    onCreateNewOnline: () -> Unit = {},
    onSelectLocalPlaylist: (PlaylistEntity) -> Unit = {},
    onSelectOnlinePlaylist: (OnlinePlaylist) -> Unit = {},
    onCreateNewLocalWithSongs: ((List<SongItem>) -> Unit)? = null,
    onCreateNewOnlineWithSongs: ((List<SongItem>) -> Unit)? = null,
    onSelectLocalPlaylistWithSongs: ((PlaylistEntity, List<SongItem>) -> Unit)? = null,
    onSelectOnlinePlaylistWithSongs: ((OnlinePlaylist, List<SongItem>) -> Unit)? = null,
    onDeleteLocalPlaylist: ((PlaylistEntity) -> Unit)? = null,
    onRenameLocalPlaylist: ((PlaylistEntity, String) -> Unit)? = null,
    onDeleteOnlinePlaylist: ((OnlinePlaylist) -> Unit)? = null,
    onRenameOnlinePlaylist: ((OnlinePlaylist, String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Local, 1 = YouTube Music
    var scopeIndex by remember { mutableIntStateOf(0) } // 0 = All Queue, 1 = From Current
    var playlistToRenameLocal by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToRenameOnline by remember { mutableStateOf<OnlinePlaylist?>(null) }

    val allQueueSongs = remember(song, songs) { songs ?: listOfNotNull(song) }
    val effectiveSongs = remember(allQueueSongs, songsFromCurrentIndex, scopeIndex) {
        if (scopeIndex == 1 && songsFromCurrentIndex != null) songsFromCurrentIndex else allQueueSongs
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (songs != null && songs.size > 1) "Save Queue to Playlist" else "Add to Playlist",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "${effectiveSongs.size} tracks selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val haptic = LocalHapticFeedback.current

            if (songsFromCurrentIndex != null && songs != null && songs.size > 1 && songsFromCurrentIndex.size != songs.size) {
                ButtonGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    expandedRatio = 0.25f,
                    overflowIndicator = {}
                ) {
                    val scope = this
                    val scopeOptions = listOf(
                        "All Queue (${songs.size})" to 0,
                        "From Current (${songsFromCurrentIndex.size})" to 1
                    )
                    scopeOptions.forEach { (label, idx) ->
                        val checked = scopeIndex == idx
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 6.dp else if (checked) 20.dp else 10.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "ScopeCorner"
                                )

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scopeIndex = idx
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(44.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium)
                                }
                            },
                            menuContent = {}
                        )
                    }
                }
            }

            if (isLoggedIn) {
                ButtonGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    expandedRatio = 0.25f,
                    overflowIndicator = {}
                ) {
                    val scope = this
                    val tabOptions = listOf(
                        Triple(0, R.drawable.ic_library_music, "Local"),
                        Triple(1, R.drawable.ic_youtube_music, "YT Music")
                    )
                    tabOptions.forEach { (tabIdx, iconRes, label) ->
                        val checked = selectedTab == tabIdx
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 6.dp else if (checked) 20.dp else 10.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "TabCorner"
                                )

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedTab = tabIdx
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(46.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (tabIdx == 1) Color.Unspecified else if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            },
                            menuContent = {}
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                AnimatedContent(
                    targetState = if (selectedTab == 0 || !isLoggedIn) PlaylistDestinationTab.LOCAL else PlaylistDestinationTab.YOUTUBE,
                    label = "PlaylistTabTransition"
                ) { tab ->
                    if (tab == PlaylistDestinationTab.LOCAL) {
                        LocalPlaylistList(
                            playlists = localPlaylists,
                            onCreateNew = {
                                if (onCreateNewLocalWithSongs != null) {
                                    onCreateNewLocalWithSongs(effectiveSongs)
                                } else {
                                    onCreateNewLocal()
                                }
                            },
                            onPlaylistSelected = { playlist ->
                                if (onSelectLocalPlaylistWithSongs != null) {
                                    onSelectLocalPlaylistWithSongs(playlist, effectiveSongs)
                                } else {
                                    onSelectLocalPlaylist(playlist)
                                }
                            },
                            onDelete = onDeleteLocalPlaylist,
                            onRename = { playlist -> playlistToRenameLocal = playlist }
                        )
                    } else {
                        OnlinePlaylistList(
                            playlists = onlinePlaylists,
                            onCreateNew = {
                                if (onCreateNewOnlineWithSongs != null) {
                                    onCreateNewOnlineWithSongs(effectiveSongs)
                                } else {
                                    onCreateNewOnline()
                                }
                            },
                            onPlaylistSelected = { playlist ->
                                if (onSelectOnlinePlaylistWithSongs != null) {
                                    onSelectOnlinePlaylistWithSongs(playlist, effectiveSongs)
                                } else {
                                    onSelectOnlinePlaylist(playlist)
                                }
                            },
                            onDelete = onDeleteOnlinePlaylist,
                            onRename = { playlist -> playlistToRenameOnline = playlist }
                        )
                    }
                }
            }
        }
    }

    playlistToRenameLocal?.let { playlist ->
        RenamePlaylistDialog(
            initialTitle = playlist.title,
            isOnline = false,
            onConfirm = { newName ->
                onRenameLocalPlaylist?.invoke(playlist, newName)
                playlistToRenameLocal = null
            },
            onDismiss = { playlistToRenameLocal = null }
        )
    }

    playlistToRenameOnline?.let { playlist ->
        RenamePlaylistDialog(
            initialTitle = playlist.title,
            isOnline = true,
            onConfirm = { newName ->
                onRenameOnlinePlaylist?.invoke(playlist, newName)
                playlistToRenameOnline = null
            },
            onDismiss = { playlistToRenameOnline = null }
        )
    }
}

/**
 * Backward compatible alias mapping AddToPlaylistDialog callers to AddToPlaylistBottomSheet.
 */
@Composable
fun AddToPlaylistDialog(
    song: SongItem?,
    localPlaylists: List<PlaylistEntity>,
    onlinePlaylists: List<OnlinePlaylist>,
    isLoggedIn: Boolean,
    onCreateNewLocal: () -> Unit,
    onCreateNewOnline: () -> Unit,
    onSelectLocalPlaylist: (PlaylistEntity) -> Unit,
    onSelectOnlinePlaylist: (OnlinePlaylist) -> Unit,
    onDeleteLocalPlaylist: ((PlaylistEntity) -> Unit)? = null,
    onRenameLocalPlaylist: ((PlaylistEntity, String) -> Unit)? = null,
    onDeleteOnlinePlaylist: ((OnlinePlaylist) -> Unit)? = null,
    onRenameOnlinePlaylist: ((OnlinePlaylist, String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AddToPlaylistBottomSheet(
        song = song,
        localPlaylists = localPlaylists,
        onlinePlaylists = onlinePlaylists,
        isLoggedIn = isLoggedIn,
        onCreateNewLocal = onCreateNewLocal,
        onCreateNewOnline = onCreateNewOnline,
        onSelectLocalPlaylist = onSelectLocalPlaylist,
        onSelectOnlinePlaylist = onSelectOnlinePlaylist,
        onDeleteLocalPlaylist = onDeleteLocalPlaylist,
        onRenameLocalPlaylist = onRenameLocalPlaylist,
        onDeleteOnlinePlaylist = onDeleteOnlinePlaylist,
        onRenameOnlinePlaylist = onRenameOnlinePlaylist,
        onDismiss = onDismiss
    )
}

@Composable
private fun LocalPlaylistList(
    playlists: List<PlaylistEntity>,
    onCreateNew: () -> Unit,
    onPlaylistSelected: (PlaylistEntity) -> Unit,
    onDelete: ((PlaylistEntity) -> Unit)? = null,
    onRename: ((PlaylistEntity) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onCreateNew,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Create Local Playlist",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No local playlists found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(playlists, key = { idx, playlist -> "pl_${idx}_${playlist.id}" }) { _, playlist ->
                    var menuExpanded by remember { mutableStateOf(false) }

                    Surface(
                        onClick = { onPlaylistSelected(playlist) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val innerCorner = 14.dp.concentricInner(10.dp)
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(innerCorner),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_library_music),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                playlist.title,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            if (onDelete != null || onRename != null) {
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .width(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                                            . clickable { menuExpanded = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        onRename?.let {
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    it(playlist)
                                                }
                                            )
                                        }
                                        onDelete?.let {
                                            DropdownMenuItem(
                                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    menuExpanded = false
                                                    it(playlist)
                                                }
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
    }
}

@Composable
private fun OnlinePlaylistList(
    playlists: List<OnlinePlaylist>,
    onCreateNew: () -> Unit,
    onPlaylistSelected: (OnlinePlaylist) -> Unit,
    onDelete: ((OnlinePlaylist) -> Unit)? = null,
    onRename: ((OnlinePlaylist) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onCreateNew,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_youtube_music),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Create YT Music Playlist",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No YouTube Music playlists found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(playlists, key = { idx, playlist -> "yt_pl_${idx}_${playlist.playlistId}" }) { _, playlist ->
                    var menuExpanded by remember { mutableStateOf(false) }

                    Surface(
                        onClick = { onPlaylistSelected(playlist) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val innerCorner = 14.dp.concentricInner(10.dp)
                            if (!playlist.thumbnailUrl.isNullOrEmpty()) {
                                SpatialImage(
                                    model = playlist.thumbnailUrl,
                                    contentDescription = null,
                                    size = ImageSemanticSize.AvatarSmall,
                                    shape = RoundedCornerShape(innerCorner),
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(innerCorner),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_youtube_music),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    playlist.title,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                playlist.songCount?.let { count ->
                                    Text(
                                        count,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (onDelete != null || onRename != null) {
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .width(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                                            .clickable { menuExpanded = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        onRename?.let {
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    it(playlist)
                                                }
                                            )
                                        }
                                        onDelete?.let {
                                            DropdownMenuItem(
                                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    menuExpanded = false
                                                    it(playlist)
                                                }
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
    }
}

@Composable
fun CreatePlaylistDialog(
    isOnline: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOnline) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_youtube_music),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(Modifier.width(10.dp))
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_library_music),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    if (isOnline) "New YouTube Music Playlist" else "New Local Playlist",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenamePlaylistDialog(
    initialTitle: String,
    isOnline: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isOnline) "Rename YouTube Playlist" else "Rename Local Playlist",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && name != initialTitle) {
                        onConfirm(name)
                    }
                },
                enabled = name.isNotBlank() && name != initialTitle
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
