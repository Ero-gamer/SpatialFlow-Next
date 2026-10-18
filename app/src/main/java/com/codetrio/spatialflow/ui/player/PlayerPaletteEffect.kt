package com.codetrio.spatialflow.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PlayerPaletteEffect"

/**
 * Extracts accurate vibrant / darkVibrant / darkMuted palette colors directly from:
 * 1. Active Canvas static artwork or live video frame (via MediaMetadataRetriever), or
 * 2. Track album artwork (fallback)
 * and writes them into [PlayerPaletteState].
 */
@Composable
fun PlayerPaletteEffect(
    artworkKey: String?,
    currentSong: SongItem?,
    canvasArtwork: CanvasArtwork? = null,
    context: Context,
) {
    val canvasVideoUrl = canvasArtwork?.preferredAnimationUrl
        ?: canvasArtwork?.preferredVerticalAnimationUrl
        ?: canvasArtwork?.videoUrl
        ?: canvasArtwork?.videoUrlVertical
    val canvasStaticUrl = canvasArtwork?.static?.takeIf { it.isNotBlank() }

    val canvasKey = canvasStaticUrl ?: canvasVideoUrl

    val effectiveKey = remember(artworkKey, canvasKey) {
        canvasKey ?: artworkKey
    }

    LaunchedEffect(effectiveKey, currentSong?.id) {
        if (effectiveKey == null && currentSong == null) {
            PlayerPaletteState.vibrantColor.value = Color.Black
            PlayerPaletteState.darkVibrantColor.value = Color.Black
            PlayerPaletteState.darkMutedColor.value = Color.Black
            PlayerPaletteState.canvasFrameBitmap.value = null
            return@LaunchedEffect
        }

        val isCanvas = canvasKey != null
        val songId = currentSong?.id?.toString() ?: "unknown"
        val cacheKey = if (isCanvas) "canvas_${songId}_${canvasKey.hashCode()}" else "art_${songId}_${artworkKey ?: ""}"

        // Check cache for this exact canvas or artwork
        val cachedColors = PlayerColorExtractor.getCached(cacheKey)
        if (cachedColors != null) {
            PlayerPaletteState.vibrantColor.value = cachedColors.vibrant
            PlayerPaletteState.darkVibrantColor.value = cachedColors.darkVibrant
            PlayerPaletteState.darkMutedColor.value = cachedColors.darkMuted
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val song = currentSong
            var extractedBitmap: Bitmap? = null

            val mediaId = song?.videoId ?: song?.id?.toString()

            // 1. If active canvas video is present, capture directly from the playing video frame!
            if (!canvasVideoUrl.isNullOrBlank()) {
                extractedBitmap = PlayerColorExtractor.extractFrameFromVideo(canvasVideoUrl, context, mediaId)
            }

            // 2. If no video or extraction failed, try canvas static poster
            if (extractedBitmap == null && !canvasStaticUrl.isNullOrBlank()) {
                try {
                    val loader = context.imageLoader
                    val request = ImageRequest.Builder(context)
                        .data(canvasStaticUrl)
                        .size(160, 160)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    extractedBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                        ?: result.drawable?.toBitmap()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load canvas static: ${e.message}")
                }
            }

            // Publish canvas frame bitmap for background rendering
            if (isCanvas && extractedBitmap != null) {
                withContext(Dispatchers.Main) {
                    PlayerPaletteState.canvasFrameBitmap.value = extractedBitmap
                }
            } else if (!isCanvas) {
                withContext(Dispatchers.Main) {
                    PlayerPaletteState.canvasFrameBitmap.value = null
                }
            }

            // 3. Fallback: track album artwork
            if (extractedBitmap == null && song != null) {
                val artworkData: Any? = song.getAlbumArtUri()
                    ?: song.thumbnailUrl
                    ?: song.videoId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
                if (artworkData != null) {
                    try {
                        val loader = context.imageLoader
                        val request = ImageRequest.Builder(context)
                            .data(artworkData)
                            .size(160, 160)
                            .allowHardware(false)
                            .build()
                        val result = loader.execute(request)
                        extractedBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                            ?: result.drawable?.toBitmap()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load song artwork: ${e.message}")
                    }
                }
            }

            if (extractedBitmap != null) {
                try {
                    val palette = Palette.from(extractedBitmap)
                        .maximumColorCount(24)
                        .resizeBitmapArea(128 * 128)
                        .generate()

                    val dominantInt = palette.getDominantColor(0xFF141218.toInt())

                    // Vibrant color: prefer vibrant or light vibrant
                    val vibrantInt = palette.vibrantSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: palette.getVibrantColor(palette.getDominantColor(0xFF8338EC.toInt()))

                    // Dark Vibrant color: rich, saturated dark tone
                    val darkVibrantInt = palette.darkVibrantSwatch?.rgb
                        ?: palette.vibrantSwatch?.rgb?.let { rgb ->
                            val hsl = FloatArray(3)
                            ColorUtils.colorToHSL(rgb, hsl)
                            hsl[2] = (hsl[2] * 0.45f).coerceIn(0.12f, 0.32f)
                            ColorUtils.HSLToColor(hsl)
                        }
                        ?: palette.getDarkVibrantColor(dominantInt)

                    // Dark Muted color
                    val darkMutedInt = palette.darkMutedSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                        ?: dominantInt

                    val vibrantColor = Color(vibrantInt)
                    val darkVibrantColor = Color(darkVibrantInt)
                    val darkMutedColor = Color(darkMutedInt)

                    val extracted = ExtractedPaletteColors(
                        dominant = Color(dominantInt),
                        vibrant = vibrantColor,
                        darkMuted = darkMutedColor,
                        lightVibrant = Color(palette.getLightVibrantColor(vibrantInt)),
                        accent = vibrantColor,
                        darkVibrant = darkVibrantColor
                    )
                    PlayerColorExtractor.putInCache(cacheKey, extracted)

                    withContext(Dispatchers.Main) {
                        PlayerPaletteState.vibrantColor.value = vibrantColor
                        PlayerPaletteState.darkVibrantColor.value = darkVibrantColor
                        PlayerPaletteState.darkMutedColor.value = darkMutedColor
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Palette generation failed: ${e.message}")
                }
            }
        }
    }
}
