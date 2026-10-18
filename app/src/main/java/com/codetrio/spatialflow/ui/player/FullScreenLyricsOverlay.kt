package com.codetrio.spatialflow.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.graphics.shapes.Morph
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.security.DeviceKeyIdentityManager
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.explore.MorphShape
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexLyricsPlain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullScreenLyricsOverlay(
    currentSong: SongItem?,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLoading: Boolean,
    lyricsError: Throwable?,
    currentPositionProvider: () -> Int,
    contentReady: Boolean = true,
    playerBackgroundColor: Color,
    canvasArtwork: CanvasArtwork? = null,
    contentColor: Color,
    contentSecondary: Color,
    dynamicAccentColor: Color,
    onRetryLyrics: () -> Unit,
    onFetchLyrics: () -> Unit,
    onSeekTo: (Int) -> Unit,
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    isPlaying: Boolean = false,
    playbackSpeed: Float = 1f,
    onPlayPauseClick: () -> Unit = {},
    duration: Long,
    onCollapse: (() -> Unit)? = null,
    onUpdateCustomLyrics: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val consumeClicks = remember { MutableInteractionSource() }
    var showProvidersSheet by remember { mutableStateOf(false) }
    var isDraggingSeekbar by remember { mutableStateOf(false) }
    var dragSeekProgress by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var lastSeekTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var lastSeekPos by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var syncOffsetMs by remember { mutableStateOf(0L) }
    var selectedLyricForShare by remember { mutableStateOf<LyricLine?>(null) }

    val effectivePositionProvider = remember(currentPositionProvider, syncOffsetMs) {
        { (currentPositionProvider() + syncOffsetMs).toInt().coerceAtLeast(0) }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = consumeClicks,
                indication = null,
                onClick = {}
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            // Centered Title Header Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onCollapse != null) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                            contentDescription = "Collapse Lyrics",
                            tint = contentColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        Text(
                            text = "LYRICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )

                        val activeResult = providerResults[selectedProvider] ?: providerResults.values
                            .filter { it.confidence >= 0f && it.hasLyrics() }
                            .maxWithOrNull(
                                compareBy<LyricsResult> { it.isWordByWord }
                                    .thenBy { it.isSynced }
                                    .thenBy { it.confidence }
                            )

                        val isActuallyKaraoke = activeResult?.isWordByWord == true ||
                                (!syncedLyrics.isNullOrEmpty() && syncedLyrics.any { !it.isInterlude && it.isWordByWord && it.words.isNotEmpty() })

                        val isSyllable = isActuallyKaraoke && (
                            activeResult?.syncedLyrics?.contains("<tt") == true ||
                            activeResult?.syncedLyrics?.contains("<span") == true ||
                            activeResult?.providerName?.contains("Unison", ignoreCase = true) == true ||
                            activeResult?.providerName?.contains("Bini", ignoreCase = true) == true ||
                            activeResult?.providerName?.contains("Apple Music", ignoreCase = true) == true
                        )

                        val lyricTypeBadge = when {
                            activeResult == null -> null
                            !activeResult.hasLyrics() -> null
                            isSyllable -> "Syllable Lyrics"
                            isActuallyKaraoke -> "Karaoke Lyrics"
                            activeResult.isSynced -> "Synced Lyrics"
                            else -> "Plain Lyrics"
                        }

                        AnimatedVisibility(
                            visible = lyricTypeBadge != null,
                            enter = fadeIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + slideInHorizontally(
                                initialOffsetX = { it / 2 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ),
                            exit = fadeOut() + slideOutHorizontally()
                        ) {
                            if (lyricTypeBadge != null) {
                                Text(
                                    text = " • $lyricTypeBadge",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor.copy(alpha = 0.4f),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = currentSong?.title ?: "Unknown Title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.basicMarqueeWithFadedEdges(edgeWidth = 8.dp)
                    )
                }

                IconButton(onClick = { showProvidersSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Change Lyrics Provider",
                        tint = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !contentReady -> Unit

                    !syncedLyrics.isNullOrEmpty() -> {
                        SyncedLyricsCompose(
                            onSeekTo = onSeekTo,
                            lyrics = syncedLyrics,
                            currentPositionProvider = effectivePositionProvider,
                            contentColor = contentColor,
                            dynamicAccentColor = dynamicAccentColor,
                            currentSong = currentSong,
                            selectedProvider = selectedProvider,
                            providerResults = providerResults,
                            isPlayingProvider = { isPlaying },
                            playbackSpeedProvider = { playbackSpeed },
                            onLineLongClick = { line -> selectedLyricForShare = line },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    !plainLyrics.isNullOrBlank() -> {
                        val isScriptRtl = remember(plainLyrics) { isRtlText(plainLyrics) }
                        CompositionLocalProvider(LocalLayoutDirection provides (if (isScriptRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 28.dp)
                            ) {
                                Text(
                                    text = plainLyrics,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = GoogleSansFlexLyricsPlain,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 44.sp,
                                        letterSpacing = (-0.2).sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    color = contentColor.copy(alpha = 0.9f)
                                )
                                LyricsMetadataFooter(
                                    currentSong = currentSong,
                                    selectedProvider = selectedProvider,
                                    providerResults = providerResults,
                                    contentColor = contentColor
                                )
                            }
                        }
                    }

                    isLoading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LinearWavyProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(4.dp),
                                color = dynamicAccentColor,
                                trackColor = contentColor.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "Searching lyrics across providers...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    lyricsError != null -> {
                        LyricsErrorState(
                            message = lyricsError.message ?: "Lyrics not found",
                            onRetry = onRetryLyrics
                        )
                    }

                    else -> {
                        LyricsErrorState(
                            message = "Lyrics are not loaded yet",
                            onRetry = onFetchLyrics
                        )
                    }
                }
            }

            // Time Progress Bar
            if (currentSong != null && contentReady) {
                val currentPosition = currentPositionProvider()
                val safeDur = if (duration > 0) duration.toFloat() else 1f
                val progressRatio = (currentPosition.toFloat() / safeDur).coerceIn(0f, 1f)

                val isWaitingForPlayer = remember(progressRatio, lastSeekTime, lastSeekPos) {
                    val elapsed = System.currentTimeMillis() - lastSeekTime
                    val diff = kotlin.math.abs(progressRatio - lastSeekPos)
                    elapsed < 1000 && diff > 0.02f
                }

                val displayProgress = when {
                    isDraggingSeekbar -> dragSeekProgress
                    isWaitingForPlayer -> lastSeekPos
                    else -> progressRatio
                }

                val currentPos = (displayProgress * safeDur).toLong()
                
                // Play/Pause morphing button state
                val playPauseMorphProgress by animateFloatAsState(
                    targetValue = if (isPlaying) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "LyricsPlayPauseMorph"
                )
                val playPauseMorph = remember {
                    Morph(MaterialShapes.Circle, MaterialShapes.Square)
                }
                val playPauseShape = MorphShape(playPauseMorph, playPauseMorphProgress)

                val playInteractionSource = remember { MutableInteractionSource() }
                val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                val pressScale by animateFloatAsState(
                    targetValue = if (isPlayPressed) 0.88f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "LyricsPlayPressScale"
                )

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .zIndex(10f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val accentLuminance = remember(dynamicAccentColor) {
                            androidx.core.graphics.ColorUtils.calculateLuminance(dynamicAccentColor.toArgb())
                        }
                        val playIconColor = if (accentLuminance > 0.40f) Color(0xFF141218) else Color.White

                        // Play/Pause button — same height as the seekbar container
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = pressScale
                                    scaleY = pressScale
                                }
                                .clip(CircleShape)
                                .background(dynamicAccentColor)
                                .clickable(
                                    interactionSource = playInteractionSource,
                                    indication = null
                                ) { onPlayPauseClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = playIconColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Seekbar container
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(dynamicAccentColor.copy(alpha = 0.15f))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = formatLyricsTime(currentPos.toLong()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = contentColor.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                
                                WavyMusicSlider(
                                    value = displayProgress,
                                    onValueChange = { newValue ->
                                        isDraggingSeekbar = true
                                        dragSeekProgress = newValue
                                    },
                                    onValueChangeFinished = {
                                        isDraggingSeekbar = false
                                        lastSeekTime = System.currentTimeMillis()
                                        lastSeekPos = dragSeekProgress
                                        onSeekTo((dragSeekProgress * safeDur).toInt())
                                    },
                                    modifier = Modifier.weight(1f),
                                    activeTrackColor = dynamicAccentColor,
                                    inactiveTrackColor = contentColor.copy(alpha = 0.2f),
                                    thumbColor = dynamicAccentColor,
                                    thumbRadius = 6.dp,
                                    waveAmplitudeWhenPlaying = 3.dp,
                                    waveLength = 36.dp,
                                    thumbLineHeightWhenInteracting = 16.dp
                                )
                                
                                Text(
                                    text = formatLyricsTime(duration),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = contentColor.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            if (showProvidersSheet && contentReady) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showProvidersSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    dragHandle = { BottomSheetDefaults.DragHandle() },
                    scrimColor = Color.Black.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp)
                    ) {
                        UnifiedLyricsBottomSheetContent(
                            providerResults = providerResults,
                            selectedProvider = selectedProvider,
                            syncOffsetMs = syncOffsetMs,
                            onSyncOffsetChange = { syncOffsetMs = it },
                            onProviderSelected = onProviderSelected,
                            onRefindClick = onRetryLyrics,
                            accentColor = dynamicAccentColor,
                            contentColor = contentColor,
                            onUpdateCustomLyrics = onUpdateCustomLyrics,
                            currentLyricsText = remember(syncedLyrics, plainLyrics) {
                                if (!syncedLyrics.isNullOrEmpty()) {
                                    syncedLyrics.joinToString("\n") { line ->
                                        val timeStr = String.format(java.util.Locale.US, "[%02d:%02d.%02d]", line.startTimeMs / 60000, (line.startTimeMs % 60000) / 1000, (line.startTimeMs % 1000) / 10)
                                        "$timeStr${line.content}"
                                    }
                                } else {
                                    plainLyrics ?: ""
                                }
                            },
                            songTitle = currentSong?.title ?: "Current Song",
                            syncedLyrics = syncedLyrics,
                            plainLyricsRaw = plainLyrics,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (selectedLyricForShare != null) {
            LyricShareBottomSheet(
                currentSong = currentSong,
                allLyrics = syncedLyrics ?: emptyList(),
                initialLine = selectedLyricForShare!!,
                onDismiss = { selectedLyricForShare = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun UnifiedLyricsBottomSheetContent(
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    syncOffsetMs: Long,
    onSyncOffsetChange: (Long) -> Unit,
    onProviderSelected: (String) -> Unit,
    onRefindClick: () -> Unit,
    accentColor: Color,
    contentColor: Color,
    onUpdateCustomLyrics: (String) -> Unit = {},
    currentLyricsText: String = "",
    songTitle: String = "",
    syncedLyrics: List<LyricLine>? = null,
    plainLyricsRaw: String? = null,
    modifier: Modifier = Modifier
) {
    var activeTabIndex by rememberSaveable { mutableIntStateOf(0) }
    // Raw provider string with FULL syntax (TTML, word-tagged LRC, etc.)
    // Try selected provider first; fall back to any provider with content.
    val rawProviderLyrics = remember(providerResults, selectedProvider) {
        // 1. Selected provider's synced raw string
        providerResults[selectedProvider]?.syncedLyrics?.takeIf { it.isNotBlank() }
        // 2. Selected provider's plain raw string
            ?: providerResults[selectedProvider]?.plainLyrics?.takeIf { it.isNotBlank() }
        // 3. Any provider with synced content (picks highest-priority one found)
            ?: providerResults.values.firstOrNull { !it.syncedLyrics.isNullOrBlank() }?.syncedLyrics
        // 4. Any provider with plain content
            ?: providerResults.values.firstOrNull { !it.plainLyrics.isNullOrBlank() }?.plainLyrics
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val haptic = LocalHapticFeedback.current

        // Expressive Material 3 ButtonGroup Tab Switcher (3 Tabs: Providers, Sync Control, Manage)
        ButtonGroup(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            expandedRatio = 0.25f,
            overflowIndicator = {}
        ) {
            val scope = this
            val tabs = listOf(
                Triple(0, R.drawable.ic_lyrics, "Providers"),
                Triple(1, R.drawable.ic_timer, "Sync"),
                Triple(2, null, "Manage")
            )
            tabs.forEach { (tabIdx, iconRes, label) ->
                val checked = activeTabIndex == tabIdx
                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val cornerRadius by animateDpAsState(
                            targetValue = if (isPressed) 6.dp else if (checked) 20.dp else 10.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "LyricsTabCorner"
                        )

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeTabIndex = tabIdx
                            },
                            modifier = with(scope) {
                                Modifier
                                    .animateWidth(interactionSource)
                                    .weight(1f)
                                    .height(48.dp)
                            },
                            interactionSource = interactionSource,
                            shape = RoundedCornerShape(cornerRadius),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (tabIdx == 2) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = label,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else if (iconRes != null) {
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = label,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                                )
                                if (tabIdx == 1 && syncOffsetMs != 0L) {
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.US, "%+.1fs", syncOffsetMs / 1000f),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    },
                    menuContent = {}
                )
            }
        }

        // Tab Content with Seamless Animated Horizontal Transition
        AnimatedContent(
            targetState = activeTabIndex,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(
                        initialOffsetX = { width -> width },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { width -> -width },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                        )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { width -> -width },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { width -> width },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                        )
                }
            },
            label = "LyricsSheetTabTransition",
            modifier = Modifier.fillMaxWidth()
        ) { tabIndex ->
            when (tabIndex) {
                0 -> {
                    ProvidersListTab(
                        providerResults = providerResults,
                        selectedProvider = selectedProvider,
                        onProviderSelected = onProviderSelected,
                        onRefindClick = onRefindClick,
                        accentColor = accentColor,
                        contentColor = contentColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                1 -> {
                    SyncControlTab(
                        syncOffsetMs = syncOffsetMs,
                        onSyncOffsetChange = onSyncOffsetChange,
                        accentColor = accentColor,
                        contentColor = contentColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                2 -> {
                    ManageLyricsTab(
                        onUpdateCustomLyrics = onUpdateCustomLyrics,
                        currentLyricsText = currentLyricsText,
                        songTitle = songTitle,
                        syncedLyrics = syncedLyrics,
                        plainLyricsRaw = plainLyricsRaw,
                        rawLyricsContent = rawProviderLyrics,
                        accentColor = accentColor,
                        contentColor = contentColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProvidersListTab(
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onRefindClick: () -> Unit,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val knownProviders = remember {
        listOf(
            "Local Cache",
            "EmbeddedID3",
            "BetterLyrics",
            "Better Lyrics Portato",
            "Musixmatch",
            "Unison",
            "BiniLyrics",
            "Kouzu",
            "YouLyPlus",
            "SyncLRC",
            "Paxsenix: Apple Music",
            "Paxsenix: Musixmatch",
            "YouTube Subtitle",
            "LrcLib",
            "KuGou",
            "Paxsenix: Spotify",
            "Paxsenix: Netease",
            "Paxsenix: YouTube",
            "YouTube Music",
            "SimpMusic"
        )
    }

    val allProviders = remember(providerResults) {
        (knownProviders + providerResults.keys).distinct()
    }

    val sortedProviders = remember(allProviders, providerResults, selectedProvider) {
        allProviders.sortedWith(
            compareByDescending<String> { selectedProvider == it }
                .thenByDescending { providerResults[it]?.hasLyrics() == true }
                .thenByDescending { !providerResults.containsKey(it) }
        )
    }

    val isSearchingAny = allProviders.any { !providerResults.containsKey(it) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            sortedProviders.forEachIndexed { index, provider ->
                val result = providerResults[provider]

                val isSelected = selectedProvider == provider || (selectedProvider == null && result != null && result.confidence >= 0f && result == providerResults.values.filter { it.confidence >= 0f && it.hasLyrics() }.maxWithOrNull(
                    compareBy<LyricsResult> { it.isWordByWord }
                        .thenBy { it.isSynced }
                        .thenBy { it.confidence }
                ))

                val hasData = result != null && result.hasLyrics() && result.confidence >= 0f

                val displayName = when {
                    provider == "EmbeddedID3" -> "Embedded ID3"
                    provider == "Unison" -> "Unison (Community)"
                    provider == "BiniLyrics" -> "BiniLyrics (TTML)"
                    provider == "Kouzu" -> "Kouzu MLC (Karaoke)"
                    provider == "Better Lyrics Portato" -> "Portato (QQ)"
                    provider == "Musixmatch" -> "Musixmatch"
                    provider == "BetterLyrics" -> "Unified Lyrics"
                    provider.startsWith("BetterLyrics") -> provider.replace("BetterLyrics", "Unified Lyrics")
                    provider == "Paxsenix: Apple Music" -> "Apple Music"
                    provider == "Paxsenix: Musixmatch" -> "Musixmatch (Paxsenix)"
                    provider == "YouTube Subtitle" -> "YouTube Captions"
                    else -> provider
                }

                val isSyllable = result?.isWordByWord == true && (
                    result.syncedLyrics?.contains("<tt") == true ||
                    result.syncedLyrics?.contains("<span") == true ||
                    result.providerName?.contains("Unison") == true ||
                    result.providerName?.contains("Bini") == true ||
                    result.providerName?.contains("Apple Music") == true
                )

                val supportingText = when {
                    !providerResults.containsKey(provider) -> "Searching..."
                    result == null -> "Searching..."
                    !hasData -> "No lyrics found"
                    isSyllable -> "✦ Syllable-synced (TTML Richsync)"
                    result.isWordByWord -> "★ Word-synced (Karaoke)"
                    result.isSynced -> "Line-synced (LRC)"
                    else -> "Plain Text"
                }

                val shape = getSegmentedShape(index = index, count = sortedProviders.size)

                val itemBgColor = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    hasData -> MaterialTheme.colorScheme.surfaceContainer
                    !providerResults.containsKey(provider) -> MaterialTheme.colorScheme.surfaceContainerLow
                    else -> MaterialTheme.colorScheme.surfaceContainerLowest
                }

                ListItem(
                    selected = isSelected,
                    onClick = { onProviderSelected(provider) },
                    supportingContent = {
                        Text(text = supportingText)
                    },
                    leadingContent = {
                        val iconRes = when {
                            provider.contains("YouTube", ignoreCase = true) -> R.drawable.ic_youtube_music
                            else -> R.drawable.ic_lyrics
                        }
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = provider,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Selected",
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (!providerResults.containsKey(provider)) {
                            androidx.compose.material3.CircularWavyProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = itemBgColor,
                        selectedContainerColor = itemBgColor,
                        contentColor = if (hasData || !providerResults.containsKey(provider)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        leadingContentColor = if (hasData || !providerResults.containsKey(provider)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTrailingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        supportingContentColor = if (hasData) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        disabledContainerColor = itemBgColor,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        disabledLeadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        disabledTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                ) {
                    Text(
                        text = displayName,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        if (isSearchingAny) {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        val buttonInteractionSource = remember { MutableInteractionSource() }
        val buttonIsPressed by buttonInteractionSource.collectIsPressedAsState()

        val buttonCornerRadius by animateDpAsState(
            targetValue = if (buttonIsPressed) 28.dp else 16.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "refind_button_corners"
        )

        FilledTonalButton(
            onClick = onRefindClick,
            interactionSource = buttonInteractionSource,
            shape = RoundedCornerShape(buttonCornerRadius),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
                    contentDescription = "Refind and Research",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refind & Re-search All Providers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SyncControlTab(
    syncOffsetMs: Long,
    onSyncOffsetChange: (Long) -> Unit,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Top Header Badge: Dynamic Sync Status ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (syncOffsetMs != 0L) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_timer),
                contentDescription = "Sync Offset",
                tint = if (syncOffsetMs != 0L) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (syncOffsetMs == 0L) "0.0s (In Sync)" else String.format(java.util.Locale.US, "%+.2fs Offset", syncOffsetMs / 1000f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (syncOffsetMs != 0L) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }

        // ── Lyrics Blur Effect Setting Card ──
        val context = LocalContext.current
        val prefs = remember(context) { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
        var isLyricsBlurEnabled by remember { mutableStateOf(prefs.getBoolean("lyrics_blur_enabled", true)) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lyrics),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Lyrics Blur Effect",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Depth-of-field blur on inactive lines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            androidx.compose.material3.Switch(
                checked = isLyricsBlurEnabled,
                onCheckedChange = { enabled ->
                    isLyricsBlurEnabled = enabled
                    prefs.edit().putBoolean("lyrics_blur_enabled", enabled).apply()
                }
            )
        }

        // ── 100% Native Google Material 3 Connected Preset Button Row ──
        val presetValues = remember {
            listOf(
                -500L to "-0.5s",
                -100L to "-0.1s",
                0L to "Reset", // Exact Center (Index 2)
                100L to "+0.1s",
                500L to "+0.5s"
            )
        }

        // ── Connected Material 3 Expressive Presets Button Group (Clickable Action Buttons with Neighbor Pushing) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presetValues.forEachIndexed { index, (delta, label) ->
                val isReset = delta == 0L
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                // Dynamic M3 Expressive Neighbor Pushing Weight Physics (Clickable Press Motion)
                val targetWeight = if (isPressed) 1.55f else 1.00f
                val animatedWeight by animateFloatAsState(
                    targetValue = targetWeight,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "clickableNeighborPushingWeight"
                )

                // 100% Fixed Stable Connected Shapes
                val buttonShape = when (index) {
                    0 -> RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                    2 -> RoundedCornerShape(8.dp)
                    4 -> RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp, topStart = 6.dp, bottomStart = 6.dp)
                    else -> RoundedCornerShape(6.dp)
                }

                val containerColor = when {
                    isReset && syncOffsetMs != 0L -> MaterialTheme.colorScheme.primaryContainer
                    isPressed -> MaterialTheme.colorScheme.surfaceContainerHighest
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }

                val itemContentColor = when {
                    isReset && syncOffsetMs != 0L -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }

                FilledTonalButton(
                    onClick = {
                        if (isReset) {
                            onSyncOffsetChange(0L)
                        } else {
                            onSyncOffsetChange(syncOffsetMs + delta)
                        }
                    },
                    interactionSource = interactionSource,
                    shape = buttonShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = containerColor,
                        contentColor = itemContentColor
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(animatedWeight)
                        .height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isReset) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_refresh),
                                contentDescription = "Reset",
                                tint = itemContentColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isReset && syncOffsetMs != 0L) FontWeight.ExtraBold else FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun getSegmentedShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 24.dp
    val inner = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

@Composable
private fun ManageLyricsTab(
    onUpdateCustomLyrics: (String) -> Unit,
    currentLyricsText: String,
    songTitle: String,
    syncedLyrics: List<LyricLine>? = null,
    plainLyricsRaw: String? = null,
    rawLyricsContent: String? = null,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportIdentityDialog by remember { mutableStateOf(false) }
    var showPasteJwtDialog by remember { mutableStateOf(false) }
    var currentIdentity by remember { mutableStateOf(DeviceKeyIdentityManager.getOrCreateIdentity(context)) }
    val petName = remember(currentIdentity.keyId) { DeviceKeyIdentityManager.getPetName(currentIdentity.keyId) }
    var tokenStatus by remember { mutableStateOf(com.codetrio.spatialflow.data.lyrics.security.BetterLyricsTokenManager.getTokenStatus(context)) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    onUpdateCustomLyrics(text)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Imported lyrics successfully")
                } else {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Selected file is empty")
                }
            } catch (e: Exception) {
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to read file: ${e.localizedMessage}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = { showImportDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_download),
                    contentDescription = "Import",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }

            FilledTonalButton(
                onClick = { showExportDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_share),
                    contentDescription = "Export",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }

        Button(
            onClick = { showEditDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Edit",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Edit Lyrics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }

        // ── Cryptographic Device Identity Card ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = "Device Identity",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Device Cryptographic Identity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = petName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = "Key ID: ${currentIdentity.keyId.take(12)}…${currentIdentity.keyId.takeLast(8)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Device KeyID", currentIdentity.keyId))
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Copied Key ID")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Copy Key ID", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = {
                        val json = DeviceKeyIdentityManager.exportIdentityJson(context)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Device Identity JSON", json))
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Exported identity JSON to clipboard")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Export JSON", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = { showImportIdentityDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Import JSON", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ── Unified Token (Portato & Musixmatch) ─────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Unified Lyrics Stream Token",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Powers Portato (QQ) & Musixmatch rich-sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (tokenStatus.isValid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (tokenStatus.isValid) "Active" else if (tokenStatus.hasToken) "Expired" else "Missing",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (tokenStatus.isValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (tokenStatus.isValid) {
                val hours = tokenStatus.expiresInSeconds / 3600
                val minutes = (tokenStatus.expiresInSeconds % 3600) / 60
                Text(
                    text = "Token: ${tokenStatus.tokenPreview} (valid for ${hours}h ${minutes}m)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Auto-refreshes when playing, or paste from extension",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Solving challenge...")
                            val newToken = com.codetrio.spatialflow.data.lyrics.security.BetterLyricsTokenManager.getOrRefreshToken(context, forceRefresh = true)
                            tokenStatus = com.codetrio.spatialflow.data.lyrics.security.BetterLyricsTokenManager.getTokenStatus(context)
                            if (newToken != null) {
                                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Token refreshed successfully!")
                            } else {
                                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Could not auto-refresh. You can paste JWT manually.")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Refresh Token", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = { showPasteJwtDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Paste JWT", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showImportIdentityDialog) {
        var importJsonText by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showImportIdentityDialog = false },
            title = { Text("Import Device Identity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste a valid JSON identity file (v1) below to synchronize credentials.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = {
                            importJsonText = it
                            errorMessage = null
                        },
                        placeholder = { Text("Paste JSON here...") },
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = DeviceKeyIdentityManager.importIdentityJson(context, importJsonText.trim())
                        result.fold(
                            onSuccess = { imported ->
                                currentIdentity = imported
                                val newPet = DeviceKeyIdentityManager.getPetName(imported.keyId)
                                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Identity imported: $newPet")
                                showImportIdentityDialog = false
                            },
                            onFailure = { err ->
                                errorMessage = err.localizedMessage ?: "Invalid identity format"
                            }
                        )
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportIdentityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPasteJwtDialog) {
        var jwtInputText by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPasteJwtDialog = false },
            title = { Text("Paste Unified Lyrics Token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste the jwtToken to enable Portato & Musixmatch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = jwtInputText,
                        onValueChange = {
                            jwtInputText = it
                            errorMessage = null
                        },
                        placeholder = { Text("eyJhbGciOi...") },
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = com.codetrio.spatialflow.data.lyrics.security.BetterLyricsTokenManager.setCustomJwt(context, jwtInputText.trim())
                        if (success) {
                            tokenStatus = com.codetrio.spatialflow.data.lyrics.security.BetterLyricsTokenManager.getTokenStatus(context)
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("JWT Token saved successfully!")
                            showPasteJwtDialog = false
                        } else {
                            errorMessage = "Invalid JWT token format or expired"
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteJwtDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        // Fallback priority:
        // 1. rawLyricsContent — full raw provider string with all word-level/TTML syntax
        // 2. plainLyricsRaw — unprocessed lyrics string from storage/custom
        // 3. currentLyricsText — line-level LRC (last resort, word timestamps stripped)
        val editInitialText = rawLyricsContent?.takeIf { it.isNotBlank() }
            ?: plainLyricsRaw?.takeIf { it.isNotBlank() }
            ?: currentLyricsText
        EditLyricsDialog(
            initialLyrics = editInitialText,
            songTitle = songTitle,
            accentColor = accentColor,
            onSave = { editedText ->
                onUpdateCustomLyrics(editedText)
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Lyrics saved")
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showImportDialog) {
        ImportLyricsDialog(
            accentColor = accentColor,
            onChooseFile = {
                showImportDialog = false
                filePickerLauncher.launch("*/*")
            },
            onPasteClipboard = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()
                    if (!text.isNullOrBlank()) {
                        onUpdateCustomLyrics(text)
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Lyrics imported from clipboard")
                    } else {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Clipboard is empty")
                    }
                } else {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Clipboard is empty")
                }
                showImportDialog = false
            },
            onFetchUrl = { url ->
                showImportDialog = false
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val text = java.net.URL(url).readText()
                        if (text.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                onUpdateCustomLyrics(text)
                                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Lyrics imported from URL")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to fetch URL: ${e.localizedMessage}")
                        }
                    }
                }
            },
            onDismiss = { showImportDialog = false }
        )
    }

    if (showExportDialog) {
        ExportLyricsDialog(
            syncedLyrics = syncedLyrics,
            plainLyricsRaw = plainLyricsRaw,
            songTitle = songTitle,
            accentColor = accentColor,
            onDismiss = { showExportDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLyricsDialog(
    initialLyrics: String,
    songTitle: String,
    accentColor: Color,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialLyrics) }
    val formatBadge = remember(text) {
        when {
            text.contains("<tt") || text.contains("<p begin=") -> "TTML XML"
            text.matches(Regex("(?s).*\\[\\d{2}:\\d{2}\\.\\d{2,3}].*")) -> "LRC Synced"
            text.isNotBlank() -> "Plain Text"
            else -> "Empty"
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
        ) {
            // ── Top bar ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit Lyrics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = songTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        keyboardController?.hide()
                        onDismiss()
                    }) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            onSave(text)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Format badge ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Format: $formatBadge",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Full-height editor (resizes when keyboard opens) ──────────
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .imePadding(),
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Paste or type LRC / TTML / Plain lyrics here...") },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            )
        }
    }
}


@Composable
private fun ImportLyricsDialog(
    accentColor: Color,
    onChooseFile: () -> Unit,
    onPasteClipboard: () -> Unit,
    onFetchUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showUrlInput by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Lyrics", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!showUrlInput) {
                    ListItem(
                        headlineContent = { Text("Pick File (.lrc, .ttml, .srt, .txt)") },
                        leadingContent = { Icon(painter = painterResource(id = R.drawable.ic_download), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            headlineColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onChooseFile)
                    )
                    ListItem(
                        headlineContent = { Text("Paste from Clipboard") },
                        leadingContent = { Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            headlineColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onPasteClipboard)
                    )
                    ListItem(
                        headlineContent = { Text("Import from Direct URL") },
                        leadingContent = { Icon(painter = painterResource(id = R.drawable.ic_explore), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            headlineColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showUrlInput = true }
                    )
                } else {
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("Lyrics Direct URL") },
                        placeholder = { Text("https://example.com/song.lrc") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            if (showUrlInput) {
                TextButton(
                    onClick = { if (urlText.isNotBlank()) onFetchUrl(urlText) }
                ) {
                    Text("Fetch", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExportLyricsDialog(
    syncedLyrics: List<LyricLine>?,
    plainLyricsRaw: String?,
    songTitle: String,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf<String?>(null) }

    // Format options
    data class FormatOption(val label: String, val ext: String)
    val hasSynced = !syncedLyrics.isNullOrEmpty()
    val formats = remember(hasSynced) {
        buildList {
            if (hasSynced) {
                add(FormatOption("LRC (Synced)", "lrc"))
                add(FormatOption("TTML / XML (Word-sync)", "ttml"))
                add(FormatOption("SRT Subtitles", "srt"))
                add(FormatOption("Plain Text + Timestamps", "txt-ts"))
            }
            add(FormatOption("Plain Text", "txt"))
        }
    }

    fun buildText(ext: String): String = when (ext) {
        "lrc" -> if (!syncedLyrics.isNullOrEmpty()) lyricsToLrc(syncedLyrics) else plainLyricsRaw ?: ""
        "ttml" -> if (!syncedLyrics.isNullOrEmpty()) lyricsToTtml(syncedLyrics, songTitle) else plainLyricsRaw ?: ""
        "srt" -> if (!syncedLyrics.isNullOrEmpty()) lyricsToSrt(syncedLyrics) else plainLyricsRaw ?: ""
        "txt-ts" -> if (!syncedLyrics.isNullOrEmpty()) lyricsToPlainWithTimestamps(syncedLyrics) else plainLyricsRaw ?: ""
        else -> plainLyricsRaw ?: if (!syncedLyrics.isNullOrEmpty()) syncedLyrics.filter { !it.isInterlude }.joinToString("\n") { it.content } else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (selectedFormat == null) "Export Format" else "Export as ${selectedFormat!!.uppercase()}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selectedFormat == null) {
                    // Step 1: format selection
                    Text(
                        text = "Choose export format:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    formats.forEach { fmt ->
                        ListItem(
                            headlineContent = { Text(fmt.label) },
                            trailingContent = {
                                Text(
                                    ".${fmt.ext.replace("-ts", "")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                headlineColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedFormat = fmt.ext }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                } else {
                    // Step 2: action selection
                    val lyricsText = buildText(selectedFormat!!)
                    val displayExt = selectedFormat!!.replace("-ts", "")
                    ListItem(
                        headlineContent = { Text("Copy to Clipboard") },
                        leadingContent = { Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            headlineColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Lyrics ($displayExt) for $songTitle", lyricsText))
                                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Lyrics copied as .$displayExt")
                                onDismiss()
                            }
                    )
                    Spacer(Modifier.height(4.dp))
                    ListItem(
                        headlineContent = { Text("Share via Apps") },
                        leadingContent = { Icon(painter = painterResource(id = R.drawable.ic_share), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            headlineColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Lyrics for $songTitle.$displayExt")
                                    putExtra(Intent.EXTRA_TEXT, lyricsText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Lyrics as .$displayExt"))
                                onDismiss()
                            }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = {
                    if (selectedFormat != null) selectedFormat = null else onDismiss()
                }
            ) {
                Text(if (selectedFormat != null) "Back" else "Cancel")
            }
        }
    )
}

private fun formatLyricsTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

// ── Format converters ──────────────────────────────────────────────────────

private fun lyricsToLrc(lines: List<LyricLine>): String =
    lines.filter { !it.isInterlude }.joinToString("\n") { line ->
        val mm = line.startTimeMs / 60000
        val ss = (line.startTimeMs % 60000) / 1000
        val cs = (line.startTimeMs % 1000) / 10
        "[${mm.toString().padStart(2,'0')}:${ss.toString().padStart(2,'0')}.${cs.toString().padStart(2,'0')}]${line.content}"
    }

private fun lyricsToPlainWithTimestamps(lines: List<LyricLine>): String =
    lines.filter { !it.isInterlude }.joinToString("\n") { line ->
        val mm = line.startTimeMs / 60000
        val ss = (line.startTimeMs % 60000) / 1000
        "[${mm.toString().padStart(2,'0')}:${ss.toString().padStart(2,'0')}] ${line.content}"
    }

private fun lyricsToSrt(lines: List<LyricLine>): String {
    val filtered = lines.filter { !it.isInterlude }
    return filtered.mapIndexed { i, line ->
        val begin = srtTime(line.startTimeMs)
        val end = srtTime(filtered.getOrNull(i + 1)?.startTimeMs ?: (line.startTimeMs + 5000L))
        "${i + 1}\n$begin --> $end\n${line.content}\n"
    }.joinToString("\n")
}

private fun srtTime(ms: Long): String {
    val h = ms / 3600000
    val m = (ms % 3600000) / 60000
    val s = (ms % 60000) / 1000
    val millis = ms % 1000
    return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", h, m, s, millis)
}

private fun lyricsToTtml(lines: List<LyricLine>, songTitle: String): String {
    val filtered = lines.filter { !it.isInterlude }
    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.appendLine("""<tt xml:lang="en" xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">""")
    sb.appendLine("""  <head><metadata><ttm:title>$songTitle</ttm:title></metadata></head>""")
    sb.appendLine("""  <body><div>""")
    filtered.forEachIndexed { i, line ->
        val begin = ttmlTime(line.startTimeMs)
        val end = ttmlTime(filtered.getOrNull(i + 1)?.startTimeMs ?: (line.startTimeMs + 5000L))
        sb.appendLine("""    <p begin="$begin" end="$end">${line.content}</p>""")
    }
    sb.appendLine("""  </div></body>""")
    sb.append("""</tt>""")
    return sb.toString()
}

private fun ttmlTime(ms: Long): String {
    val h = ms / 3600000
    val m = (ms % 3600000) / 60000
    val s = (ms % 60000) / 1000
    val millis = ms % 1000
    return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
}
