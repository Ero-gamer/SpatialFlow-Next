package com.codetrio.spatialflow.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.data.sourcepool.LosslessProvider
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

/**
 * Minimalist, dynamic-colored Modal Bottom Sheet for drag-and-drop lossless stream priority.
 *
 * Features:
 *  - Native press & hold reordering via ReorderableColumn + ReorderableItem
 *  - 100% dynamic color adaptation (Material You primary / primaryContainer)
 *  - Clean, minimal, uncluttered typography
 *  - M3 Expressive 28dp radius and edge-to-edge system insets
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LosslessPriorityBottomSheet(
    priorityOrder: List<LosslessProvider>,
    disabledProviders: Set<String>,
    onOrderChange: (List<LosslessProvider>) -> Unit,
    onToggleProvider: (LosslessProvider, Boolean) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    var list by remember(priorityOrder) { mutableStateOf(priorityOrder) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            // Header: Title & Reset Button (Minimal text)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Lossless Priority",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Press & hold to reorder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onResetDefaults()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset order",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reorderable Column (Press, hold & drag)
            ReorderableColumn(
                list = list,
                onSettle = { fromIndex: Int, toIndex: Int ->
                    if (fromIndex != toIndex) {
                        val updated = list.toMutableList()
                        val moved = updated.removeAt(fromIndex)
                        updated.add(toIndex, moved)
                        list = updated
                        onOrderChange(updated)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { index, provider, isDragging ->
                val isEnabled = !disabledProviders.contains(provider.id)
                val rank = index + 1

                ReorderableItem {
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                        label = "card_elevation"
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = elevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isEnabled) 1f else 0.45f)
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Priority Rank Badge (Dynamic Theme Tint)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "#$rank",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Dynamic Themed Icon Badge
                            Surface(
                                shape = CircleShape,
                                color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(provider.logoDrawableRes),
                                        contentDescription = provider.displayName,
                                        tint = if (isEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Compact Title & Quality Tag (No long descriptions)
                            val tag = when (provider) {
                                LosslessProvider.KOUZU -> "Studio FLAC"
                                LosslessProvider.DEEZER -> "CD FLAC"
                                LosslessProvider.TIDAL -> "Hi-Fi FLAC"
                                LosslessProvider.QOBUZ -> "24-bit Hi-Res"
                                LosslessProvider.SAAVN -> "320k Hi-Fi"
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Toggle Switch
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleProvider(provider, checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )

                            // Direct Drag Handle (instant drag or press-hold)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .draggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
