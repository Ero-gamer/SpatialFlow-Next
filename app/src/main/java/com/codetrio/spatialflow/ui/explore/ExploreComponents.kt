@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
 
package com.codetrio.spatialflow.ui.explore
 
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.codetrio.spatialflow.ui.theme.concentricInner
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.codetrio.spatialflow.ui.theme.SpatialShapes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import coil.request.ImageRequest
import com.codetrio.spatialflow.data.innertube.HomeSection
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.innertube.isWidescreenVideo
import com.codetrio.spatialflow.data.innertube.preferredThumbnailRatio
import com.codetrio.spatialflow.data.innertube.resize
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.components.SwipeToEnqueueContainer
import com.codetrio.spatialflow.util.FavoritesManager
import com.codetrio.spatialflow.util.SongDownloader
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel

/**
 * M3 Expressive shape morph clip.
 *
 * Normalizes the morphed path's bounding box and maps its center directly to
 * (size.width / 2, size.height / 2) while scaling to fill the composable container.
 * This guarantees perfect centering and sizing behind PFPs and in skeleton states.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float = 0f,
    private val springScale: Float = 1f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val androidPath = morph.toPath(progress = clampedProgress).asComposePath().asAndroidPath()

        val bounds = android.graphics.RectF()
        androidPath.computeBounds(bounds, true)

        val pathWidth = bounds.width()
        val pathHeight = bounds.height()

        if (pathWidth <= 0f || pathHeight <= 0f) {
            return Outline.Generic(androidPath.asComposePath())
        }

        val pathCx = bounds.centerX()
        val pathCy = bounds.centerY()

        val targetCx = size.width / 2f
        val targetCy = size.height / 2f

        val containerMin = minOf(size.width, size.height)
        val maxRadius = kotlin.math.hypot(pathWidth / 2f, pathHeight / 2f)

        // Base scale calculation
        val baseScale = if (rotationDegrees != 0f && maxRadius > 0f) {
            (containerMin / 2f * 0.99f) / maxRadius
        } else {
            containerMin / maxOf(pathWidth, pathHeight)
        }

        // Apply M3 Expressive spring bounce overshoot scale if progress exceeds [0,1]
        val overshootDelta = progress - clampedProgress
        val effectiveScale = baseScale * (springScale + overshootDelta * 0.45f)

        val m = android.graphics.Matrix()
        // 1. Translate path center to origin (0,0)
        m.postTranslate(-pathCx, -pathCy)
        // 2. Scale with spring bounce
        m.postScale(effectiveScale, effectiveScale)
        // 3. Rotate around origin (which is the path center)
        if (rotationDegrees != 0f) {
            m.postRotate(rotationDegrees)
        }
        // 4. Translate from origin to container center
        m.postTranslate(targetCx, targetCy)

        androidPath.transform(m)
        return Outline.Generic(androidPath.asComposePath())
    }
}

data class ExpressiveShapeMorphState(
    val morphShape: MorphShape,
    val easedProgress: Float
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberExpressiveShapeMorph(
    shapes: List<androidx.graphics.shapes.RoundedPolygon> = ExpressiveMaterialShapes,
    segmentDurationMillis: Int = 1400,
    rotationDurationMillis: Int = 12000
): ExpressiveShapeMorphState {
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_shape_morph")
    val globalProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = shapes.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(segmentDurationMillis * shapes.size, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "globalProgress"
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val currentIndex = globalProgress.toInt() % shapes.size
    val nextIndex = (currentIndex + 1) % shapes.size
    val segmentFraction = globalProgress - globalProgress.toInt()
    
    // M3 Expressive spring bounce pop curve during morphing
    val springBounce = (kotlin.math.sin(segmentFraction * Math.PI.toFloat()) * 0.06f)
    val easedProgress = remember(segmentFraction) {
        (1f - kotlin.math.cos(segmentFraction * Math.PI.toFloat())) / 2f
    }
    val morph = remember(currentIndex, nextIndex) {
        Morph(shapes[currentIndex], shapes[nextIndex])
    }
    val morphShape = MorphShape(morph, easedProgress, rotationAngle, springScale = 1f + springBounce)
    return ExpressiveShapeMorphState(morphShape, easedProgress)
}


val ExpressiveMaterialShapes = listOf(
    MaterialShapes.Arch,
    MaterialShapes.Arrow,
    MaterialShapes.Boom,
    MaterialShapes.Bun,
    MaterialShapes.Burst,
    MaterialShapes.Circle,
    MaterialShapes.ClamShell,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Diamond,
    MaterialShapes.Flower,
    MaterialShapes.Gem,
    MaterialShapes.Ghostish,
    MaterialShapes.Oval,
    MaterialShapes.Pentagon,
    MaterialShapes.Pill,
    MaterialShapes.PixelCircle,
    MaterialShapes.PuffyDiamond,
    MaterialShapes.Slanted,
    MaterialShapes.SoftBoom,
    MaterialShapes.SoftBurst,
    MaterialShapes.Square,
    MaterialShapes.Sunny,
    MaterialShapes.Triangle,
    MaterialShapes.VerySunny
)
/**
 * Composition-local shimmer offset that drives a single unified sweep across
 * every skeleton element in the subtree. Parent skeleton containers provide
 * this value via [UnifiedShimmerProvider].
 */
val LocalShimmerOffset = androidx.compose.runtime.compositionLocalOf { 0f }

/**
 * Wraps skeleton content and provides a single InfiniteTransition-driven
 * shimmer offset that all child [ShimmerModifier] elements read, producing
 * one cohesive diagonal sweep across the entire skeleton layout.
 */
@Composable
fun UnifiedShimmerProvider(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "unified_shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = -1200f,
        targetValue = 2400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "unified_shimmer_offset"
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalShimmerOffset provides shimmerOffset) {
        content()
    }
}

