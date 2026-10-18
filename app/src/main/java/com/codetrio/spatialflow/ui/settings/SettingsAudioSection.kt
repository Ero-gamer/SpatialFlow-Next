@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DisconnectActionRow(
    currentAction: String,
    onActionChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val actionText = when (currentAction) {
        "pause" -> "Pause Playback"
        "none" -> "Do Nothing"
        else -> "Pause Playback"
    }

    ListItem(
        onClick = { showDialog = true },
        content = {
            Column {
                Text(
                    text = "Action on Disconnect",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Headset,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        DisconnectActionDialog(
            currentAction = currentAction,
            onDismiss = { showDialog = false },
            onSelect = { selectedAction ->
                onActionChange(selectedAction)
                showDialog = false
            }
        )
    }
}

@Composable
fun ResumeOnConnectRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Auto-Resume on Connect",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Automatically resume playback when headphones or Bluetooth device connects",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Headset,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
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
