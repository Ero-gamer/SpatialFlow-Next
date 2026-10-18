package com.codetrio.spatialflow.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.ArtworkPager
import com.codetrio.spatialflow.ui.player.DynamicIslandMiniPlayer
import com.codetrio.spatialflow.ui.player.FullPlayerScreen
import com.codetrio.spatialflow.ui.player.PlayerUiState
import com.codetrio.spatialflow.ui.theme.LocalAppBackdrop
import com.codetrio.spatialflow.ui.theme.LocalAppHazeState
import com.codetrio.spatialflow.ui.theme.observeKey
import com.codetrio.spatialflow.ui.theme.rememberSpatialHazeStyle
import com.codetrio.spatialflow.ui.theme.spatialHazeEffect
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PlayerSheetState {
    COLLAPSED,
    EXPANDED
}

class PlayerSheetDynamicShape(
    private val topRadiusPxProvider: () -> Float,
    private val bottomRadiusPxProvider: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val topRadiusPx = topRadiusPxProvider().coerceAtLeast(0f)
        val bottomRadiusPx = bottomRadiusPxProvider().coerceAtLeast(0f)
        val height = size.height.coerceAtLeast(0f)
        return Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, height),
                topLeft = CornerRadius(topRadiusPx, topRadiusPx),
                topRight = CornerRadius(topRadiusPx, topRadiusPx),
                bottomRight = CornerRadius(bottomRadiusPx, bottomRadiusPx),
                bottomLeft = CornerRadius(bottomRadiusPx, bottomRadiusPx)
            )
        )
    }
}

private fun Dp.nonNegative(): Dp = takeIf { it.value.isFinite() && it.value > 0f } ?: 0.dp

val MiniPlayerHeight = 80.dp

data class SheetVisualState(
    val currentBottomPadding: Dp,
    val playerContentAreaHeightPxProvider: () -> Float,
    val visualSheetTranslationYProvider: () -> Float,
    val overallSheetTopCornerRadiusProvider: () -> Dp,
    val playerContentActualBottomRadiusProvider: () -> Dp,
    val overallSheetTopCornerRadiusPxProvider: () -> Float,
    val playerContentActualBottomRadiusPxProvider: () -> Float,
    val currentHorizontalPaddingStartPxProvider: () -> Float,
    val currentHorizontalPaddingEndPxProvider: () -> Float
)

@Composable
fun rememberSheetVisualState(
    showPlayerContentArea: Boolean,
    collapsedStateHorizontalPadding: Dp,
    predictiveBackCollapseProgress: Float,
    sheetProgress: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    sheetHiddenOffsetPxProvider: () -> Float,
    sheetCollapsedTargetYProvider: () -> Float,
    isNavBarHiddenProvider: () -> Boolean,
    navBarCornerRadiusDp: Dp
): SheetVisualState {

    val currentBottomPadding by remember(showPlayerContentArea, collapsedStateHorizontalPadding, predictiveBackCollapseProgress) {
        derivedStateOf {
            if (predictiveBackCollapseProgress > 0f && showPlayerContentArea) {
                lerp(0.dp, collapsedStateHorizontalPadding, predictiveBackCollapseProgress)
            } else {
                0.dp
            }
        }
    }

    val density = LocalDensity.current
    val miniHeightPx = remember(density) { with(density) { MiniPlayerHeight.toPx() } }
    val containerHeightPx = remember(containerHeight, density) { with(density) { containerHeight.toPx() } }
    val collapsedCornerTargetPx = remember(density) { with(density) { 40.dp.toPx() } }
    val intermediateBottomCornerTargetPx = remember(density) { with(density) { 26.dp.toPx() } }

    val playerContentAreaHeightPxProvider: () -> Float = remember(showPlayerContentArea, sheetProgress, miniHeightPx, containerHeightPx) {
        {
            if (showPlayerContentArea) {
                androidx.compose.ui.util.lerp(miniHeightPx, containerHeightPx, sheetProgress.value)
            } else {
                0f
            }
        }
    }

    val predictiveBackCollapseProgressState = rememberUpdatedState(predictiveBackCollapseProgress)
    val visualSheetTranslationYProvider: () -> Float = remember(sheetProgress, sheetCollapsedTargetYProvider, sheetHiddenOffsetPxProvider) {
        {
            val progress = predictiveBackCollapseProgressState.value
            val collapsedY = sheetCollapsedTargetYProvider()
            val baseTranslationY = androidx.compose.ui.util.lerp(collapsedY, 0f, sheetProgress.value) + sheetHiddenOffsetPxProvider()
            baseTranslationY * (1f - progress) + (collapsedY * progress)
        }
    }

    val overallSheetTopCornerRadiusProvider: () -> Dp = remember(showPlayerContentArea, sheetProgress) {
        {
            if (showPlayerContentArea) {
                val collapsedCornerTarget = 40.dp
                val fraction = sheetProgress.value
                lerp(collapsedCornerTarget, 0.dp, fraction)
            } else {
                40.dp
            }
        }
    }

    val overallSheetTopCornerRadiusPxProvider: () -> Float = remember(showPlayerContentArea, sheetProgress, collapsedCornerTargetPx) {
        {
            if (showPlayerContentArea) {
                androidx.compose.ui.util.lerp(collapsedCornerTargetPx, 0f, sheetProgress.value)
            } else {
                collapsedCornerTargetPx
            }
        }
    }

    val playerContentActualBottomRadiusProvider: () -> Dp = remember(showPlayerContentArea, sheetProgress) {
        {
            val fraction = sheetProgress.value
            val collapsedRadius = 40.dp

            if (fraction < 0.2f) {
                lerp(collapsedRadius, 26.dp, (fraction / 0.2f).coerceIn(0f, 1f))
            } else {
                lerp(26.dp, 0.dp, ((fraction - 0.2f) / 0.8f).coerceIn(0f, 1f))
            }
        }
    }

    val playerContentActualBottomRadiusPxProvider: () -> Float = remember(showPlayerContentArea, sheetProgress, collapsedCornerTargetPx, intermediateBottomCornerTargetPx) {
        {
            val fraction = sheetProgress.value
            if (fraction < 0.2f) {
                androidx.compose.ui.util.lerp(collapsedCornerTargetPx, intermediateBottomCornerTargetPx, (fraction / 0.2f).coerceIn(0f, 1f))
            } else {
                androidx.compose.ui.util.lerp(intermediateBottomCornerTargetPx, 0f, ((fraction - 0.2f) / 0.8f).coerceIn(0f, 1f))
            }
        }
    }

    val collapsedStateHorizontalPaddingPx = remember(collapsedStateHorizontalPadding, density) {
        with(density) { collapsedStateHorizontalPadding.toPx() }
    }

    val currentHorizontalPaddingStartPxProvider: () -> Float = remember(showPlayerContentArea, sheetProgress, collapsedStateHorizontalPaddingPx) {
        {
            androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, sheetProgress.value)
        }
    }

    val currentHorizontalPaddingEndPxProvider: () -> Float = remember(showPlayerContentArea, sheetProgress, collapsedStateHorizontalPaddingPx) {
        {
            androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, sheetProgress.value)
        }
    }

    return remember(
        currentBottomPadding,
        playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider,
        overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider,
        overallSheetTopCornerRadiusPxProvider,
        playerContentActualBottomRadiusPxProvider,
        currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider
    ) {
        SheetVisualState(
            currentBottomPadding = currentBottomPadding,
            playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
            visualSheetTranslationYProvider = visualSheetTranslationYProvider,
            overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
            playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
            overallSheetTopCornerRadiusPxProvider = overallSheetTopCornerRadiusPxProvider,
            playerContentActualBottomRadiusPxProvider = playerContentActualBottomRadiusPxProvider,
            currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
            currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider
        )
    }
}

