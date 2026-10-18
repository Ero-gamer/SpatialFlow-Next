@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import com.codetrio.spatialflow.domain.model.CacheBreakdown
import com.codetrio.spatialflow.domain.model.CacheCategory
import com.codetrio.spatialflow.domain.model.CategoryCacheInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.codetrio.spatialflow.R

@Composable
fun MusicManagementScreen(
    navController: NavController,
    libraryPaths: List<String>,
    onRemovePath: (String) -> Unit,
    onAddPathClick: () -> Unit,
    cacheBreakdown: CacheBreakdown? = null,
    onClearCategory: ((CacheCategory) -> Unit)? = null,
    onClearAllCaches: (() -> Unit)? = null,
    onRefreshCache: (() -> Unit)? = null,
    songCacheSize: String = "0 B",
    imageCacheSize: String = "0 B",
    onClearSongCache: () -> Unit = {},
    onClearImageCache: () -> Unit = {},
    ignoreShortAudio: Boolean,
    onIgnoreShortAudioChange: (Boolean) -> Unit,
    ignoreShortAudioDuration: Float,
    onIgnoreShortAudioDurationChange: (Float) -> Unit,
    songCacheMaxSize: Int,
    onSongCacheMaxSizeChange: (Int) -> Unit,
    imageCacheMaxSize: Int,
    onImageCacheMaxSizeChange: (Int) -> Unit,
    downloadFolder: String?,
    onDownloadFolderClick: () -> Unit,
    hiddenFolders: List<String>,
    onAddHiddenFolderClick: () -> Unit,
    onRemoveHiddenFolder: (String) -> Unit,
    isScanning: Boolean,
    onRescanClick: () -> Unit,
    onRebuildDatabaseClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SettingsScaffold(
            title = "Music Management",
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
                SettingsHeader(stringResource(R.string.settings_header_library))
                SettingsGroupCard(buildList {
                    add { LibrarySourceHeader() }
                    libraryPaths.forEach { path ->
                        add { LibraryPathRow(path, onRemovePath) }
                    }
                    if (libraryPaths.isEmpty()) {
                        add { LibraryPathRow(stringResource(R.string.setting_music_source_default), null) }
                    }
                    add { AddMorePathRow(onAddPathClick) }
                })

                SettingsHeader("Audio Filtering")
                SettingsGroupCard(buildList {
                    add {
                        IgnoreShortAudioRow(ignoreShortAudio, onIgnoreShortAudioChange)
                    }
                    if (ignoreShortAudio) {
                        add {
                            IgnoreShortAudioDurationRow(ignoreShortAudioDuration, onIgnoreShortAudioDurationChange)
                        }
                    }
                })

                SettingsHeader("Hidden Folders")
                SettingsGroupCard(buildList {
                    hiddenFolders.forEach { folder ->
                        add { HiddenFolderRow(folder, onRemoveHiddenFolder) }
                    }
                    if (hiddenFolders.isEmpty()) {
                        add {
                            ListItem(
                                headlineContent = { Text("No folders blacklisted", style = MaterialTheme.typography.bodyLarge) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                    add {
                        AddHiddenFolderRow(onAddHiddenFolderClick)
                    }
                })

                SettingsHeader("Downloads")
                SettingsGroupCard(listOf(
                    { DownloadFolderRow(downloadFolder, onDownloadFolderClick) }
                ))

                SettingsHeader(stringResource(R.string.settings_header_storage))
                if (cacheBreakdown != null) {
                    ExpressiveCacheOverviewCard(
                        breakdown = cacheBreakdown,
                        onClearAll = { onClearAllCaches?.invoke() },
                        onRefresh = { onRefreshCache?.invoke() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsGroupCard(buildList {
                        add {
                            ExpressiveCacheCategoryRow(
                                category = CacheCategory.SONGS,
                                info = cacheBreakdown.categories[CacheCategory.SONGS],
                                onMaxSizeChange = onSongCacheMaxSizeChange,
                                onClear = { onClearCategory?.invoke(CacheCategory.SONGS) ?: onClearSongCache() }
                            )
                        }
                        add {
                            ExpressiveCacheCategoryRow(
                                category = CacheCategory.IMAGES,
                                info = cacheBreakdown.categories[CacheCategory.IMAGES],
                                onMaxSizeChange = onImageCacheMaxSizeChange,
                                onClear = { onClearCategory?.invoke(CacheCategory.IMAGES) ?: onClearImageCache() }
                            )
                        }
                        add {
                            ExpressiveCacheCategoryRow(
                                category = CacheCategory.CANVAS,
                                info = cacheBreakdown.categories[CacheCategory.CANVAS],
                                onMaxSizeChange = null,
                                onClear = { onClearCategory?.invoke(CacheCategory.CANVAS) }
                            )
                        }
                        add {
                            ExpressiveCacheCategoryRow(
                                category = CacheCategory.NETWORK_TEMP,
                                info = cacheBreakdown.categories[CacheCategory.NETWORK_TEMP],
                                onMaxSizeChange = null,
                                onClear = { onClearCategory?.invoke(CacheCategory.NETWORK_TEMP) }
                            )
                        }
                    })
                } else {
                    SettingsGroupCard(buildList {
                        add { SongCacheSizeRow(songCacheSize, songCacheMaxSize, onSongCacheMaxSizeChange, onClearSongCache) }
                        add { ImageCacheSizeRow(imageCacheSize, imageCacheMaxSize, onImageCacheMaxSizeChange, onClearImageCache) }
                    })
                }

                SettingsHeader("Database & Scanning")
                SettingsGroupCard(buildList {
                    add {
                        ListItem(
                            onClick = onRescanClick,
                            content = {
                                Column {
                                    Text("Full Library Rescan", style = MaterialTheme.typography.bodyLarge)
                                    Text("Scan local storage for new music files", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            leadingContent = {
                                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    add {
                        ListItem(
                            onClick = onRebuildDatabaseClick,
                            content = {
                                Column {
                                    Text("Rebuild Database", style = MaterialTheme.typography.bodyLarge)
                                    Text("Clear media & image caches, then full re-index", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            leadingContent = {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                })
            }
        }

        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanning & syncing library...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibrarySourceHeader() {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.setting_music_source),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun LibraryPathRow(path: String, onRemove: ((String) -> Unit)?) {
    ListItem(
        onClick = { },
        content = {
            Text(
                text = path.substringAfterLast(":"),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(
                    if (onRemove != null) R.drawable.ic_folder_music else R.drawable.ic_folder_open
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = if (onRemove != null) {
            {
                Text(
                    text = "Remove",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onRemove(path) }
                        .padding(8.dp)
                )
            }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun AddMorePathRow(onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        content = {
            Text(
                text = stringResource(R.string.setting_add_more_path),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DownloadFolderRow(path: String?, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        content = {
            Column {
                Text(
                    text = "Download Location",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = path ?: "Not Set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun IgnoreShortAudioRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Ignore Short Audio (Voice Notes)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Hide voice notes and short recordings from library",
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
fun IgnoreShortAudioDurationRow(
    value: Float, onValueChange: (Float) -> Unit
) {
    ListItem(
        onClick = { },
        content = {
            Column {
                Text(
                    text = "Minimum Duration Threshold",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Ignore tracks under ${value.toInt()} seconds",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = {
            Slider(
                value = value.coerceIn(10f, 120f),
                onValueChange = onValueChange,
                valueRange = 10f..120f,
                steps = 10,
                modifier = Modifier.fillMaxWidth()
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun HiddenFolderRow(path: String, onRemove: (String) -> Unit) {
    ListItem(
        onClick = { },
        content = {
            Text(
                text = path.substringAfterLast("/"),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = path,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Text(
                text = "Remove",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onRemove(path) }
                    .padding(8.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun AddHiddenFolderRow(onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        content = {
            Text(
                text = "Blacklist a Folder",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun CacheSizeRow(
    title: String,
    currentSize: String,
    maxSize: Int,
    onMaxSizeChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Max $title Size") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    listOf(100, 500, 1024, 2048, 0).forEach { sizeOption ->
                        val text = if (sizeOption == 0) "Unlimited" else if (sizeOption >= 1024) "${sizeOption / 1024} GB" else "$sizeOption MB"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMaxSizeChange(sizeOption)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (maxSize == sizeOption),
                                onClick = {
                                    onMaxSizeChange(sizeOption)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    val currentBytes = parseCacheSize(currentSize)
    val maxBytes = if (maxSize == 0) 0L else maxSize * 1024L * 1024L
    val progress = if (maxBytes > 0) (currentBytes.toFloat() / maxBytes).coerceIn(0f, 1f) else 0f

    ListItem(
        onClick = { showDialog = true },
        content = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = currentSize,
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        },
                        label = "cache_size"
                    ) { size ->
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    val maxText = if (maxSize == 0) "Unlimited" else if (maxSize >= 1024) "${maxSize / 1024} GB" else "$maxSize MB"
                    Text(
                        text = "Max: $maxText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (maxBytes > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Cache",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

private fun parseCacheSize(sizeStr: String): Long {
    try {
        val split = sizeStr.trim().split(" ")
        if (split.size != 2) return 0L
        val value = split[0].toFloatOrNull() ?: return 0L
        val unit = split[1].uppercase()
        return when (unit) {
            "KB" -> (value * 1024).toLong()
            "MB" -> (value * 1024 * 1024).toLong()
            "GB" -> (value * 1024 * 1024 * 1024).toLong()
            "B" -> value.toLong()
            else -> 0L
        }
    } catch (_: Exception) {
        return 0L
    }
}

@Composable
fun SongCacheSizeRow(
    cacheSize: String,
    maxSize: Int,
    onMaxSizeChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    CacheSizeRow(
        title = "Song Cache",
        currentSize = cacheSize,
        maxSize = maxSize,
        onMaxSizeChange = onMaxSizeChange,
        onClear = onClear
    )
}

@Composable
fun ImageCacheSizeRow(
    cacheSize: String,
    maxSize: Int,
    onMaxSizeChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    CacheSizeRow(
        title = "Image Cache",
        currentSize = cacheSize,
        maxSize = maxSize,
        onMaxSizeChange = onMaxSizeChange,
        onClear = onClear
    )
}

@Composable
fun ExpressiveCacheOverviewCard(
    breakdown: CacheBreakdown,
    onClearAll: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Clear All Cache?") },
            text = {
                Text(
                    "This will safely remove cached audio streams, cover artwork, canvas loop videos, and temporary files. Downloaded songs and your library database will remain intact.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onClearAll()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Cache Consumed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = breakdown.formattedTotalSize,
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        },
                        label = "total_cache"
                    ) { totalSize ->
                        Text(
                            text = totalSize,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Cache Sizes",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledTonalButton(
                        onClick = { showConfirmDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (breakdown.isCalculating) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            } else {
                val totalQuota = breakdown.categories.values.sumOf { it.quotaBytes }
                val overallRatio = if (breakdown.totalBytes <= 0L) {
                    0f
                } else if (totalQuota > 0L) {
                    (breakdown.totalBytes.toFloat() / totalQuota).coerceIn(0.02f, 1f)
                } else {
                    0.1f
                }
                LinearWavyProgressIndicator(
                    progress = { overallRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

@Composable
fun ExpressiveCacheCategoryRow(
    category: CacheCategory,
    info: CategoryCacheInfo?,
    onMaxSizeChange: ((Int) -> Unit)? = null,
    onClear: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog && onMaxSizeChange != null && info != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Max ${category.displayName} Size") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    listOf(100, 256, 512, 1024, 2048, 0).forEach { sizeOption ->
                        val text = if (sizeOption == 0) "Unlimited" else if (sizeOption >= 1024) "${sizeOption / 1024} GB" else "$sizeOption MB"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMaxSizeChange(sizeOption)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (info.maxSizeMb == sizeOption),
                                onClick = {
                                    onMaxSizeChange(sizeOption)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    val currentSize = info?.formattedSize ?: "0 B"
    val maxSize = info?.maxSizeMb ?: 0
    val usageRatio = info?.usageRatio ?: 0f

    ListItem(
        onClick = { if (onMaxSizeChange != null) showDialog = true },
        content = {
            Column {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = currentSize,
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        },
                        label = "cat_cache_size"
                    ) { size ->
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (onMaxSizeChange != null) {
                        val maxText = if (maxSize <= 0) "Unlimited" else if (maxSize >= 1024) "${maxSize / 1024} GB" else "$maxSize MB"
                        Text(
                            text = "Max: $maxText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (info != null && info.quotaBytes > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearWavyProgressIndicator(
                        progress = { usageRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear ${category.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