/**
 * Standard UI Shimmer Modifier to render placeholder skeleton states efficiently.
 * Reads from [LocalShimmerOffset] so all elements share a single diagonal sweep.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShimmerModifier(
    width: Dp,
    height: Dp,
    shape: Shape = RoundedCornerShape(8.dp),
    useMorphingLoadingShape: Boolean = false
) {
    val finalShape = if (useMorphingLoadingShape) {
        val morphState = rememberExpressiveShapeMorph(
            shapes = remember {
                listOf(
                    MaterialShapes.Cookie6Sided,
                    MaterialShapes.SoftBurst,
                    MaterialShapes.PuffyDiamond,
                    MaterialShapes.Square
                )
            }
        )
        morphState.morphShape
    } else {
        shape
    }

    val globalShimmerOffset = LocalShimmerOffset.current
    var posInRoot by remember { mutableStateOf(Offset.Zero) }
    val colorLow = MaterialTheme.colorScheme.surfaceContainerLow
    val colorHigh = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(finalShape)
            .onGloballyPositioned { coords ->
                posInRoot = coords.positionInRoot()
            }
            .drawWithContent {
                drawContent()
                // Offset the shared sweep band by this element's root position
                // so the gradient appears to sweep across the entire screen as one.
                val localOffset = globalShimmerOffset - posInRoot.x - posInRoot.y
                val brush = Brush.linearGradient(
                    colors = listOf(colorLow, colorHigh, colorLow),
                    start = Offset(localOffset, localOffset),
                    end = Offset(localOffset + 600f, localOffset + 600f)
                )
                drawRect(brush = brush)
            }
    )
}

fun Modifier.shimmerEffect(shape: Shape = RoundedCornerShape(8.dp)): Modifier =
    clip(shape).background(Color.Transparent)

/**
 * Material 3 Expressive spring press scale modifier.
 * Animates touch press scale below recomposition layer via graphicsLayer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.95f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "expressive_press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Rhythmic audio level indicators used to communicate "now playing" context visually.
 */
