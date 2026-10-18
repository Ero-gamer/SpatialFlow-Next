package com.codetrio.spatialflow.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.ui.SnackbarController
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private data class SwipeActionVisual(
    val pillColor: Color,
    val iconColor: Color,
    val iconResId: Int? = null,
    val iconVector: ImageVector? = null
)

/**
 * Material 3 Expressive swipe-to-enqueue container styled like Gmail.
 * Features layout-phase expanding stadium pill for 120 FPS fluid scrolling/dragging,
 * system dynamic color extraction, spring physics, and tactile haptics.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwipeToEnqueueContainer(
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.28f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onPlayNext()
                    SnackbarController.showMessage("Playing next")
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onAddToQueue()
                    SnackbarController.showMessage("Added to queue")
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val progress = dismissState.progress
            val isArmed = progress >= 0.28f

            var hasVibratedForThisSwipe by remember(direction) { mutableStateOf(false) }

            LaunchedEffect(isArmed, direction) {
                if (isArmed && !hasVibratedForThisSwipe && direction != SwipeToDismissBoxValue.Settled) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    hasVibratedForThisSwipe = true
                } else if (!isArmed) {
                    hasVibratedForThisSwipe = false
                }
            }

            val iconScale by animateFloatAsState(
                targetValue = if (isArmed) 1.22f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "gmail_swipe_icon_scale"
            )

            // Dynamic Material 3 Expressive Color Roles & Icons matching DynamicIslandMiniPlayer
            val visual = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeActionVisual(
                    pillColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconResId = R.drawable.ic_skip_next
                )
                SwipeToDismissBoxValue.EndToStart -> SwipeActionVisual(
                    pillColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconVector = Icons.AutoMirrored.Rounded.QueueMusic
                )
                else -> null
            }

            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = alignment
            ) {
                if (visual != null) {
                    // Expanding stadium capsule pill sized in layout phase (no recomposition lag)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .layout { measurable, constraints ->
                                val offsetPx = try {
                                    dismissState.requireOffset().absoluteValue.roundToInt()
                                } catch (_: Exception) {
                                    0
                                }
                                val targetWidth = if (offsetPx > 16) offsetPx else 0
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = targetWidth,
                                        maxWidth = targetWidth
                                    )
                                )
                                layout(placeable.width, placeable.height) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                            .clip(CircleShape)
                            .background(visual.pillColor),
                        contentAlignment = Alignment.Center
                    ) {
                        val iconModifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }

                        if (visual.iconResId != null) {
                            Icon(
                                painter = painterResource(id = visual.iconResId),
                                contentDescription = "Play Next",
                                tint = visual.iconColor,
                                modifier = iconModifier
                            )
                        } else if (visual.iconVector != null) {
                            Icon(
                                imageVector = visual.iconVector,
                                contentDescription = "Add to Queue",
                                tint = visual.iconColor,
                                modifier = iconModifier
                            )
                        }
                    }
                }
            }
        },
        content = { content() }
    )
}
