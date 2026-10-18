package com.codetrio.spatialflow.ui.player

import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlayer
import com.codetrio.spatialflow.ui.theme.observeKey

private const val CANVAS_BACKDROP_UPSCALE = 6f
private val CANVAS_BACKDROP_BLUR_RADIUS = 72.dp
private const val CANVAS_BACKDROP_MAX_VIDEO_EDGE_PX = 360
private const val CANVAS_COVER_BLUR_SCALE = 1.2f
private const val CANVAS_BACKDROP_TOTAL_SCALE = CANVAS_COVER_BLUR_SCALE * CANVAS_BACKDROP_UPSCALE

/**
 * Full-screen Now Playing background engine.
 * Supports:
 * 1. "Blurred" Mode: RenderScript hardware blurred cover artwork + KenBurns 12s pan/zoom + low-opacity palette gradient.
 * 2. "Solid" Mode: Clean high-contrast spring-animated palette gradient.
 * 3. Canvas Artwork Video layer integration.
 */
@Composable
fun AppleMusicBackground(
    song: SongItem?,
    modifier: Modifier = Modifier,
    canvasArtwork: CanvasArtwork? = null,
    isPlaying: Boolean = true,
    isLyricsModeEnabled: Boolean = false,
    allowCanvas: Boolean = true,
    accentColor: Color = Color(0xFF6200EE),
    backgroundColor: Color = Color.Black,
    content: @Composable () -> Unit = {}
) {
    val context = LocalContext.current

    val prefs = remember(context) { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }

    // ── SharedPreferences Settings Observers ───────────────────────────────────
    val bgMode by prefs.observeKey("now_playing_background_v2", "Blurred")
    val isBlurred = bgMode == "Blurred"
    val kenBurnsEnabled by prefs.observeKey("now_playing_background_effect", false)
    val showAnimatedArt by prefs.observeKey("show_animated_art", true)
    val playerTheme by prefs.observeKey("player_theme", "fluid")
    val isStatic = playerTheme == "static"
    val filmGrainEnabled by prefs.observeKey("lyrics_film_grain_enabled", true)

    // ── Artwork URI ───────────────────────────────────────────────────────────────
    val artworkUrl = remember(song?.id, song?.videoId, song?.thumbnailUrl) {
        val rawUri = song?.getAlbumArtUri()
        val videoId = song?.videoId
        when {
            rawUri != null && rawUri.toString().isNotEmpty() ->
                SongItem.enhanceThumbnailUrl(rawUri.toString())
            !videoId.isNullOrEmpty() ->
                "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            else -> null
        }
    }

    // ── Palette colors from global singleton ───────────────────────────────────────
    val vibrant     = PlayerPaletteState.vibrantColor.value
    val darkVibrant = PlayerPaletteState.darkVibrantColor.value
    val darkMuted   = PlayerPaletteState.darkMutedColor.value

    // Refined Static Target Color Computation
    val staticTargetColor = remember(vibrant, darkVibrant, darkMuted) {
        val baseColor = when {
            vibrant != Color.Black -> vibrant
            darkVibrant != Color.Black -> darkVibrant
            darkMuted != Color.Black -> darkMuted
            else -> Color(0xFF141218)
        }
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
        hsl[1] = hsl[1].coerceIn(0.25f, 0.65f)
        hsl[2] = hsl[2].coerceIn(0.12f, 0.45f)
        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
    }

    // ── Canvas motion artwork ──────────────────────────────────────────────────────
    val isCanvasAllowed = remember(context) {
        com.codetrio.spatialflow.util.QualityModeSelector.isCanvasVideoAllowed(context)
    }
    val hasCanvas = showAnimatedArt && canvasArtwork != null && isCanvasAllowed
    val canBlurCanvasVideo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val canvasUrl = remember(canvasArtwork) {
        canvasArtwork?.preferredVerticalAnimationUrl
            ?: canvasArtwork?.preferredAnimationUrl
            ?: canvasArtwork?.videoUrlVertical
            ?: canvasArtwork?.videoUrl
    }
    val useCanvasBackdrop = hasCanvas && !canvasUrl.isNullOrBlank() && canBlurCanvasVideo
    val canvasFrame = PlayerPaletteState.canvasFrameBitmap.value
    val backgroundArtwork = remember(artworkUrl, canvasArtwork?.static, canvasFrame, hasCanvas) {
        if (hasCanvas) {
            canvasFrame
                ?: canvasArtwork.static?.takeIf { it.isNotBlank() }
                ?: artworkUrl
        } else {
            artworkUrl
        }
    }

    var foregroundCanvasPlayer by remember(canvasUrl) { mutableStateOf<ExoPlayer?>(null) }

    val targetGrainAlpha = if (isLyricsModeEnabled && filmGrainEnabled) 1f else 0f
    val animatedGrainAlpha by animateFloatAsState(
        targetValue = targetGrainAlpha,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 320f
        ),
        label = "film_grain_alpha"
    )

    // ═════════════════════════════════════════════════════════════════════════════
    //  BACKGROUND COMPOSITION
    // ═════════════════════════════════════════════════════════════════════════════
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        if (isStatic) {
            // REFINED STATIC THEME — ELEGANT COLOR-HARMONIZED BACKGROUND
            StaticBackground(staticTargetColor)
        } else {
            // FLUID THEME:
            // When real-time blurred canvas video backdrop is active (Android 12+), avoid SpatialFloatingLight
            // (its 2x-saturation filter distorts colors). Instead, the live blurred canvas backdrop runs.
            // On pre-Android 12 or when canvas is absent, run SpatialFloatingLight for the blurred album cover.
            if (!useCanvasBackdrop) {
                SpatialWrapper {
                    SpatialFloatingLight(
                        modifier = Modifier.fillMaxSize(),
                        album = { backgroundArtwork },
                        isPlaying = { isPlaying },
                        isLyricsPage = { isLyricsModeEnabled },
                        backgroundEffectEnabled = kenBurnsEnabled
                    )
                }
            }

            // Dynamic Palette Gradient Overlay (Isolated leaf composable: only composed when canvas is NOT active)
            if (!hasCanvas) {
                NonCanvasPaletteOverlay(vibrant, darkVibrant, darkMuted)
            }

            // ── Real-Time Blurred Canvas Backdrop (36x Downscale-Blur-Upscale Engine) ──
            if (useCanvasBackdrop) {
                val backdropAlpha by animateFloatAsState(
                    targetValue = if (!allowCanvas) 0f else if (isLyricsModeEnabled) 0.35f else 1f,
                    animationSpec = tween(durationMillis = 350),
                    label = "canvasBackdropAlpha"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = CANVAS_BACKDROP_TOTAL_SCALE
                            scaleY = CANVAS_BACKDROP_TOTAL_SCALE
                            alpha = backdropAlpha
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CanvasArtworkPlayer(
                        primaryUrl = canvasUrl,
                        fallbackUrl = null,
                        isPlaying = isPlaying && allowCanvas,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        visible = allowCanvas,
                        maxVideoEdgePx = CANVAS_BACKDROP_MAX_VIDEO_EDGE_PX,
                        syncToPlayer = foregroundCanvasPlayer,
                        isSyncSlave = hasCanvas && !isLyricsModeEnabled,
                        modifier = Modifier
                            .fillMaxWidth(1f / CANVAS_BACKDROP_UPSCALE)
                            .fillMaxHeight(1f / CANVAS_BACKDROP_UPSCALE)
                            .blur(CANVAS_BACKDROP_BLUR_RADIUS / CANVAS_BACKDROP_UPSCALE),
                    )
                }
            }
        }

        // ── Canvas Video Overlay (Adaptive Multi-Format Filled Effect with Dissolve) ──
        if (hasCanvas && !canvasUrl.isNullOrBlank() && !isLyricsModeEnabled) {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            // Detect and adapt dynamically to video format (vertical 9:16, square 1:1, widescreen 16:9)
            var detectedAspectRatio by remember(canvasUrl) { mutableFloatStateOf(0.5625f) }

            // Pre-cached gradient brushes to eliminate 700+ allocations/sec during 60/120 FPS draw loop
            val verticalDissolveBrush = remember {
                Brush.verticalGradient(
                    0.0f to Color.White,
                    0.50f to Color.White,
                    0.82f to Color.Transparent
                )
            }
            val horizontalDissolveBrush = remember {
                Brush.horizontalGradient(
                    0.0f to Color.White,
                    0.45f to Color.White,
                    1.0f to Color.Transparent
                )
            }

            val canvasAlpha by animateFloatAsState(
                targetValue = if (allowCanvas) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "canvasOverlayAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = canvasAlpha }
            ) {
                if (isLandscape) {
                    // Landscape (Phones & Tablets): Left-anchored Edge-to-Edge Filled Effect with Horizontal Dissolve
                    val landscapeWidthFraction = if (detectedAspectRatio > 1.20f) 0.65f else 0.55f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(landscapeWidthFraction)
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = horizontalDissolveBrush,
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) {
                        CanvasArtworkPlayer(
                            primaryUrl = canvasUrl,
                            fallbackUrl = null,
                            isPlaying = isPlaying && allowCanvas,
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                            visible = allowCanvas,
                            onPlayerCreated = { foregroundCanvasPlayer = it },
                            onVideoAspectRatioDetected = { ratio ->
                                detectedAspectRatio = ratio
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // Portrait (Phones & Tablets): Centered & Zoomed with Bottom Inset for Player Controls
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 110.dp)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = verticalDissolveBrush,
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) {
                        CanvasArtworkPlayer(
                            primaryUrl = canvasUrl,
                            fallbackUrl = null,
                            isPlaying = isPlaying && allowCanvas,
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                            visible = allowCanvas,
                            onPlayerCreated = { foregroundCanvasPlayer = it },
                            onVideoAspectRatioDetected = { ratio ->
                                detectedAspectRatio = ratio
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // ── Apple Music Tactile Film Grain Layer ─────────────────────────────────────
        if (filmGrainEnabled) {
            FilmGrainLayer(
                alphaProvider = { animatedGrainAlpha },
                intensity = 0.055f,
                enabled = true,
                modifier = Modifier.fillMaxSize()
            )
        }

        content()
    }
}

@Composable
private fun StaticBackground(staticTargetColor: Color) {
    val animatedStaticBg by animateColorAsState(
        targetValue = staticTargetColor,
        animationSpec = spring(stiffness = 100f, dampingRatio = 0.8f),
        label = "static_bg_spring"
    )
    SpatialWrapper {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(animatedStaticBg)
                }
        )
    }
}

@Composable
private fun NonCanvasPaletteOverlay(
    vibrant: Color,
    darkVibrant: Color,
    darkMuted: Color
) {
    val animatedVibrant by animateColorAsState(
        targetValue = vibrant,
        animationSpec = spring(stiffness = 100f, dampingRatio = 0.8f),
        label = "vibrant_spring"
    )
    val animatedDarkVibrant by animateColorAsState(
        targetValue = darkVibrant,
        animationSpec = spring(stiffness = 100f, dampingRatio = 0.8f),
        label = "dark_vibrant_spring"
    )
    val animatedDarkMuted by animateColorAsState(
        targetValue = darkMuted,
        animationSpec = spring(stiffness = 100f, dampingRatio = 0.8f),
        label = "dark_muted_spring"
    )

    SpatialWrapper {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                animatedVibrant.copy(alpha = 0.30f),
                                animatedDarkVibrant.copy(alpha = 0.20f),
                                animatedDarkMuted.copy(alpha = 0.15f)
                            )
                        )
                    )
                }
        )
    }
}

