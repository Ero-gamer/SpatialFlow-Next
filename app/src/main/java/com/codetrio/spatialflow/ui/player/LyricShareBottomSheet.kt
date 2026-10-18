package com.codetrio.spatialflow.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.util.ComposeToImage
import kotlinx.coroutines.launch

/**
 * Apple Music Styled Lyrics Share Bottom Sheet.
 * Implements official Apple Music interaction and motion (Screenrecorder-2026-10-12-15-55-54-353.mp4):
 * - Clean header: "X Line(s) Selected", character count ("X of 150 characters").
 * - Segmented list of lyric lines with borderless rounded bubbles and spring selection animation.
 * - Direct share targets (General Share, WhatsApp, Instagram Stories, Copy Text).
 * - Expandable studio customization (Aspect ratio, Theme, Alignment).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricShareBottomSheet(
    currentSong: SongItem?,
    allLyrics: List<LyricLine>,
    initialLine: LyricLine,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isExportingImage by remember { mutableStateOf(false) }
    var isStudioExpanded by remember { mutableStateOf(true) }

    // Filter out interlude lines
    val selectableLyrics = remember(allLyrics) {
        allLyrics.filter { !it.isInterlude && it.content.isNotBlank() }
    }

    val selectedLines = remember {
        mutableStateListOf<LyricLine>().apply {
            if (!initialLine.isInterlude && initialLine.content.isNotBlank()) {
                add(initialLine)
            } else if (selectableLyrics.isNotEmpty()) {
                add(selectableLyrics.first())
            }
        }
    }

    val totalCharacters = remember(selectedLines.toList()) {
        selectedLines.sumOf { it.content.length }
    }

    // Calculate initial selected index to auto-scroll selection list to tapped line
    val initialIndex = remember(selectableLyrics, initialLine) {
        val idx = selectableLyrics.indexOfFirst {
            it.startTimeMs == initialLine.startTimeMs && it.content == initialLine.content
        }
        if (idx >= 0) idx else 0
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = maxOf(0, initialIndex - 1)
    )

    LaunchedEffect(initialIndex) {
        if (initialIndex > 0) {
            listState.scrollToItem(maxOf(0, initialIndex - 1))
        }
    }

    // Studio Customization State
    var shareOptions by remember { mutableStateOf(LyricsShareImageOptions()) }

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
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── TOP HEADER (Matching Apple Music Reference) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${selectedLines.size} ${if (selectedLines.size == 1) "Line" else "Lines"} Selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$totalCharacters of 450 characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (totalCharacters > 450) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isStudioExpanded = !isStudioExpanded }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = if (isStudioExpanded) "Collapse Preview" else "Show Preview",
                            tint = if (isStudioExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            // ── LIVE CARD PREVIEW & COMPACT CONTROLS ──
            AnimatedVisibility(
                visible = isStudioExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Live Apple Music Card Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .animateContentSize()
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val cardHeight = when (shareOptions.aspectRatio) {
                            LyricsShareAspectRatio.SQUARE_1_1 -> 205.dp
                            LyricsShareAspectRatio.PORTRAIT_4_5 -> 235.dp
                            LyricsShareAspectRatio.STORY_9_16 -> 265.dp
                        }
                        LyricsImageCard(
                            currentSong = currentSong,
                            selectedLines = selectedLines.sortedBy { it.startTimeMs },
                            options = shareOptions,
                            modifier = Modifier.height(cardHeight)
                        )
                    }

                    // Consolidated Aspect Ratio & Text Alignment Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Aspect Ratio Controls
                        ButtonGroup(
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                            expandedRatio = 0.25f,
                            overflowIndicator = {}
                        ) {
                            val scope = this
                            LyricsShareAspectRatio.entries.forEach { ratio ->
                                val checked = shareOptions.aspectRatio == ratio
                                customItem(
                                    buttonGroupContent = {
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isPressed by interactionSource.collectIsPressedAsState()
                                        val cornerRadius by animateDpAsState(
                                            targetValue = if (isPressed) 6.dp else if (checked) 16.dp else 8.dp,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                            label = "RatioCorner"
                                        )
                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                shareOptions = shareOptions.copy(aspectRatio = ratio)
                                            },
                                            modifier = with(scope) {
                                                Modifier.animateWidth(interactionSource).height(34.dp)
                                            },
                                            shape = RoundedCornerShape(cornerRadius),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = when (ratio) {
                                                    LyricsShareAspectRatio.SQUARE_1_1 -> "1:1"
                                                    LyricsShareAspectRatio.PORTRAIT_4_5 -> "4:5"
                                                    LyricsShareAspectRatio.STORY_9_16 -> "9:16"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    },
                                    menuContent = {}
                                )
                            }
                        }

                        // Text Alignment Controls
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LyricsShareTextAlign.entries.forEach { align ->
                                val checked = shareOptions.textAlign == align
                                val icon = when (align) {
                                    LyricsShareTextAlign.LEFT -> Icons.AutoMirrored.Filled.FormatAlignLeft
                                    LyricsShareTextAlign.CENTER -> Icons.Default.FormatAlignCenter
                                    LyricsShareTextAlign.RIGHT -> Icons.AutoMirrored.Filled.FormatAlignRight
                                }
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        shareOptions = shareOptions.copy(textAlign = align)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(icon, null, modifier = Modifier.size(16.dp), tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── SEGMENTED LYRICS LIST (M3 Expressive Connected Segmented List, Translucent Frosted Glass) ──
            val totalSelectable = selectableLyrics.size
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isStudioExpanded) 195.dp else 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.5.dp)
            ) {
                itemsIndexed(
                    items = selectableLyrics,
                    key = { idx, line -> line.startTimeMs * 31L + idx },
                    contentType = { _, _ -> "lyric_share_item" }
                ) { index, line ->
                    val isSelected = selectedLines.contains(line)

                    // M3 Expressive Connected Shape morphing tokens
                    val itemShape = remember(index, totalSelectable) {
                        when {
                            totalSelectable <= 1 -> RoundedCornerShape(16.dp)
                            index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                            index == totalSelectable - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                            else -> RoundedCornerShape(4.dp)
                        }
                    }

                    // Translucent spring-animated colors matching M3 Expressive tonal selection
                    val bubbleColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                      else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.22f),
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "bubbleColor"
                    )

                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White
                                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "textColor"
                    )

                    // Borderless connected translucent bubble
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .background(bubbleColor)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isSelected) {
                                    if (selectedLines.size > 1) selectedLines.remove(line)
                                } else {
                                    if (selectedLines.size >= 12) {
                                        SnackbarController.showMessage("Maximum 12 lines reached")
                                    } else if (totalCharacters + line.content.length <= 450 || selectedLines.isEmpty()) {
                                        selectedLines.add(line)
                                    } else {
                                        SnackbarController.showMessage("Character limit reached (450 max)")
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                    ) {
                        Text(
                            text = line.content,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }

            // ── ACTION BUTTONS: DIRECT SHARE ROW (Apple Music Style) ──
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Primary Action Button: Share Image Card
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                isExportingImage = true
                                val bitmap = ComposeToImage.createLyricsImage(
                                    context = context,
                                    song = currentSong,
                                    lyrics = selectedLines.sortedBy { it.startTimeMs },
                                    options = shareOptions
                                )
                                val uri = ComposeToImage.saveBitmapAsFile(
                                    context = context,
                                    bitmap = bitmap,
                                    fileName = "SpatialFlow_Lyrics_${System.currentTimeMillis()}"
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Lyrics Card"))
                                onDismiss()
                            } catch (e: Exception) {
                                SnackbarController.showMessage("Failed to generate image: ${e.message}")
                            } finally {
                                isExportingImage = false
                            }
                        }
                    },
                    enabled = !isExportingImage && selectedLines.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isExportingImage) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share Lyrics Card", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                // Secondary Quick Actions (Direct Targets)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Copy Formatted Text
                    OutlinedButton(
                        onClick = {
                            val lyricsText = selectedLines.sortedBy { it.startTimeMs }.joinToString("\n") { it.content }
                            val fullText = "\"$lyricsText\"\n— ${currentSong?.title} by ${currentSong?.artist}"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Lyrics", fullText))
                            SnackbarController.showMessage("Lyrics copied to clipboard!")
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Text", fontWeight = FontWeight.SemiBold)
                    }

                    // Direct Instagram Stories Action
                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    isExportingImage = true
                                    val bitmap = ComposeToImage.createLyricsImage(
                                        context = context,
                                        song = currentSong,
                                        lyrics = selectedLines.sortedBy { it.startTimeMs },
                                        options = shareOptions.copy(aspectRatio = LyricsShareAspectRatio.STORY_9_16)
                                    )
                                    val uri = ComposeToImage.saveBitmapAsFile(
                                        context = context,
                                        bitmap = bitmap,
                                        fileName = "SpatialFlow_Lyrics_Story_${System.currentTimeMillis()}"
                                    )
                                    val igIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                                        setDataAndType(uri, "image/png")
                                        setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        setPackage("com.instagram.android")
                                    }
                                    try {
                                        context.startActivity(igIntent)
                                    } catch (_: Exception) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share to Story"))
                                    }
                                    onDismiss()
                                } catch (e: Exception) {
                                    SnackbarController.showMessage("Failed: ${e.message}")
                                } finally {
                                    isExportingImage = false
                                }
                            }
                        },
                        enabled = !isExportingImage && selectedLines.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_instagram), null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Story", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
