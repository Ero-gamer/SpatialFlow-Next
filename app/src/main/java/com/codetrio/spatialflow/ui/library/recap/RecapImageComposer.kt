package com.codetrio.spatialflow.ui.library.recap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.util.CanvasFontProvider
import com.codetrio.spatialflow.util.CanvasFontProvider.applyBody
import com.codetrio.spatialflow.util.CanvasFontProvider.applyLabel
import com.codetrio.spatialflow.util.CanvasFontProvider.applyRecapTitle
import com.codetrio.spatialflow.util.CanvasFontProvider.applyRecapWide
import com.codetrio.spatialflow.util.CanvasFontProvider.applyTitle
import com.codetrio.spatialflow.util.CanvasShapeProvider
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 100% Programmatic Canvas-Based Recap Poster Composer for SpatialFlow.
 * Eliminates screen-shotting / PixelCopy completely.
 *
 * Implements 1:1 visual parity with the interactive in-app Recap Story (RecapStoryDialog.kt):
 * - Solid M3 expressive dark tonal background (no artificial radial purple blobs).
 * - Header: SPATIALFLOW RECAP pill + Period label.
 * - Zone A: #1 Track monumental card with authentic Compose MaterialShapes.Cookie12Sided artwork (no border stroke).
 * - Zone B: Listening stats (YOU LISTENED FOR, hours/minutes, play count, streak, #1 Artist) + MaterialShapes.Sunny avatar.
 * - Zone C: Top Replay runner-up rows (#2 and #3) with rank badges and play counts.
 * - Unified typography powered by CanvasFontProvider with Google Sans Flex variable font axes.
 * - Brand footer: SpatialFlow logo + SpatialFlow Music watermark.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object RecapImageComposer {

    private const val POSTER_WIDTH = 1080
    private const val POSTER_HEIGHT = 1920

    suspend fun createRecapImage(
        context: Context,
        recap: PlayerSharedViewModel.ListeningRecap,
        timeRangeLabel: String,
        slideType: StorySlideType = StorySlideType.GRAND_SUMMARY,
        viewModel: PlayerSharedViewModel? = null
    ): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = createBitmap(POSTER_WIDTH, POSTER_HEIGHT)
        val canvas = Canvas(bitmap)

        val heroSong = recap.topSongs.firstOrNull()
        val heroArtist = recap.topArtists.firstOrNull()
        val runnersUp = recap.topSongs.drop(1).take(2)

        // 1. Fetch artwork bitmaps for palette and composition
        val artistImageUrl = viewModel?.artistProfileMap?.get(heroArtist?.artist)
            ?: heroArtist?.let { viewModel?.getArtistProfileImage(it.artist) ?: it.thumbnailUrl }
            ?: recap.topSongs.firstOrNull { s -> s.artist.equals(heroArtist?.artist, ignoreCase = true) }?.thumbnailUrl

        var artistBitmap: Bitmap? = null
        if (!artistImageUrl.isNullOrBlank()) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(artistImageUrl)
                    .allowHardware(false)
                    .build()
                artistBitmap = context.imageLoader.execute(req).drawable?.toBitmap()
            } catch (_: Exception) {}
        }

        var songBitmap: Bitmap? = null
        val songThumbUrl = heroSong?.thumbnailUrl
        if (!songThumbUrl.isNullOrBlank()) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(songThumbUrl)
                    .allowHardware(false)
                    .build()
                songBitmap = context.imageLoader.execute(req).drawable?.toBitmap()
            } catch (_: Exception) {}
        }

        val runnerBitmaps = mutableListOf<Bitmap?>()
        for (runner in runnersUp) {
            var rBmp: Bitmap? = null
            if (!runner.thumbnailUrl.isNullOrBlank()) {
                try {
                    val req = ImageRequest.Builder(context)
                        .data(runner.thumbnailUrl)
                        .allowHardware(false)
                        .build()
                    rBmp = context.imageLoader.execute(req).drawable?.toBitmap()
                } catch (_: Exception) {}
            }
            runnerBitmaps.add(rBmp)
        }

        // 2. Extract Harmonized M3 Expressive Palette (Exact Parity with RecapThemeEngine)
        val seedBitmap = songBitmap ?: artistBitmap
        val palette = seedBitmap?.let { Palette.from(it).generate() }
        val dominantColor = palette?.dominantSwatch?.rgb
            ?: palette?.vibrantSwatch?.rgb
            ?: 0xFF4A148C.toInt()

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(dominantColor, hsl)
        val hue = hsl[0]
        val sat = hsl[1].coerceIn(0.55f, 0.90f)

        val accentColor = ColorUtils.HSLToColor(floatArrayOf(hue, sat, 0.65f))
        val onAccentColor = if (ColorUtils.calculateLuminance(accentColor) > 0.45) 0xFF0E0E12.toInt() else 0xFFFFFFFF.toInt()
        val secColor = ColorUtils.HSLToColor(floatArrayOf((hue + 45f) % 360f, 0.50f, 0.35f))
        val onSecColor = 0xFFFFFFFF.toInt()
        val darkBgColor = ColorUtils.HSLToColor(floatArrayOf(hue, 0.28f, 0.07f))
        val surfHigh = ColorUtils.HSLToColor(floatArrayOf(hue, 0.20f, 0.15f))
        val onSurfColor = 0xFFFFFFFF.toInt()

        // 3. Draw Solid M3 Expressive Background (Exact Parity with RecapStoryDialog bgColor)
        canvas.drawColor(darkBgColor)

        // 4. Initialize Unified Font Typeface
        CanvasFontProvider.getBaseTypeface(context)

        // 5. Draw Top Header Bar (Matching RecapStoryDialog)
        val headerY = 110f
        val badgeHeight = 52f
        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onAccentColor
            textSize = 23f
            applyRecapWide(weight = 800, width = 115f, round = 100f)
            letterSpacing = 0.16f
        }
        val badgeText = "SPATIALFLOW RECAP"
        val badgeWidth = badgeTextPaint.measureText(badgeText) + 42f
        val headerBadgeRect = RectF(70f, headerY, 70f + badgeWidth, headerY + badgeHeight)

        val headerBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
        }
        canvas.drawRoundRect(headerBadgeRect, 26f, 26f, headerBadgePaint)
        canvas.drawText(badgeText, headerBadgeRect.left + 21f, headerBadgeRect.centerY() + 8f, badgeTextPaint)

        // Period Label
        val periodText = formatRecapPeriod(timeRangeLabel).uppercase(Locale.ROOT)
        val periodPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(onSurfColor, 205)
            textSize = 27f
            applyRecapWide(weight = 750, width = 110f, round = 80f)
            letterSpacing = 0.08f
        }
        canvas.drawText(periodText, headerBadgeRect.right + 24f, headerBadgeRect.centerY() + 9f, periodPaint)

        // Render Slide Content
        when (slideType) {
            StorySlideType.TOP_ARTIST -> {
                drawFocusedArtistSlide(
                    canvas, recap, heroArtist, artistBitmap,
                    accentColor, onAccentColor, secColor, onSecColor, surfHigh, onSurfColor
                )
            }
            StorySlideType.TOP_TRACK -> {
                drawFocusedTrackSlide(
                    canvas, recap, heroSong, songBitmap,
                    accentColor, onAccentColor, secColor, onSecColor, surfHigh, onSurfColor
                )
            }
            StorySlideType.LISTENING_TIME -> {
                drawFocusedTimeSlide(
                    canvas, recap,
                    accentColor, onAccentColor, secColor, onSecColor, surfHigh, onSurfColor
                )
            }
            else -> {
                // Main Grand Summary Poster (1:1 with in-app RecapStoryDialog Zones A, B, C)
                drawGrandSummaryPoster(
                    canvas, recap, heroSong, heroArtist, runnersUp,
                    songBitmap, artistBitmap, runnerBitmaps,
                    accentColor, onAccentColor, secColor, onSecColor, surfHigh, onSurfColor
                )
            }
        }

        // 6. Draw Bottom Brand Footer
        val logoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_applogo)
        val logoSize = 40
        val footerY = POSTER_HEIGHT - 90f
        if (logoDrawable != null) {
            val logoBmp = logoDrawable.toBitmap(logoSize, logoSize)
            val logoFilter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = android.graphics.PorterDuffColorFilter(
                    ColorUtils.setAlphaComponent(onSurfColor, 185),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
            canvas.drawBitmap(logoBmp, 70f, footerY - logoSize + 4f, logoFilter)
        }

        val footerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(onSurfColor, 185)
            textSize = 27f
            applyTitle(weight = 600, width = 105f)
            letterSpacing = 0.05f
        }
        canvas.drawText("SpatialFlow Music", 70f + logoSize + 14f, footerY - 4f, footerTextPaint)

        val brandWatermarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(onSurfColor, 130)
            textSize = 24f
            applyBody(weight = 450, width = 100f)
        }
        val brandingRight = "SpatialFlow"
        canvas.drawText(brandingRight, POSTER_WIDTH - 70f - brandWatermarkPaint.measureText(brandingRight), footerY - 4f, brandWatermarkPaint)

        bitmap
    }

    /**
     * Renders 1:1 Grand Summary Poster matching in-app RecapStoryDialog.kt Zones A, B, C.
     */
    private fun drawGrandSummaryPoster(
        canvas: Canvas,
        recap: PlayerSharedViewModel.ListeningRecap,
        heroSong: PlayerSharedViewModel.TopSongStat?,
        heroArtist: PlayerSharedViewModel.TopArtistStat?,
        runnersUp: List<PlayerSharedViewModel.TopSongStat>,
        songBitmap: Bitmap?,
        artistBitmap: Bitmap?,
        runnerBitmaps: List<Bitmap?>,
        accent: Int,
        onAccent: Int,
        sec: Int,
        onSec: Int,
        surfHigh: Int,
        onSurf: Int
    ) {
        val contentLeft = 70f
        val contentRight = POSTER_WIDTH - 70f
        val contentWidth = contentRight - contentLeft
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(surfHigh, 140)
            strokeWidth = 2.5f
        }

        var curY = 190f

        // ═════════════════════════════════════════════════════════════════════
        // ZONE A: HERO ALBUM ART — CENTERED MONUMENTAL (#1 TRACK)
        // ═════════════════════════════════════════════════════════════════════
        val trackPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val trackPillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onAccent
            textSize = 24f
            applyRecapWide(weight = 800, width = 115f, round = 100f)
            letterSpacing = 0.18f
        }
        val trackPillText = "#1 TRACK"
        val trackPillW = trackPillTextPaint.measureText(trackPillText) + 44f
        val trackPillRect = RectF((POSTER_WIDTH - trackPillW) / 2f, curY, (POSTER_WIDTH + trackPillW) / 2f, curY + 48f)
        canvas.drawRoundRect(trackPillRect, 24f, 24f, trackPillPaint)
        canvas.drawText(trackPillText, trackPillRect.left + 22f, trackPillRect.centerY() + 8f, trackPillTextPaint)

        curY += 66f

        // Centered Album Artwork — Compose MaterialShapes.Cookie12Sided (No border stroke)
        val artSize = 410f
        val artLeft = (POSTER_WIDTH - artSize) / 2f
        val artTop = curY
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)

        val cookie12Path = CanvasShapeProvider.getPolygonPath(MaterialShapes.Cookie12Sided, artLeft, artTop, artSize, artSize)

        if (songBitmap != null) {
            canvas.save()
            canvas.clipPath(cookie12Path)
            canvas.drawBitmap(songBitmap, null, artRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(accent, 40)
            }
            canvas.drawPath(cookie12Path, placeholderPaint)
        }

        curY += artSize + 30f

        // Track Title (Enlarged Adaptive Bold Single Line)
        val titleText = heroSong?.title ?: "—"
        var titleSize = 62f
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurf
            textSize = titleSize
            applyRecapTitle(weight = 850, width = 120f)
            letterSpacing = -0.02f
        }
        while (titleSize > 40f && titlePaint.measureText(titleText) > contentWidth) {
            titleSize -= 2f
            titlePaint.textSize = titleSize
        }
        val titleLayout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, titlePaint, contentWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(contentLeft, curY)
        titleLayout.draw(canvas)
        canvas.restore()

        curY += titleLayout.height + 8f

        // Artist Name (Enlarged)
        val artistText = heroSong?.artist ?: ""
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(onSurf, 195)
            textSize = 38f
            applyTitle(weight = 600, width = 105f)
        }
        val artistLayout = StaticLayout.Builder.obtain(artistText, 0, artistText.length, artistPaint, contentWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(contentLeft, curY)
        artistLayout.draw(canvas)
        canvas.restore()

        curY += artistLayout.height + 32f

        // Solid Tonal Divider
        canvas.drawLine(contentLeft + 20f, curY, contentRight - 20f, curY, dividerPaint)
        curY += 34f

        // ═════════════════════════════════════════════════════════════════════
        // ZONE B: STATS + ARTIST AVATAR — SIDE-BY-SIDE
        // ═════════════════════════════════════════════════════════════════════
        val zoneBTop = curY
        val avatarSize = 290f
        val statsWidth = contentWidth - avatarSize - 40f

        // Left: "YOU LISTENED FOR"
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 25f
            applyRecapWide(weight = 800, width = 115f, round = 80f)
            letterSpacing = 0.16f
        }
        canvas.drawText("YOU LISTENED FOR", contentLeft, curY + 24f, captionPaint)
        curY += 36f

        // Huge Listening Duration (e.g. 74 HOURS / 4,420 MINS)
        val formattedTime = if (recap.totalMinutes >= 60) {
            "${recap.totalMinutes / 60} HOURS"
        } else {
            "${recap.totalMinutes} MINS"
        }
        val durationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurf
            textSize = 84f
            applyRecapTitle(weight = 900, width = 125f)
            letterSpacing = -0.03f
        }
        canvas.drawText(formattedTime, contentLeft, curY + 72f, durationPaint)
        curY += 92f

        // Stats Badges Row: [X PLAYS] and [X D STREAK]
        val statBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val statBadgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onAccent
            textSize = 22f
            applyRecapWide(weight = 800, width = 115f, round = 100f)
            letterSpacing = 0.06f
        }
        val playsStr = "${recap.totalPlayCount} PLAYS"
        val playsW = statBadgeTextPaint.measureText(playsStr) + 32f
        val playsRect = RectF(contentLeft, curY, contentLeft + playsW, curY + 42f)
        canvas.drawRoundRect(playsRect, 21f, 21f, statBadgePaint)
        canvas.drawText(playsStr, playsRect.left + 16f, playsRect.centerY() + 7f, statBadgeTextPaint)

        val streakBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sec }
        val streakBadgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSec
            textSize = 22f
            applyRecapWide(weight = 800, width = 115f, round = 100f)
            letterSpacing = 0.06f
        }
        val streakStr = "${recap.listeningStreakDays}D STREAK"
        val streakW = streakBadgeTextPaint.measureText(streakStr) + 32f
        val streakRect = RectF(playsRect.right + 14f, curY, playsRect.right + 14f + streakW, curY + 42f)
        canvas.drawRoundRect(streakRect, 21f, 21f, streakBadgePaint)
        canvas.drawText(streakStr, streakRect.left + 16f, streakRect.centerY() + 7f, streakBadgeTextPaint)

        curY += 60f

        // #1 ARTIST Badge
        val artistPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(accent, 220) }
        val artistPillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onAccent
            textSize = 22f
            applyRecapWide(weight = 800, width = 115f, round = 100f)
            letterSpacing = 0.12f
        }
        val aPillText = "#1 ARTIST"
        val aPillW = artistPillTextPaint.measureText(aPillText) + 32f
        val aPillRect = RectF(contentLeft, curY, contentLeft + aPillW, curY + 42f)
        canvas.drawRoundRect(aPillRect, 21f, 21f, artistPillPaint)
        canvas.drawText(aPillText, aPillRect.left + 16f, aPillRect.centerY() + 7f, artistPillTextPaint)

        curY += 54f

        // Artist Name (Enlarged Single Line)
        val heroArtistName = heroArtist?.artist ?: "—"
        val hArtistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurf
            textSize = 50f
            applyRecapTitle(weight = 800, width = 115f)
            letterSpacing = -0.01f
        }
        val hArtistLayout = StaticLayout.Builder.obtain(heroArtistName, 0, heroArtistName.length, hArtistPaint, statsWidth.toInt())
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(contentLeft, curY)
        hArtistLayout.draw(canvas)
        canvas.restore()

        // Right: Artist Avatar cropped to MaterialShapes.Sunny (No border stroke)
        val avatarLeft = contentRight - avatarSize
        val avatarTop = zoneBTop + 14f
        val avatarRect = RectF(avatarLeft, avatarTop, avatarLeft + avatarSize, avatarTop + avatarSize)

        val sunnyPath = CanvasShapeProvider.getPolygonPath(MaterialShapes.Sunny, avatarLeft, avatarTop, avatarSize, avatarSize)

        if (artistBitmap != null) {
            canvas.save()
            canvas.clipPath(sunnyPath)
            canvas.drawBitmap(artistBitmap, null, avatarRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(accent, 35)
            }
            canvas.drawPath(sunnyPath, placeholderPaint)
        }

        curY = maxOf(curY + hArtistLayout.height + 26f, avatarTop + avatarSize + 26f)

        // Solid Tonal Divider
        canvas.drawLine(contentLeft + 20f, curY, contentRight - 20f, curY, dividerPaint)
        curY += 34f

        // ═════════════════════════════════════════════════════════════════════
        // ZONE C: TOP REPLAY — RUNNER-UP ROWS
        // ═════════════════════════════════════════════════════════════════════
        val replayHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 36f
            applyRecapWide(weight = 800, width = 115f, round = 80f)
            letterSpacing = 0.12f
        }
        canvas.drawText("TOP REPLAY", contentLeft, curY + 32f, replayHeaderPaint)

        val rotBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val rotBadgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onAccent
            textSize = 21f
            applyRecapWide(weight = 800, width = 115f, round = 100f)
            letterSpacing = 0.08f
        }
        val rotText = "HEAVY ROTATION"
        val rotW = rotBadgeTextPaint.measureText(rotText) + 32f
        val rotRect = RectF(contentRight - rotW, curY, contentRight, curY + 42f)
        canvas.drawRoundRect(rotRect, 21f, 21f, rotBadgePaint)
        canvas.drawText(rotText, rotRect.left + 16f, rotRect.centerY() + 7f, rotBadgeTextPaint)

        curY += 68f

        // Runner-up Rows (#2 and #3)
        val rowThumbSize = 130f
        runnersUp.forEachIndexed { i, runnerSong ->
            val rankStr = "0${i + 2}"
            val rankPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (i == 0) accent else sec
                textSize = 62f
                applyRecapTitle(weight = 850, width = 120f)
                letterSpacing = -0.04f
            }
            canvas.drawText(rankStr, contentLeft, curY + 84f, rankPaint)

            val thumbX = contentLeft + 98f
            val thumbY = curY
            val thumbRect = RectF(thumbX, thumbY, thumbX + rowThumbSize, thumbY + rowThumbSize)

            // Shape: MaterialShapes.Cookie12Sided for #2, MaterialShapes.Sunny for #3 (No border stroke)
            val runnerShape = if (i % 2 == 0) {
                CanvasShapeProvider.getPolygonPath(MaterialShapes.Cookie12Sided, thumbX, thumbY, rowThumbSize, rowThumbSize)
            } else {
                CanvasShapeProvider.getPolygonPath(MaterialShapes.Sunny, thumbX, thumbY, rowThumbSize, rowThumbSize)
            }

            val rBmp = runnerBitmaps.getOrNull(i)
            if (rBmp != null) {
                canvas.save()
                canvas.clipPath(runnerShape)
                canvas.drawBitmap(rBmp, null, thumbRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                canvas.restore()
            } else {
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ColorUtils.setAlphaComponent(accent, 35)
                }
                canvas.drawPath(runnerShape, placeholderPaint)
            }

            // Title & Artist
            val textStartX = thumbX + rowThumbSize + 26f
            val textAvailableW = contentRight - textStartX - 150f

            val rTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = onSurf
                textSize = 38f
                applyTitle(weight = 700, width = 105f)
            }
            val rTitleLayout = StaticLayout.Builder.obtain(runnerSong.title, 0, runnerSong.title.length, rTitlePaint, textAvailableW.toInt())
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            canvas.translate(textStartX, curY + 18f)
            rTitleLayout.draw(canvas)
            canvas.restore()

            val rArtistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(onSurf, 185)
                textSize = 28f
                applyBody(weight = 450, width = 100f)
            }
            val rArtistLayout = StaticLayout.Builder.obtain(runnerSong.artist, 0, runnerSong.artist.length, rArtistPaint, textAvailableW.toInt())
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            canvas.translate(textStartX, curY + 18f + rTitleLayout.height + 6f)
            rArtistLayout.draw(canvas)
            canvas.restore()

            // Plays on right
            val playText = "${runnerSong.count} plays"
            val playPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(onSurf, 165)
                textSize = 28f
                applyLabel(weight = 600, width = 105f)
            }
            val playW = playPaint.measureText(playText)
            canvas.drawText(playText, contentRight - playW, curY + 76f, playPaint)

            curY += rowThumbSize + 28f
        }
    }

    /**
     * Dedicated Top Artist Slide Generator matching in-app Story presentation.
     */
    private fun drawFocusedArtistSlide(
        canvas: Canvas,
        recap: PlayerSharedViewModel.ListeningRecap,
        heroArtist: PlayerSharedViewModel.TopArtistStat?,
        artistBitmap: Bitmap?,
        accent: Int,
        onAccent: Int,
        sec: Int,
        onSec: Int,
        surfHigh: Int,
        onSurf: Int
    ) {
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onAccent
            textSize = 26f
            applyRecapWide(weight = 800, width = 115f, round = 100f)
            letterSpacing = 0.18f
        }
        val pillText = "TOP ARTIST"
        val pillW = pillTextPaint.measureText(pillText) + 44f
        val pillRect = RectF((POSTER_WIDTH - pillW) / 2f, 220f, (POSTER_WIDTH + pillW) / 2f, 272f)
        canvas.drawRoundRect(pillRect, 26f, 26f, pillPaint)
        canvas.drawText(pillText, pillRect.left + 22f, pillRect.centerY() + 9f, pillTextPaint)

        // Huge MaterialShapes.Sunny Avatar (No border stroke)
        val avatarSize = 560f
        val avatarLeft = (POSTER_WIDTH - avatarSize) / 2f
        val avatarTop = 580f - (avatarSize / 2f)
        val avatarRect = RectF(avatarLeft, avatarTop, avatarLeft + avatarSize, avatarTop + avatarSize)
        val sunnyPath = CanvasShapeProvider.getPolygonPath(MaterialShapes.Sunny, avatarLeft, avatarTop, avatarSize, avatarSize)

        if (artistBitmap != null) {
            canvas.save()
            canvas.clipPath(sunnyPath)
            canvas.drawBitmap(artistBitmap, null, avatarRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(accent, 40)
            }
            canvas.drawPath(sunnyPath, placeholderPaint)
        }

        // Artist Name (Enlarged)
        val aName = heroArtist?.artist ?: "Unknown Artist"
        val aNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurf
            textSize = 70f
            applyRecapTitle(weight = 850, width = 120f)
        }
        val nameLayout = StaticLayout.Builder.obtain(aName, 0, aName.length, aNamePaint, 940)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(70f, 910f)
        nameLayout.draw(canvas)
        canvas.restore()

        // Stat Pill
        val statStr = "${heroArtist?.count ?: 0} PLAYS"
        val statPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSec
            textSize = 30f
            applyRecapWide(weight = 800, width = 115f, round = 100f)
            letterSpacing = 0.08f
        }
        val sW = statPaint.measureText(statStr) + 52f
        val sRect = RectF((POSTER_WIDTH - sW) / 2f, 1080f, (POSTER_WIDTH + sW) / 2f, 1148f)
        val sBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sec }
        canvas.drawRoundRect(sRect, 34f, 34f, sBgPaint)
        canvas.drawText(statStr, sRect.left + 26f, sRect.centerY() + 10f, statPaint)
    }

    /**
     * Dedicated Top Track Slide Generator matching in-app Story presentation.
     */
    private fun drawFocusedTrackSlide(
        canvas: Canvas,
        recap: PlayerSharedViewModel.ListeningRecap,
        heroSong: PlayerSharedViewModel.TopSongStat?,
        songBitmap: Bitmap?,
        accent: Int,
        onAccent: Int,
        sec: Int,
        onSec: Int,
        surfHigh: Int,
        onSurf: Int
    ) {
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onAccent
            textSize = 26f
            applyRecapWide(weight = 800, width = 115f, round = 100f)
            letterSpacing = 0.18f
        }
        val pillText = "TOP TRACK"
        val pillW = pillTextPaint.measureText(pillText) + 44f
        val pillRect = RectF((POSTER_WIDTH - pillW) / 2f, 220f, (POSTER_WIDTH + pillW) / 2f, 272f)
        canvas.drawRoundRect(pillRect, 26f, 26f, pillPaint)
        canvas.drawText(pillText, pillRect.left + 22f, pillRect.centerY() + 9f, pillTextPaint)

        // Huge MaterialShapes.Cookie12Sided Artwork (No border stroke)
        val artSize = 560f
        val artLeft = (POSTER_WIDTH - artSize) / 2f
        val artTop = 580f - (artSize / 2f)
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
        val cookie12Path = CanvasShapeProvider.getPolygonPath(MaterialShapes.Cookie12Sided, artLeft, artTop, artSize, artSize)

        if (songBitmap != null) {
            canvas.save()
            canvas.clipPath(cookie12Path)
            canvas.drawBitmap(songBitmap, null, artRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(accent, 40)
            }
            canvas.drawPath(cookie12Path, placeholderPaint)
        }

        // Track Title (Enlarged)
        val tTitle = heroSong?.title ?: "Unknown Track"
        val tTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurf
            textSize = 66f
            applyRecapTitle(weight = 850, width = 120f)
        }
        val tLayout = StaticLayout.Builder.obtain(tTitle, 0, tTitle.length, tTitlePaint, 940)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(70f, 910f)
        tLayout.draw(canvas)
        canvas.restore()

        // Artist Name (Enlarged)
        val aName = heroSong?.artist ?: "Unknown Artist"
        val aPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(onSurf, 195)
            textSize = 40f
            applyTitle(weight = 600, width = 105f)
        }
        val aLayout = StaticLayout.Builder.obtain(aName, 0, aName.length, aPaint, 940)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(70f, 910f + tLayout.height + 14f)
        aLayout.draw(canvas)
        canvas.restore()

        // Stat Pill
        val statStr = "${heroSong?.count ?: 0} PLAYS"
        val statPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSec
            textSize = 30f
            applyRecapWide(weight = 800, width = 115f, round = 100f)
            letterSpacing = 0.08f
        }
        val sW = statPaint.measureText(statStr) + 52f
        val sRect = RectF((POSTER_WIDTH - sW) / 2f, 1140f, (POSTER_WIDTH + sW) / 2f, 1208f)
        val sBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sec }
        canvas.drawRoundRect(sRect, 34f, 34f, sBgPaint)
        canvas.drawText(statStr, sRect.left + 26f, sRect.centerY() + 10f, statPaint)
    }

    /**
     * Dedicated Listening Time Slide Generator matching in-app Story presentation.
     */
    private fun drawFocusedTimeSlide(
        canvas: Canvas,
        recap: PlayerSharedViewModel.ListeningRecap,
        accent: Int,
        onAccent: Int,
        sec: Int,
        onSec: Int,
        surfHigh: Int,
        onSurf: Int
    ) {
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 34f
            applyRecapWide(weight = 800, width = 115f, round = 80f)
            letterSpacing = 0.18f
        }
        val captionText = "TOTAL LISTENING TIME"
        val capW = captionPaint.measureText(captionText)
        canvas.drawText(captionText, (POSTER_WIDTH - capW) / 2f, 400f, captionPaint)

        // Huge Minutes Counter (Enlarged)
        val minutesPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurf
            textSize = 150f
            applyRecapTitle(weight = 900, width = 125f)
        }
        val minText = String.format(Locale.getDefault(), "%,d", recap.totalMinutes)
        val minWidth = minutesPaint.measureText(minText)
        canvas.drawText(minText, (POSTER_WIDTH - minWidth) / 2f, 580f, minutesPaint)

        val minLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 46f
            applyRecapWide(weight = 800, width = 120f, round = 100f)
            letterSpacing = 0.20f
        }
        val minLbl = "MINUTES"
        canvas.drawText(minLbl, (POSTER_WIDTH - minLabelPaint.measureText(minLbl)) / 2f, 660f, minLabelPaint)

        // Hours subtitle
        val hoursText = "≈ ${recap.totalMinutes / 60} hours of music"
        val hoursPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(onSurf, 185)
            textSize = 36f
            applyBody(weight = 500, width = 100f)
        }
        canvas.drawText(hoursText, (POSTER_WIDTH - hoursPaint.measureText(hoursText)) / 2f, 730f, hoursPaint)

        // Personality Archetype Container
        val archRect = RectF(100f, 920f, POSTER_WIDTH - 100f, 1180f)
        val archPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = surfHigh
        }
        canvas.drawRoundRect(archRect, 36f, 36f, archPaint)

        val archTagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 26f
            applyRecapWide(weight = 800, width = 115f, round = 80f)
            letterSpacing = 0.12f
        }
        canvas.drawText("MUSIC PERSONALITY", archRect.left + 50f, archRect.top + 70f, archTagPaint)

        val archTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurf
            textSize = 52f
            applyRecapTitle(weight = 800, width = 115f)
        }
        canvas.drawText(recap.personality.title, archRect.left + 50f, archRect.top + 140f, archTitlePaint)

        val archDescPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(onSurf, 195)
            textSize = 30f
            applyBody(weight = 450, width = 100f)
        }
        canvas.drawText(recap.personality.subtitle, archRect.left + 50f, archRect.top + 200f, archDescPaint)
    }
}
