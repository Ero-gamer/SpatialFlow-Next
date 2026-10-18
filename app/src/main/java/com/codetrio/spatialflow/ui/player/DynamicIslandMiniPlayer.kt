package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.ui.SnackbarEvent
import com.codetrio.spatialflow.ui.theme.SpatialFlowTheme
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private enum class IslandContentType {
    PLAYBACK,
    TOAST
}

private val IslandSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * DynamicIslandMiniPlayer handles both music playback controls and spring-morphing
 * toast notifications (from SnackbarController events) within the bottom Mini Player container.
 * Powered by Material 3 Dynamic Colors & Context-Aware Icon Resolution.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DynamicIslandMiniPlayer(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem?,
    songList: List<SongItem>,
    currentSongIndex: Int,
    isPlaying: Boolean,
    isProcessing: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    activeToast: SnackbarEvent? = null,
    onDismissToast: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var internalActiveToast by remember { mutableStateOf<SnackbarEvent?>(null) }
    val scope = rememberCoroutineScope()
    var autoDismissJob by remember { mutableStateOf<Job?>(null) }

    val pillScaleAnim = remember { Animatable(1f) }

    // Listen to SnackbarController events
    LaunchedEffect(Unit) {
        SnackbarController.events.collect { event ->
            autoDismissJob?.cancel()
            internalActiveToast = event

            // Spring squeeze pulse animation on toast arrival
            scope.launch {
                pillScaleAnim.animateTo(1.04f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                pillScaleAnim.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            }

            // Auto-dismiss timer
            autoDismissJob = scope.launch {
                delay(event.displayDurationMs)
                internalActiveToast = null
            }
        }
    }

    val currentToast = activeToast ?: internalActiveToast
    val currentContentType = if (currentToast != null) IslandContentType.TOAST else IslandContentType.PLAYBACK

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = pillScaleAnim.value
                scaleX = scale
                scaleY = scale
            }
    ) {
        AnimatedContent(
            targetState = currentContentType,
            transitionSpec = {
                (scaleIn(initialScale = 0.82f, animationSpec = IslandSpringSpec) + fadeIn(tween(200)))
                    .togetherWith(scaleOut(targetScale = 0.85f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(140)))
            },
            label = "DynamicIslandContentMorph",
            modifier = Modifier.fillMaxSize()
        ) { targetState ->
            when (targetState) {
                IslandContentType.TOAST -> {
                    currentToast?.let { toast ->
                        DynamicIslandToastContent(
                            event = toast,
                            accentColor = accentColor,
                            onDismiss = remember(toast, onDismissToast) {
                                {
                                    autoDismissJob?.cancel()
                                    internalActiveToast = null
                                    onDismissToast?.invoke()
                                }
                            }
                        )
                    }
                }
                IslandContentType.PLAYBACK -> {
                    if (currentSong != null) {
                        DynamicIslandPlaybackContent(
                            viewModel = viewModel,
                            currentSong = currentSong,
                            songList = songList,
                            currentSongIndex = currentSongIndex,
                            isPlaying = isPlaying,
                            isProcessing = isProcessing,
                            accentColor = accentColor,
                            onClick = onClick
                        )
                    } else {
                        DynamicIslandIdleContent(onClick = onClick)
                    }
                }
            }
        }
    }
}

@Immutable
private data class EventVisual(
    val iconVector: ImageVector? = null,
    val iconResId: Int? = null,
    val tintColor: Color
)

