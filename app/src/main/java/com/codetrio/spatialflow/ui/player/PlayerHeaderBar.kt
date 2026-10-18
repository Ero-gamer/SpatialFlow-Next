package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem

/**
 * Top header bar of the Full Player.
 *
 * - **Left**: Collapse/back icon button. In lyrics mode it acts as the back arrow (disables lyrics).
 * - **Center**: In standard mode shows "NOW PLAYING" (hidden when canvas is active).
 *               In lyrics mode shows the current song title + artist, sliding in from the left.
 * - **Right**: ⋮ options menu — opens the lyrics-options sheet when in lyrics mode,
 *               or the main player-options sheet otherwise.
 *
 * [lyricsProgressAnim] drives the cross-fade between standard ↔ lyrics header content.
 */
@Composable
fun PlayerHeaderBar(
    isLyricsModeEnabled: Boolean,
    hasCanvas: Boolean,
    currentSong: SongItem?,
    lyricsProgressAnim: Animatable<Float, AnimationVector1D>,
    contentColor: Color,
    contentSecondary: Color,
    onCollapse: () -> Unit,
    onLyricsModeChanged: (Boolean) -> Unit,
    onShowPlayerOptions: () -> Unit,
    onShowLyricsOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: collapse / back arrow
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {
                    if (isLyricsModeEnabled) {
                        onLyricsModeChanged(false)
                    } else {
                        onCollapse()
                    }
                },
                modifier = Modifier.graphicsLayer {
                    val progress = lyricsProgressAnim.value
                    alpha = (1f - progress).coerceIn(0f, 1f)
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Collapse Player",
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Center: context-sensitive title area
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            // Lyrics mode: song title + artist sliding in from left
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val progress = lyricsProgressAnim.value
                        alpha = progress.coerceIn(0f, 1f)
                        translationX = -12.dp.toPx() * (1f - progress)
                    }
            ) {
                Text(
                    text = currentSong?.title ?: "Unknown Title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier.basicMarqueeWithFadedEdges(edgeWidth = 8.dp, initialDelayMillis = 3000)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentSong?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier.basicMarqueeWithFadedEdges(edgeWidth = 8.dp, initialDelayMillis = 3000)
                )
            }

            // Standard mode: "NOW PLAYING" centered (hidden when canvas video is active)
            if (!hasCanvas) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val progress = lyricsProgressAnim.value
                            alpha = (1f - progress).coerceIn(0f, 1f)
                            translationY = 12.dp.toPx() * progress
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }

        // Right: options menu (lyrics options vs. player options)
        IconButton(
            onClick = {
                if (isLyricsModeEnabled) onShowLyricsOptions() else onShowPlayerOptions()
            }
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = if (isLyricsModeEnabled) "Lyrics Options" else "Player Options",
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
