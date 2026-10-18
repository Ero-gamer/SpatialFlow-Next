@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.ui.SettingsViewModel
import com.codetrio.spatialflow.util.SongDownloader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreScreen(
    navController: NavController,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
    val discoveredBackup by viewModel.discoveredBackupPrompt.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val autoBackupFreq by viewModel.autoBackupFreq.collectAsStateWithLifecycle()

    var showExportSheet by remember { mutableStateOf(false) }
    var showAutoBackupSheet by remember { mutableStateOf(false) }
    var inspectedSummary by remember { mutableStateOf<SettingsViewModel.BackupSummaryData?>(null) }
    var missingSongsList by remember { mutableStateOf<List<SongItem>?>(null) }
    var totalManifestCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.scanLocalBackups(context)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            viewModel.inspectBackupFile(context, fileUri) { summary, err ->
                if (summary != null) {
                    inspectedSummary = summary
                } else {
                    SnackbarController.showMessage(err ?: "Invalid backup file")
                }
            }
        }
    }

    if (showExportSheet) {
        ExportOptionsBottomSheet(
            onDismiss = { showExportSheet = false },
            onConfirmExport = { incSet, incFav, incPlay, incHist, incDown ->
                showExportSheet = false
                viewModel.exportBackupDirectToDefaultFolder(
                    context = context,
                    includeSettings = incSet,
                    includeFavorites = incFav,
                    includePlaylists = incPlay,
                    includeHistory = incHist,
                    includeDownloadsManifest = incDown
                ) { success, msg, _ ->
                    SnackbarController.showMessage(msg)
                }
            }
        )
    }

    if (showAutoBackupSheet) {
        AutoBackupSettingsBottomSheet(
            enabled = autoBackupEnabled,
            currentFreq = autoBackupFreq,
            onDismiss = { showAutoBackupSheet = false },
            onToggleEnable = { viewModel.setAutoBackupEnabled(it) },
            onSelectFreq = { viewModel.setAutoBackupFreq(it) }
        )
    }

    if (inspectedSummary != null) {
        BackupPreviewBottomSheet(
            summary = inspectedSummary!!,
            onDismiss = { inspectedSummary = null },
            onConfirmRestore = { mode, rSet, rFav, rPlay, rHist, rDown ->
                val summaryToRestore = inspectedSummary!!
                inspectedSummary = null
                viewModel.importBackup(
                    context = context,
                    summary = summaryToRestore,
                    restoreMode = mode,
                    restoreSettings = rSet,
                    restoreFavorites = rFav,
                    restorePlaylists = rPlay,
                    restoreHistory = rHist,
                    restoreDownloadsManifest = rDown
                ) { success, msg, manifestSongs ->
                    SnackbarController.showMessage(msg)
                    if (success && manifestSongs.isNotEmpty()) {
                        val missing = manifestSongs.filter { song ->
                            !SongDownloader.isSongDownloaded(context, song)
                        }
                        if (missing.isNotEmpty()) {
                            missingSongsList = missing
                            totalManifestCount = manifestSongs.size
                        }
                    }
                }
            }
        )
    }

    if (missingSongsList != null) {
        MissingSongsRecoveryBottomSheet(
            missingSongs = missingSongsList!!,
            totalManifestSongsCount = totalManifestCount,
            onDismiss = { missingSongsList = null },
            onConfirmDownload = {
                val songsToDownload = missingSongsList!!
                missingSongsList = null
                songsToDownload.forEach { song ->
                    SongDownloader.downloadSong(context, song)
                }
                SnackbarController.showMessage("Queued ${songsToDownload.size} missing track downloads")
            }
        )
    }

    SettingsScaffold(
        title = "Backup & Restore",
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
            if (discoveredBackup != null) {
                val backup = discoveredBackup!!
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SettingsBackupRestore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Existing Backup Discovered",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val formattedDate = remember(backup.timestamp) {
                            SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(
                                Date(backup.timestamp)
                            )
                        }
                        Text(
                            text = "Found ${backup.fileName} ($formattedDate) with ${backup.playlistsCount} playlists, ${backup.favoritesCount} favorites, and ${backup.settingsCount} settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { viewModel.dismissDiscoveredBackupPrompt() }
                            ) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { inspectedSummary = backup },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Review & Restore")
                            }
                        }
                    }
                }
            }

            SettingsHeader("Backup Actions")
            SettingsGroupCard(buildList {
                add {
                    ListItem(
                        onClick = { showExportSheet = true },
                        content = {
                            Column {
                                Text("Create Local Backup", style = MaterialTheme.typography.bodyLarge)
                                Text("Save JSON backup directly to Downloads/SpatialFlow/Backups/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                add {
                    ListItem(
                        onClick = { showAutoBackupSheet = true },
                        content = {
                            Column {
                                Text("Auto-Backup Schedule", style = MaterialTheme.typography.bodyLarge)
                                Text(if (autoBackupEnabled) "Active: ${autoBackupFreq.replaceFirstChar { it.uppercase() }}" else "Disabled", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Update, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                add {
                    ListItem(
                        onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                        content = {
                            Column {
                                Text("Import External File", style = MaterialTheme.typography.bodyLarge)
                                Text("Pick an external JSON backup file to inspect & restore", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.SettingsBackupRestore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            })

            SettingsHeader("Available Local Backups (${localBackups.size})")
            if (localBackups.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No local backup files found in Downloads or Documents", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                SettingsGroupCard(buildList {
                    localBackups.forEach { summary ->
                        add {
                            val dateFormatted = remember(summary.timestamp) {
                                if (summary.timestamp > 0) {
                                    SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(summary.timestamp))
                                } else "Unknown Date"
                            }
                            val sizeFormatted = remember(summary.fileSizeBytes) {
                                val kb = summary.fileSizeBytes / 1024.0
                                if (kb >= 1024) String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
                                else String.format(Locale.getDefault(), "%.0f KB", kb)
                            }

                            ListItem(
                                onClick = { inspectedSummary = summary },
                                content = {
                                    Column {
                                        Text(summary.fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        Text("$dateFormatted · $sizeFormatted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${summary.playlistsCount} Playlists | ${summary.favoritesCount} Favorites | ${summary.historyCount} History Events", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                leadingContent = {
                                    Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/json"
                                                putExtra(Intent.EXTRA_STREAM, summary.uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Backup File"))
                                        }) {
                                            Icon(Icons.Rounded.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = {
                                            viewModel.deleteLocalBackup(context, summary)
                                        }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                })
            }
        }
    }
}

@Composable
fun ExportOptionsBottomSheet(
    onDismiss: () -> Unit,
    onConfirmExport: (includeSettings: Boolean, includeFavorites: Boolean, includePlaylists: Boolean, includeHistory: Boolean, includeDownloadsManifest: Boolean) -> Unit
) {
    var incSettings by remember { mutableStateOf(true) }
    var incFavorites by remember { mutableStateOf(true) }
    var incPlaylists by remember { mutableStateOf(true) }
    var incHistory by remember { mutableStateOf(true) }
    var incDownloads by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Export Backup Options",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Select elements to include in JSON backup (saved directly to Downloads/SpatialFlow/Backups):",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { incSettings = !incSettings }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(checked = incSettings, onCheckedChange = { incSettings = it })
                Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Preferences & App Settings", style = MaterialTheme.typography.bodyLarge)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { incFavorites = !incFavorites }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(checked = incFavorites, onCheckedChange = { incFavorites = it })
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Favorites & Liked Tracks", style = MaterialTheme.typography.bodyLarge)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { incPlaylists = !incPlaylists }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(checked = incPlaylists, onCheckedChange = { incPlaylists = it })
                Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Playlists & Track Data", style = MaterialTheme.typography.bodyLarge)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { incHistory = !incHistory }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(checked = incHistory, onCheckedChange = { incHistory = it })
                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Listening Recap History", style = MaterialTheme.typography.bodyLarge)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { incDownloads = !incDownloads }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(checked = incDownloads, onCheckedChange = { incDownloads = it })
                Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Offline Download Manifest", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onConfirmExport(incSettings, incFavorites, incPlaylists, incHistory, incDownloads) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Backup, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create Backup File")
            }
        }
    }
}

@Composable
fun BackupPreviewBottomSheet(
    summary: SettingsViewModel.BackupSummaryData,
    onDismiss: () -> Unit,
    onConfirmRestore: (mode: SettingsViewModel.RestoreMode, resSettings: Boolean, resFav: Boolean, resPlaylists: Boolean, resHistory: Boolean, resDownloads: Boolean) -> Unit
) {
    var restoreMode by remember { mutableStateOf(SettingsViewModel.RestoreMode.MERGE) }
    var resSettings by remember { mutableStateOf(summary.settingsCount > 0) }
    var resFavorites by remember { mutableStateOf(summary.favoritesCount > 0) }
    var resPlaylists by remember { mutableStateOf(summary.playlistsCount > 0) }
    var resHistory by remember { mutableStateOf(summary.historyCount > 0) }
    var resDownloads by remember { mutableStateOf(summary.downloadsManifestCount > 0) }

    val dateFormatted = remember(summary.timestamp) {
        if (summary.timestamp > 0) {
            SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(summary.timestamp))
        } else "Unknown Date"
    }

    val sizeFormatted = remember(summary.fileSizeBytes) {
        val kb = summary.fileSizeBytes / 1024.0
        if (kb >= 1024) String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
        else String.format(Locale.getDefault(), "%.0f KB", kb)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Inspect & Restore Backup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(summary.fileName.ifEmpty { "Backup File" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("Created: $dateFormatted · $sizeFormatted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!summary.device.isNullOrBlank()) {
                            Text("Device: ${summary.device}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Text("Restore Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { restoreMode = SettingsViewModel.RestoreMode.MERGE }
            ) {
                RadioButton(selected = restoreMode == SettingsViewModel.RestoreMode.MERGE, onClick = { restoreMode = SettingsViewModel.RestoreMode.MERGE })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Merge (Recommended)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Combine backup data with current library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { restoreMode = SettingsViewModel.RestoreMode.OVERWRITE }
            ) {
                RadioButton(selected = restoreMode == SettingsViewModel.RestoreMode.OVERWRITE, onClick = { restoreMode = SettingsViewModel.RestoreMode.OVERWRITE })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Overwrite", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Text("Clean replace existing records", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("Select Elements to Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (summary.settingsCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { resSettings = !resSettings }) {
                    Checkbox(checked = resSettings, onCheckedChange = { resSettings = it })
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Preferences (${summary.settingsCount} keys)", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (summary.favoritesCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { resFavorites = !resFavorites }) {
                    Checkbox(checked = resFavorites, onCheckedChange = { resFavorites = it })
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Favorites (${summary.favoritesCount} tracks)", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (summary.playlistsCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { resPlaylists = !resPlaylists }) {
                    Checkbox(checked = resPlaylists, onCheckedChange = { resPlaylists = it })
                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Playlists (${summary.playlistsCount} playlists, ${summary.songsCount} items)", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (summary.historyCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { resHistory = !resHistory }) {
                    Checkbox(checked = resHistory, onCheckedChange = { resHistory = it })
                    Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Listening History (${summary.historyCount} play events)", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (summary.downloadsManifestCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { resDownloads = !resDownloads }) {
                    Checkbox(checked = resDownloads, onCheckedChange = { resDownloads = it })
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Offline Downloads (${summary.downloadsManifestCount} tracks manifest)", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onConfirmRestore(restoreMode, resSettings, resFavorites, resPlaylists, resHistory, resDownloads) },
                colors = if (restoreMode == SettingsViewModel.RestoreMode.OVERWRITE) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.SettingsBackupRestore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (restoreMode == SettingsViewModel.RestoreMode.OVERWRITE) "Overwrite & Restore" else "Restore Backup Data")
            }
        }
    }
}

@Composable
fun AutoBackupSettingsBottomSheet(
    enabled: Boolean,
    currentFreq: String,
    onDismiss: () -> Unit,
    onToggleEnable: (Boolean) -> Unit,
    onSelectFreq: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Auto-Backup Schedule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Automatically generate local backups saved into SpatialFlow/Backups/",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Enable Automatic Backups", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Switch(checked = enabled, onCheckedChange = onToggleEnable)
            }

            if (enabled) {
                Text("Backup Frequency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                val freqs = listOf("daily" to "Daily", "weekly" to "Weekly", "monthly" to "Monthly")
                freqs.forEach { (key, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectFreq(key) }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(selected = currentFreq == key, onClick = { onSelectFreq(key) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

@Composable
fun MissingSongsRecoveryBottomSheet(
    missingSongs: List<SongItem>,
    totalManifestSongsCount: Int,
    onDismiss: () -> Unit,
    onConfirmDownload: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text("Re-Download Offline Tracks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Found ${missingSongs.size} missing offline tracks out of $totalManifestSongsCount in the backup manifest. Queue missing tracks for background download now?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Skip")
                }
                Button(onClick = onConfirmDownload, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Download (${missingSongs.size})")
                }
            }
        }
    }
}