@Composable
internal fun BoxScope.UnifiedPlayerMiniAndFullLayers(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    baseAccentColor: Color,
    context: Context,
    sheetProgress: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    shouldRenderFullPlayer: Boolean,
    currentHorizontalPaddingStartPxProvider: () -> Float,
    currentHorizontalPaddingEndPxProvider: () -> Float,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    dragModifier: Modifier,
    lyricsArtworkAnim: Animatable<Float, AnimationVector1D>,
    fullPlayerVisualState: com.codetrio.spatialflow.ui.player.FullPlayerVisualState,
    fullPlayerRuntimePolicy: com.codetrio.spatialflow.ui.player.FullPlayerRuntimePolicy,
    activeToast: SnackbarEvent? = null,
    onDismissToast: (() -> Unit)? = null
) {
    val currentSong = uiState.currentSong
    val isPlaying = uiState.isPlaying

    val isMiniPlayerVisible by remember {
        derivedStateOf { sheetProgress.value < 0.98f }
    }

    val isFullPlayerOnTop by remember {
        derivedStateOf { sheetProgress.value >= 0.5f }
    }

    if (isMiniPlayerVisible) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .graphicsLayer {
                    val progress = sheetProgress.value
                    alpha = (1f - progress * 2f).coerceIn(0f, 1f)
                }
                .layout { measurable, constraints ->
                    val startPaddingPx = currentHorizontalPaddingStartPxProvider().toInt().coerceAtLeast(0)
                    val endPaddingPx = currentHorizontalPaddingEndPxProvider().toInt().coerceAtLeast(0)
                    val targetWidth = (constraints.maxWidth - startPaddingPx - endPaddingPx).coerceAtLeast(0)
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = targetWidth,
                            maxWidth = targetWidth
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeRelative(startPaddingPx, 0)
                    }
                }
                .zIndex(if (isFullPlayerOnTop) 0f else 1f)
        ) {
            DynamicIslandMiniPlayer(
                viewModel = viewModel,
                currentSong = currentSong,
                songList = songList,
                currentSongIndex = uiState.currentSongIndex,
                isPlaying = isPlaying,
                isProcessing = uiState.isProcessing,
                accentColor = baseAccentColor,
                onClick = onExpand,
                activeToast = activeToast,
                onDismissToast = onDismissToast,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (shouldRenderFullPlayer) {
        val isCanvasAllowedForFullPlayer by remember {
            derivedStateOf { sheetProgress.value >= 0.98f && !sheetProgress.isRunning }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(containerHeight)
                .offset {
                    if (sheetProgress.value <= 0.005f) IntOffset(0, 10000)
                    else IntOffset.Zero
                }
                .graphicsLayer {
                    // PixelPlayer zero-recomposition draw-phase getters
                    alpha = fullPlayerVisualState.contentAlpha
                    translationY = fullPlayerVisualState.translationY
                }
                .zIndex(if (isFullPlayerOnTop) 1f else 0f)
        ) {
            FullPlayerScreen(
                activity = activity,
                viewModel = viewModel,
                uiState = uiState,
                songList = songList,
                accentColor = baseAccentColor,
                context = context,
                onCollapse = onCollapse,
                dragModifier = dragModifier,
                lyricsArtworkAnim = lyricsArtworkAnim,
                allowCanvas = isCanvasAllowedForFullPlayer,
                allowRealtimeUpdates = fullPlayerRuntimePolicy.allowRealtimeUpdates,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ==========================================
// 5. STEP 5: VERTICAL GESTURE HANDLER & MATH
// ==========================================
fun computeSheetVerticalDragProgress(
    accumulatedDragY: Float,
    totalDistance: Float,
    initialFractionOnDragStart: Float
): Float {
    val dragFraction = -accumulatedDragY / totalDistance.coerceAtLeast(1f)
    return (initialFractionOnDragStart + dragFraction).coerceIn(0f, 1f)
}

fun resolveVerticalSheetTargetState(
    currentSheetContentState: PlayerSheetState,
    accumulatedDragY: Float,
    minDragThresholdPx: Float,
    verticalVelocity: Float,
    velocityThreshold: Float,
    currentFraction: Float
): PlayerSheetState {
    return when {
        currentSheetContentState == PlayerSheetState.EXPANDED && accumulatedDragY <= 0f ->
            PlayerSheetState.EXPANDED

        abs(accumulatedDragY) > minDragThresholdPx ->
            if (accumulatedDragY < 0f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        abs(verticalVelocity) > velocityThreshold ->
            if (verticalVelocity < 0f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        else ->
            if (currentFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
    }
}

class SheetVerticalDragGestureHandler(
    private val scope: CoroutineScope,
    private val velocityTracker: VelocityTracker,
    private val densityProvider: () -> Density,
    private val sheetProgress: Animatable<Float, AnimationVector1D>,
    private val expandedYProvider: () -> Float,
    private val collapsedYProvider: () -> Float,
    private val currentSheetStateProvider: () -> PlayerSheetState,
    private val visualOvershootScaleY: Animatable<Float, AnimationVector1D>,
    private val motionController: com.codetrio.spatialflow.ui.player.SheetMotionController,
    private val onExpandSheetState: () -> Unit,
    private val onCollapseSheetState: () -> Unit,
    private val onDragStateChange: (Boolean) -> Unit
) {
    private var initialFractionOnDragStart = 0f
    private var accumulatedDragYSinceStart = 0f
    private var isActivelyDragging = false
    private val dragChannel = kotlinx.coroutines.channels.Channel<Float>(kotlinx.coroutines.channels.Channel.CONFLATED)

    init {
        scope.launch {
            for (fraction in dragChannel) {
                motionController.snapTo(fraction)
            }
        }
    }

    fun onDragStart() {
        isActivelyDragging = true
        onDragStateChange(true)
        velocityTracker.resetTracking()
        initialFractionOnDragStart = sheetProgress.value
        accumulatedDragYSinceStart = 0f
        scope.launch {
            motionController.stop()
        }
    }

    fun onVerticalDrag(uptimeMillis: Long, position: Offset, dragAmount: Float) {
        if (sheetProgress.value >= 1f && dragAmount < 0) {
            // Dragging UP when already at top boundary: Ignore over-scroll above top
            return
        }
        accumulatedDragYSinceStart += dragAmount
        val totalDistance = (collapsedYProvider() - expandedYProvider()).coerceAtLeast(1f)
        val newFraction = computeSheetVerticalDragProgress(
            accumulatedDragY = accumulatedDragYSinceStart,
            totalDistance = totalDistance,
            initialFractionOnDragStart = initialFractionOnDragStart
        )
        dragChannel.trySend(newFraction)
        velocityTracker.addPosition(uptimeMillis, position)
    }

    fun onDragEnd() {
        if (!isActivelyDragging) return
        isActivelyDragging = false
        onDragStateChange(false)
        val verticalVelocity = velocityTracker.calculateVelocity().y
        val currentFraction = sheetProgress.value
        val minDragThresholdPx = with(densityProvider()) { 5.dp.toPx() }
        val totalDistance = (collapsedYProvider() - expandedYProvider()).coerceAtLeast(1f)
        val fractionVelocity = -verticalVelocity / totalDistance

        val targetState = resolveVerticalSheetTargetState(
            currentSheetContentState = currentSheetStateProvider(),
            accumulatedDragY = accumulatedDragYSinceStart,
            minDragThresholdPx = minDragThresholdPx,
            verticalVelocity = verticalVelocity,
            velocityThreshold = 150f,
            currentFraction = currentFraction
        )

        scope.launch {
            val isExpanded = targetState == PlayerSheetState.EXPANDED
            motionController.animateTo(
                targetExpanded = isExpanded,
                initialVelocity = fractionVelocity
            )
            if (isExpanded) onExpandSheetState() else onCollapseSheetState()
        }
    }
}

fun Modifier.playerSheetVerticalDragGesture(
    enabled: Boolean,
    handler: SheetVerticalDragGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(true, handler) {
        detectVerticalDragGestures(
            onDragStart = { handler.onDragStart() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                handler.onVerticalDrag(change.uptimeMillis, change.position, dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragEnd() }
        )
    }
}

class MiniPlayerDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticFeedback: HapticFeedback,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val onDismissQueue: () -> Unit
) {
    private var accumulatedDragX = 0f
    private var isDismissTriggered = false
    private var animJob: Job? = null

    fun onDragStart() {
        animJob?.cancel()
        accumulatedDragX = offsetAnimatable.value
        isDismissTriggered = false
    }

    fun onHorizontalDrag(dragAmount: Float) {
        accumulatedDragX += dragAmount

        val dismissThreshold = screenWidthPx * 0.28f
        if (!isDismissTriggered && abs(accumulatedDragX) >= dismissThreshold) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            isDismissTriggered = true
        } else if (isDismissTriggered && abs(accumulatedDragX) < dismissThreshold) {
            isDismissTriggered = false
        }

        scope.launch {
            offsetAnimatable.snapTo(accumulatedDragX)
        }
    }

    fun onDragEnd() {
        animJob?.cancel()
        val dismissThreshold = screenWidthPx * 0.28f

        if (abs(accumulatedDragX) >= dismissThreshold) {
            val targetDismissOffset = if (accumulatedDragX < 0) -screenWidthPx else screenWidthPx
            animJob = scope.launch {
                offsetAnimatable.animateTo(
                    targetValue = targetDismissOffset,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )
                onDismissQueue()
                offsetAnimatable.snapTo(0f)
            }
        } else {
            animJob = scope.launch {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    fun onDragCancel() {
        onDragEnd()
    }
}

@Composable
fun rememberMiniPlayerDismissGestureHandler(
    scope: CoroutineScope,
    density: Density,
    hapticFeedback: HapticFeedback,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onDismissQueue: () -> Unit
): MiniPlayerDismissGestureHandler {
    val onDismissQueueState = rememberUpdatedState(onDismissQueue)
    return remember(scope, density, hapticFeedback, offsetAnimatable, screenWidthPx) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticFeedback = hapticFeedback,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissQueue = { onDismissQueueState.value() }
        )
    }
}

fun Modifier.miniPlayerDismissHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerDismissGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(true, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragCancel() }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerBottomSheetCompose(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val hapticFeedback = LocalHapticFeedback.current
        val appHazeState = LocalAppHazeState.current
        val appBackdrop = LocalAppBackdrop.current
        val prefs = remember(context) { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
        val miniPlayerThemeState = prefs.observeKey("mini_player_theme", "blur")
        val miniPlayerTheme = miniPlayerThemeState.value
        val isMiniPlayerGlass = (miniPlayerTheme == "liquid" && appBackdrop != null) ||
            ((miniPlayerTheme == "blur" || miniPlayerTheme == "frosted") && appHazeState != null)

        // Playback state observers combined into stable UI model
        val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
        val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
        val duration by viewModel.duration.collectAsStateWithLifecycle()
        val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
        val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
        val isHapticsEnabled by viewModel.isHapticsEnabled.collectAsStateWithLifecycle()
        val miniPlayerBlendColor by viewModel.miniPlayerBlendColor.collectAsStateWithLifecycle()
        val isCurrentSongFavorite by viewModel.isCurrentSongFavoriteFlow.collectAsStateWithLifecycle()
        val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
        val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
        val playerBackgroundColor by viewModel.playerBackgroundColor.collectAsStateWithLifecycle()
        val likesCount by viewModel.likesCountFlow.collectAsStateWithLifecycle()
        val isDark = isSystemInDarkTheme()
        val dynamicBgColor = remember(playerBackgroundColor, isDark) {
            val baseColor = Color(playerBackgroundColor)
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
            val isMonochrome = hsl[1] < 0.06f
            if (isDark) {
                // Dark Theme: Set a premium, richly-colored dark solid background (Exactly 15.5% lightness for gorgeous color presence)
                hsl[2] = 0.155f
                hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.32f, 0.54f)
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            } else {
                // Light Theme: Set a premium, elegant soft pastel solid background (Exactly 83.5% lightness for deep pastel flavor)
                hsl[2] = 0.835f
                hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.30f, 0.48f)
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            }
        }
        val isCurrentSongDisliked by viewModel.isCurrentSongDisliked.collectAsStateWithLifecycle()
        val isCurrentSongDownloaded by viewModel.isCurrentSongDownloaded.collectAsStateWithLifecycle()
        val streamingQuality by viewModel.streamingQuality.collectAsStateWithLifecycle()
        val playbackFormat by viewModel.playbackFormat.collectAsStateWithLifecycle()
        val playbackProvider by viewModel.playbackProvider.collectAsStateWithLifecycle()
        val currentSongDownloadProgress by viewModel.currentSongDownloadProgress.collectAsStateWithLifecycle()
        val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
        val showSignInDialog by viewModel.showSignInDialog.collectAsStateWithLifecycle()
        if (showSignInDialog) {
            SignInRequiredDialog(
                onDismiss = { viewModel.dismissSignInDialog() },
                onNavigateToSettings = {
                    viewModel.dismissSignInDialog()
                    activity.navigateToSettings()
                }
            )
        }
        val lyricsArtworkAnim = remember { Animatable(0f) }
        LaunchedEffect(isLyricsModeEnabled) {
            lyricsArtworkAnim.animateTo(
                targetValue = if (isLyricsModeEnabled) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 140f
                )
            )
        }

        val uiState = remember(
            currentSong, isPlaying, duration, isProcessing, currentSongIndex,
            isHapticsEnabled, miniPlayerBlendColor, isCurrentSongFavorite, isCurrentSongDisliked, repeatMode, isShuffleEnabled,
            playerBackgroundColor, likesCount, isCurrentSongDownloaded, currentSongDownloadProgress, streamingQuality, playbackFormat,
            playbackProvider
        ) {
            PlayerUiState(
                currentSong = currentSong,
                isPlaying = isPlaying,
                duration = duration,
                isProcessing = isProcessing,
                currentSongIndex = currentSongIndex,
                isHapticsEnabled = isHapticsEnabled,
                miniPlayerBlendColor = miniPlayerBlendColor,
                isCurrentSongFavorite = isCurrentSongFavorite,
                isCurrentSongDisliked = isCurrentSongDisliked,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                playerBackgroundColor = playerBackgroundColor,
                likesCount = likesCount,
                isCurrentSongDownloaded = isCurrentSongDownloaded,
                currentSongDownloadProgress = currentSongDownloadProgress,
                streamingQuality = streamingQuality,
                playbackFormat = playbackFormat,
                playbackProvider = playbackProvider
            )
        }

        val songList by viewModel.songList.collectAsStateWithLifecycle()
        val isQueueExpanded by viewModel.isQueueExpanded.collectAsStateWithLifecycle()

        // Sliding Layout States
        val containerHeight = with(density) { maxHeight.toPx().toDp() }.coerceAtLeast(1.dp)
        val screenHeightPx = with(density) { containerHeight.toPx() }
        val bottomNavTranslationYState = viewModel.bottomNavTranslationY.collectAsStateWithLifecycle()
        val bottomNavBaseTranslationYState = viewModel.bottomNavBaseTranslationY.collectAsStateWithLifecycle()
        val dynamicBottomNavHeightState = viewModel.bottomNavHeight.collectAsStateWithLifecycle()

        // Animation States
        val playerContentExpansionFraction = remember { Animatable(0f) }

        var activeToastEvent by remember { mutableStateOf<SnackbarEvent?>(null) }
        var toastJob by remember { mutableStateOf<Job?>(null) }
        LaunchedEffect(Unit) {
            SnackbarController.events.collect { event ->
                toastJob?.cancel()
                activeToastEvent = event
                toastJob = launch {
                    delay(event.displayDurationMs)
                    activeToastEvent = null
                }
            }
        }
        val isToastActive = activeToastEvent != null

        val sheetProgress = remember { Animatable(0f) }
        val sheetHiddenOffset = remember { Animatable(screenHeightPx) }
        val visualOvershootScaleY = remember { Animatable(1f) }
        val offsetAnimatable = remember { Animatable(0f) }

        val screenWidthPx = with(density) { maxWidth.toPx() }

        var currentSheetContentState by remember { mutableStateOf(PlayerSheetState.COLLAPSED) }
        var isDragging by remember { mutableStateOf(false) }
        var isToastAnimatingOut by remember { mutableStateOf(false) }

        // Zero-Recomposition: read activeToastEvent state directly inside derivedStateOf
        val isCollapsedToastActive by remember {
            derivedStateOf { activeToastEvent != null && sheetProgress.value < 0.5f }
        }

        val artworkScale by animateFloatAsState(
            targetValue = if (isCollapsedToastActive) 0.85f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "ArtworkScale"
        )

        val artworkAlpha by animateFloatAsState(
            targetValue = if (isQueueExpanded || isCollapsedToastActive) 0f else 1f,
            animationSpec = if (activeToastEvent != null) tween(140) else tween(200),
            label = "ArtworkAlpha"
        )

        val miniPlayerHeightPx = remember(density) { with(density) { MiniPlayerHeight.toPx() } }
        val bottomGapPx = remember(density) { with(density) { 6.dp.toPx() } }

        // Zero-Recomposition logic: calculating layout targets inside derivedStateOf
        // This ensures the composable body doesn't re-run for every pixel of nav bar scroll
        val sheetCollapsedTargetYState = remember(screenHeightPx, miniPlayerHeightPx, bottomGapPx) {
            derivedStateOf {
                val hasBottomNav = dynamicBottomNavHeightState.value > 0f
                val bottomNavHeightPx = if (hasBottomNav) dynamicBottomNavHeightState.value else 0f
                val baseNavTranslationY = bottomNavBaseTranslationYState.value

                val effectiveBottomNavHeight = if (hasBottomNav) {
                    (bottomNavHeightPx - baseNavTranslationY).coerceAtLeast(0f)
                } else {
                    0f
                }

                screenHeightPx - miniPlayerHeightPx - effectiveBottomNavHeight - bottomGapPx
            }
        }

        val isPlaybackReady by viewModel.isPlaybackReady.collectAsStateWithLifecycle()

        val showPlayerContentArea = currentSong != null || isToastActive || isToastAnimatingOut

        LaunchedEffect(currentSheetContentState) {
            viewModel.setPlayerExpanded(currentSheetContentState == PlayerSheetState.EXPANDED)
            if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                viewModel.setQueueExpanded(false)
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.expandPlayerRequest.collect {
                currentSheetContentState = PlayerSheetState.EXPANDED
            }
        }

        val sheetBackProgress = 0f

        androidx.activity.compose.BackHandler(enabled = currentSheetContentState == PlayerSheetState.EXPANDED && !isLyricsModeEnabled) {
            currentSheetContentState = PlayerSheetState.COLLAPSED
        }

        // Auto-expand/collapse sheet when currentSong is changed / ready
        LaunchedEffect(currentSong, isPlaybackReady) {
            if (currentSong != null && isPlaybackReady) {
                if (sheetHiddenOffset.value > 0f) {
                    sheetHiddenOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                    currentSheetContentState = PlayerSheetState.COLLAPSED
                }
            }
        }

        // Slide In / Slide Out mini player skeleton when a toast arrives with no active song
        LaunchedEffect(isToastActive, currentSong) {
            if (currentSong == null) {
                if (isToastActive) {
                    isToastAnimatingOut = false
                    sheetHiddenOffset.snapTo(screenHeightPx)
                    sheetHiddenOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                    currentSheetContentState = PlayerSheetState.COLLAPSED
                } else if (sheetHiddenOffset.value < screenHeightPx - 1f) {
                    isToastAnimatingOut = true
                    sheetHiddenOffset.animateTo(
                        targetValue = screenHeightPx,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                    isToastAnimatingOut = false
                }
            } else {
                isToastAnimatingOut = false
            }
        }

        // Dynamic Blend Colors from Artwork
        val defaultPrimary = MaterialTheme.colorScheme.primary
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val baseAccentColor = remember(miniPlayerBlendColor, isDark, defaultPrimary, onSurfaceColor) {
            val rawAccent = if (miniPlayerBlendColor != 0) Color(miniPlayerBlendColor) else defaultPrimary
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(rawAccent.toArgb(), hsl)
            if (hsl[1] < 0.05f) {
                // Monochromatic / Grayscale
                onSurfaceColor
            } else {
                rawAccent
            }
        }

        val sheetAnimationMutex = remember { androidx.compose.foundation.MutatorMutex() }
        val sheetMotionController = remember(sheetProgress, sheetAnimationMutex) {
            com.codetrio.spatialflow.ui.player.SheetMotionController(
                sheetProgress = sheetProgress,
                mutex = sheetAnimationMutex
            )
        }

        // Single authoritative sheet animation resolver using SheetMotionController
        suspend fun animatePlayerSheet(targetExpanded: Boolean, animationSpec: AnimationSpec<Float>? = null, initialVelocity: Float = 0f) {
            sheetMotionController.animateTo(
                targetExpanded = targetExpanded,
                animationSpec = animationSpec,
                initialVelocity = initialVelocity
            )
        }

        LaunchedEffect(currentSheetContentState) {
            val targetFraction = if (currentSheetContentState == PlayerSheetState.EXPANDED) 1f else 0f
            if (sheetProgress.targetValue != targetFraction) {
                animatePlayerSheet(currentSheetContentState == PlayerSheetState.EXPANDED)
            }
        }

        LaunchedEffect(sheetProgress) {
            snapshotFlow { sheetProgress.value }
                .distinctUntilChanged()
                .collect { viewModel.setPlayerExpansionFraction(it) }
        }

        val sheetVisualState = rememberSheetVisualState(
            showPlayerContentArea = showPlayerContentArea,
            collapsedStateHorizontalPadding = 12.dp,
            predictiveBackCollapseProgress = sheetBackProgress,
            sheetProgress = sheetProgress,
            containerHeight = containerHeight,
            sheetHiddenOffsetPxProvider = { sheetHiddenOffset.value },
            sheetCollapsedTargetYProvider = { sheetCollapsedTargetYState.value },
            isNavBarHiddenProvider = { (bottomNavTranslationYState.value / (if (dynamicBottomNavHeightState.value > 0) dynamicBottomNavHeightState.value else 1f)) > 0.5f },
            navBarCornerRadiusDp = 32.dp
        )

        // Dynamic Shape instantiation
        val playerShadowShape = remember(sheetVisualState.overallSheetTopCornerRadiusPxProvider, sheetVisualState.playerContentActualBottomRadiusPxProvider) {
            PlayerSheetDynamicShape(
                topRadiusPxProvider = sheetVisualState.overallSheetTopCornerRadiusPxProvider,
                bottomRadiusPxProvider = sheetVisualState.playerContentActualBottomRadiusPxProvider
            )
        }

        val velocityTracker = remember { VelocityTracker() }

        val sheetVerticalDragGestureHandler = remember(density, sheetMotionController) {
            SheetVerticalDragGestureHandler(
                scope = scope,
                velocityTracker = velocityTracker,
                densityProvider = { density },
                sheetProgress = sheetProgress,
                expandedYProvider = { 0f },
                collapsedYProvider = { sheetCollapsedTargetYState.value },
                currentSheetStateProvider = { currentSheetContentState },
                visualOvershootScaleY = visualOvershootScaleY,
                motionController = sheetMotionController,
                onExpandSheetState = { currentSheetContentState = PlayerSheetState.EXPANDED },
                onCollapseSheetState = { currentSheetContentState = PlayerSheetState.COLLAPSED },
                onDragStateChange = { dragging -> isDragging = dragging }
            )
        }

        val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticFeedback = hapticFeedback,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissQueue = {
                scope.launch {
                    sheetHiddenOffset.snapTo(screenHeightPx)
                }
                viewModel.dismissPlayer()
            }
        )

        val isMiniPlayerThresholdActive by remember {
            derivedStateOf { sheetProgress.value < 0.5f }
        }

        val spatialHazeStyle = rememberSpatialHazeStyle(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            blurRadius = 28.dp,
            tint = if (isDark) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f)
            }
        )

        val backdropBlurRadiusPx = remember(density) { with(density) { 8f.dp.toPx() } }
        val backdropLensRadiusPx = remember(density) { with(density) { 18f.dp.toPx() } }
        val backdropLensDepthPx = remember(density) { with(density) { 32f.dp.toPx() } }

        val fullPlayerCompositionPolicy = com.codetrio.spatialflow.ui.player.rememberFullPlayerCompositionPolicy(
            currentSongId = currentSong?.id,
            currentSheetState = currentSheetContentState,
            expansionFraction = sheetProgress
        )
        val shouldRenderFullPlayer = fullPlayerCompositionPolicy.shouldRenderFullPlayer

        val initialOffsetY = remember(density) { with(density) { 48.dp.toPx() } }
        val fullPlayerVisualState = com.codetrio.spatialflow.ui.player.rememberFullPlayerVisualState(
            expansionFraction = sheetProgress,
            initialOffsetY = initialOffsetY
        )
        val fullPlayerRuntimePolicy = com.codetrio.spatialflow.ui.player.rememberFullPlayerRuntimePolicy(
            currentSheetState = currentSheetContentState,
            expansionFraction = sheetProgress,
            isDragging = isDragging
        )

        DisposableEffect(shouldRenderFullPlayer) {
            viewModel.setSliderUiMounted(shouldRenderFullPlayer)
            onDispose { viewModel.setSliderUiMounted(false) }
        }

        val visualCardShadowElevation by remember(currentSheetContentState, isDragging) {
            derivedStateOf {
                if (
                    isDragging ||
                    sheetProgress.isRunning ||
                    sheetProgress.value > 0.18f
                ) {
                    0.dp
                } else {
                    6.dp
                }
            }
        }

        if (showPlayerContentArea) {
            val miniPlayerGlassTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, sheetVisualState.visualSheetTranslationYProvider().roundToInt()) }
                    .height(containerHeight),
                color = Color.Transparent
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = sheetVisualState.currentBottomPadding)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isQueueExpanded) 5f else 2f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = offsetAnimatable.value
                                    scaleY = visualOvershootScaleY.value
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                // outerLayout:
                                // Measures downstream chain with innerWidth and targetHeightPx.
                                // Places child at startPaddingPx to center it horizontally.
                                // Reports full screen width to parent to satisfy fillMaxWidth() constraints.
                                .layout { measurable, constraints ->
                                    val targetHeightPx = sheetVisualState.playerContentAreaHeightPxProvider()
                                        .toInt().coerceAtLeast(0)
                                    val startPaddingPx = sheetVisualState.currentHorizontalPaddingStartPxProvider()
                                        .toInt().coerceAtLeast(0)
                                    val endPaddingPx = sheetVisualState.currentHorizontalPaddingEndPxProvider()
                                        .toInt().coerceAtLeast(0)
                                    val innerWidth = (constraints.maxWidth - startPaddingPx - endPaddingPx)
                                        .coerceAtLeast(0)

                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = innerWidth,
                                            maxWidth = innerWidth,
                                            minHeight = targetHeightPx,
                                            maxHeight = targetHeightPx
                                        )
                                    )
                                    layout(constraints.maxWidth, targetHeightPx) {
                                        placeable.placeRelative(startPaddingPx, 0)
                                    }
                                }
                                .shadow(
                                    elevation = visualCardShadowElevation,
                                    shape = playerShadowShape,
                                    clip = false
                                )
                                    .background(
                                        color = if (currentSheetContentState == PlayerSheetState.COLLAPSED && isMiniPlayerThresholdActive && isMiniPlayerGlass) {
                                            Color.Transparent
                                        } else {
                                            dynamicBgColor
                                        },
                                        shape = playerShadowShape
                                    )
                                    .clip(playerShadowShape)
                                    .spatialHazeEffect(
                                        state = appHazeState,
                                        style = spatialHazeStyle,
                                        shape = playerShadowShape,
                                        enabled = currentSheetContentState == PlayerSheetState.COLLAPSED && isMiniPlayerThresholdActive && (miniPlayerTheme == "blur" || miniPlayerTheme == "frosted") && appHazeState != null
                                    )
                                    .then(
                                        if (currentSheetContentState == PlayerSheetState.COLLAPSED && isMiniPlayerThresholdActive && miniPlayerTheme == "liquid" && appBackdrop != null) {
                                        Modifier.drawBackdrop(
                                            backdrop = appBackdrop,
                                            shape = {
                                                RoundedCornerShape(
                                                    topStart = sheetVisualState.overallSheetTopCornerRadiusProvider(),
                                                    topEnd = sheetVisualState.overallSheetTopCornerRadiusProvider(),
                                                    bottomStart = sheetVisualState.playerContentActualBottomRadiusProvider(),
                                                    bottomEnd = sheetVisualState.playerContentActualBottomRadiusProvider()
                                                )
                                            },
                                            effects = {
                                                vibrancy()
                                                colorControls(
                                                    brightness = 0.04f,
                                                    contrast = 1.16f,
                                                    saturation = 1.65f
                                                )
                                                blur(backdropBlurRadiusPx)
                                                lens(
                                                    refractionHeight = backdropLensRadiusPx,
                                                    refractionAmount = backdropLensDepthPx,
                                                    depthEffect = true,
                                                    chromaticAberration = true
                                                )
                                            },
                                            onDrawSurface = {
                                                drawRect(miniPlayerGlassTint)
                                            }
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                // innerLayout:
                                // Measures the actual player content with full screen height targetContentHeightPx
                                // so that it can render correctly, while reporting targetHeightPx to the outer
                                // clip/background/shadow so that they are perfectly constrained to the miniplayer card bounds.
                                // During drag/animation, we measure at stable full-screen constraints to prevent jank.
                                .layout { measurable, constraints ->
                                    val targetContentHeightPx = containerHeight.roundToPx()
                                    val startPaddingPx = sheetVisualState.currentHorizontalPaddingStartPxProvider().toInt()
                                    val measureWidth = screenWidthPx.roundToInt()
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = measureWidth,
                                            maxWidth = measureWidth,
                                            minHeight = targetContentHeightPx,
                                            maxHeight = targetContentHeightPx
                                        )
                                    )
                                    layout(constraints.maxWidth, constraints.maxHeight) {
                                        placeable.placeRelative(-startPaddingPx, 0)
                                    }
                                }
                                .miniPlayerDismissHorizontalGesture(
                                    enabled = currentSheetContentState == PlayerSheetState.COLLAPSED,
                                    handler = miniDismissGestureHandler
                                )
                                .playerSheetVerticalDragGesture(
                                    enabled = currentSong != null && currentSheetContentState == PlayerSheetState.COLLAPSED && !isLyricsModeEnabled && !isQueueExpanded,
                                    handler = sheetVerticalDragGestureHandler
                                )
                                .clickable(
                                    enabled = currentSong != null && currentSheetContentState == PlayerSheetState.COLLAPSED,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                                        currentSheetContentState = PlayerSheetState.EXPANDED
                                    }
                                }
                        ) {
                            val verticalDragModifier = Modifier.playerSheetVerticalDragGesture(
                                enabled = currentSheetContentState == PlayerSheetState.EXPANDED && !isLyricsModeEnabled && !isQueueExpanded,
                                handler = sheetVerticalDragGestureHandler
                            )

                            UnifiedPlayerMiniAndFullLayers(
                                activity = activity,
                                viewModel = viewModel,
                                uiState = uiState,
                                songList = songList,
                                baseAccentColor = baseAccentColor,
                                context = context,
                                sheetProgress = sheetProgress,
                                containerHeight = containerHeight,
                                shouldRenderFullPlayer = shouldRenderFullPlayer,
                                currentHorizontalPaddingStartPxProvider = sheetVisualState.currentHorizontalPaddingStartPxProvider,
                                currentHorizontalPaddingEndPxProvider = sheetVisualState.currentHorizontalPaddingEndPxProvider,
                                onCollapse = { currentSheetContentState = PlayerSheetState.COLLAPSED },
                                onExpand = { currentSheetContentState = PlayerSheetState.EXPANDED },
                                dragModifier = verticalDragModifier,
                                lyricsArtworkAnim = lyricsArtworkAnim,
                                fullPlayerVisualState = fullPlayerVisualState,
                                fullPlayerRuntimePolicy = fullPlayerRuntimePolicy,

                                activeToast = activeToastEvent,
                                onDismissToast = {
                                    toastJob?.cancel()
                                    activeToastEvent = null
                                }
                            )
                        }
                    }

                    // Integrated shared artwork layer. In lyrics mode the same ArtworkPager
                    // moves into the app bar as a compact thumbnail instead of being hidden.
                    // Single source of truth for artwork SIZE: the same computeScreenMetrics()
                    // calc FullPlayer uses, so this shared layer and FullPlayer's placeholder are
                    // always identical in size — including landscape, where the size changes.
                    val metrics = com.codetrio.spatialflow.ui.player.metrics.computeScreenMetrics()
                    val albumArtSizeDp = metrics.albumArtSize
                    val twoPane = metrics.isLandscape
                    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
                    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()

                    val canvasArtwork by viewModel.canvasArtwork.collectAsStateWithLifecycle()
                    val showAnimatedArt = prefs.getBoolean("show_animated_art", true)
                    val isCanvasAllowed = remember(context) { com.codetrio.spatialflow.util.QualityModeSelector.isCanvasVideoAllowed(context) }
                    val hasCanvas = showAnimatedArt && isCanvasAllowed && canvasArtwork != null &&
                            (!canvasArtwork!!.preferredVerticalAnimationUrl.isNullOrBlank() || !canvasArtwork!!.preferredAnimationUrl.isNullOrBlank())
                    val playerTheme = prefs.getString("player_theme", "fluid") ?: "fluid"

                    val miniSizePx = with(density) { 48.dp.toPx() }
                    val fullSizePx = with(density) { albumArtSizeDp.toPx() }
                    val appBarThumbSizePx = with(density) { 48.dp.toPx() }
                    val appBarScale = appBarThumbSizePx / fullSizePx
                    // Perfectly align with the 56.dp left icon container in PlayerHeaderBar:
                    // Header Column has statusBarsPadding() + dimens.smallPadding (8.dp) + Spacer(16.dp).
                    // In portrait, horizontal padding is dimens.screenMargin (16.dp).
                    // For a 48.dp thumbnail centered inside the 56.dp container:
                    // Inset = (56.dp - 48.dp) / 2 = 4.dp
                    // appBarX = 16.dp + 4.dp = 20.dp
                    // appBarY = statusBarTopPx + 8.dp + 16.dp + 4.dp = statusBarTopPx + 28.dp
                    val appBarX = with(density) { 20.dp.toPx() }
                    val appBarY = statusBarTopPx + with(density) { 28.dp.toPx() }
                    val xStartOffsetPx = with(density) { 16.dp.toPx() }
                    val yStartPx = with(density) { 16.dp.toPx() }

                    // Anchor on the REGION width (maxWidth from BoxWithConstraints = screenWidthPx),
                    // not configuration.screenWidthDp: the expanded artwork must land where FullPlayer
                    // actually centers it inside this region, even when a NavigationRail insets it.
                    val xEndPx = remember(twoPane, screenWidthPx, fullSizePx) {
                        if (twoPane) {
                            // Centered in the left pane (~quarter of the region width).
                            (screenWidthPx / 4f) - (fullSizePx / 2f)
                        } else {
                            (screenWidthPx - fullSizePx) / 2f
                        }
                    }

                    val yEndPx = remember(twoPane, statusBarTopPx, navBarBottomPx, density, containerHeight, albumArtSizeDp, fullSizePx) {
                        if (twoPane) {
                            val topOffsetPx = statusBarTopPx + with(density) { 8.dp.toPx() }
                            val bottomOffsetPx = navBarBottomPx + with(density) { 8.dp.toPx() }
                            val containerHeightPx = with(density) { containerHeight.toPx() }
                            val availableHeightPx = (containerHeightPx - topOffsetPx - bottomOffsetPx).coerceAtLeast(0f)
                            val centerY = topOffsetPx + (availableHeightPx / 2f)
                            centerY - (fullSizePx / 2f)
                        } else {
                            val minTopOffsetDp = with(density) { statusBarTopPx.toDp() } + 68.dp
                            val topOffsetDp = ((containerHeight - albumArtSizeDp) / 2f - 130.dp).coerceAtLeast(minTopOffsetDp)
                            with(density) { topOffsetDp.toPx() }
                        }
                    }
                    val targetAppBarCornerRadiusPx = with(density) { (10.dp / appBarScale).toPx() }
                    val targetAppBarShadowPx = with(density) { 6.dp.toPx() }
                    val targetExpandedCornerRadiusPx = with(density) { 12.dp.toPx() }
                    val parabolicArcBaseXPx = with(density) { 18.dp.toPx() }
                    val parabolicArcBaseYPx = with(density) { 9.dp.toPx() }

                    val isLyricsModeActive by remember(isLyricsModeEnabled, lyricsArtworkAnim.isRunning) {
                        derivedStateOf { isLyricsModeEnabled || lyricsArtworkAnim.isRunning }
                    }
                    val isArtworkScrollable by remember(twoPane, isLyricsModeEnabled, isQueueExpanded) {
                        derivedStateOf { sheetProgress.value > 0.95f && (twoPane || !isLyricsModeEnabled) && !isQueueExpanded }
                    }
                    val isArtworkCanvasAllowed by remember {
                        derivedStateOf { false }
                    }

                    val dynamicArtworkShape = remember(targetExpandedCornerRadiusPx, targetAppBarCornerRadiusPx, fullSizePx, twoPane) {
                        object : Shape {
                            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                                val t = sheetProgress.value
                                // In two-pane the artwork never migrates to the app-bar thumbnail, so keep its
                                // corner radius on the normal (expanded) track regardless of lyrics mode.
                                val lyricsT = if (twoPane) 0f else lyricsArtworkAnim.value
                                val normalCornerRadius = androidx.compose.ui.util.lerp(fullSizePx / 2f, targetExpandedCornerRadiusPx, t)
                                val cornerRadius = androidx.compose.ui.util.lerp(normalCornerRadius, targetAppBarCornerRadiusPx, lyricsT).coerceAtLeast(0f)
                                return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(cornerRadius, cornerRadius)))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(albumArtSizeDp)
                            .graphicsLayer {
                                val t = sheetProgress.value
                                // lyricsActiveT reflects lyrics mode itself (kept live in both orientations so
                                // canvas-hide stays disabled and the static artwork remains visible). moveT
                                // drives the migration to the app-bar thumbnail — suppressed in two-pane so the
                                // artwork stays anchored in the left pane while lyrics render on the right.
                                val lyricsActiveT = lyricsArtworkAnim.value
                                val moveT = if (twoPane) 0f else lyricsActiveT

                                val normalScale = androidx.compose.ui.util.lerp(miniSizePx / fullSizePx, 1f, t)
                                val startPaddingPx = sheetVisualState.currentHorizontalPaddingStartPxProvider()
                                val xStartPx = startPaddingPx + xStartOffsetPx

                                val normalX = androidx.compose.ui.util.lerp(xStartPx, xEndPx, t)
                                val normalY = androidx.compose.ui.util.lerp(yStartPx, yEndPx, t)
                                val normalShadow = androidx.compose.ui.util.lerp(0f, targetAppBarShadowPx, t)

                                val dismissOffsetPx = offsetAnimatable.value * (1f - t)

                                val tClamped = moveT.coerceIn(0f, 1f)

                                // Non-linear tensile pull scaling (accelerates scaling down as if reeled by a line)
                                val pullScaleT = Math.pow(tClamped.toDouble(), 1.25).toFloat()
                                val targetScale = androidx.compose.ui.util.lerp(normalScale, appBarScale, pullScaleT) * artworkScale

                                // Dynamic tensile stretch/squash & subtle corner tilt along the flight pull vector
                                val tensionWave = kotlin.math.sin(tClamped * kotlin.math.PI.toFloat())
                                val tensionSquashX = 1f - 0.02f * tensionWave
                                val tensionSquashY = 1f + 0.015f * tensionWave

                                scaleX = targetScale * tensionSquashX
                                scaleY = targetScale * tensionSquashY
                                rotationZ = -1.8f * tensionWave

                                // Parabolic curved motion trajectory (Apple-style outward sweeping flight arc)
                                val parabolicFactor = 4f * tClamped * (1f - tClamped)
                                val arcOffsetX = parabolicArcBaseXPx * parabolicFactor
                                val arcOffsetY = -parabolicArcBaseYPx * parabolicFactor

                                translationX = androidx.compose.ui.util.lerp(normalX, appBarX, moveT) + arcOffsetX + dismissOffsetPx
                                translationY = androidx.compose.ui.util.lerp(normalY, appBarY, moveT) + arcOffsetY
                                transformOrigin = TransformOrigin(0f, 0f)

                                shape = dynamicArtworkShape
                                val isExpandedFullPlayer = t >= 0.95f && !isLyricsModeActive
                                clip = !isExpandedFullPlayer
                                shadowElevation = if (isExpandedFullPlayer) 0f else androidx.compose.ui.util.lerp(normalShadow, targetAppBarShadowPx, moveT)

                                val canvasHideT = if (hasCanvas) {
                                    ((t - 0.75f).coerceAtLeast(0f) * 4f) * (1f - lyricsActiveT)
                                } else {
                                    0f
                                }
                                alpha = artworkAlpha * (1f - canvasHideT)
                            }
                            .clickable(
                                enabled = isLyricsModeActive && sheetProgress.value >= 0.95f,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.setLyricsModeEnabled(false)
                            }
                            .zIndex(if (isQueueExpanded) 1f else if (isLyricsModeActive) 6f else 3f)
                    ) {
                        currentSong?.let { song ->
                            ArtworkPager(
                                viewModel = viewModel,
                                currentSong = song,
                                songList = songList,
                                currentSongIndex = uiState.currentSongIndex,
                                context = context,
                                userScrollEnabled = isArtworkScrollable,
                                allowCanvas = isArtworkCanvasAllowed,
                                isPlaying = isPlaying,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
internal fun MiniPlayerContentInternal(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem,
    isPlaying: Boolean,
    isProcessing: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val contentColor = MaterialTheme.colorScheme.onSurface
    val contentSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val playBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            MiniPlayerProgressIndicator(
                viewModel = viewModel,
                isPlaying = isPlaying,
                isProcessing = isProcessing,
                accentColor = accentColor
            )

            Box(modifier = Modifier.size(48.dp)) {
                // Placeholder Box for structure: AsyncImage is managed globally via parent's floating shared element!
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentSong.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = currentSong.artist,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = contentSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        // Controls Row using custom icons painterResource
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.playPreviousSong() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous),
                    contentDescription = "Previous",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = {
                    if (isPlaying) viewModel.pauseAudio() else viewModel.playAudio()
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(playBgColor, CircleShape)
            ) {
                if (isProcessing) {
                    val density = LocalDensity.current
                    val miniWavyStroke = remember(density) {
                        androidx.compose.ui.graphics.drawscope.Stroke(
                            width = with(density) { 3.8.dp.toPx() },
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.30f),
                        stroke = miniWavyStroke,
                        trackStroke = miniWavyStroke
                    )
                } else {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = "Play/Pause",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(onClick = { viewModel.playNextSong(force = true, fromUser = true) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = "Next",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniPlayerProgressIndicator(
    viewModel: PlayerSharedViewModel,
    isPlaying: Boolean,
    isProcessing: Boolean,
    accentColor: Color
) {
    val progressAnimatable = remember { Animatable(0f) }
    val progressFlow = remember(viewModel) {
        combine(viewModel.currentPosition, viewModel.duration) { pos, dur ->
            if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
        }.distinctUntilChanged()
    }

    LaunchedEffect(progressFlow) {
        progressFlow.collect { target ->
            progressAnimatable.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 350, easing = LinearEasing)
            )
        }
    }

    val amplitudeAnimatable = remember { Animatable(if (isPlaying) 1f else 0f) }
    LaunchedEffect(isPlaying) {
        amplitudeAnimatable.animateTo(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val density = LocalDensity.current
    val strokeCapRound = remember { androidx.compose.ui.graphics.StrokeCap.Round }
    val strokeWidthPx = remember(density) { with(density) { 6.dp.toPx() } }
    val wavyStroke = remember(strokeWidthPx, strokeCapRound) {
        androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeWidthPx,
            cap = strokeCapRound
        )
    }

    if (isProcessing) {
        CircularWavyProgressIndicator(
            modifier = Modifier.size(58.dp),
            color = accentColor,
            trackColor = accentColor.copy(alpha = 0.25f),
            amplitude = amplitudeAnimatable.value,
            stroke = wavyStroke,
            trackStroke = wavyStroke
        )
    } else {
        CircularWavyProgressIndicator(
            progress = { progressAnimatable.value },
            modifier = Modifier.size(58.dp),
            color = accentColor,
            trackColor = accentColor.copy(alpha = 0.25f),
            waveSpeed = WavyProgressIndicatorDefaults.CircularWavelength * 0.4f,
            amplitude = { amplitudeAnimatable.value },
            stroke = wavyStroke,
            trackStroke = wavyStroke
        )
    }
}

@Composable
private fun SignInRequiredDialog(
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sign in Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "To like or dislike songs on YouTube Music, please connect your account in Settings. This enables full synchronization of liked tracks and personalization algorithms.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onNavigateToSettings) {
                Text(text = "Go to Settings", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}