@Composable
private fun resolveEventVisual(
    event: SnackbarEvent,
    dynamicAccent: Color,
    colorScheme: ColorScheme
): EventVisual {
    val msg = event.message.lowercase(Locale.ROOT)

    // Context-first pattern matching overrides generic caller icons
    when {
        msg.contains("play next") || msg.contains("playing next") -> {
            return EventVisual(
                iconResId = R.drawable.ic_skip_next,
                tintColor = colorScheme.secondary
            )
        }
        msg.contains("queue") || msg.contains("enqueued") -> {
            return EventVisual(
                iconVector = Icons.AutoMirrored.Rounded.QueueMusic,
                tintColor = colorScheme.secondary
            )
        }
        msg.contains("favorite") || msg.contains("liked") || msg.contains("like") || msg.contains("library") || msg.contains("saved") -> {
            val favColor = if (dynamicAccent != Color.Unspecified && dynamicAccent != Color.Transparent) dynamicAccent else colorScheme.primary
            return EventVisual(
                iconVector = Icons.Rounded.Favorite,
                tintColor = favColor
            )
        }
        msg.contains("download") || msg.contains("downloading") || msg.contains("downloaded") || msg.contains("offline") -> {
            return EventVisual(
                iconVector = Icons.Rounded.Download,
                tintColor = colorScheme.tertiary
            )
        }
        msg.contains("playlist") -> {
            return EventVisual(
                iconVector = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                tintColor = colorScheme.primary
            )
        }
        msg.contains("shuffle") -> {
            return EventVisual(
                iconVector = Icons.Rounded.Shuffle,
                tintColor = colorScheme.primary
            )
        }
        msg.contains("repeat") || msg.contains("loop") -> {
            return EventVisual(
                iconVector = Icons.Rounded.Repeat,
                tintColor = colorScheme.primary
            )
        }
        msg.contains("fail") || msg.contains("error") || msg.contains("cannot") || msg.contains("unable") || msg.contains("invalid") -> {
            return EventVisual(
                iconVector = Icons.Rounded.Error,
                tintColor = colorScheme.error
            )
        }
        msg.contains("copy") || msg.contains("copied") || msg.contains("link") -> {
            return EventVisual(
                iconVector = Icons.Rounded.ContentCopy,
                tintColor = colorScheme.tertiary
            )
        }
        msg.contains("share") || msg.contains("shared") -> {
            return EventVisual(
                iconVector = Icons.Rounded.Share,
                tintColor = colorScheme.tertiary
            )
        }
        msg.contains("remove") || msg.contains("delete") || msg.contains("deleted") || msg.contains("clear") || msg.contains("cleared") -> {
            return EventVisual(
                iconVector = Icons.Rounded.Delete,
                tintColor = colorScheme.error
            )
        }
    }

    // Caller explicit icon overrides (when not matching standard event keywords)
    if (event.iconResId != null) {
        return EventVisual(iconResId = event.iconResId, tintColor = dynamicAccent)
    }
    if (event.iconVector != null) {
        return EventVisual(iconVector = event.iconVector, tintColor = dynamicAccent)
    }

    val fallbackColor = if (dynamicAccent != Color.Unspecified && dynamicAccent != Color.Transparent) dynamicAccent else colorScheme.primary
    return EventVisual(
        iconVector = Icons.Rounded.CheckCircle,
        tintColor = fallbackColor
    )
}

@Composable
private fun DynamicIslandToastContent(
    event: SnackbarEvent,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val visual = resolveEventVisual(event, accentColor, colorScheme)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(visual.tintColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    visual.iconResId != null -> {
                        Icon(
                            painter = painterResource(id = visual.iconResId),
                            contentDescription = "Event icon",
                            tint = visual.tintColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    visual.iconVector != null -> {
                        Icon(
                            imageVector = visual.iconVector,
                            contentDescription = "Event icon",
                            tint = visual.tintColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Success",
                            tint = visual.tintColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Text(
                text = event.message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val actionText = event.actionLabel
            if (!actionText.isNullOrBlank()) {
                TextButton(
                    onClick = remember(event, onDismiss) {
                        {
                            event.onActionClick?.invoke()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = visual.tintColor
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss notification",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun DynamicIslandPlaybackContent(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem,
    songList: List<SongItem>,
    currentSongIndex: Int,
    isPlaying: Boolean,
    isProcessing: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    com.codetrio.spatialflow.ui.MiniPlayerContentInternal(
        viewModel = viewModel,
        currentSong = currentSong,
        isPlaying = isPlaying,
        isProcessing = isProcessing,
        accentColor = accentColor,
        onClick = onClick,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun DynamicIslandIdleContent(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_music_note),
            contentDescription = "SpatialFlow Player",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = "SpatialFlow Music",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Dynamic Island Playback Preview", showBackground = true)
@Composable
private fun DynamicIslandPlaybackPreview() {
    SpatialFlowTheme {
        Surface(
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(8.dp)
        ) {
            DynamicIslandToastContent(
                event = SnackbarEvent(
                    message = "Added 1 song to queue",
                    actionLabel = "Undo",
                    onActionClick = {}
                ),
                accentColor = MaterialTheme.colorScheme.primary,
                onDismiss = {}
            )
        }
    }
}