@Composable
fun AnimatedEqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val height1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(450, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bar1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(350, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bar2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(550, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bar3"
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        val barWidth = 3.dp
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = height1
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = height2
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = height3
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}

/**
 * Main content skeleton loading structure for the explore home feed.
 */
@Composable
fun HomeFeedSkeleton() {
    UnifiedShimmerProvider {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                    ShimmerModifier(width = 160.dp, height = 40.dp, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(20.dp))
                    // Chip row shimmer
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) {
                            ShimmerModifier(width = 72.dp, height = 32.dp, shape = CircleShape)
                        }
                    }
                }
            }
            items(4) {
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        ShimmerModifier(width = 140.dp, height = 26.dp, shape = RoundedCornerShape(8.dp))
                        ShimmerModifier(width = 64.dp, height = 28.dp, shape = CircleShape)
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(4) {
                            Column(horizontalAlignment = Alignment.Start) {
                                ShimmerModifier(width = 170.dp, height = 170.dp, useMorphingLoadingShape = true)
                                Spacer(modifier = Modifier.height(8.dp))
                                ShimmerModifier(width = 120.dp, height = 16.dp, shape = RoundedCornerShape(6.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                ShimmerModifier(width = 80.dp, height = 12.dp, shape = RoundedCornerShape(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contextual welcome banner dynamically responding to internal application time states.
 */
@Composable
fun WelcomeGreetingBanner(userName: String?) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) { in 0..11 -> "Good Morning"; in 12..16 -> "Good Afternoon"; else -> "Good Evening" }
    }
    val iconResId = remember(hour) {
        when (hour) {
            in 0..11 -> com.codetrio.spatialflow.R.drawable.ic_morning
            in 12..16 -> com.codetrio.spatialflow.R.drawable.ic_afternoon
            else -> com.codetrio.spatialflow.R.drawable.ic_evening
        }
    }
    val name = if (!userName.isNullOrBlank()) userName else "Listener"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = (-1).sp),
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
// ===== Top Result Hero Card (YouTube Music Style) =====

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopResultCard(
    topResult: SearchItem.TopResult,
    isCurrentlyPlaying: Boolean,
    onPlayClick: () -> Unit,
    onSaveClick: () -> Unit = {},
    onMoreClick: () -> Unit,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isArtist = topResult.artist != null || topResult.itemType.equals("Artist", ignoreCase = true)
    val isAlbum = topResult.album != null || topResult.itemType.equals("Album", ignoreCase = true) || topResult.itemType.equals("EP", ignoreCase = true) || topResult.itemType.equals("Single", ignoreCase = true)
    val isPlaylist = topResult.playlist != null || topResult.itemType.equals("Playlist", ignoreCase = true)
    val isSong = !isArtist && !isAlbum && !isPlaylist

    // Concentric Corner: Outer Card (28.dp) - Padding (20.dp) = 8.dp squircle
    val imageShape = if (isArtist) CircleShape else SpatialShapes.concentricSquircle(28.dp, 20.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize()
            .clip(SpatialShapes.ExtraLarge)
            .clickable(onClick = onClick),
        shape = SpatialShapes.ExtraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header / Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = "TOP RESULT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }

                val typeLabel = when {
                    isArtist -> "Artist"
                    isAlbum -> if (topResult.itemType.equals("EP", ignoreCase = true)) "EP" else if (topResult.itemType.equals("Single", ignoreCase = true)) "Single" else "Album"
                    isPlaylist -> "Playlist"
                    topResult.itemType.equals("Video", ignoreCase = true) -> "Video"
                    else -> "Song"
                }

                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Content Row (Thumbnail + Details)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(imageShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    SpatialImage(
                        model = topResult.thumbnailUrl,
                        contentDescription = null,
                        size = ImageSemanticSize.ThumbnailMedium,
                        shape = imageShape,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (isCurrentlyPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Equalizer,
                                contentDescription = "Playing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topResult.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = topResult.subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Row: Hero Action and Secondary Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Hero Action Button
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed) 12.dp else 24.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "PrimaryActionCorner"
                )

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isSong) onPlayClick() else onClick()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    interactionSource = interactionSource,
                    shape = SpatialShapes.squircle(cornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isArtist -> Icons.Rounded.Person
                            isAlbum -> Icons.Filled.Album
                            isPlaylist -> Icons.AutoMirrored.Filled.PlaylistPlay
                            isCurrentlyPlaying -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = when {
                            isArtist -> "View Artist"
                            isAlbum -> "View Album"
                            isPlaylist -> "View Playlist"
                            isCurrentlyPlaying -> "Pause"
                            else -> "Play"
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isArtist -> "View Artist"
                            isAlbum -> "View Album"
                            isPlaylist -> "View Playlist"
                            isCurrentlyPlaying -> "Playing"
                            else -> "Play"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                // Secondary Action: More Options
                val moreInteractionSource = remember { MutableInteractionSource() }
                val isMorePressed by moreInteractionSource.collectIsPressedAsState()
                val moreCornerRadius by animateDpAsState(
                    targetValue = if (isMorePressed) 10.dp else 16.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "MoreCorner"
                )

                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMoreClick()
                    },
                    modifier = Modifier.size(48.dp),
                    interactionSource = moreInteractionSource,
                    shape = SpatialShapes.squircle(moreCornerRadius),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More Options",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ===== Search Section Header =====

@Composable
fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ===== Search Result Item =====

@Composable
fun SearchResultItem(
    item: SearchItem,
    isCurrentlyPlaying: Boolean,
    isLoading: Boolean,
    onSongMenuClick: ((OnlineSong) -> Unit)? = null,
    onTouchDown: () -> Unit = {},
    onClick: () -> Unit
) {
    if (item is SearchItem.Song) {
        val context = LocalContext.current
        val activity = remember(context) { context as? androidx.fragment.app.FragmentActivity }
        val playerSharedViewModel = remember(activity) {
            activity?.let { androidx.lifecycle.ViewModelProvider(it)[PlayerSharedViewModel::class.java] }
        }

        SwipeToEnqueueContainer(
            onPlayNext = {
                val songItem = SongItem.createOnlineSong(
                    item.song.videoId, item.song.title, item.song.artist, "", item.song.durationMs, item.song.thumbnailUrl, item.song.artistId
                )
                playerSharedViewModel?.addToQueueNext(songItem)
            },
            onAddToQueue = {
                val songItem = SongItem.createOnlineSong(
                    item.song.videoId, item.song.title, item.song.artist, "", item.song.durationMs, item.song.thumbnailUrl, item.song.artistId
                )
                playerSharedViewModel?.addToQueue(songItem)
            }
        ) {
            SearchResultItemContent(
                item = item,
                isCurrentlyPlaying = isCurrentlyPlaying,
                isLoading = isLoading,
                onSongMenuClick = onSongMenuClick,
                onTouchDown = onTouchDown,
                onClick = onClick
            )
        }
    } else {
        SearchResultItemContent(
            item = item,
            isCurrentlyPlaying = isCurrentlyPlaying,
            isLoading = isLoading,
            onSongMenuClick = onSongMenuClick,
            onTouchDown = onTouchDown,
            onClick = onClick
        )
    }
}

@Composable
private fun SearchResultItemContent(
    item: SearchItem,
    isCurrentlyPlaying: Boolean,
    isLoading: Boolean,
    onSongMenuClick: ((OnlineSong) -> Unit)? = null,
    onTouchDown: () -> Unit = {},
    onClick: () -> Unit
) {
    val backgroundColor = if (isCurrentlyPlaying)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else Color.Transparent

    val containerShape = RoundedCornerShape(12.dp)

    val thumbnailUrl = when (item) {
        is SearchItem.TopResult -> item.thumbnailUrl
        is SearchItem.Header -> null
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }
    val isCircle = item is SearchItem.Artist
    val itemId = when (item) {
        is SearchItem.TopResult -> "top-${item.title}"
        is SearchItem.Header -> "header-${item.title}"
        is SearchItem.Song -> item.song.videoId
        is SearchItem.Album -> item.album.browseId
        is SearchItem.Artist -> item.artist.browseId
        is SearchItem.Playlist -> item.playlist.playlistId
    }
    val imageShape = if (isCircle) CircleShape else RoundedCornerShape(6.dp)

    val itemBadge = when (item) {
        is SearchItem.Song -> item.badge ?: item.typeText
        is SearchItem.Album -> item.badge
        is SearchItem.Playlist -> item.badge
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(containerShape)
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (item is SearchItem.Song && onSongMenuClick != null) {
                        onSongMenuClick(item.song)
                    }
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Image
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(imageShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            SpatialImage(
                model = thumbnailUrl,
                contentDescription = null,
                size = ImageSemanticSize.ThumbnailMedium,
                shape = imageShape,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Titles and Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (item) {
                    is SearchItem.TopResult -> item.title
                    is SearchItem.Header -> item.title
                    is SearchItem.Song -> item.song.title
                    is SearchItem.Album -> item.album.title
                    is SearchItem.Artist -> item.artist.title
                    is SearchItem.Playlist -> item.playlist.title
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 15.5.sp
                ),
                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isExplicit = when (item) {
                    is SearchItem.Song -> item.song.isExplicit
                    is SearchItem.TopResult -> item.song?.isExplicit == true
                    else -> false
                }
                if (isExplicit) {
                    com.codetrio.spatialflow.ui.components.ExplicitBadge(
                        size = 13.dp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                if (!itemBadge.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = itemBadge.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = when (item) {
                        is SearchItem.TopResult -> {
                            if (item.song != null) {
                                val dur = item.song.duration ?: if (item.song.durationMs > 0) {
                                    val totalSec = item.song.durationMs / 1000
                                    val min = totalSec / 60
                                    val sec = totalSec % 60
                                    if (sec < 10) "$min:0$sec" else "$min:$sec"
                                } else null
                                "Song • ${item.song.artist}${dur?.let { " • $it" } ?: ""}"
                            } else if (item.album != null) {
                                "Album • ${item.album.artists.joinToString { it.name }}${item.album.year?.let { " • $it" } ?: ""}"
                            } else {
                                item.subtitle
                            }
                        }
                        is SearchItem.Header -> ""
                        is SearchItem.Song -> {
                            val dur = item.song.duration ?: if (item.song.durationMs > 0) {
                                val totalSec = item.song.durationMs / 1000
                                val min = totalSec / 60
                                val sec = totalSec % 60
                                if (sec < 10) "$min:0$sec" else "$min:$sec"
                            } else null
                            "${item.song.artist}${dur?.let { " • $it" } ?: ""}"
                        }
                        is SearchItem.Album -> "Album • ${item.album.artists.joinToString { it.name }}${item.album.year?.let { " • $it" } ?: ""}"
                        is SearchItem.Artist -> "Artist${item.artist.subscriberCount?.let { count ->
                            val clean = if (count.contains("Spotify", ignoreCase = true) || count.contains("Monthly", ignoreCase = true)) {
                                count.replace("Spotify", "", ignoreCase = true)
                                    .replace("Monthly Monthly Listeners", "Monthly Listeners", ignoreCase = true)
                                    .trim()
                            } else count.trim()
                            if (clean.isNotEmpty()) " • $clean" else ""
                        } ?: ""}"
                        is SearchItem.Playlist -> "Playlist${item.playlist.songCount?.let { " • $it songs" } ?: ""}"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing Content
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item is SearchItem.Song && isCurrentlyPlaying && !isLoading) {
                AnimatedEqualizerBars(
                    modifier = Modifier.size(22.dp).height(14.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            when (item) {
                is SearchItem.Song -> {
                    if (onSongMenuClick != null) {
                        IconButton(
                            onClick = { onSongMenuClick(item.song) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(36.dp))
                    }
                }
                is SearchItem.Album, is SearchItem.Playlist -> {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

// ===== Search Skeleton Loaders =====

@Composable
fun TopResultCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerModifier(
                    width = 72.dp,
                    height = 72.dp,
                    shape = RoundedCornerShape(24.dp.concentricInner(18.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    ShimmerModifier(
                        width = 180.dp,
                        height = 20.dp,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerModifier(
                        width = 120.dp,
                        height = 14.dp,
                        shape = RoundedCornerShape(4.dp)
                    )
                }

                ShimmerModifier(
                    width = 36.dp,
                    height = 36.dp,
                    shape = CircleShape
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ShimmerModifier(
                width = 320.dp,
                height = 46.dp,
                shape = CircleShape
            )
        }
    }
}

@Composable
fun SearchResultItemSkeleton(
    isCircle: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerModifier(
            width = 56.dp,
            height = 56.dp,
            shape = if (isCircle) CircleShape else RoundedCornerShape(6.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            ShimmerModifier(
                width = 180.dp,
                height = 16.dp,
                shape = RoundedCornerShape(4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            ShimmerModifier(
                width = 120.dp,
                height = 12.dp,
                shape = RoundedCornerShape(4.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        ShimmerModifier(
            width = 24.dp,
            height = 24.dp,
            shape = CircleShape
        )
    }
}

@Composable
fun SearchResultsSkeletonList(modifier: Modifier = Modifier) {
    UnifiedShimmerProvider {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) {
            TopResultCardSkeleton()
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Section Header Skeleton
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ShimmerModifier(
                    width = 110.dp,
                    height = 18.dp,
                    shape = RoundedCornerShape(4.dp)
                )
            }
            
            repeat(6) { index ->
                SearchResultItemSkeleton(isCircle = index == 3)
            }
        }
    }
}

@Composable
fun SearchPaginationSkeleton(modifier: Modifier = Modifier) {
    UnifiedShimmerProvider {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            repeat(2) {
                SearchResultItemSkeleton()
            }
        }
    }
}

// ===== Bento Discovery Cells =====

@Composable
fun BentoCell(
    item: SearchItem,
    modifier: Modifier = Modifier,
    isFeature: Boolean = false,
    onClick: () -> Unit
) {
    val title = when (item) {
        is SearchItem.TopResult -> item.title
        is SearchItem.Header -> item.title
        is SearchItem.Song -> item.song.title
        is SearchItem.Album -> item.album.title
        is SearchItem.Artist -> item.artist.title
        is SearchItem.Playlist -> item.playlist.title
    }
    val subtitle = when (item) {
        is SearchItem.TopResult -> {
            if (item.song != null) {
                item.song.artist
            } else if (item.album != null) {
                item.album.artists.joinToString { it.name }
            } else {
                item.subtitle
            }
        }
        is SearchItem.Header -> ""
        is SearchItem.Song -> item.song.artist
        is SearchItem.Album -> item.album.artists.joinToString { it.name }
        is SearchItem.Artist -> "Artist"
        is SearchItem.Playlist -> item.playlist.author?.name ?: "Playlist"
    }
    val thumbnailUrl = when (item) {
        is SearchItem.TopResult -> item.thumbnailUrl
        is SearchItem.Header -> null
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }

    Card(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            SpatialImage(
                model = thumbnailUrl,
                contentDescription = null,
                size = ImageSemanticSize.ThumbnailLarge,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (isFeature) 0.9f else 0.7f)),
                        startY = 50f
                    )
                )
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(if (isFeature) 16.dp else 12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                if (isFeature) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        shape = CircleShape,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "Feature",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = title,
                    style = if (isFeature) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = if (isFeature) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ===== Home Feed Section =====

@Composable
fun HomeSectionRow(
    section: HomeSection,
    currentSongId: String? = null,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSongTouchDown: (OnlineSong) -> Unit = {},
    onSectionClick: (browseId: String, params: String?, title: String) -> Unit = { _, _, _ -> },
    onRadioClick: (SearchItem) -> Unit = {},
    onSaveClick: (SearchItem) -> Unit = {}
) {
    val hasSongsOnly = section.items.isNotEmpty() && section.items.all { it is SearchItem.Song }

    val sectionSongs = remember(section.items) {
        section.items.filterIsInstance<SearchItem.Song>().map { it.song }
    }

    val handleSongClick = remember(sectionSongs, onSongClick) {
        { song: OnlineSong ->
            val idx = sectionSongs.indexOfFirst { it.videoId == song.videoId }
            onSongClick(song, sectionSongs, idx)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateContentSize()) {
        // Universal Section Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasSongsOnly || section.title.contains("daily discover", ignoreCase = true)) {
                    OutlinedButton(
                        onClick = {
                            val songs = section.items.filterIsInstance<SearchItem.Song>().map { it.song }
                            if (songs.isNotEmpty()) handleSongClick(songs.first())
                        },
                        modifier = Modifier.height(32.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Text("Play all", style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                if (section.browseEndpoint != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onSectionClick(section.browseEndpoint, section.params, section.title) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "See All",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        when {
            // 0. BENTO GRID — High Fidelity Personal Mix
            section.title.contains("personalized mix", ignoreCase = true) && section.items.size >= 5 -> {
                val items = section.items
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.heightIn(min = 220.dp, max = 280.dp).animateContentSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Dominant Feature
                        BentoCell(
                            item = items[0],
                            modifier = Modifier.weight(1.25f).fillMaxHeight(),
                            isFeature = true,
                            onClick = {
                                when (val it = items[0]) {
                                    is SearchItem.Song -> handleSongClick(it.song)
                                    is SearchItem.Album -> onAlbumClick(it.album)
                                    is SearchItem.Artist -> onArtistClick(it.artist)
                                    is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                    is SearchItem.TopResult, is SearchItem.Header -> {}
                                }
                            }
                        )
                        // Column of stacked tiles
                        Column(modifier = Modifier.weight(0.75f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            BentoCell(item = items[1], modifier = Modifier.weight(1f).fillMaxWidth(), onClick = {
                                when (val it = items[1]) {
                                    is SearchItem.Song -> handleSongClick(it.song)
                                    is SearchItem.Album -> onAlbumClick(it.album)
                                    is SearchItem.Artist -> onArtistClick(it.artist)
                                    is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                    is SearchItem.TopResult, is SearchItem.Header -> {}
                                }
                            })
                            BentoCell(item = items[2], modifier = Modifier.weight(1f).fillMaxWidth(), onClick = {
                                when (val it = items[2]) {
                                    is SearchItem.Song -> handleSongClick(it.song)
                                    is SearchItem.Album -> onAlbumClick(it.album)
                                    is SearchItem.Artist -> onArtistClick(it.artist)
                                    is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                    is SearchItem.TopResult, is SearchItem.Header -> {}
                                }
                            })
                        }
                    }
                    Row(modifier = Modifier.heightIn(min = 110.dp, max = 150.dp).animateContentSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Lower row wide tiles
                        BentoCell(item = items[3], modifier = Modifier.weight(1f).fillMaxHeight(), onClick = {
                            when (val it = items[3]) {
                                is SearchItem.Song -> handleSongClick(it.song)
                                is SearchItem.Album -> onAlbumClick(it.album)
                                is SearchItem.Artist -> onArtistClick(it.artist)
                                is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                is SearchItem.TopResult, is SearchItem.Header -> {}
                            }
                        })
                        BentoCell(item = items[4], modifier = Modifier.weight(1f).fillMaxHeight(), onClick = {
                            when (val it = items[4]) {
                                is SearchItem.Song -> handleSongClick(it.song)
                                is SearchItem.Album -> onAlbumClick(it.album)
                                is SearchItem.Artist -> onArtistClick(it.artist)
                                is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                is SearchItem.TopResult, is SearchItem.Header -> {}
                            }
                        })
                    }
                }
            }

            // 1. SPEED DIAL — 3×3 Squircle Grid
            section.title.contains("speed dial", ignoreCase = true) -> {
                val rows = remember(section.items) { section.items.take(9).chunked(3) }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rows.forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { item ->
                                val title = when (item) {
                                    is SearchItem.TopResult -> item.title
                                    is SearchItem.Header -> item.title
                                    is SearchItem.Song -> item.song.title
                                    is SearchItem.Album -> item.album.title
                                    is SearchItem.Artist -> item.artist.title
                                    is SearchItem.Playlist -> item.playlist.title
                                }
                                val thumbnailUrl = when (item) {
                                    is SearchItem.TopResult -> item.thumbnailUrl
                                    is SearchItem.Header -> null
                                    is SearchItem.Song -> item.song.thumbnailUrl
                                    is SearchItem.Album -> item.album.thumbnailUrl
                                    is SearchItem.Artist -> item.artist.thumbnailUrl
                                    is SearchItem.Playlist -> item.playlist.thumbnailUrl
                                }
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            when (item) {
                                                is SearchItem.Song -> handleSongClick(item.song)
                                                is SearchItem.Album -> onAlbumClick(item.album)
                                                is SearchItem.Artist -> onArtistClick(item.artist)
                                                is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                                is SearchItem.TopResult, is SearchItem.Header -> {}
                                            }
                                        }
                                ) {
                                    SpatialImage(
                                        model = thumbnailUrl,
                                        contentDescription = null,
                                        size = ImageSemanticSize.ThumbnailMedium,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Scrim overlay using theme token scrim is always dark by M3 spec
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)
                                                ),
                                                startY = 80f
                                            )
                                        )
                                    )
                                    Text(
                                        text = title,
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                    )
                                }
                            }
                            if (rowItems.size < 3) repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // 2. YOUR DAILY DISCOVER — Large Immersive Cards
            section.title.contains("daily discover", ignoreCase = true) -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = section.items,
                        key = { idx, item ->
                            val sectionScope = section.title.hashCode()
                            val contentId = when (item) {
                                is SearchItem.Song -> item.song.videoId
                                is SearchItem.Album -> item.album.browseId
                                is SearchItem.Artist -> item.artist.browseId
                                is SearchItem.Playlist -> item.playlist.playlistId
                                is SearchItem.TopResult -> item.title
                                is SearchItem.Header -> item.title
                            }
                            "discover-$sectionScope-$idx-$contentId"
                        },
                        contentType = { _, _ -> "discover_card" }
                    ) { _, item ->
                        val title = when (item) {
                            is SearchItem.TopResult -> item.title
                            is SearchItem.Header -> item.title
                            is SearchItem.Song -> item.song.title
                            is SearchItem.Album -> item.album.title
                            is SearchItem.Artist -> item.artist.title
                            is SearchItem.Playlist -> item.playlist.title
                        }
                        val subtitle = when (item) {
                            is SearchItem.TopResult -> item.subtitle
                            is SearchItem.Header -> ""
                            is SearchItem.Song -> item.song.artist
                            is SearchItem.Album -> item.album.artists.joinToString { it.name }
                            is SearchItem.Artist -> item.artist.subscriberCount?.replace("Spotify", "", ignoreCase = true)
                                ?.replace("Monthly Monthly Listeners", "Monthly Listeners", ignoreCase = true)
                                ?.trim() ?: "Artist"
                            is SearchItem.Playlist -> item.playlist.author?.name ?: "Playlist"
                        }
                        val thumbnailUrl = when (item) {
                            is SearchItem.TopResult -> item.thumbnailUrl
                            is SearchItem.Header -> null
                            is SearchItem.Song -> item.song.thumbnailUrl
                            is SearchItem.Album -> item.album.thumbnailUrl
                            is SearchItem.Artist -> item.artist.thumbnailUrl
                            is SearchItem.Playlist -> item.playlist.thumbnailUrl
                        }
                        val promptText = when (item) {
                            is SearchItem.Song -> "Because you liked ${item.song.artist}"
                            else -> "Handpicked based on your interests"
                        }

                        Box(
                            modifier = Modifier.widthIn(min = 260.dp, max = 320.dp).aspectRatio(1f).animateContentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    when (item) {
                                        is SearchItem.Song -> handleSongClick(item.song)
                                        is SearchItem.Album -> onAlbumClick(item.album)
                                        is SearchItem.Artist -> onArtistClick(item.artist)
                                        is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                        is SearchItem.TopResult, is SearchItem.Header -> {}
                                    }
                                }
                        ) {
                            SpatialImage(
                                model = thumbnailUrl,
                                contentDescription = null,
                                size = ImageSemanticSize.ThumbnailMedium,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                        0.35f to Color.Transparent,
                                        1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.88f)
                                    )
                                )
                            )
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = title,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = promptText,
                                    color = Color.White.copy(alpha = 0.65f),
                                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 3. FROM THE COMMUNITY — Expressive Playlist Card
            section.title.contains("community", ignoreCase = true) -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = section.items,
                        key = { idx, item ->
                            val sectionScope = section.title.hashCode()
                            val contentId = when (item) {
                                is SearchItem.Song -> item.song.videoId
                                is SearchItem.Album -> item.album.browseId
                                is SearchItem.Artist -> item.artist.browseId
                                is SearchItem.Playlist -> item.playlist.playlistId
                                is SearchItem.TopResult -> item.title
                                is SearchItem.Header -> item.title
                            }
                            "community-$sectionScope-$idx-$contentId"
                        },
                        contentType = { _, _ -> "community_card" }
                    ) { _, item ->
                        CommunityExpressiveCard(
                            item = item,
                            onSongClick = handleSongClick,
                            onPlaylistClick = onPlaylistClick,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            onRadioClick = onRadioClick,
                            onSaveClick = onSaveClick
                        )
                    }
                }
            }

            // 4. GENERAL FALLBACK — Song List or Carousel
            else -> {
                val isVideoSection = section.title.contains("video", ignoreCase = true)
                if (hasSongsOnly && !isVideoSection) {
                    val chunkedSongs = remember(section.items) {
                        section.items.filterIsInstance<SearchItem.Song>().chunked(4)
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = chunkedSongs,
                            key = { idx, chunk -> "song-col-$idx-${chunk.firstOrNull()?.song?.videoId ?: chunk.hashCode()}" },
                            contentType = { _, _ -> "song_column" }
                        ) { _, songChunk ->
                            Column(modifier = Modifier.widthIn(min = 280.dp, max = 340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                songChunk.forEach { item ->
                                    HomeSongListItem(
                                        song = item.song,
                                        isPlaying = item.song.videoId == currentSongId,
                                        onTouchDown = { onSongTouchDown(item.song) },
                                        onClick = { handleSongClick(item.song) },
                                        onMenuClick = { onSongMenuClick(item.song) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = section.items,
                            key = { idx, item ->
                                val sectionScope = section.title.hashCode()
                                val contentId = when (item) {
                                    is SearchItem.Song -> item.song.videoId
                                    is SearchItem.Album -> item.album.browseId
                                    is SearchItem.Artist -> item.artist.browseId
                                    is SearchItem.Playlist -> item.playlist.playlistId
                                    is SearchItem.TopResult -> item.title
                                    is SearchItem.Header -> item.title
                                }
                                "carousel-$sectionScope-$idx-$contentId"
                            },
                            contentType = { _, item ->
                                if (item.isWidescreenVideo || isVideoSection) "video_card" else "carousel_card"
                            }
                        ) { _, item ->
                            HomeCarouselItem(
                                item = item,
                                sectionTitle = section.title,
                                isPlaying = when (item) {
                                    is SearchItem.Song -> item.song.videoId == currentSongId
                                    else -> false
                                },
                                onTouchDown = {
                                    if (item is SearchItem.Song) onSongTouchDown(item.song)
                                },
                                onClick = {
                                    when (item) {
                                        is SearchItem.Song -> handleSongClick(item.song)
                                        is SearchItem.Album -> onAlbumClick(item.album)
                                        is SearchItem.Artist -> onArtistClick(item.artist)
                                        is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                        is SearchItem.TopResult, is SearchItem.Header -> {}
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== Carousel Item =====

@Composable
fun HomeCarouselItem(
    item: SearchItem,
    isPlaying: Boolean,
    sectionTitle: String? = null,
    onTouchDown: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCircle = item is SearchItem.Artist
    val ratio = remember(item, sectionTitle) { item.preferredThumbnailRatio(sectionTitle) }
    val isWidescreen = ratio > 1.2f
    val imageShape = if (isCircle) CircleShape else RoundedCornerShape(8.dp)
    val baseAlbumHeight = 160.dp
    val cardWidth = when {
        isWidescreen -> (baseAlbumHeight * ratio)
        isCircle -> baseAlbumHeight
        else -> baseAlbumHeight
    }
    val itemId = when (item) {
        is SearchItem.TopResult -> "top-${item.title}"
        is SearchItem.Header -> "header-${item.title}"
        is SearchItem.Song -> item.song.videoId
        is SearchItem.Album -> item.album.browseId
        is SearchItem.Artist -> item.artist.browseId
        is SearchItem.Playlist -> item.playlist.playlistId
    }
    val thumbnailUrl = when (item) {
        is SearchItem.TopResult -> item.thumbnailUrl
        is SearchItem.Header -> null
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }
    val title = when (item) {
        is SearchItem.TopResult -> item.title
        is SearchItem.Header -> item.title
        is SearchItem.Song -> item.song.title
        is SearchItem.Album -> item.album.title
        is SearchItem.Artist -> item.artist.title
        is SearchItem.Playlist -> item.playlist.title
    }
    val subtitle = when (item) {
        is SearchItem.TopResult -> item.subtitle
        is SearchItem.Header -> ""
        is SearchItem.Song -> item.song.artist
        is SearchItem.Album -> item.album.artists.joinToString { it.name }
        is SearchItem.Artist -> item.artist.subscriberCount?.replace("Spotify", "", ignoreCase = true)
            ?.replace("Monthly Monthly Listeners", "Monthly Listeners", ignoreCase = true)
            ?.trim() ?: ""
        is SearchItem.Playlist -> item.playlist.author?.name ?: ""
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .animateContentSize()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
            contentAlignment = Alignment.TopStart
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = imageShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SpatialImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        size = if (isWidescreen) ImageSemanticSize.ThumbnailLarge else ImageSemanticSize.ThumbnailMedium,
                        shape = imageShape,
                        aspectRatio = ratio,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isPlaying) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedEqualizerBars(
                                modifier = Modifier.size(32.dp).height(24.dp),
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    }
                    val durationText = (item as? SearchItem.Song)?.song?.duration
                    if (isWidescreen && !durationText.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = durationText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ===== Song List Item =====

@Composable
fun HomeSongListItem(
    song: OnlineSong,
    isPlaying: Boolean,
    onTouchDown: () -> Unit = {},
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMenuClick
            )
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp)) {
            SpatialImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                size = ImageSemanticSize.ThumbnailSmall,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizerBars(
                        modifier = Modifier.size(24.dp).height(16.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.isExplicit) {
                    com.codetrio.spatialflow.ui.components.ExplicitBadge(modifier = Modifier.padding(end = 6.dp))
                }
                Text(
                    text = "${song.artist}${song.duration?.let { " • $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompactMenuItem(
    text: String,
    icon: ImageVector,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    onClick: () -> Unit
) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        shapes = shapes,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.primary
        ),
        content = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlineSongBottomSheet(
    song: OnlineSong,
    isPinned: Boolean,
    onDismissRequest: () -> Unit,
    playerSharedViewModel: PlayerSharedViewModel,
    exploreViewModel: ExploreViewModel,
    onPlaylistAddClick: (SongItem) -> Unit,
    onViewCreditsClick: (OnlineSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val favoritesManager = remember { FavoritesManager(context) }
    var isFavorite by remember(song.videoId) { mutableStateOf(favoritesManager.isFavorite(song.videoId.hashCode().toLong())) }

    val onlineSongItem = remember(song.videoId) {
        SongItem.createOnlineSong(
            song.videoId,
            song.title,
            song.artist,
            "https://music.youtube.com/watch?v=${song.videoId}",
            song.durationMs,
            song.thumbnailUrl,
            song.artistId
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp, top = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Title/Artist + Like + Close
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        val target = !isFavorite
                        favoritesManager.setFavorite(song.videoId.hashCode().toLong(), target)
                        isFavorite = target
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage(
                            if (target) "Added to library" else "Removed from library"
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.ThumbUp else Icons.Filled.ThumbUpOffAlt,
                        contentDescription = "Like",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Big Square Button Grid: Play next, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Play next
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            playerSharedViewModel.addToQueueNext(onlineSongItem)
                            exploreViewModel.addToQueueNext(song)
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Playing next: ${song.title}")
                            onDismissRequest()
                        }
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1.5f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text("Play next", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }

                // Card 2: Share
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, "Check out ${song.title} by ${song.artist}: https://music.youtube.com/watch?v=${song.videoId}")
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
                            onDismissRequest()
                        }
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1.5f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text("Share", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Optimized 2-Pane Actions for Landscape Width
            val menuItems = listOf(
                Triple("Start mix", Icons.Default.Radio) {
                    exploreViewModel.playOnlineSongWithQueue(song, emptyList(), 0)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Starting mix based on ${song.title}")
                    onDismissRequest()
                },
                Triple("Add to queue", Icons.AutoMirrored.Filled.PlaylistPlay) {
                    playerSharedViewModel.addToQueue(onlineSongItem)
                    exploreViewModel.addToQueueLast(song)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(
                        "Added to queue",
                        iconVector = Icons.AutoMirrored.Filled.PlaylistAdd
                    )
                    onDismissRequest()
                },
                Triple("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) {
                    onPlaylistAddClick(onlineSongItem)
                    onDismissRequest()
                },
                Triple("Download", Icons.Default.Download) {
                    SongDownloader.downloadSong(context, onlineSongItem)
                    onDismissRequest()
                },
                Triple("Go to album", Icons.Default.Album) {
                    val id = song.albumId
                    if (!id.isNullOrBlank()) {
                        exploreViewModel.loadAlbum(id)
                    } else {
                        exploreViewModel.loadAlbumForSong(
                            songTitle = song.title,
                            artistName = song.artist,
                            albumName = song.albumName,
                            videoId = song.videoId
                        )
                    }
                    onDismissRequest()
                },
                Triple("Go to artist", Icons.Default.Person) {
                    val aId = song.artistId
                    if (!aId.isNullOrBlank()) {
                        exploreViewModel.loadArtist(aId)
                    } else {
                        exploreViewModel.loadArtistByName(song.artist)
                    }
                    onDismissRequest()
                },
                Triple("View song credits", Icons.Default.Groups) {
                    onViewCreditsClick(song)
                    onDismissRequest()
                },
                Triple(
                    if (isPinned) "Unpin from Speed dial" else "Pin to Speed dial",
                    Icons.Default.PushPin
                ) {
                    exploreViewModel.pinToSpeedDial(song)
                    val msg = if (isPinned) "Song removed from Speed Dial!" else "Song pinned to Speed Dial!"
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                    onDismissRequest()
                },
                Triple("Not interested", Icons.Default.Block) {
                    exploreViewModel.setNotInterested(song)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("We will suggest fewer songs like this")
                    onDismissRequest()
                }
            )

            val menuColumns = if (isLandscape) 2 else 1
            val menuChunks = remember(menuItems, menuColumns) { menuItems.chunked(menuColumns) }

            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                menuChunks.forEachIndexed { chunkIdx, chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                    ) {
                        chunk.forEachIndexed { innerIdx, itemTuple ->
                            val (title, icon, action) = itemTuple
                            val globalIndex = chunkIdx * menuColumns + innerIdx
                            Box(modifier = Modifier.weight(1f)) {
                                CompactMenuItem(
                                    text = title,
                                    icon = icon,
                                    shapes = if (isLandscape) {
                                        ListItemDefaults.shapes()
                                    } else {
                                        ListItemDefaults.segmentedShapes(index = globalIndex, count = menuItems.size)
                                    },
                                    onClick = action
                                )
                            }
                        }
                        if (chunk.size < menuColumns) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ===== Expressive Community Card =====

@Composable
fun CommunityExpressiveCard(
    item: SearchItem,
    onSongClick: (OnlineSong) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onRadioClick: (SearchItem) -> Unit = {},
    onSaveClick: (SearchItem) -> Unit = {}
) {
    val title = when (item) {
        is SearchItem.TopResult -> item.title
        is SearchItem.Header -> item.title
        is SearchItem.Song -> item.song.title
        is SearchItem.Album -> item.album.title
        is SearchItem.Artist -> item.artist.title
        is SearchItem.Playlist -> item.playlist.title
    }
    val author = when (item) {
        is SearchItem.TopResult -> item.subtitle
        is SearchItem.Header -> ""
        is SearchItem.Song -> item.song.artist
        is SearchItem.Album -> item.album.artists.firstOrNull()?.name ?: ""
        is SearchItem.Artist -> "Artist"
        is SearchItem.Playlist -> item.playlist.author?.name ?: "Community Curator"
    }
    val songCount = when (item) {
        is SearchItem.Playlist -> item.playlist.songCount?.takeIf { it.isNotBlank() } ?: ""
        else -> ""
    }
    val thumbnailUrl = when (item) {
        is SearchItem.TopResult -> item.thumbnailUrl
        is SearchItem.Header -> null
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }
    val id = when (item) {
        is SearchItem.TopResult -> "top-${item.title}"
        is SearchItem.Header -> "header-${item.title}"
        is SearchItem.Song -> item.song.videoId
        is SearchItem.Album -> item.album.browseId
        is SearchItem.Artist -> item.artist.browseId
        is SearchItem.Playlist -> item.playlist.playlistId
    }

    val haptic = LocalHapticFeedback.current
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.97f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "CardScale"
    )

    val cardCornerRadius by animateDpAsState(
        targetValue = if (isCardPressed) 18.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "CardCorner"
    )

    fun handleMainClick() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (item) {
            is SearchItem.Song -> onSongClick(item.song)
            is SearchItem.Album -> onAlbumClick(item.album)
            is SearchItem.Artist -> onArtistClick(item.artist)
            is SearchItem.Playlist -> onPlaylistClick(item.playlist)
            is SearchItem.TopResult, is SearchItem.Header -> {}
        }
    }

    Surface(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .animateContentSize()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clip(RoundedCornerShape(cardCornerRadius))
            .clickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = { handleMainClick() }
            ),
        shape = RoundedCornerShape(cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(0f, 0f),
                        radius = 450f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Tag Row: Community Pill Badge + Song Count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Whatshot,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Community Pick",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (songCount.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = songCount,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Middle Section: Artwork + Title + Curator Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SpatialImage(
                        model = thumbnailUrl,
                        contentDescription = title,
                        size = ImageSemanticSize.ThumbnailMedium,
                        shape = RoundedCornerShape(18.dp),
                        allowHardware = false,
                        modifier = Modifier
                            .size(86.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentScale = ContentScale.Crop
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = author.ifBlank { "Community Curator" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Bottom Action Bar: Hero Play Button + Tonal Radio & Save Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hero Play Button with Spring Shape Morphing
                    val playInteractionSource = remember { MutableInteractionSource() }
                    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                    val playCorner by animateDpAsState(
                        targetValue = if (isPlayPressed) 8.dp else 16.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "PlayCornerMorph"
                    )

                    Button(
                        onClick = { handleMainClick() },
                        interactionSource = playInteractionSource,
                        shape = RoundedCornerShape(playCorner),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Play",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Secondary Tonal Actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val radioInteractionSource = remember { MutableInteractionSource() }
                        val isRadioPressed by radioInteractionSource.collectIsPressedAsState()
                        val radioCorner by animateDpAsState(
                            targetValue = if (isRadioPressed) 8.dp else 14.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "RadioCornerMorph"
                        )

                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRadioClick(item)
                            },
                            interactionSource = radioInteractionSource,
                            shape = RoundedCornerShape(radioCorner),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = com.codetrio.spatialflow.R.drawable.ic_radio),
                                contentDescription = "Start Radio",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        val saveInteractionSource = remember { MutableInteractionSource() }
                        val isSavePressed by saveInteractionSource.collectIsPressedAsState()
                        val saveCorner by animateDpAsState(
                            targetValue = if (isSavePressed) 8.dp else 14.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "SaveCornerMorph"
                        )

                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSaveClick(item)
                            },
                            interactionSource = saveInteractionSource,
                            shape = RoundedCornerShape(saveCorner),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BookmarkBorder,
                                contentDescription = "Save to Library",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
