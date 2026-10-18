@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.codetrio.spatialflow.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import android.os.Build
import com.codetrio.spatialflow.ui.theme.SpatialShapes
import com.codetrio.spatialflow.ui.theme.spatialHazeEffect
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

@Immutable
data class SpatialNavItem(
    val route: String,
    val label: String,
    val iconResId: Int
)

private val NavFloatSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)
private val NavIntSizeSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)
private val NavDpSpring = spring<androidx.compose.ui.unit.Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)
private val NavPressSpring = spring<Float>(
    dampingRatio = 0.5f,
    stiffness = 300f,
    visibilityThreshold = 0.001f
)
private val NavColorTween = tween<Color>(220)
private val NavFadeTweenEnter = tween<Float>(200)
private val NavFadeTweenExit = tween<Float>(200)

val FloatingNavShape = SpatialShapes.FloatingBar
private val FloatingNavShapeLambda: () -> androidx.compose.ui.graphics.Shape = { FloatingNavShape }
private val CircleShapeLambda: () -> androidx.compose.ui.graphics.Shape = { CircleShape }

@Composable
fun SpatialFloatingNavBar(
    items: List<SpatialNavItem>,
    currentRoute: String?,
    onItemSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    hideLabels: Boolean = false,
    navVisibilityFraction: Float = 1f,
    hazeState: HazeState? = null,
    isBlurActive: Boolean = false,
    frostedBg: Color = MaterialTheme.colorScheme.surfaceContainer,
    frostedTint: Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
    backdrop: Backdrop? = null,
    useBackdropGlass: Boolean = false,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val containerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val navContainerColor = remember(isBlurActive, useBackdropGlass, backdrop, containerHigh) {
        if (isBlurActive || (useBackdropGlass && backdrop != null)) Color.Transparent else containerHigh
    }

    val density = LocalDensity.current
    val blurPx = remember(density) { with(density) { 4f.dp.toPx() } }
    val lensHeightPx = remember(density) { with(density) { 14f.dp.toPx() } }
    val lensAmountPx = remember(density) { with(density) { 24f.dp.toPx() } }

    val backdropGlassModifier = if (useBackdropGlass && backdrop != null) {
        remember(backdrop, frostedBg, blurPx, lensHeightPx, lensAmountPx) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = FloatingNavShapeLambda,
                effects = {
                    vibrancy()
                    colorControls(
                        brightness = 0.03f,
                        contrast = 1.15f,
                        saturation = 1.60f
                    )
                    blur(blurPx)
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = true,
                        chromaticAberration = true
                    )
                },
                onDrawSurface = {
                    drawRect(frostedBg.copy(alpha = 0.45f))
                }
            )
        }
    } else null

    val backdropModifier = backdropGlassModifier ?: Modifier.spatialHazeEffect(
        state = hazeState,
        backgroundColor = frostedBg,
        tint = frostedTint,
        enabled = isBlurActive
    )

    Surface(
        modifier = modifier
            .clip(FloatingNavShape)
            .then(backdropModifier),
        shape = FloatingNavShape,
        color = navContainerColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                androidx.compose.runtime.key(item.route) {
                    NavPill(
                        item = item,
                        isSelected = item.route == currentRoute,
                        hideLabels = hideLabels,
                        navVisibilityFraction = navVisibilityFraction,
                        backdrop = backdrop,
                        useBackdropGlass = useBackdropGlass,
                        activeContainerColor = activeContainerColor,
                        activeContentColor = activeContentColor,
                        onClick = { onItemSelect(item.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPill(
    item: SpatialNavItem,
    isSelected: Boolean,
    hideLabels: Boolean,
    navVisibilityFraction: Float,
    backdrop: Backdrop?,
    useBackdropGlass: Boolean,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val pressProgress = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }

    val targetBackground = remember(isSelected, useBackdropGlass, backdrop, activeContainerColor) {
        if (isSelected) {
            if (useBackdropGlass && backdrop != null) Color.Transparent else activeContainerColor
        } else Color.Transparent
    }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val targetContentColor = remember(isSelected, activeContentColor, onSurfaceVariant) {
        if (isSelected) activeContentColor else onSurfaceVariant
    }

    val background by animateColorAsState(targetBackground, NavColorTween, label = "navPillBackground")
    val contentColor by animateColorAsState(targetContentColor, NavColorTween, label = "navPillContent")

    val showLabel = isSelected && !hideLabels && navVisibilityFraction > 0.08f

    val pillPaddingHorizontal by animateDpAsState(
        targetValue = if (showLabel) 14.dp else 10.dp,
        animationSpec = NavDpSpring,
        label = "navPillPadding"
    )

    val activeGlassTint = remember(activeContainerColor) {
        activeContainerColor.copy(alpha = 0.75f)
    }

    val density = LocalDensity.current
    val blurPx = remember(density) { with(density) { 4f.dp.toPx() } }
    val lensHeightPx = remember(density) { with(density) { 14f.dp.toPx() } }
    val lensAmountPx = remember(density) { with(density) { 24f.dp.toPx() } }

    val glassPillModifier = remember(
        useBackdropGlass,
        backdrop,
        isSelected,
        activeGlassTint,
        blurPx,
        lensHeightPx,
        lensAmountPx
    ) {
        if (useBackdropGlass && backdrop != null && isSelected) {
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = CircleShapeLambda,
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
                    layerBlock = {
                        val progress = pressProgress.value
                        val scale = lerp(1f, 0.94f, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRect(activeGlassTint)
                    }
                )
        } else {
            Modifier.graphicsLayer {
                val progress = pressProgress.value
                val scale = lerp(1f, 0.96f, progress)
                scaleX = scale
                scaleY = scale
            }
        }
    }

    Row(
        modifier = Modifier
            .then(glassPillModifier)
            .height(48.dp)
            .clip(CircleShape)
            .background(background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    animationScope.launch { pressProgress.animateTo(1f, NavPressSpring) }
                    waitForUpOrCancellation()
                    animationScope.launch { pressProgress.animateTo(0f, NavPressSpring) }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = pillPaddingHorizontal)
            .animateContentSize(animationSpec = NavIntSizeSpring),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(id = item.iconResId),
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(animationSpec = NavFloatSpring) +
                expandHorizontally(animationSpec = NavIntSizeSpring, expandFrom = Alignment.Start) +
                scaleIn(initialScale = 0.88f, animationSpec = NavFloatSpring),
            exit = fadeOut(animationSpec = NavFloatSpring) +
                shrinkHorizontally(animationSpec = NavIntSizeSpring, shrinkTowards = Alignment.Start) +
                scaleOut(targetScale = 0.88f, animationSpec = NavFloatSpring)
        ) {
            Text(
                text = item.label,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    alpha = navVisibilityFraction.coerceIn(0f, 1f)
                }
            )
        }
    }
}

@Composable
fun SpatialFloatingSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isScrollToTop: Boolean = false,
    hazeState: HazeState? = null,
    isBlurActive: Boolean = false,
    frostedBg: Color = MaterialTheme.colorScheme.surfaceContainer,
    frostedTint: Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
    backdrop: Backdrop? = null,
    useBackdropGlass: Boolean = false,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val animationScope = rememberCoroutineScope()
    val pressProgress = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }

    val containerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val navContainerColor = remember(isBlurActive, useBackdropGlass, backdrop, activeContainerColor, containerHigh, isScrollToTop) {
        if (isBlurActive || (useBackdropGlass && backdrop != null)) {
            Color.Transparent
        } else {
            if (isScrollToTop) activeContainerColor else containerHigh
        }
    }

    val activeGlassTint = remember(activeContainerColor) {
        activeContainerColor.copy(alpha = 0.75f)
    }

    val density = LocalDensity.current
    val blurPx = remember(density) { with(density) { 4f.dp.toPx() } }
    val lensHeightPx = remember(density) { with(density) { 16f.dp.toPx() } }
    val lensAmountPx = remember(density) { with(density) { 26f.dp.toPx() } }

    val backdropGlassModifier = if (useBackdropGlass && backdrop != null) {
        remember(backdrop, activeGlassTint, blurPx, lensHeightPx, lensAmountPx) {
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = CircleShapeLambda,
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
                    layerBlock = {
                        val progress = pressProgress.value
                        val scale = lerp(1f, 0.92f, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRect(activeGlassTint)
                    }
                )
        }
    } else null

    val backdropModifier = backdropGlassModifier ?: Modifier.spatialHazeEffect(
        state = hazeState,
        backgroundColor = frostedBg,
        tint = frostedTint,
        enabled = isBlurActive
    )

    val graphicsModifier = remember(useBackdropGlass, backdrop) {
        if (!useBackdropGlass || backdrop == null) {
            Modifier.graphicsLayer {
                val progress = pressProgress.value
                val scale = lerp(1f, 0.92f, progress)
                scaleX = scale
                scaleY = scale
            }
        } else Modifier
    }

    Surface(
        modifier = modifier
            .size(60.dp)
            .clip(CircleShape)
            .then(backdropModifier)
            .then(graphicsModifier)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    animationScope.launch { pressProgress.animateTo(1f, NavPressSpring) }
                    waitForUpOrCancellation()
                    animationScope.launch { pressProgress.animateTo(0f, NavPressSpring) }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = CircleShape,
        color = navContainerColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val targetIcon = if (isScrollToTop) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.Search
            val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
            val contentColor = if (isScrollToTop) activeContentColor else onSurfaceVariant

            val spatialFloatSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

            AnimatedContent(
                targetState = targetIcon,
                transitionSpec = {
                    (fadeIn(animationSpec = effectsSpec) + scaleIn(initialScale = 0.8f, animationSpec = spatialFloatSpec))
                        .togetherWith(fadeOut(animationSpec = effectsSpec) + scaleOut(targetScale = 0.8f, animationSpec = spatialFloatSpec))
                },
                label = "FloatingSearchIconMorph"
            ) { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = if (isScrollToTop) "Scroll to top" else "Search",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

