@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.data.innertube.AccountManager
import com.codetrio.spatialflow.ui.theme.observeKey

@Composable
fun ExploreSectionPriorityRow() {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
    val priorityState = prefs.observeKey("explore_section_priority", "yt")
    val priority = priorityState.value
    val subtitle = if (priority == "spotify") "Spotify First" else "YouTube Music First"

    ListItem(
        onClick = { showDialog = true },
        content = {
            Column {
                Text(
                    text = "Explore Section Priority",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        ExploreSectionPriorityDialog(
            currentPriority = priority,
            onDismiss = { showDialog = false },
            onSelect = { selectedPriority ->
                prefs.edit().putString("explore_section_priority", selectedPriority).apply()
                showDialog = false
            }
        )
    }
}

@Composable
fun ExploreSectionPriorityDialog(
    currentPriority: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf("YouTube Music First" to "yt", "Spotify First" to "spotify")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Explore Section Priority") },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (currentPriority == value || (currentPriority.isBlank() && value == "yt")),
                            onClick = { onSelect(value) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DefaultLibraryTabRow() {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
    val defaultTabState = prefs.observeKey("default_library_tab", "")
    val defaultTab = defaultTabState.value
    val subtitle = if (defaultTab.isBlank()) "None (Overview)" else defaultTab

    ListItem(
        onClick = { showDialog = true },
        content = {
            Column {
                Text(
                    text = "Default Library Tab",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        DefaultLibraryTabDialog(
            currentDefault = defaultTab,
            onDismiss = { showDialog = false },
            onSelect = { selectedTab ->
                prefs.edit().putString("default_library_tab", selectedTab).apply()
                showDialog = false
            }
        )
    }
}

@Composable
fun DefaultLibraryTabDialog(
    currentDefault: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val isLoggedIn = remember { AccountManager.isLoggedIn(context) }
    val options = remember(isLoggedIn) {
        if (isLoggedIn) {
            listOf(
                "" to "None (Overview)",
                "Playlists" to "Playlists",
                "Spotify" to "Spotify",
                "Recap" to "Recap",
                "Podcasts" to "Podcasts",
                "Songs" to "Songs",
                "Albums" to "Albums",
                "Artists" to "Artists",
                "Device Files" to "Device Files"
            )
        } else {
            listOf(
                "" to "None (Overview)",
                "Playlists" to "Playlists",
                "Spotify" to "Spotify",
                "Recap" to "Recap",
                "Device Files" to "Device Files"
            )
        }
    }

    var selectedKey by remember(currentDefault) { mutableStateOf(currentDefault) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Default Library Tab",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isLoggedIn) "Select default active tab for logged-in library:" else "Select default active tab for logged-out library:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { (key, labelText) ->
                        val isSelected = selectedKey == key
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.96f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "chipPressScale"
                        )

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedKey = key
                                onSelect(key)
                            },
                            interactionSource = interactionSource,
                            label = {
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                iconColor = MaterialTheme.colorScheme.primary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
