package com.codetrio.spatialflow.ui.components

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.WeakHashMap

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.codetrio.spatialflow.ui.theme.LocalAppBackdrop
import com.codetrio.spatialflow.ui.theme.LocalAppHazeState

private val LiquidGlassPressSpring = spring<Float>(
    dampingRatio = 0.5f,
    stiffness = 300f,
    visibilityThreshold = 0.001f
)

/**
 * Configuration data class for Liquid Glass optics, shapes, and colors.
 * Values are strictly preserved from SpatialFlow's baseline design.
 */
@Immutable
data class LiquidGlassConfig(
    val blurRadius: Dp = 24.dp,
    val glassBodyColor: Color = Color(0xFF1E1E26).copy(alpha = 0.50f),
    val tintColor: Color = Color.White.copy(alpha = 0.08f),
    val contentColor: Color = Color.White,
    val shape: Shape = CircleShape,
    val borderWidth: Dp = 1.dp,
    val borderBrush: Brush? = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.38f),
            Color.White.copy(alpha = 0.08f)
        )
    )
)

@Composable
fun rememberDefaultLiquidGlassConfig(
    shape: Shape = CircleShape
): LiquidGlassConfig {
    val isDark = isSystemInDarkTheme()
    return remember(isDark, shape) {
        if (isDark) {
            LiquidGlassConfig(
                blurRadius = 24.dp,
                glassBodyColor = Color(0xFF1E1E26).copy(alpha = 0.50f),
                tintColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White,
                shape = shape,
                borderBrush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.38f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
        } else {
            LiquidGlassConfig(
                blurRadius = 24.dp,
                glassBodyColor = Color.White.copy(alpha = 0.70f),
                tintColor = Color.White.copy(alpha = 0.25f),
                contentColor = Color(0xFF1C1B1F),
                shape = shape,
                borderBrush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.80f),
                        Color.Black.copy(alpha = 0.08f)
                    )
                )
            )
        }
    }
}

// =========================================================================
// 1. PERFORMANCE ENGINE: ThrottledLayerBackdrop (100ms throttle via uptime)
// =========================================================================

private val liveRecorderBackdrops =
    Collections.synchronizedMap(WeakHashMap<ThrottledLayerBackdrop, Boolean>())

@Stable
class ThrottledLayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal val minIntervalMillis: Long = 100L,
    internal val contentPrefix: DrawScope.() -> Unit = {},
) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = true

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var consumerInvalidationTick by mutableStateOf(0)
    internal var recordingInProgress by mutableStateOf(false)

    internal fun notifyRecorderAttached() {
        consumerInvalidationTick++
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        @Suppress("UNUSED_VARIABLE") val tick = consumerInvalidationTick
        if (recordingInProgress) return
        val coords = coordinates ?: return
        val lCoords = layerCoordinates ?: return

        if (!lCoords.isAttached || !coords.isAttached) return
        if (graphicsLayer.size.width <= 0 || graphicsLayer.size.height <= 0) return
        val offset = try {
            lCoords.localPositionOf(coords)
        } catch (_: Exception) {
            runCatching {
                coords.positionInWindow() - lCoords.positionInWindow()
            }.getOrNull()
        } ?: return

        withTransform({
            translate(-offset.x, -offset.y)
        }) {
            runCatching { drawLayer(graphicsLayer) }
        }
    }
}

@Composable
fun rememberThrottledBackdrop(
    color: Color,
    minIntervalMillis: Long = 100L,
): ThrottledLayerBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = remember(graphicsLayer, minIntervalMillis, color) {
        ThrottledLayerBackdrop(
            graphicsLayer = graphicsLayer,
            minIntervalMillis = minIntervalMillis,
            contentPrefix = { drawRect(color) },
        )
    }
    DisposableEffect(backdrop) {
        onDispose { backdrop.layerCoordinates = null }
    }
    return backdrop
}

fun Modifier.throttledLayerBackdrop(backdrop: Backdrop?): Modifier =
    when (backdrop) {
        is ThrottledLayerBackdrop -> this then ThrottledLayerBackdropElement(backdrop)
        is LayerBackdrop -> this.kyantLayerBackdrop(backdrop)
        else -> this
    }

private class ThrottledLayerBackdropElement(
    val backdrop: ThrottledLayerBackdrop,
) : ModifierNodeElement<ThrottledLayerBackdropNode>() {
    override fun create() = ThrottledLayerBackdropNode(backdrop)

    override fun update(node: ThrottledLayerBackdropNode) {
        if (node.backdrop !== backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.invalidateDraw()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThrottledLayerBackdropElement) return false
        return backdrop === other.backdrop
    }

    override fun hashCode(): Int = backdrop.hashCode()
}

