package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.util.ComposeToImage
import com.codetrio.spatialflow.util.SongShareHelper
import kotlinx.coroutines.launch

/**
 * Dedicated Material 3 Expressive Song Share Bottom Sheet.
 * Allows users to:
 * - Preview the 9:16 Song Story Card (frosted glass, ambient blur, 1:1 artwork).
 * - Share directly to Instagram Stories with interactive deep link.
 * - Share the rendered 1080x1920 Story PNG across messaging & social apps.
 * - Copy the SpatialFlow song share link to clipboard.
 * - Share the text link via Android system share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongShareBottomSheet(
    song: SongItem?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isExportingImage by remember { mutableStateOf(false) }
    var shareUrl by remember { mutableStateOf<String?>(null) }

    // Pre-resolve SpatialFlow share URL
    LaunchedEffect(song) {
        if (song != null) {
            shareUrl = SongShareHelper.getOrGenerateShareUrl(song)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── TOP HEADER ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Share Song",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Post as 9:16 Story or share link",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            // ── INTERACTIVE LIVE STORY CARD PREVIEW ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                SongStoryCard(
                    song = song,
                    modifier = Modifier
                        .fillMaxWidth(0.66f)
                        .aspectRatio(9f / 16f)
                        .heightIn(max = 420.dp)
                )

                // Export loading overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = isExportingImage,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.matchParentSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Composing 1080x1920 Story...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // ── STORY SHARE ACTIONS (Instagram + General Image Share) ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Story Actions",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Instagram Stories Action (Hero Option)
                ShareActionTile(
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_instagram),
                            contentDescription = "Instagram",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    iconContainerColor = Color(0xFFE1306C),
                    title = "Instagram Stories",
                    subtitle = "Post 9:16 card directly to Instagram",
                    enabled = !isExportingImage,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            try {
                                isExportingImage = true
                                val bmp = ComposeToImage.createSongStoryImage(context, song)
                                val uri = ComposeToImage.saveBitmapAsFile(
                                    context = context,
                                    bitmap = bmp,
                                    fileName = "SpatialFlow_Story_${song?.id ?: System.currentTimeMillis()}"
                                )
                                val url = shareUrl ?: SongShareHelper.getFallbackShareUrl(song)
                                SongShareHelper.shareToInstagramStory(context, uri, url)
                            } catch (e: Exception) {
                                SnackbarController.showMessage("Failed to generate Story: ${e.message}")
                            } finally {
                                isExportingImage = false
                            }
                        }
                    }
                )

                // 2. Share Story Image (Other Apps: WhatsApp, Telegram, Snapchat)
                ShareActionTile(
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_share),
                            contentDescription = "Share Image",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    title = "Share Story Image",
                    subtitle = "WhatsApp, Telegram, Snapchat & other apps",
                    enabled = !isExportingImage,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            try {
                                isExportingImage = true
                                val bmp = ComposeToImage.createSongStoryImage(context, song)
                                val uri = ComposeToImage.saveBitmapAsFile(
                                    context = context,
                                    bitmap = bmp,
                                    fileName = "SpatialFlow_Story_${song?.id ?: System.currentTimeMillis()}"
                                )
                                val url = shareUrl ?: SongShareHelper.getFallbackShareUrl(song)
                                SongShareHelper.shareStoryImage(context, uri, song, url)
                            } catch (e: Exception) {
                                SnackbarController.showMessage("Failed to generate Story: ${e.message}")
                            } finally {
                                isExportingImage = false
                            }
                        }
                    }
                )
            }

            // ── LINK ACTIONS (Copy Link + Send Text Link) ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Link Actions",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 3. Copy Link
                ShareActionTile(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    title = "Copy Link",
                    subtitle = shareUrl ?: "Creating SpatialFlow link...",
                    enabled = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            val url = shareUrl ?: SongShareHelper.getOrGenerateShareUrl(song ?: return@launch)
                            SongShareHelper.copyShareLink(context, url)
                        }
                    }
                )

                // 4. Share Text Link (System Chooser)
                ShareActionTile(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    title = "Share Link",
                    subtitle = "Send song URL via messaging apps",
                    enabled = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            val s = song ?: return@launch
                            val url = shareUrl ?: SongShareHelper.getOrGenerateShareUrl(s)
                            SongShareHelper.shareLink(context, s, url)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ShareActionTile(
    icon: @Composable () -> Unit,
    iconContainerColor: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.50f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
