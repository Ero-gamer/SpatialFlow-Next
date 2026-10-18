@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.codetrio.spatialflow.BuildConfig
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.ui.components.SegmentedGroup
import com.codetrio.spatialflow.ui.components.SegmentedRow
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.MorphShape
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.ui.explore.rememberExpressiveShapeMorph
import com.codetrio.spatialflow.util.TelegramHelper

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AboutScreen(
    navController: NavController,
    onCheckUpdate: () -> Unit,
    onWhatsNew: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val context = LocalContext.current

    // Real dynamic SDK, build, and platform specifications
    val appInfo = context.applicationInfo
    val targetSdk = appInfo.targetSdkVersion
    val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 25
    val runningAndroidVersion = Build.VERSION.RELEASE
    val runningApiLevel = Build.VERSION.SDK_INT
    val primaryAbi = remember { Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a" }
    val deviceModel = remember {
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }
    val media3Version = remember {
        try {
            androidx.media3.common.MediaLibraryInfo.VERSION
        } catch (_: Throwable) {
            "1.5.1"
        }
    }

    SettingsScaffold(
        title = "About",
        onBack = { navController.popBackStack() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── 1. Hero Stage with Floating Material Shapes (Glow Removed) ─
            val infiniteTransition = rememberInfiniteTransition(label = "heroFloatingShapes")

            val floatY1 by infiniteTransition.animateFloat(
                initialValue = -7f,
                targetValue = 7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "floatY1"
            )

            val floatY2 by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4100, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "floatY2"
            )

            val floatY3 by infiniteTransition.animateFloat(
                initialValue = -8f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3700, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "floatY3"
            )

            val floatY4 by infiniteTransition.animateFloat(
                initialValue = 6f,
                targetValue = -9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "floatY4"
            )

            val floatY5 by infiniteTransition.animateFloat(
                initialValue = -5f,
                targetValue = 7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "floatY5"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(top = 16.dp)
            ) {
                // Orbital constellation container tightly wrapping the hero logo
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(width = 270.dp, height = 240.dp)
                ) {
                    // Center Hero Brand Icon with Alpha
                    Icon(
                        painter = painterResource(R.drawable.ic_applogo),
                        contentDescription = stringResource(R.string.app_name),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(140.dp)
                            .graphicsLayer {
                                alpha = 0.90f
                            }
                    )

                    // 1. Dolby Atmos (Top Start - snug to logo)
                    FloatingShapeFeatureIcon(
                        icon = R.drawable.ic_dolby,
                        polygon = MaterialShapes.Cookie4Sided,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        offsetY = floatY1.dp,
                        morphDuration = 3400,
                        size = 58.dp,
                        iconSize = 30.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 4.dp, top = 22.dp)
                    )

                    // 2. Lossless (Top End - snug to logo)
                    FloatingShapeFeatureIcon(
                        icon = R.drawable.ic_lossless,
                        polygon = MaterialShapes.Gem,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        offsetY = floatY2.dp,
                        morphDuration = 4100,
                        size = 58.dp,
                        iconSize = 30.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 4.dp, top = 22.dp)
                    )

                    // 3. Binaural / Headphones (Top Center - perched directly above logo)
                    FloatingShapeFeatureIcon(
                        icon = Icons.Rounded.Headphones,
                        polygon = MaterialShapes.Clover4Leaf,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        offsetY = floatY5.dp,
                        morphDuration = 3900,
                        size = 58.dp,
                        iconSize = 30.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp)
                    )

                    // 4. Spatial Audio / Waves (Bottom Start - snug to logo)
                    FloatingShapeFeatureIcon(
                        icon = Icons.Rounded.Waves,
                        polygon = MaterialShapes.Flower,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        offsetY = floatY3.dp,
                        morphDuration = 3700,
                        size = 58.dp,
                        iconSize = 30.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 4.dp)
                    )

                    // 5. 32-Bit / DSP / Equalizer (Bottom End - snug to logo)
                    FloatingShapeFeatureIcon(
                        icon = R.drawable.ic_equalizer,
                        polygon = MaterialShapes.Sunny,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        offsetY = floatY4.dp,
                        morphDuration = 4500,
                        size = 58.dp,
                        iconSize = 30.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )

            Text(
                text = "Next-Generation Spatial Audio Experience",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Version Capsule
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • ${BuildConfig.BUILD_TYPE.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── 2. Connected Action Buttons (Material 3 Expressive Defaults) ─
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                val updatesInteraction = remember { MutableInteractionSource() }
                val updatesPressed by updatesInteraction.collectIsPressedAsState()

                ToggleButton(
                    checked = updatesPressed,
                    onCheckedChange = { onCheckUpdate() },
                    interactionSource = updatesInteraction,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.Update, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Updates",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val whatsNewInteraction = remember { MutableInteractionSource() }
                val whatsNewPressed by whatsNewInteraction.collectIsPressedAsState()

                ToggleButton(
                    checked = whatsNewPressed,
                    onCheckedChange = { onWhatsNew() },
                    interactionSource = whatsNewInteraction,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        checkedContainerColor = MaterialTheme.colorScheme.secondary,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Rounded.Info, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "What's New",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ─── 3. Bento System & Engine Specs (100% Real Values, Zero Borders)
            Text(
                text = "System & Engine Specs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Row 1: Target Platform & Audio Pipeline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BentoSpecCard(
                    icon = Icons.Rounded.Android,
                    category = "Target Platform",
                    title = "Android 15",
                    subtitle = "API $targetSdk • Min API $minSdk",
                    badge = "API $targetSdk",
                    modifier = Modifier.weight(1f)
                )

                BentoSpecCard(
                    icon = Icons.Rounded.GraphicEq,
                    category = "Audio Engine",
                    title = "ExoPlayer $media3Version",
                    subtitle = "SpatialFlow • 32-bit Float",
                    badge = "MEDIA3",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2: Architecture & Runtime Device
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BentoSpecCard(
                    icon = Icons.Rounded.Memory,
                    category = "Architecture",
                    title = primaryAbi.uppercase(),
                    subtitle = "Universal APK • JNI Opt",
                    badge = "64-BIT",
                    modifier = Modifier.weight(1f)
                )

                BentoSpecCard(
                    icon = Icons.Rounded.PhoneAndroid,
                    category = "Device Runtime",
                    title = deviceModel,
                    subtitle = "Android $runningAndroidVersion (API $runningApiLevel)",
                    badge = "API $runningApiLevel",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ─── 4. Developer Credits Showcase ──────────────────────────────
            Text(
                text = "Creator & Development",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            CreditsCard(onOpenUrl = onOpenUrl)

            Spacer(modifier = Modifier.height(24.dp))

            // ─── 5. Contributors Showcase (Segmented List) ──────────────────
            Text(
                text = "Contributors",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            ContributorsCard(onOpenUrl = onOpenUrl)

            Spacer(modifier = Modifier.height(24.dp))

            // ─── 6. Community & Support Showcase ────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_telegram),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SpatialFlow Community",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Join discussions, report bugs & get beta builds",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val telegramInteraction = remember { MutableInteractionSource() }
                    val telegramPressed by telegramInteraction.collectIsPressedAsState()

                    Button(
                        onClick = {
                            TelegramHelper.openTelegram(context, domain = "SpatialFlow")
                        },
                        interactionSource = telegramInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_telegram),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open Telegram Group",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Organic floating feature badge shaped directly with MaterialShapes polygon morphing.
 * Clean, borderless, floating shape containing only the feature icon (no pill, no text).
 */
@Composable
private fun FloatingShapeFeatureIcon(
    icon: Any,
    polygon: androidx.graphics.shapes.RoundedPolygon,
    containerColor: Color,
    contentColor: Color,
    offsetY: Dp,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    iconSize: Dp = 30.dp,
    morphDuration: Int = 3600
) {
    val morphProgress by rememberInfiniteTransition(label = "shapeMorph").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(morphDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morphProgress"
    )

    val shapeMorph = remember(polygon) {
        Morph(polygon, MaterialShapes.Circle)
    }
    val badgeShape = MorphShape(shapeMorph, morphProgress)

    Surface(
        shape = badgeShape,
        color = containerColor.copy(alpha = 0.94f),
        shadowElevation = 4.dp,
        modifier = modifier
            .offset(y = offsetY)
            .size(size)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            when (icon) {
                is ImageVector -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
                is Int -> {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

/**
 * Bento Specification Tile Container with Stroke-Free Tonal Elevation
 */
@Composable
private fun BentoSpecCard(
    icon: ImageVector,
    category: String,
    title: String,
    subtitle: String,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (badge != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column {
                Text(
                    text = category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Developer Profile Showcase with Morphing Halo & Stroke-Free Tonal Social Containers
 */
@Composable
fun CreditsCard(onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val morphState = rememberExpressiveShapeMorph(
                segmentDurationMillis = 1400,
                rotationDurationMillis = 12000
            )

            var isImageLoading by remember { mutableStateOf(true) }
            val loadedProgress by animateFloatAsState(
                targetValue = if (isImageLoading) 0f else 1f,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "loadedProgress"
            )

            val imageMorph = remember { Morph(MaterialShapes.Cookie6Sided, MaterialShapes.Circle) }
            val imageMaskShape = MorphShape(imageMorph, loadedProgress)

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(190.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(morphState.morphShape)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                        )
                )

                // Inner separator circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = CircleShape
                        )
                )

                // Profile Picture
                coil.compose.AsyncImage(
                    model = "https://github.com/MythicalSHUB.png",
                    contentDescription = "Shubham Karande",
                    onState = { state ->
                        if (state is coil.compose.AsyncImagePainter.State.Success) {
                            isImageLoading = false
                        }
                    },
                    modifier = Modifier
                        .size(108.dp)
                        .clip(imageMaskShape)
                        .graphicsLayer {
                            alpha = loadedProgress
                        },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )

                // Shimmer loader placeholder
                if (loadedProgress < 1f) {
                    val shimmerColors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                    )

                    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
                    val shimmerTranslateX by shimmerTransition.animateFloat(
                        initialValue = -200f,
                        targetValue = 200f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "shimmerTranslateX"
                    )

                    val shimmerBrush = Brush.linearGradient(
                        colors = shimmerColors,
                        start = Offset(shimmerTranslateX - 80f, 0f),
                        end = Offset(shimmerTranslateX + 80f, 160f)
                    )

                    Box(
                        modifier = Modifier
                            .size(108.dp)
                            .graphicsLayer {
                                alpha = 1f - loadedProgress
                            }
                            .clip(imageMaskShape)
                            .background(brush = shimmerBrush)
                    )
                }
            }

            Text(
                text = "Shubham Karande",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "Lead Android Architect & Creator",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Stroke-Free Tonal Social Containers
            Row(
                modifier = Modifier.padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SocialIconButton(
                    icon = R.drawable.ic_github,
                    label = "GitHub",
                    onClick = { onOpenUrl("https://github.com/MythicalSHUB") }
                )
                SocialIconButton(
                    icon = R.drawable.ic_telegram,
                    label = "Telegram",
                    onClick = { TelegramHelper.openTelegram(context, domain = "SpatialFlow") }
                )
                SocialIconButton(
                    icon = R.drawable.ic_kofi,
                    label = "Ko-fi",
                    onClick = { onOpenUrl("https://ko-fi.com/mythicalshub") }
                )
                SocialIconButton(
                    icon = R.drawable.ic_instagram,
                    label = "Instagram",
                    onClick = { onOpenUrl("https://instagram.com/mythicalshub") }
                )
                SocialIconButton(
                    icon = R.drawable.ic_youtube,
                    label = "YouTube",
                    onClick = { onOpenUrl("https://youtube.com/@8dmusic_s") }
                )
            }
        }
    }
}

private data class ContributorProfile(
    val name: String,
    val subtitle: String,
    val avatarUrl: String,
    val profileUrl: String
)

/**
 * Contributors Segmented List Showcase displaying contributor avatars, names, and GitHub links.
 */
@Composable
fun ContributorsCard(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val contributors = remember {
        listOf(
            ContributorProfile(
                name = "4nx3b (yuki)",
                subtitle = "github.com/4nx3b",
                avatarUrl = "https://github.com/4nx3b.png",
                profileUrl = "https://github.com/4nx3b"
            ),
            ContributorProfile(
                name = "vossgraves",
                subtitle = "github.com/vossgraves",
                avatarUrl = "https://github.com/vossgraves.png",
                profileUrl = "https://github.com/vossgraves"
            )
        )
    }

    SegmentedGroup(
        horizontalPadding = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        contributors.forEach { contributor ->
            item {
                SegmentedRow(
                    onClick = { onOpenUrl(contributor.profileUrl) },
                    leadingShape = CircleShape,
                    leadingBackground = Color.Transparent,
                    leadingIcon = {
                        SpatialImage(
                            model = contributor.avatarUrl,
                            contentDescription = contributor.name,
                            size = ImageSemanticSize.AvatarSmall,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.ic_github),
                            error = painterResource(id = R.drawable.ic_github)
                        )
                    },
                    title = {
                        Text(
                            text = contributor.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    subtitle = {
                        Text(
                            text = contributor.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "Contributor",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = "Open ${contributor.name}'s GitHub profile",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * Social Action Pill Container with Tactile Spring Scaling and Stroke-Free Elevation
 */
@Composable
private fun SocialIconButton(
    icon: Int,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "socialScale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = if (isPressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = if (isPressed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
