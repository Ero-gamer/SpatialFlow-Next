package com.codetrio.spatialflow.player.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.innertube.InnerTubeClient
import com.codetrio.spatialflow.data.innertube.InnerTubeParser
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.data.innertube.path
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.deriveArtworkSurfaceColor
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

// --- 1. Strategies Section ---

interface Queue {
    suspend fun getInitialStatus(): Status
    fun hasNextPage(): Boolean
    suspend fun nextPage(): List<SongItem>
    
    val currentTitle: String?
    val preloadItem: SongItem?

    fun shouldExpandToFullQueueWhenAutoLoadMoreDisabled(): Boolean = false

    data class Status(
        val title: String?,
        val items: List<SongItem>,
        val mediaItemIndex: Int = 0,
        val position: Long = 0L
    )
}

class StaticQueue(
    override val currentTitle: String? = null,
    private val initialItems: List<SongItem>,
    override val preloadItem: SongItem? = null,
    private val startIndex: Int = 0,
    private val position: Long = 0L
) : Queue {

    override suspend fun getInitialStatus(): Queue.Status {
        return Queue.Status(
            title = currentTitle,
            items = initialItems,
            mediaItemIndex = startIndex,
            position = position
        )
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<SongItem> = emptyList()
}

open class YouTubeQueue(
    protected val videoId: String? = null,
    protected val playlistId: String? = null,
    protected val initialParams: String? = null,
    protected var continuationToken: String? = null,
    override val preloadItem: SongItem? = null,
    protected val followAutomixPreview: Boolean = true,
    protected val startIndex: Int = 0,
    protected val position: Long = 0L
) : Queue {

    override var currentTitle: String? = null
        protected set

    companion object {
        fun radio(song: SongItem, params: String? = null) = YouTubeQueue(
            videoId = song.videoId,
            playlistId = song.videoId?.let { "RDAMVM$it" },
            initialParams = params,
            preloadItem = song,
            followAutomixPreview = true
        )

        fun playlist(
            playlistId: String,
            videoId: String? = null,
            initialParams: String? = null,
            preloadItem: SongItem? = null,
            startIndex: Int = 0,
            followAutomixPreview: Boolean = true
        ) = YouTubeQueue(
            videoId = videoId,
            playlistId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId",
            initialParams = initialParams,
            preloadItem = preloadItem,
            followAutomixPreview = followAutomixPreview,
            startIndex = startIndex
        )
    }

    override suspend fun getInitialStatus(): Queue.Status {
        val status = fetchNext(isInitial = true)
        val preload = preloadItem
        val matchedIndex = if (preload != null) {
            status.items.indexOfFirst { it.videoId == preload.videoId }
        } else {
            startIndex
        }
        val finalIndex = if (matchedIndex >= 0) matchedIndex else 0
        return Queue.Status(
            title = status.title,
            items = status.items,
            mediaItemIndex = finalIndex,
            position = position
        )
    }

    override fun hasNextPage(): Boolean {
        return continuationToken != null
    }

    override suspend fun nextPage(): List<SongItem> {
        if (continuationToken == null) return emptyList()
        return fetchNext(isInitial = false).items
    }

    private suspend fun fetchNext(isInitial: Boolean): Queue.Status = withContext(Dispatchers.IO) {
        val response = if (isInitial) {
            InnerTubeClient.next(videoId = videoId, playlistId = playlistId, params = initialParams)
        } else {
            InnerTubeClient.next(videoId = null, continuation = continuationToken)
        }

        val songs = mutableListOf<SongItem>()
        
        val playlistPanel = if (isInitial) {
            findJsonObjectRecursively(response, "playlistPanelRenderer")
        } else {
            findJsonObjectRecursively(response, "playlistPanelRenderer") ?: 
            response.path("continuationContents.playlistPanelContinuation")?.asJsonObject
        }

        if (isInitial) {
            currentTitle = playlistPanel?.path("title")?.asString
        }

        val playlistContents = playlistPanel?.path("contents")?.asJsonArray

        playlistContents?.forEach { content ->
            val renderer = content.asJsonObject.path("playlistPanelVideoRenderer")?.asJsonObject ?: return@forEach
            val id = renderer.path("videoId")?.asString ?: return@forEach
            val titleText = renderer.path("title.runs.0.text")?.asString ?: return@forEach
            val shortByline = renderer.path("shortBylineText.runs")?.asJsonArray
                ?.joinToString("") { it.asJsonObject.path("text")?.asString ?: "" } ?: ""
            val lengthText = renderer.path("lengthText.runs.0.text")?.asString
            val thumb = InnerTubeParser.getHighResThumbnailUrl(
                renderer.path("thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.path("url")?.asString
            )

            songs.add(SongItem.createOnlineSong(
                videoId = id,
                title = titleText,
                artist = shortByline.trim(),
                streamUrl = "",
                durationMs = parseDuration(lengthText),
                thumbnailUrl = thumb
            ))
        }

        continuationToken = YouTubeMusic.extractContinuationToken(playlistPanel, playlistContents)
            ?: playlistPanel?.path("continuations.0.nextRadioContinuationData.continuation")?.asString
            ?: playlistPanel?.path("continuations.0.nextContinuationData.continuation")?.asString

        val automixRenderer = findJsonObjectRecursively(response, "automixPreviewVideoRenderer")
        if (followAutomixPreview && automixRenderer != null) {
            val watchPlaylistEndpoint = findJsonObjectRecursively(automixRenderer, "watchPlaylistEndpoint")
            val autoPlaylistId = watchPlaylistEndpoint?.path("playlistId")?.asString
            val autoParams = watchPlaylistEndpoint?.path("params")?.asString
            if (!autoPlaylistId.isNullOrEmpty()) {
                android.util.Log.d("YouTubeQueue", "Found automixPreviewVideoRenderer! Recursively resolving automix playlistId=$autoPlaylistId, params=$autoParams")
                try {
                    val autoResponse = InnerTubeClient.next(videoId = null, playlistId = autoPlaylistId, params = autoParams)
                    val autoPlaylistPanel = findJsonObjectRecursively(autoResponse, "playlistPanelRenderer")
                    val autoPlaylistContents = autoPlaylistPanel?.path("contents")?.asJsonArray
                    
                    autoPlaylistContents?.forEach { content ->
                        val renderer = content.asJsonObject.path("playlistPanelVideoRenderer")?.asJsonObject ?: return@forEach
                        val id = renderer.path("videoId")?.asString ?: return@forEach
                        val titleText = renderer.path("title.runs.0.text")?.asString ?: return@forEach
                        val shortByline = renderer.path("shortBylineText.runs")?.asJsonArray
                            ?.joinToString("") { it.asJsonObject.path("text")?.asString ?: "" } ?: ""
                        val lengthText = renderer.path("lengthText.runs.0.text")?.asString
                        val thumb = InnerTubeParser.getHighResThumbnailUrl(
                            renderer.path("thumbnail.thumbnails")
                                ?.asJsonArray?.lastOrNull()?.asJsonObject?.path("url")?.asString
                        )

                        if (songs.none { it.videoId == id }) {
                            songs.add(SongItem.createOnlineSong(
                                videoId = id,
                                title = titleText,
                                artist = shortByline.trim(),
                                streamUrl = "",
                                durationMs = parseDuration(lengthText),
                                thumbnailUrl = thumb
                            ))
                        }
                    }
                    
                    val autoContinuation = YouTubeMusic.extractContinuationToken(autoPlaylistPanel, autoPlaylistContents)
                        ?: autoPlaylistPanel?.path("continuations.0.nextRadioContinuationData.continuation")?.asString
                        ?: autoPlaylistPanel?.path("continuations.0.nextContinuationData.continuation")?.asString
                    if (autoContinuation != null) {
                        continuationToken = autoContinuation
                    }
                } catch (e: Exception) {
                    android.util.Log.e("YouTubeQueue", "Failed recursively resolving automix playlist", e)
                }
            }
        }

        Queue.Status(title = currentTitle, items = songs)
    }

    private fun findJsonObjectRecursively(element: com.google.gson.JsonElement?, targetKey: String): com.google.gson.JsonObject? {
        if (element == null || !element.isJsonObject) return null
        val obj = element.asJsonObject
        if (obj.has(targetKey) && obj.get(targetKey).isJsonObject) {
            return obj.getAsJsonObject(targetKey)
        }
        for (entry in obj.entrySet()) {
            val child = entry.value
            if (child.isJsonObject) {
                val found = findJsonObjectRecursively(child, targetKey)
                if (found != null) return found
            } else if (child.isJsonArray) {
                for (item in child.asJsonArray) {
                    val found = findJsonObjectRecursively(item, targetKey)
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun parseDuration(duration: String?): Long {
        if (duration.isNullOrEmpty()) return 0L
        val parts = duration.split(":")
        var millis = 0L
        try {
            when (parts.size) {
                3 -> {
                    millis += parts[0].toLong() * 3600000L
                    millis += parts[1].toLong() * 60000L
                    millis += parts[2].toLong() * 1000L
                }
                2 -> {
                    millis += parts[0].toLong() * 60000L
                    millis += parts[1].toLong() * 1000L
                }
                1 -> {
                    millis += parts[0].toLong() * 1000L
                }
            }
        } catch (e: Exception) {
            // Ignore format errors
        }
        return millis
    }
}

/**
 * Dedicated Queue implementation for YouTube Music Playlists.
 * Automatically prepends "VL" if necessary, resolves playlist tracks, handles paged continuations,
 * and seamlessly transitions into Automix when autoplay is enabled.
 */
class YouTubePlaylistQueue(
    playlistId: String,
    videoId: String? = null,
    initialParams: String? = null,
    preloadItem: SongItem? = null,
    startIndex: Int = 0,
    followAutomixPreview: Boolean = true
) : YouTubeQueue(
    videoId = videoId,
    playlistId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId",
    initialParams = initialParams,
    preloadItem = preloadItem,
    followAutomixPreview = followAutomixPreview,
    startIndex = startIndex
)

/**
 * Dedicated Queue implementation for Local / Database Playlists.
 */
class LocalPlaylistQueue(
    val playlistTitle: String,
    private val songs: List<SongItem>,
    private val startIndex: Int = 0,
    private val position: Long = 0L
) : Queue {

    override val currentTitle: String = playlistTitle
    override val preloadItem: SongItem? = songs.getOrNull(startIndex)

    override suspend fun getInitialStatus(): Queue.Status {
        return Queue.Status(
            title = playlistTitle,
            items = songs,
            mediaItemIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0)),
            position = position
        )
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<SongItem> = emptyList()
}

// --- 2. Reordering Core Section ---

data class QueueItemWrapper(
    val key: String,
    val song: SongItem
)

// --- 3. UI Presentation Section ---

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun SlidingQueueDrawer(
    isQueueExpanded: Boolean,
    onQueueExpandedChange: (Boolean) -> Unit,
    songList: List<SongItem>,
    currentSongIndex: Int,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    sleepTimerMode: PlayerSharedViewModel.SleepTimerMode,
    onReorderQueue: (Int, Int) -> Unit,
    onPlaySongAtIndex: (Int) -> Unit,
    onRemoveSongAtIndex: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleLoopMode: () -> Unit,
    onShowSleepTimerDialog: () -> Unit,
    playerBackgroundColor: Int,
    dynamicAccentColor: Color,
    isDark: Boolean,
    isAutoplayEnabled: Boolean,
    onAutoplayToggle: (Boolean) -> Unit,
    queueTitle: String? = null,
    onSaveQueueClick: (() -> Unit)? = null,
    onClearUpcomingClick: (() -> Unit)? = null,
    onClearAllClick: (() -> Unit)? = null,
    // Real-time upward swipe distance from parent FullPlayer (for queue peek-open effect)
    peekOffsetPx: Float = 0f,
    peekOffsetPxProvider: (() -> Float)? = null,
    onQueueInteractingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val screenHeightPx = with(density) { screenHeight.toPx() }
    // 25% of screen height = dismiss threshold
    val dismissThresholdPx = screenHeightPx * 0.25f
    val coroutineScope = rememberCoroutineScope()

    // ── Hoisted list state so NestedScrollConnection can read scroll position ─────
    val lazyListState = rememberLazyListState()

    val slidingAnimatable = remember { Animatable(screenHeightPx + 100f) }

    // Expose current value as a State for graphicsLayer / derivedStateOf reads.
    val dragOffsetPx = slidingAnimatable.value

    // Reset drag when queue closes so it never leaks.
    LaunchedEffect(isQueueExpanded) {
        onQueueInteractingChanged(true)
        if (isQueueExpanded) {
            val peek = peekOffsetPxProvider?.invoke() ?: peekOffsetPx
            val startY = (screenHeightPx - peek).coerceAtMost(screenHeightPx)
            // Snap to the current peek position if starting from collapsed
            if (slidingAnimatable.value >= screenHeightPx - 5f) {
                slidingAnimatable.snapTo(startY)
            }
            slidingAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            slidingAnimatable.animateTo(
                targetValue = screenHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        onQueueInteractingChanged(false)
    }

    // ── NestedScrollConnection: intercepts downward overscroll from LazyColumn ──────
    // Handles the case where the user is swiping down while viewing the LIST.
    val haptic = LocalHapticFeedback.current
    val nestedScrollConnection = remember(isQueueExpanded) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val isAtTop = lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
                return if (available.y > 0f && isQueueExpanded && isAtTop) {
                    onQueueInteractingChanged(true)
                    coroutineScope.launch {
                        slidingAnimatable.snapTo((slidingAnimatable.value + available.y).coerceIn(0f, screenHeightPx))
                    }
                    available // fully consume — prevent overscroll glow
                } else if (available.y < 0f && slidingAnimatable.value > 0f) {
                    onQueueInteractingChanged(true)
                    val newDrag = (slidingAnimatable.value + available.y).coerceIn(0f, screenHeightPx)
                    val consumedY = slidingAnimatable.value - newDrag
                    coroutineScope.launch {
                        slidingAnimatable.snapTo(newDrag)
                    }
                    Offset(0f, -consumedY)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (slidingAnimatable.value > 0f) {
                    val shouldDismiss = if (available.y < -1500f) {
                        false
                    } else if (available.y > 1500f) {
                        true
                    } else {
                        slidingAnimatable.value > dismissThresholdPx
                    }
                    
                    if (shouldDismiss) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQueueExpandedChange(false)
                    } else {
                        coroutineScope.launch {
                            slidingAnimatable.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                    onQueueInteractingChanged(false)
                    if (shouldDismiss) available else Velocity.Zero
                } else {
                    onQueueInteractingChanged(false)
                    Velocity.Zero
                }
            }
        }
    }

    // ── Header draggable: handles swipes on the non-scrollable header area ────────
    val headerDraggableState = rememberDraggableState { delta ->
        coroutineScope.launch {
            slidingAnimatable.snapTo((slidingAnimatable.value + delta).coerceIn(0f, screenHeightPx))
        }
    }

    // ── Sliding fraction: 0 = fully open, 1 = fully dismissed ────────────────────
    val closeFraction by derivedStateOf {
        (slidingAnimatable.value / screenHeightPx).coerceIn(0f, 1f)
    }
    // Opening peek: how much the queue should rise from the bottom while the user
    // is swiping up before releasing (driven by FullPlayer's peekOffsetPx).
    val openFraction by derivedStateOf {
        val peek = peekOffsetPxProvider?.invoke() ?: peekOffsetPx
        (peek / screenHeightPx).coerceIn(0f, 0.6f)
    }

    val queueBgColor = remember(playerBackgroundColor, isDark) {
        val baseColor = if (playerBackgroundColor != 0) Color(playerBackgroundColor) else if (isDark) Color(0xFF141418) else Color(0xFFF2F2F7)
        deriveArtworkSurfaceColor(
            sourceColor = baseColor,
            isDark = isDark,
            darkLightness = 0.12f,
            lightLightness = 0.92f,
            darkSaturationRange = 0.28f..0.48f,
            lightSaturationRange = 0.24f..0.42f
        ).copy(alpha = 1f)
    }

    val queueTrayBackgroundColor = remember(playerBackgroundColor, isDark) {
        deriveArtworkSurfaceColor(
            sourceColor = Color(playerBackgroundColor),
            isDark = isDark,
            darkLightness = 0.18f,
            lightLightness = 0.80f,
            darkSaturationRange = 0.28f..0.52f,
            lightSaturationRange = 0.20f..0.44f
        ).copy(alpha = 1f)
    }

    val queueTrayInactiveButtonColor = remember(queueTrayBackgroundColor, isDark) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(queueTrayBackgroundColor.toArgb(), hsl)
        hsl[1] = if (hsl[1] < 0.08f) 0f else hsl[1].coerceIn(0.20f, 0.40f)
        hsl[2] = if (isDark) 0.28f else 0.72f
        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
    }

    val queueTrayActiveButtonColor = remember(dynamicAccentColor, isDark) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(dynamicAccentColor.toArgb(), hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.40f)
        hsl[2] = if (isDark) 0.58f else 0.42f
        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
    }

    val queueTrayInactiveContentColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)
    val queueTrayActiveContentColor = if (isDark) Color.Black else Color.White

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            // ── Sliding fraction visual effect ────────────────────────────────────
            // Scale during close-drag. Transform origin at top so the sheet
            // appears to "fall" away from the top edge.
            .graphicsLayer {
                val scale = 1f - closeFraction * 0.04f
                scaleX = scale
                scaleY = scale
                val peek = peekOffsetPxProvider?.invoke() ?: peekOffsetPx
                alpha = if (!isQueueExpanded && peek == 0f) 0f else 1f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            }
            // Combined offset: animated position + live close drag
            .offset {
                val peek = peekOffsetPxProvider?.invoke() ?: peekOffsetPx
                val isFullyClosed = !isQueueExpanded && peek == 0f && (slidingAnimatable.value >= screenHeightPx - 5f || !slidingAnimatable.isRunning)
                val currentY = if (isFullyClosed) {
                    100000f
                } else if (isQueueExpanded) {
                    slidingAnimatable.value
                } else {
                    screenHeightPx - peek
                }
                androidx.compose.ui.unit.IntOffset(
                    x = 0,
                    y = currentY.toInt().coerceAtLeast(0)
                )
            },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        color = queueBgColor,
        shadowElevation = 16.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // NestedScrollConnection sits here — inside Surface, outside LazyColumn.
                // Events bubble up from LazyColumn → this connection → stops here.
                .nestedScroll(nestedScrollConnection)
        ) {

            var localQueueItems by remember {
                mutableStateOf(songList.mapIndexed { idx, song ->
                    QueueItemWrapper("q_${song.id}_${System.identityHashCode(song)}_$idx", song)
                })
            }
            var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
            var lastMovedTo by remember { mutableStateOf<Int?>(null) }
            var searchQuery by remember { mutableStateOf("") }
            var isSearchExpanded by remember { mutableStateOf(false) }

            val filteredQueueItems = remember(localQueueItems, searchQuery) {
                if (searchQuery.isEmpty()) {
                    localQueueItems
                } else {
                    localQueueItems.filter {
                        it.song.title.contains(searchQuery, ignoreCase = true) ||
                                it.song.artist.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            val reorderableState = rememberReorderableLazyListState(
                lazyListState = lazyListState,
                onMove = { from, to ->
                    val fromIndex = localQueueItems.indexOfFirst { it.key == from.key }
                    val toIndex = localQueueItems.indexOfFirst { it.key == to.key }
                    if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                        localQueueItems = localQueueItems.toMutableList().apply {
                            add(toIndex, removeAt(fromIndex))
                        }
                        if (lastMovedFrom == null) {
                            lastMovedFrom = fromIndex
                        }
                        lastMovedTo = toIndex
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            )

            LaunchedEffect(songList) {
                if (!reorderableState.isAnyItemDragging) {
                    val currentSongsInLocal = localQueueItems.map { it.song }
                    if (currentSongsInLocal != songList) {
                        val pool = localQueueItems.toMutableList()
                        val newWrappers = songList.mapIndexed { idx, song ->
                            val matchIndex = pool.indexOfFirst { it.song.id == song.id }
                            if (matchIndex != -1) {
                                pool.removeAt(matchIndex)
                            } else {
                                QueueItemWrapper("q_${song.id}_${System.currentTimeMillis()}_$idx", song)
                            }
                        }
                        localQueueItems = newWrappers
                    }
                }
            }

            LaunchedEffect(reorderableState.isAnyItemDragging) {
                if (!reorderableState.isAnyItemDragging) {
                    val fromIdx = lastMovedFrom
                    val toIdx = lastMovedTo
                    lastMovedFrom = null
                    lastMovedTo = null

                    if (fromIdx != null && toIdx != null && fromIdx != toIdx) {
                        onReorderQueue(fromIdx, toIdx)
                    }
                }
            }

            LaunchedEffect(isQueueExpanded, currentSongIndex) {
                if (isQueueExpanded && currentSongIndex in songList.indices) {
                    val currentSongId = songList[currentSongIndex].id
                    val localIndex = localQueueItems.indexOfFirst { it.song.id == currentSongId }
                    if (localIndex != -1) {
                        val distance = abs(lazyListState.firstVisibleItemIndex - localIndex)
                        if (distance > 24) {
                            lazyListState.scrollToItem(localIndex)
                        } else {
                            lazyListState.animateScrollToItem(localIndex)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // ── Draggable header area: handle pill + title row + subtitle/controls ──────
                // These are siblings of the LazyColumn, so NestedScrollConnection never
                // sees their touch events. We add a separate draggable() here, which
                // only fires for the header region, preventing any LazyColumn conflict.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = headerDraggableState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { onQueueInteractingChanged(true) },
                            onDragStopped = { velocity ->
                                onQueueInteractingChanged(false)
                                coroutineScope.launch {
                                    val shouldDismiss = if (velocity < -1500f) {
                                        false
                                    } else if (velocity > 1500f) {
                                        true
                                    } else {
                                        slidingAnimatable.value > dismissThresholdPx
                                    }
                                    
                                    if (shouldDismiss) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onQueueExpandedChange(false)
                                    } else {
                                        slidingAnimatable.animateTo(
                                            0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            }
                        )
                ) {
                    Column {
                        // Handle pill — visual cue + tap-to-close
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onQueueExpandedChange(false) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Pill expands as close-fraction grows — gives tactile visual feedback
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = (36f + closeFraction * 20f).dp,
                                        height = 4.dp
                                    )
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        (if (isDark) Color.White else Color.Black)
                                            .copy(alpha = 0.2f + closeFraction * 0.3f)
                                    )
                            )
                        }

                        // Row 1: "Playing from" + Mix Title + Summary + Actions (Save, Clear)
                        val queueSummaryText = remember(songList, currentSongIndex) {
                            formatQueueSummaryText(songList, currentSongIndex)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Playing from",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = queueTitle ?: songList.getOrNull(currentSongIndex)?.artist?.let { "$it Mix" } ?: "Playback Queue",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp
                                    ),
                                    color = if (isDark) Color.White else Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = queueSummaryText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.55f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (onSaveQueueClick != null) {
                                    val saveInteractionSource = remember { MutableInteractionSource() }
                                    val saveIsPressed by saveInteractionSource.collectIsPressedAsState()
                                    val saveScale by animateFloatAsState(
                                        targetValue = if (saveIsPressed) 0.90f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "saveScale"
                                    )

                                    Surface(
                                        onClick = onSaveQueueClick,
                                        interactionSource = saveInteractionSource,
                                        shape = RoundedCornerShape(20.dp),
                                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.1f),
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = saveScale
                                            scaleY = saveScale
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlaylistAdd,
                                                contentDescription = "Save Queue to Playlist",
                                                tint = if (isDark) Color.White else Color.Black,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Save",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) Color.White else Color.Black
                                            )
                                        }
                                    }
                                }

                                if (onClearUpcomingClick != null || onClearAllClick != null) {
                                    var showClearMenu by remember { mutableStateOf(false) }
                                    val clearInteractionSource = remember { MutableInteractionSource() }
                                    val clearIsPressed by clearInteractionSource.collectIsPressedAsState()
                                    val clearScale by animateFloatAsState(
                                        targetValue = if (clearIsPressed) 0.90f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "clearScale"
                                    )

                                    Box {
                                        Surface(
                                            onClick = { showClearMenu = true },
                                            interactionSource = clearInteractionSource,
                                            shape = RoundedCornerShape(20.dp),
                                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.1f),
                                            modifier = Modifier.graphicsLayer {
                                                scaleX = clearScale
                                                scaleY = clearScale
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteSweep,
                                                    contentDescription = "Clear Queue Options",
                                                    tint = if (isDark) Color.White else Color.Black,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showClearMenu,
                                            onDismissRequest = { showClearMenu = false },
                                            modifier = Modifier.background(
                                                if (isDark) Color(0xFF222226) else Color(0xFFF4F4F8)
                                            )
                                        ) {
                                            if (onClearUpcomingClick != null) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "Clear Up Next",
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (isDark) Color.White else Color.Black
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Rounded.DeleteSweep,
                                                            contentDescription = null,
                                                            tint = if (isDark) Color.White else Color.Black
                                                        )
                                                    },
                                                    onClick = {
                                                        showClearMenu = false
                                                        onClearUpcomingClick()
                                                    }
                                                )
                                            }
                                            if (onClearAllClick != null) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "Clear All Except Current",
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Rounded.Clear,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    },
                                                    onClick = {
                                                        showClearMenu = false
                                                        onClearAllClick()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Divider below Song Title / Mix Title
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .height(1.dp)
                                .background((if (isDark) Color.White else Color.Black).copy(alpha = 0.08f))
                        )

                        // Autoplay Row (Bigger text + PlayArrow filled icon on thumb when checked)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Autoplay",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Text(
                                    text = "Add similar content for endless listening",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f)
                                )
                            }
                            Switch(
                                checked = isAutoplayEnabled,
                                onCheckedChange = onAutoplayToggle,
                                thumbContent = {
                                    if (isAutoplayEnabled) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    } else null
                                },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        // Divider below Autoplay Row (Full Width)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(1.dp)
                                .background((if (isDark) Color.White else Color.Black).copy(alpha = 0.08f))
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 4.dp,
                                bottom = 120.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val currentSongId = songList.getOrNull(currentSongIndex)?.id
                            itemsIndexed(
                                items = filteredQueueItems,
                                key = { _, item -> item.key },
                                contentType = { _, _ -> "queue-song" }
                            ) { index, item ->
                                val song = item.song
                                val isPlaying = (song.id == currentSongId)

                                if (searchQuery.isNotEmpty()) {
                                    val originalIndex = songList.indexOfFirst { it.id == song.id }
                                    RebuiltQueueListItem(
                                        song = song,
                                        isPlaying = isPlaying,
                                        isDark = isDark,
                                        dynamicAccentColor = dynamicAccentColor,
                                        onClick = {
                                            if (originalIndex != -1) {
                                                onPlaySongAtIndex(originalIndex)
                                            }
                                        },
                                        onRemoveClick = {
                                            if (originalIndex != -1) {
                                                onRemoveSongAtIndex(originalIndex)
                                            }
                                        },
                                        dragHandle = {}
                                    )
                                } else {
                                    ReorderableItem(
                                        state = reorderableState,
                                        key = item.key
                                    ) { isDragging ->
                                        val scale by animateFloatAsState(
                                            targetValue = if (isDragging) 1.03f else 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            label = "scaleAnimation"
                                        )

                                        RebuiltQueueListItem(
                                            song = song,
                                            isPlaying = isPlaying,
                                            isDark = isDark,
                                            dynamicAccentColor = dynamicAccentColor,
                                            onClick = {
                                                val currentIndexInList = localQueueItems.indexOfFirst { it.key == item.key }
                                                if (currentIndexInList != -1) {
                                                    onPlaySongAtIndex(currentIndexInList)
                                                }
                                            },
                                            onRemoveClick = {
                                                val currentIndexInList = localQueueItems.indexOfFirst { it.key == item.key }
                                                if (currentIndexInList != -1) {
                                                    onRemoveSongAtIndex(currentIndexInList)
                                                }
                                            },
                                            dragHandle = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .draggableHandle(
                                                            onDragStarted = {
                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                            },
                                                            onDragStopped = {
                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                            }
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Menu,
                                                        contentDescription = "Drag to reorder",
                                                        tint = (if (isDark) Color.White else Color.Black).copy(
                                                            alpha = 0.35f
                                                        ),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    compositingStrategy =
                                                        androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = queueTrayBackgroundColor,
                        shadowElevation = 8.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 20.dp, bottom = 24.dp)
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.ButtonGroup(
                                modifier = Modifier.fillMaxWidth(),
                                expandedRatio = 0.3f,
                                overflowIndicator = {}
                            ) {
                                val scope = this

                                customItem(
                                    buttonGroupContent = {
                                        val interactionSource =
                                            remember { MutableInteractionSource() }
                                        val isPressed by interactionSource.collectIsPressedAsState()
                                        val cornerRadius by animateDpAsState(
                                            targetValue = if (isPressed) 12.dp else 28.dp,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            ),
                                            label = "ShuffleCorner"
                                        )
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                onToggleShuffle()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            modifier = with(scope) {
                                                Modifier
                                                    .animateWidth(interactionSource)
                                                    .weight(1f)
                                                    .height(56.dp)
                                            },
                                            interactionSource = interactionSource,
                                            shape = RoundedCornerShape(cornerRadius),
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = if (isShuffleEnabled) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                                contentColor = if (isShuffleEnabled) queueTrayActiveContentColor else queueTrayInactiveContentColor
                                            ),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_shuffle),
                                                contentDescription = "Shuffle",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    menuContent = {}
                                )

                                customItem(
                                    buttonGroupContent = {
                                        val interactionSource =
                                            remember { MutableInteractionSource() }
                                        val isPressed by interactionSource.collectIsPressedAsState()
                                        val cornerRadius by animateDpAsState(
                                            targetValue = if (isPressed) 12.dp else 28.dp,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            ),
                                            label = "LoopCorner"
                                        )
                                        val loopIcon =
                                            if (repeatMode == PlayerSharedViewModel.REPEAT_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
                                        val loopActive =
                                            repeatMode != PlayerSharedViewModel.REPEAT_OFF
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                onToggleLoopMode()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            modifier = with(scope) {
                                                Modifier
                                                    .animateWidth(interactionSource)
                                                    .weight(1f)
                                                    .height(56.dp)
                                            },
                                            interactionSource = interactionSource,
                                            shape = RoundedCornerShape(cornerRadius),
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = if (loopActive) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                                contentColor = if (loopActive) queueTrayActiveContentColor else queueTrayInactiveContentColor
                                            ),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = loopIcon),
                                                contentDescription = "Repeat Mode",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    menuContent = {}
                                )

                                customItem(
                                    buttonGroupContent = {
                                        val interactionSource =
                                            remember { MutableInteractionSource() }
                                        val isPressed by interactionSource.collectIsPressedAsState()
                                        val cornerRadius by animateDpAsState(
                                            targetValue = if (isPressed) 12.dp else 28.dp,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            ),
                                            label = "TimerCorner"
                                        )
                                        val timerActive =
                                            sleepTimerMode != PlayerSharedViewModel.SleepTimerMode.OFF
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                onShowSleepTimerDialog()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            modifier = with(scope) {
                                                Modifier
                                                    .animateWidth(interactionSource)
                                                    .weight(1f)
                                                    .height(56.dp)
                                            },
                                            interactionSource = interactionSource,
                                            shape = RoundedCornerShape(cornerRadius),
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = if (timerActive) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                                contentColor = if (timerActive) queueTrayActiveContentColor else queueTrayInactiveContentColor
                                            ),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_timer),
                                                contentDescription = "Sleep Timer",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    menuContent = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatQueueDurationMs(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

private fun formatQueueSummaryText(songList: List<SongItem>, currentSongIndex: Int): String {
    val totalSongs = songList.size
    if (totalSongs == 0) return "0 songs"

    val remainingSongs = if (currentSongIndex in songList.indices) {
        songList.subList(currentSongIndex, songList.size)
    } else {
        songList
    }

    val totalDurationMs = remainingSongs.sumOf { it.duration }
    if (totalDurationMs <= 0) {
        return "$totalSongs songs"
    }

    val totalMinutes = totalDurationMs / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    val durationText = when {
        hours > 0 -> "${hours} hr ${minutes} min remaining"
        minutes > 0 -> "${minutes} min remaining"
        else -> "less than 1 min remaining"
    }

    return "$totalSongs songs • $durationText"
}

@Composable
private fun RebuiltQueueListItem(
    song: SongItem,
    isPlaying: Boolean,
    isDark: Boolean,
    dynamicAccentColor: Color,
    onClick: () -> Unit,
    onRemoveClick: (() -> Unit)?,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemBgColor = if (isPlaying) {
        dynamicAccentColor.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = itemBgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                com.codetrio.spatialflow.ui.explore.SpatialImage(
                    model = song,
                    contentDescription = null,
                    size = com.codetrio.spatialflow.ui.explore.ImageSemanticSize.ThumbnailSmall,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val transition = rememberInfiniteTransition(label = "equalizer")
                        val line1Scale by transition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 0.8f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 480, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "line1"
                        )
                        val line2Scale by transition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 0.95f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 400, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "line2"
                        )
                        val line3Scale by transition.animateFloat(
                            initialValue = 0.1f,
                            targetValue = 0.75f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "line3"
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier
                                .height(16.dp)
                                .align(Alignment.Center)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .height(16.dp)
                                    .graphicsLayer {
                                        scaleY = line1Scale
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                    }
                                    .background(dynamicAccentColor, RoundedCornerShape(2.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .height(16.dp)
                                    .graphicsLayer {
                                        scaleY = line2Scale
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                    }
                                    .background(dynamicAccentColor, RoundedCornerShape(2.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .height(16.dp)
                                    .graphicsLayer {
                                        scaleY = line3Scale
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                    }
                                    .background(dynamicAccentColor, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = song.title,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    color = if (isPlaying) dynamicAccentColor else (if (isDark) Color.White else Color.Black),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (song.isExplicit == true) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "E",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }

                    val formattedDuration = remember(song.duration) { formatQueueDurationMs(song.duration) }
                    val subtitleText = remember(song.artist, formattedDuration) {
                        if (formattedDuration.isNotEmpty()) "${song.artist} • $formattedDuration" else song.artist
                    }

                    Text(
                        text = subtitleText,
                        fontSize = 13.sp,
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onRemoveClick != null) {
                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Remove from Queue",
                            tint = (if (isDark) Color.White else Color.Black).copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                dragHandle()
            }
        }
    }
}