private class ThrottledLayerBackdropNode(
    var backdrop: ThrottledLayerBackdrop,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {
    private var lastRecordUptimeMillis = 0L

    override fun onAttach() {
        super.onAttach()
        liveRecorderBackdrops[backdrop] = true
        lastRecordUptimeMillis = 0L
        backdrop.notifyRecorderAttached()
    }

    override fun ContentDrawScope.draw() {
        if (size.width <= 0f || size.height <= 0f) {
            drawContent()
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastRecordUptimeMillis >= backdrop.minIntervalMillis) {
            lastRecordUptimeMillis = now
            val density = requireDensity()

            val recorded = runCatching {
                backdrop.recordingInProgress = true
                try {
                    backdrop.graphicsLayer.record(size.toIntSize()) {
                        val previousDensity = drawContext.density
                        drawContext.density = density
                        try {
                            backdrop.contentPrefix(this@draw)
                            this@draw.drawContent()
                        } finally {
                            drawContext.density = previousDensity
                        }
                    }
                } finally {
                    backdrop.recordingInProgress = false
                }
            }.isSuccess

            if (recorded && backdrop.graphicsLayer.size == size.toIntSize()) {
                runCatching { drawLayer(backdrop.graphicsLayer) }
                return
            }
        }
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        liveRecorderBackdrops.remove(backdrop)
        if (backdrop.layerCoordinates?.isAttached == false) {
            backdrop.layerCoordinates = null
            backdrop.consumerInvalidationTick++
        }
    }
}

// =========================================================================
// 2. LIQUID GLASS ACTION BUTTON (Single interactive circle / shaped button)
// =========================================================================

@Composable
fun LiquidGlassActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    hazeState: HazeState? = null,
    backdrop: Backdrop? = null,
    config: LiquidGlassConfig? = null
) {
    val animationScope = rememberCoroutineScope()
    val pressProgress = remember { Animatable(0f) }
    val appHazeState = LocalAppHazeState.current
    val effectiveHazeState = hazeState ?: appHazeState
    val appBackdrop = LocalAppBackdrop.current
    val effectiveBackdrop = backdrop ?: appBackdrop

    val effectiveConfig = config ?: rememberDefaultLiquidGlassConfig(CircleShape)

    val hazeStyle = remember(effectiveConfig.blurRadius, effectiveConfig.glassBodyColor, effectiveConfig.tintColor) {
        HazeStyle(
            backgroundColor = effectiveConfig.glassBodyColor,
            blurRadius = effectiveConfig.blurRadius,
            tint = HazeDefaults.tint(effectiveConfig.tintColor),
            noiseFactor = 0.05f
        )
    }

    val density = LocalDensity.current
    val blurPx = remember(density) { with(density) { 4f.dp.toPx() } }
    val lensHeightPx = remember(density) { with(density) { 14f.dp.toPx() } }
    val lensAmountPx = remember(density) { with(density) { 24f.dp.toPx() } }

    val glassOpticsModifier = remember(
        effectiveBackdrop,
        effectiveHazeState,
        hazeStyle,
        effectiveConfig,
        blurPx,
        lensHeightPx,
        lensAmountPx
    ) {
        if (effectiveBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.drawBackdrop(
                backdrop = effectiveBackdrop,
                shape = { effectiveConfig.shape },
                effects = {
                    vibrancy()
                    colorControls(
                        brightness = 0.04f,
                        contrast = 1.15f,
                        saturation = 1.70f
                    )
                    blur(blurPx)
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = true,
                        chromaticAberration = true
                    )
                },
                onDrawBackdrop = { drawBackdrop ->
                    drawBackdrop()
                },
                onDrawSurface = {
                    drawRect(effectiveConfig.glassBodyColor)
                }
            )
        } else if (effectiveHazeState != null) {
            Modifier.hazeEffect(state = effectiveHazeState, style = hazeStyle)
        } else {
            Modifier.drawBehind {
                drawRect(effectiveConfig.glassBodyColor)
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val scale = lerp(1f, 0.92f, pressProgress.value)
                scaleX = scale
                scaleY = scale
                clip = true
                shape = effectiveConfig.shape
            }
            .clip(effectiveConfig.shape)
            .then(glassOpticsModifier)
            .then(
                if (effectiveConfig.borderBrush != null && effectiveConfig.borderWidth > 0.dp) {
                    Modifier.border(effectiveConfig.borderWidth, effectiveConfig.borderBrush, effectiveConfig.shape)
                } else Modifier
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    animationScope.launch { pressProgress.animateTo(1f, LiquidGlassPressSpring) }
                    waitForUpOrCancellation()
                    animationScope.launch { pressProgress.animateTo(0f, LiquidGlassPressSpring) }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = effectiveConfig.contentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

// =========================================================================
// 3. LIQUID GLASS ACTION PILL (Header back button + title + action capsule)
// =========================================================================

/**
 * Universal Liquid Glass Action Pill for Header Navigation & Grouped Controls.
 *
 * Uses SpatialFlow's exact optical values (lens refraction, blur, saturation, contrast, tint)
 * with a 48.dp capsule shape and spring touch interactions.
 */
@Composable
fun LiquidGlassActionPill(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    config: LiquidGlassConfig? = null,
    content: @Composable RowScope.() -> Unit
) {
    val appHazeState = LocalAppHazeState.current
    val effectiveHazeState = hazeState ?: appHazeState
    val appBackdrop = LocalAppBackdrop.current
    val effectiveBackdrop = backdrop ?: appBackdrop

    val effectiveConfig = config ?: rememberDefaultLiquidGlassConfig(shape)

    val hazeStyle = remember(effectiveConfig.blurRadius, effectiveConfig.glassBodyColor, effectiveConfig.tintColor) {
        HazeStyle(
            backgroundColor = effectiveConfig.glassBodyColor,
            blurRadius = effectiveConfig.blurRadius,
            tint = HazeDefaults.tint(effectiveConfig.tintColor),
            noiseFactor = 0.05f
        )
    }

    val density = LocalDensity.current
    val blurPx = remember(density) { with(density) { 4f.dp.toPx() } }
    val lensHeightPx = remember(density) { with(density) { 14f.dp.toPx() } }
    val lensAmountPx = remember(density) { with(density) { 24f.dp.toPx() } }

    val glassOpticsModifier = remember(
        effectiveBackdrop,
        effectiveHazeState,
        hazeStyle,
        effectiveConfig,
        shape,
        blurPx,
        lensHeightPx,
        lensAmountPx
    ) {
        if (effectiveBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.drawBackdrop(
                backdrop = effectiveBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    colorControls(
                        brightness = 0.04f,
                        contrast = 1.15f,
                        saturation = 1.70f
                    )
                    blur(blurPx)
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = true,
                        chromaticAberration = true
                    )
                },
                onDrawBackdrop = { drawBackdrop ->
                    drawBackdrop()
                },
                onDrawSurface = {
                    drawRect(effectiveConfig.glassBodyColor)
                }
            )
        } else if (effectiveHazeState != null) {
            Modifier.hazeEffect(state = effectiveHazeState, style = hazeStyle)
        } else {
            Modifier.drawBehind {
                drawRect(effectiveConfig.glassBodyColor)
            }
        }
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .then(glassOpticsModifier)
            .then(
                if (effectiveConfig.borderBrush != null && effectiveConfig.borderWidth > 0.dp) {
                    Modifier.border(effectiveConfig.borderWidth, effectiveConfig.borderBrush, shape)
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Icon button for use inside LiquidGlassActionPill with spring feedback.
 */
@Composable
fun LiquidGlassPillIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val animationScope = rememberCoroutineScope()
    val pressProgress = remember { Animatable(0f) }
    val effectiveTint = if (tint != Color.Unspecified) tint
        else if (isSystemInDarkTheme()) Color.White else Color(0xFF1C1B1F)

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                val scale = lerp(1f, 0.90f, pressProgress.value)
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    animationScope.launch { pressProgress.animateTo(1f, LiquidGlassPressSpring) }
                    waitForUpOrCancellation()
                    animationScope.launch { pressProgress.animateTo(0f, LiquidGlassPressSpring) }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = effectiveTint,
            modifier = Modifier.size(22.dp)
        )
    }
}

// =========================================================================
// 4. FROSTED HEADER PILL (High-performance fallback for API < 31)
// =========================================================================

@Composable
fun FrostedHeaderPill(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    plain: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val pillShape = RoundedCornerShape(24.dp)
    val appBackdrop = LocalAppBackdrop.current
    val effectiveBackdrop = backdrop ?: appBackdrop

    if (plain) {
        Row(
            modifier = modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    } else if (effectiveBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        LiquidGlassActionPill(
            modifier = modifier,
            backdrop = effectiveBackdrop,
            shape = pillShape,
            content = content
        )
    } else {
        val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
        Surface(
            modifier = modifier
                .height(48.dp)
                .clip(pillShape),
            shape = pillShape,
            color = baseColor.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.72f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
