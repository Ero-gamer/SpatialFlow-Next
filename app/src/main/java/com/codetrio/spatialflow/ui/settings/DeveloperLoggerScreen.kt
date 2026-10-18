package com.codetrio.spatialflow.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.util.DeviceLogManager
import com.codetrio.spatialflow.util.LogcatEntry
import com.codetrio.spatialflow.util.LogcatLevel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeveloperLoggerScreen(
    navController: androidx.navigation.NavController
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        DeviceLogManager.startLogging()
    }

    val rawLogs by DeviceLogManager.logsFlow.collectAsStateWithLifecycle()
    val isPaused by DeviceLogManager.isPaused.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf(LogcatLevel.ALL) }

    // High performance derived filter
    val filteredLogs by remember(rawLogs, searchQuery, selectedLevel) {
        derivedStateOf {
            val query = searchQuery.trim()
            rawLogs.filter { entry ->
                if (selectedLevel != LogcatLevel.ALL && entry.level.priority < selectedLevel.priority) {
                    return@filter false
                }
                if (query.isNotEmpty()) {
                    entry.message.contains(query, ignoreCase = true) || entry.tag.contains(query, ignoreCase = true)
                } else {
                    true
                }
            }
        }
    }

    val listState = rememberLazyListState()

    // Smooth auto-scroll when new items arrive
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(filteredLogs.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Dev Console",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.popBackStack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Clear logs
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            DeviceLogManager.clear()
                            Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Logs")
                    }

                    Spacer(Modifier.width(4.dp))

                    // Pause / Resume
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            DeviceLogManager.togglePause()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isPaused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isPaused) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause"
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    // Share logs
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val exportText = DeviceLogManager.getExportText(filteredLogs)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, exportText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share App Logs"))
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share Logs")
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Clean Search Bar (Zero Borders, Tonal) ──────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Filter tag or message...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Compact Level Selector (M3 Expressive ButtonGroup) ──────────
            val levels = listOf(
                LogcatLevel.ALL,
                LogcatLevel.DEBUG,
                LogcatLevel.INFO,
                LogcatLevel.WARN,
                LogcatLevel.ERROR
            )

            ButtonGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                overflowIndicator = {}
            ) {
                val groupScope = this
                levels.forEach { level ->
                    val checked = selectedLevel == level
                    customItem(
                        buttonGroupContent = {
                            Button(
                                onClick = {
                                    if (!checked) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedLevel = level
                                    }
                                },
                                modifier = with(groupScope) {
                                    Modifier
                                        .weight(1f)
                                        .height(ButtonDefaults.MinHeight)
                                },
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (checked) {
                                        when (level) {
                                            LogcatLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
                                            LogcatLevel.WARN -> MaterialTheme.colorScheme.tertiaryContainer
                                            LogcatLevel.INFO -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.secondaryContainer
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    contentColor = if (checked) {
                                        when (level) {
                                            LogcatLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                                            LogcatLevel.WARN -> MaterialTheme.colorScheme.onTertiaryContainer
                                            LogcatLevel.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = level.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        menuContent = {}
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Fast, Lightweight Monospace Log List ────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching logs" else "Listening for app logs...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = filteredLogs,
                            key = { idx, entry -> "log_${idx}_${entry.id}" }
                        ) { _, entry ->
                            LogItemRow(
                                entry = entry,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Log", "${entry.tag}: ${entry.message}"))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                                },
                                onShare = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${entry.timestamp} [${entry.level.letter}] ${entry.tag}: ${entry.message}")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Log"))
                                }
                            )
                        }
                    }
                }
            }

            // ── Minimal Status Pill ─────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Logs: ${filteredLogs.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isPaused) "Paused" else "Streaming",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(
    entry: LogcatEntry,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val levelColor = when (entry.level) {
        LogcatLevel.ERROR -> MaterialTheme.colorScheme.error
        LogcatLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogcatLevel.INFO -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onCopy,
                onLongClick = onShare
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Level badge
            Text(
                text = entry.level.letter.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = levelColor,
                modifier = Modifier.padding(top = 1.dp)
            )

            Spacer(Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = levelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.timestamp.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = entry.timestamp,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
