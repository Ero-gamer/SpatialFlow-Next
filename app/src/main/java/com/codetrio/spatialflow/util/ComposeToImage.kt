package com.codetrio.spatialflow.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.LyricsShareImageOptions
import com.codetrio.spatialflow.ui.player.LyricsShareTextAlign
import com.codetrio.spatialflow.ui.player.LyricsShareTheme
import com.codetrio.spatialflow.ui.player.PlayerColorExtractor
import com.codetrio.spatialflow.util.CanvasFontProvider
import com.codetrio.spatialflow.util.CanvasFontProvider.applyBody
import com.codetrio.spatialflow.util.CanvasFontProvider.applyLabel
import com.codetrio.spatialflow.util.CanvasFontProvider.applyLyrics
import com.codetrio.spatialflow.util.CanvasFontProvider.applyRecapTitle
import com.codetrio.spatialflow.util.CanvasFontProvider.applyTitle
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ComposeToImage {

    suspend fun createLyricsImage(
        context: Context,
        song: SongItem?,
        lyrics: List<LyricLine>,
        options: LyricsShareImageOptions
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = options.aspectRatio.width
        val height = options.aspectRatio.height
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val lyricsText = lyrics.joinToString("\n") { it.content }

        // Fetch cover drawable & bitmap (preserving 1:1 intrinsic aspect ratio without stretching)
        var coverBitmap: Bitmap? = null
        val thumbnailUrl = song?.thumbnailUrl
        if (!thumbnailUrl.isNullOrBlank()) {
            try {
                val imageLoader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .allowHardware(false)
                    .build()
                val coverDrawable = imageLoader.execute(request).drawable
                coverBitmap = coverDrawable?.toBitmap()
            } catch (_: Exception) {}
        }

        val paletteColors = PlayerColorExtractor.extractFromBitmap(song?.videoId, coverBitmap)

        // W3C Relative Background Luminance Calculation for dynamic text contrast
        val refCardBgColor = if (options.theme == LyricsShareTheme.FLUID) {
            AndroidColor.rgb(32, 32, 37)
        } else {
            paletteColors.dominant.copy(alpha = 1f).toArgb()
        }
        val cardLuminance = androidx.core.graphics.ColorUtils.calculateLuminance(refCardBgColor)
        val isCardLight = cardLuminance > 0.45

        val exportTitleColor = if (isCardLight) AndroidColor.rgb(15, 15, 20) else AndroidColor.WHITE
        val exportArtistColor = if (isCardLight) AndroidColor.argb(165, 15, 15, 20) else AndroidColor.argb(190, 255, 255, 255)
        val exportLyricsColor = if (isCardLight) AndroidColor.rgb(15, 15, 20) else AndroidColor.WHITE
        val exportWatermarkColor = if (isCardLight) AndroidColor.argb(215, 15, 15, 20) else AndroidColor.argb(235, 255, 255, 255)

        // 1. Draw Base Background (Matching AppleMusicBackground.kt)
        if (options.theme == LyricsShareTheme.STATIC) {
            canvas.drawColor(paletteColors.darkMuted.toArgb())
        } else {
            canvas.drawColor(AndroidColor.BLACK)
            if (coverBitmap != null) {
                // Maintain 1:1 aspect ratio without stretching
                val srcW = coverBitmap.width.toFloat()
                val srcH = coverBitmap.height.toFloat()
                val scale = maxOf(width.toFloat() / srcW, height.toFloat() / srcH)
                val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
                val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
                val scaledBitmap = Bitmap.createScaledBitmap(coverBitmap, scaledW, scaledH, true)

                // Downscale for balanced smooth ambient blur
                val downscaleFactor = 10
                val blurW = (scaledW / downscaleFactor).coerceAtLeast(60)
                val blurH = (scaledH / downscaleFactor).coerceAtLeast(60)
                val smallBitmap = Bitmap.createScaledBitmap(scaledBitmap, blurW, blurH, true)

                val effectiveRadius = (options.blurRadius * 1.15f).toInt().coerceIn(20, 55)
                val blurredSmallBitmap = stackBlur(smallBitmap, effectiveRadius)

                val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    val cm = android.graphics.ColorMatrix()
                    cm.setSaturation(2.0f) // 100% exact parity with SpatialFloatingLight 2.0x saturation boost
                    colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                }
                val cropWidth = (width / downscaleFactor).coerceAtMost(blurW)
                val cropHeight = (height / downscaleFactor).coerceAtMost(blurH)
                val cropLeft = ((blurW - cropWidth) / 2).coerceAtLeast(0)
                val cropTop = ((blurH - cropHeight) / 2).coerceAtLeast(0)
                val srcCropRect = android.graphics.Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)

                val destRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(blurredSmallBitmap, srcCropRect, destRect, filterPaint)

                // Dynamic Palette Gradient Overlay (100% matching AppleMusicBackground.kt)
                val topColor = paletteColors.vibrant.copy(alpha = 0.30f).toArgb()
                val midColor = paletteColors.accent.copy(alpha = 0.20f).toArgb()
                val bottomColor = paletteColors.darkMuted.copy(alpha = 0.15f).toArgb()

                val gradientPaint = Paint().apply {
                    isAntiAlias = true
                    shader = android.graphics.LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(topColor, midColor, bottomColor),
                        floatArrayOf(0.0f, 0.50f, 1.0f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(destRect, gradientPaint)
            }

            // Dark Dim Paint
            val dimPaint = Paint().apply {
                color = AndroidColor.BLACK
                alpha = (115 * options.backgroundDim).toInt().coerceIn(20, 230)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        }

        // 2. Setup Unified Variable Font
        CanvasFontProvider.getBaseTypeface(context)

        // 3. Dynamic Height & Layout Pre-Measurement (Matching LyricsImageCard.kt)
        val isStory = options.aspectRatio == com.codetrio.spatialflow.ui.player.LyricsShareAspectRatio.STORY_9_16
        val cardWidthFactor = if (isStory) 0.82f else 0.86f
        val cardWidth = width * cardWidthFactor
        val cardLeft = (width - cardWidth) / 2f
        val cardRight = cardLeft + cardWidth

        val cardPadding = cardWidth * 0.065f
        val cardCornerLarge = cardWidth * 0.060f
        val cardCornerSmall = cardWidth * 0.015f
        val connectedGap = cardWidth * 0.008f
        val artworkSize = cardWidth * 0.155f
        val artworkCorner = cardWidth * 0.038f

        val titleFontSize = cardWidth * 0.050f
        val artistFontSize = cardWidth * 0.038f
        val badgeFontSize = cardWidth * 0.032f
        val badgeIconSize = cardWidth * 0.036f

        val lineCount = lyrics.size.coerceAtLeast(1)
        val lyricsScale = when {
            lineCount <= 2 -> 0.075f
            lineCount <= 4 -> 0.062f
            lineCount <= 6 -> 0.052f
            lineCount <= 8 -> 0.044f
            lineCount <= 10 -> 0.038f
            else -> 0.034f
        }
        val lyricsTextSize = cardWidth * lyricsScale

        val lyricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = exportLyricsColor
            textSize = lyricsTextSize
            applyLyrics(weight = 750, width = 105f)
        }

        val layoutAlignment = when (options.textAlign) {
            LyricsShareTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            LyricsShareTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            LyricsShareTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        val contentWidth = (cardWidth - (2 * cardPadding)).toInt().coerceAtLeast(100)
        val lineSpacingExtra = (lyricsTextSize * 0.34f)
        val lyricsLayout = StaticLayout.Builder
            .obtain(lyricsText, 0, lyricsText.length, lyricsPaint, contentWidth)
            .setAlignment(layoutAlignment)
            .setLineSpacing(lineSpacingExtra, 1.0f)
            .setMaxLines(16)
            .build()

        val topSegmentHeight = lyricsLayout.height + (2 * cardPadding)

        // Metadata pre-measurement (Segment 2)
        val hasArtwork = options.showArtwork && !song?.thumbnailUrl.isNullOrBlank()
        val metaArtworkGap = if (hasArtwork) cardWidth * 0.034f else 0f
        val metaArtworkWidth = if (hasArtwork) artworkSize else 0f
        val metadataMaxWidth = (contentWidth - metaArtworkWidth - metaArtworkGap).toInt().coerceAtLeast(100)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = exportTitleColor
            textSize = titleFontSize
            applyTitle(weight = 700, width = 105f)
        }
        val titleText = song?.title ?: "Unknown Track"
        val titleLayout = StaticLayout.Builder
            .obtain(titleText, 0, titleText.length, titlePaint, metadataMaxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = exportArtistColor
            textSize = artistFontSize
            applyBody(weight = 500, width = 100f)
        }
        val artistText = song?.artist ?: "Unknown Artist"
        val artistLayout = StaticLayout.Builder
            .obtain(artistText, 0, artistText.length, artistPaint, metadataMaxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val watermarkGap = if (options.showWatermark) cardWidth * 0.010f else 0f
        val watermarkHeight = if (options.showWatermark) badgeIconSize else 0f
        val textGroupHeight = titleLayout.height + (cardWidth * 0.008f) + artistLayout.height + watermarkGap + watermarkHeight
        val botPaddingY = cardPadding * 0.70f
        val botSegmentHeight = maxOf(artworkSize, textGroupHeight) + (2 * botPaddingY)

        // Total Connected Card Group Dimensions & Vertical Centering
        val totalCardGroupHeight = topSegmentHeight + connectedGap + botSegmentHeight
        val startY = (height - totalCardGroupHeight) / 2f

        val cardBgColor = if (options.theme == LyricsShareTheme.FLUID) {
            AndroidColor.argb(138, 20, 20, 24)
        } else {
            paletteColors.dominant.copy(alpha = 0.65f).toArgb()
        }
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBgColor
        }

        // 4. Draw Segment 1 (Top / Lyrics Box) — Connected Leading Shape
        val topRect = RectF(cardLeft, startY, cardRight, startY + topSegmentHeight)
        val topPath = Path().apply {
            addRoundRect(
                topRect,
                floatArrayOf(
                    cardCornerLarge, cardCornerLarge, // Top-Left
                    cardCornerLarge, cardCornerLarge, // Top-Right
                    cardCornerSmall, cardCornerSmall, // Bottom-Right
                    cardCornerSmall, cardCornerSmall  // Bottom-Left
                ),
                Path.Direction.CW
            )
        }
        canvas.drawPath(topPath, cardBgPaint)

        // Draw Lyrics inside Segment 1
        canvas.save()
        canvas.translate(cardLeft + cardPadding, startY + cardPadding)
        lyricsLayout.draw(canvas)
        canvas.restore()

        // 5. Draw Segment 2 (Bottom / Metadata Box) — Connected Trailing Shape
        val botRectTop = startY + topSegmentHeight + connectedGap
        val botRect = RectF(cardLeft, botRectTop, cardRight, botRectTop + botSegmentHeight)
        val botPath = Path().apply {
            addRoundRect(
                botRect,
                floatArrayOf(
                    cardCornerSmall, cardCornerSmall, // Top-Left
                    cardCornerSmall, cardCornerSmall, // Top-Right
                    cardCornerLarge, cardCornerLarge, // Bottom-Right
                    cardCornerLarge, cardCornerLarge  // Bottom-Left
                ),
                Path.Direction.CW
            )
        }
        canvas.drawPath(botPath, cardBgPaint)

        // Draw Metadata inside Segment 2
        var textLeft = cardLeft + cardPadding
        val artworkY = botRectTop + botPaddingY
        val metaTop = artworkY

        if (hasArtwork && song?.thumbnailUrl != null) {
            val imageLoader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(song.thumbnailUrl)
                .allowHardware(false)
                .build()
            val thumbDrawable = imageLoader.execute(request).drawable
            val thumbBitmap = thumbDrawable?.toBitmap(artworkSize.toInt(), artworkSize.toInt())

            val thumbRect = RectF(textLeft, metaTop, textLeft + artworkSize, metaTop + artworkSize)
            val thumbPath = Path().apply {
                addRoundRect(thumbRect, artworkCorner, artworkCorner, Path.Direction.CW)
            }

            if (thumbBitmap != null) {
                canvas.save()
                canvas.clipPath(thumbPath)
                canvas.drawBitmap(thumbBitmap, null, thumbRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                canvas.restore()
            }

            // Inner border stroke matching SpatialImage inner border
            val innerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = AndroidColor.argb(25, 255, 255, 255)
            }
            canvas.drawPath(thumbPath, innerStrokePaint)

            textLeft += artworkSize + metaArtworkGap
        }

        // Draw Title & Artist Text Column
        var metaY = metaTop
        canvas.save()
        canvas.translate(textLeft, metaY)
        titleLayout.draw(canvas)
        canvas.restore()

        metaY += titleLayout.height + (cardWidth * 0.008f)
        canvas.save()
        canvas.translate(textLeft, metaY)
        artistLayout.draw(canvas)
        canvas.restore()

        // Draw SpatialFlow Branding Badge
        if (options.showWatermark) {
            metaY += artistLayout.height + (cardWidth * 0.010f)
            val logoSize = badgeIconSize
            val pxSize = logoSize.toInt().coerceAtLeast(20)
            val rawDrawable = ContextCompat.getDrawable(context, R.drawable.ic_applogo)?.mutate()

            val logoColor = exportWatermarkColor
            if (rawDrawable != null) {
                DrawableCompat.setTint(rawDrawable, logoColor)
                DrawableCompat.setTintMode(rawDrawable, android.graphics.PorterDuff.Mode.SRC_IN)
            }
            val logoBitmap = rawDrawable?.toBitmap(pxSize, pxSize)

            if (logoBitmap != null) {
                val logoRect = RectF(textLeft, metaY, textLeft + logoSize, metaY + logoSize)
                canvas.drawBitmap(logoBitmap, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }

            val badgeTextPaint = TextPaint().apply {
                isAntiAlias = true
                color = logoColor
                textSize = badgeFontSize
                applyLabel(weight = 600, width = 105f)
                letterSpacing = 0.02f
            }
            val badgeMetrics = badgeTextPaint.fontMetrics
            val badgeX = textLeft + logoSize + (cardWidth * 0.012f)
            val badgeY = metaY + (logoSize / 2f) - ((badgeMetrics.ascent + badgeMetrics.descent) / 2f)
            canvas.drawText("SpatialFlow", badgeX, badgeY, badgeTextPaint)
        }

        bitmap
    }

    /**
     * Programmatic Canvas-Based 1080x1920 (9:16) Song Story Share Generator.
     * Matches user design spec (IMG_20261015_201149_331.webp & IMG_20261015_201235_896.webp):
     * - Rich ambient blurred artwork background with saturation boost.
     * - Centered floating card with LOW OPACITY frosted glass (~0.53 alpha) instead of harsh solid black.
     * - 1:1 Album artwork squircle with SpatialImage innerBorder edge highlight.
     * - Bold track title, artist name, and SpatialFlow watermark badge.
     */
    suspend fun createSongStoryImage(
        context: Context,
        song: SongItem?
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = 1080
        val height = 1920
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        var coverBitmap: Bitmap? = null
        val thumbnailUrl = song?.thumbnailUrl
        if (!thumbnailUrl.isNullOrBlank()) {
            try {
                val imageLoader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .allowHardware(false)
                    .build()
                val coverDrawable = imageLoader.execute(request).drawable
                coverBitmap = coverDrawable?.toBitmap()
            } catch (_: Exception) {}
        }

        val paletteColors = PlayerColorExtractor.extractFromBitmap(song?.videoId, coverBitmap)

        // 1. Ambient Blurred Artwork Background
        canvas.drawColor(AndroidColor.BLACK)
        if (coverBitmap != null) {
            val srcW = coverBitmap.width.toFloat()
            val srcH = coverBitmap.height.toFloat()
            val scale = maxOf(width.toFloat() / srcW, height.toFloat() / srcH)
            val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
            val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
            val scaledBitmap = Bitmap.createScaledBitmap(coverBitmap, scaledW, scaledH, true)

            val downscaleFactor = 10
            val blurW = (scaledW / downscaleFactor).coerceAtLeast(60)
            val blurH = (scaledH / downscaleFactor).coerceAtLeast(60)
            val smallBitmap = Bitmap.createScaledBitmap(scaledBitmap, blurW, blurH, true)
            val blurredSmallBitmap = stackBlur(smallBitmap, 38)

            val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                val cm = android.graphics.ColorMatrix()
                cm.setSaturation(2.0f)
                colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
            val cropWidth = (width / downscaleFactor).coerceAtMost(blurW)
            val cropHeight = (height / downscaleFactor).coerceAtMost(blurH)
            val cropLeft = ((blurW - cropWidth) / 2).coerceAtLeast(0)
            val cropTop = ((blurH - cropHeight) / 2).coerceAtLeast(0)
            val srcCropRect = android.graphics.Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
            val destRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(blurredSmallBitmap, srcCropRect, destRect, filterPaint)

            // Dynamic Palette Gradient Overlay
            val topColor = paletteColors.vibrant.copy(alpha = 0.35f).toArgb()
            val midColor = paletteColors.accent.copy(alpha = 0.22f).toArgb()
            val bottomColor = paletteColors.darkMuted.copy(alpha = 0.20f).toArgb()
            val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(topColor, midColor, bottomColor),
                    floatArrayOf(0.0f, 0.50f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(destRect, gradientPaint)
        }

        // Dark Dim Paint
        val dimPaint = Paint().apply {
            color = AndroidColor.BLACK
            alpha = 115
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        // 2. Setup Unified Variable Font
        CanvasFontProvider.getBaseTypeface(context)

        // 3. Floating Centered Song Card (Low-Opacity Frosted Glass)
        val cardWidth = width * 0.78f // 842f
        val cardPadding = cardWidth * 0.055f // 46f
        val cardCorner = cardWidth * 0.055f // 46f
        val artworkCorner = cardWidth * 0.040f // 34f
        val contentWidth = (cardWidth - 2 * cardPadding).toInt()
        val artworkSize = contentWidth.toFloat()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = cardWidth * 0.065f // 55f
            applyRecapTitle(weight = 800, width = 115f)
        }
        val titleText = song?.title ?: "Unknown Track"
        val titleLayout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, titlePaint, contentWidth)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(195, 255, 255, 255)
            textSize = cardWidth * 0.045f // 38f
            applyTitle(weight = 550, width = 105f)
        }
        val artistText = song?.artist ?: "Unknown Artist"
        val artistLayout = StaticLayout.Builder.obtain(artistText, 0, artistText.length, artistPaint, contentWidth)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val badgeIconSize = cardWidth * 0.042f // 35f
        val badgeHeight = cardWidth * 0.046f // 39f

        val artToTitleGap = cardWidth * 0.045f // 38f
        val titleToArtistGap = cardWidth * 0.015f // 13f
        val artistToBadgeGap = cardWidth * 0.032f // 27f

        val cardContentHeight = artworkSize + artToTitleGap + titleLayout.height + titleToArtistGap + artistLayout.height + artistToBadgeGap + badgeHeight
        val cardTotalHeight = cardContentHeight + 2 * cardPadding

        val cardLeft = (width - cardWidth) / 2f
        val cardTop = (height - cardTotalHeight) / 2f
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardTotalHeight)

        // Low-opacity translucent frosted glass
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(135, 20, 20, 26) // ~0.53 alpha translucent frosted glass
        }
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, cardPaint)

        // Artwork (1:1 with proportional squircle + SpatialImage innerBorder replication)
        val artRect = RectF(cardLeft + cardPadding, cardTop + cardPadding, cardLeft + cardPadding + artworkSize, cardTop + cardPadding + artworkSize)
        val artPath = Path().apply {
            addRoundRect(artRect, artworkCorner, artworkCorner, Path.Direction.CW)
        }
        if (coverBitmap != null) {
            canvas.save()
            canvas.clipPath(artPath)
            canvas.drawBitmap(coverBitmap, null, artRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(45, 255, 255, 255)
            }
            canvas.drawPath(artPath, placeholderPaint)
        }

        // Inner border stroke matching SpatialImageDefaults.innerBorder()
        val innerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = AndroidColor.argb(25, 255, 255, 255)
        }
        canvas.drawPath(artPath, innerStrokePaint)

        // Title
        val titleY = artRect.bottom + artToTitleGap
        canvas.save()
        canvas.translate(cardLeft + cardPadding, titleY)
        titleLayout.draw(canvas)
        canvas.restore()

        // Artist
        val artistY = titleY + titleLayout.height + titleToArtistGap
        canvas.save()
        canvas.translate(cardLeft + cardPadding, artistY)
        artistLayout.draw(canvas)
        canvas.restore()

        // SpatialFlow Branding Badge (White icon + SpatialFlow text)
        val badgeY = artistY + artistLayout.height + artistToBadgeGap
        val rawLogo = ContextCompat.getDrawable(context, R.drawable.ic_applogo)?.mutate()
        val logoColor = AndroidColor.argb(185, 255, 255, 255)
        if (rawLogo != null) {
            DrawableCompat.setTint(rawLogo, logoColor)
            val logoBmp = rawLogo.toBitmap(badgeIconSize.toInt(), badgeIconSize.toInt())
            if (logoBmp != null) {
                canvas.drawBitmap(logoBmp, cardLeft + cardPadding, badgeY + 2f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
        }

        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = logoColor
            textSize = 30f
            applyLabel(weight = 600, width = 105f)
            letterSpacing = 0.02f
        }
        val badgeMetrics = badgeTextPaint.fontMetrics
        val badgeX = cardLeft + cardPadding + badgeIconSize + 12f
        val textBaselineY = badgeY + (badgeIconSize / 2f) - ((badgeMetrics.ascent + badgeMetrics.descent) / 2f)
        canvas.drawText("SpatialFlow", badgeX, textBaselineY, badgeTextPaint)

        bitmap
    }

    suspend fun saveBitmapAsFile(context: Context, bitmap: Bitmap, fileName: String): Uri = withContext(Dispatchers.IO) {
        val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SpatialFlow")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Failed to create MediaStore record")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            uri
        } else {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val imageFile = File(cachePath, "$fileName.png")
            FileOutputStream(imageFile).use { outputStream ->
                safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        }
    }

    private fun stackBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return bitmap

        val w = bitmap.width
        val h = bitmap.height

        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int

        val vmin = IntArray(Math.max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (i in 0 until 256 * divsum) {
            dv[i] = i / divsum
        }

        yw = 0
        yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (y in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (i in -radius..radius) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))]
                val sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (x in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                val sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                val sirNext = stack[stackpointer % div]

                routsum += sirNext[0]
                goutsum += sirNext[1]
                boutsum += sirNext[2]

                rinsum -= sirNext[0]
                ginsum -= sirNext[1]
                binsum -= sirNext[2]

                yi++
            }
            yw += w
        }

        for (x in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (i in -radius..radius) {
                yi = Math.max(0, yp) + x
                val sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]

                rbs = r1 - Math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs

                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (i < hm) {
                    yp += w
                }
            }
            yi = x
            stackpointer = radius
            for (y in 0 until h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                val sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                val sirNext = stack[stackpointer % div]

                routsum += sirNext[0]
                goutsum += sirNext[1]
                boutsum += sirNext[2]

                rinsum -= sirNext[0]
                ginsum -= sirNext[1]
                binsum -= sirNext[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}
