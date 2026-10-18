package com.codetrio.spatialflow.ui.library.recap

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.codetrio.spatialflow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

/**
 * 100% Dynamic Cover Art Generator for SpatialFlow Recap Playlists.
 * Implements the vector architecture and visual language of Group 11.svg:
 * - 1000x1000 px resolution
 * - Harmonized 3-tone HSL extraction from playlist tracks (zero hardcoded colors)
 * - Exact swooping cubic bezier wave geometry
 * - Iconic central vinyl record disc silhouette with optional top song spindle art
 * - Google Sans Flex monumental brand typography ("SPATIALFLOW")
 * - Dynamic period subtitle ("TOP PLAYLIST [MONTH / PERIOD]")
 * - SpatialFlow official vector logo (headphones + 5-bar equalizer)
 */
object RecapCoverArtGenerator {

    private const val TAG = "RecapCoverArtGenerator"
    const val COVER_SIZE = 1000

    /**
     * 100% dynamically derived HSL color roles. Zero hardcoded colors, 100% solid (no transparency).
     */
    data class HarmonizedRecapPalette(
        val primaryColor: Int,       // Lightness ~62%, Saturation 70-95% - Base Canvas, Brand Title, Logo
        val darkColor: Int,          // Lightness ~16%, Saturation 25-45% - Swooping Wave & Vinyl Body
        val darkElevated: Int,       // Lightness ~24%, Saturation 25-45% - Solid Elevated Chip Container on Dark Wave
        val darkBorder: Int,         // Lightness ~32%, Saturation 25-45% - Solid Chip Border
        val lightPillContainer: Int, // Lightness ~76%, Saturation 65-90% - Solid Header Pill Container
        val lightTint: Int,          // Lightness ~84%, Saturation 40-75% - High-Contrast Subtitle & Text
        val grooveColor: Int,        // Lightness ~28%, Saturation 25-45% - Solid Concentric Vinyl Groove Rings
        val dominantHue: Float,
        val dominantSat: Float
    ) {
        companion object {
            val Default = HarmonizedRecapPalette(
                primaryColor = android.graphics.Color.parseColor("#FF4B4B"),
                darkColor = android.graphics.Color.parseColor("#341414"),
                darkElevated = android.graphics.Color.parseColor("#4A1D1D"),
                darkBorder = android.graphics.Color.parseColor("#692929"),
                lightPillContainer = android.graphics.Color.parseColor("#FFA3A3"),
                lightTint = android.graphics.Color.parseColor("#FFD1D1"),
                grooveColor = android.graphics.Color.parseColor("#5A2323"),
                dominantHue = 0f,
                dominantSat = 0.85f
            )
        }
    }

    /**
     * Extracts dominant hue and saturation dynamically from playlist artwork via Palette and HSL.
     * Derives a full 100% solid harmonized tonal spectrum (zero transparency).
     */
    suspend fun extractHarmonizedPalette(
        context: Context,
        artworkUrls: List<String?>
    ): HarmonizedRecapPalette = withContext(Dispatchers.IO) {
        try {
            val firstValidUrl = artworkUrls.firstOrNull { !it.isNullOrBlank() }
            if (firstValidUrl != null) {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(firstValidUrl)
                    .size(160, 160)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                val bitmap: Bitmap? = (result.drawable as? BitmapDrawable)?.bitmap
                    ?: result.drawable?.toBitmap()

                if (bitmap != null) {
                    val palette = Palette.from(bitmap)
                        .maximumColorCount(24)
                        .resizeBitmapArea(6400)
                        .generate()

                    val seedColor = palette.vibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: palette.getDominantColor(0xFF8338EC.toInt())

                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(seedColor, hsl)

                    val seedH = hsl[0] // 0.0f - 360.0f
                    val seedS = hsl[1].coerceIn(0.35f, 1.0f) // Keep saturation vibrant

                    // 1. primaryColor: Base Canvas, Brand Title, Logo (Lightness ~62%) - 100% Solid
                    val primaryColor = ColorUtils.HSLToColor(
                        floatArrayOf(seedH, seedS.coerceIn(0.70f, 0.95f), 0.62f)
                    )

                    // 2. darkColor: Swooping Wave & Center Vinyl Record (Lightness ~16%) - 100% Solid
                    val darkColor = ColorUtils.HSLToColor(
                        floatArrayOf(seedH, (seedS * 0.45f).coerceIn(0.20f, 0.40f), 0.16f)
                    )

                    // 3. darkElevated: Solid elevated chip container on dark wave (Lightness ~24%) - 100% Solid
                    val darkElevated = ColorUtils.HSLToColor(
                        floatArrayOf(seedH, (seedS * 0.45f).coerceIn(0.20f, 0.40f), 0.24f)
                    )

                    // 4. darkBorder: Solid chip border on dark wave (Lightness ~32%) - 100% Solid
                    val darkBorder = ColorUtils.HSLToColor(
                        floatArrayOf(seedH, (seedS * 0.50f).coerceIn(0.25f, 0.45f), 0.32f)
                    )

                    // 5. lightPillContainer: Solid pill header container (Lightness ~76%) - 100% Solid
                    val lightPillContainer = ColorUtils.HSLToColor(
                        floatArrayOf(seedH, seedS.coerceIn(0.65f, 0.90f), 0.76f)
                    )

                    // 6. lightTint: High-Contrast Subtitle (Lightness ~84%) - 100% Solid
                    val lightTint = ColorUtils.HSLToColor(
                        floatArrayOf(seedH, (seedS * 0.70f).coerceIn(0.35f, 0.70f), 0.84f)
                    )

                    // 7. grooveColor: Solid concentric vinyl groove rings (Lightness ~28%) - 100% Solid
                    val grooveColor = ColorUtils.HSLToColor(
                        floatArrayOf(seedH, (seedS * 0.45f).coerceIn(0.20f, 0.40f), 0.28f)
                    )

                    return@withContext HarmonizedRecapPalette(
                        primaryColor = primaryColor,
                        darkColor = darkColor,
                        darkElevated = darkElevated,
                        darkBorder = darkBorder,
                        lightPillContainer = lightPillContainer,
                        lightTint = lightTint,
                        grooveColor = grooveColor,
                        dominantHue = seedH,
                        dominantSat = seedS
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting dynamic palette: ${e.message}", e)
        }
        HarmonizedRecapPalette.Default
    }

    /**
     * Resolves the dynamic subtitle label based on user's recap timeframe:
     * - Monthly: "TOP PLAYLIST JANUARY", ..., "TOP PLAYLIST JUNE", ..., "TOP PLAYLIST DECEMBER"
     * - Weekly: "TOP PLAYLIST WEEKLY"
     * - Yearly: "TOP PLAYLIST 2026"
     * - Seasonal: "TOP PLAYLIST SUMMER" or "TOP PLAYLIST [SEASON]"
     */
    fun formatPeriodSubtitle(periodLabel: String, timeRangeLabel: String? = null): String {
        val labelClean = periodLabel.trim().uppercase(Locale.ROOT)
        val timeRangeClean = timeRangeLabel?.trim()?.uppercase(Locale.ROOT) ?: ""

        val months = listOf(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
        )

        // 1. Direct single-month match
        for (m in months) {
            if (labelClean == m || labelClean.startsWith(m) || labelClean.contains(m)) {
                return "TOP PLAYLIST $m"
            }
        }

        // 2. Weekly check
        if (labelClean.contains("WEEK") || timeRangeClean.contains("WEEK")) {
            return "TOP PLAYLIST WEEKLY"
        }

        // 3. Year / All-Time check
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (labelClean.contains("YEAR") || labelClean.contains("$currentYear") || timeRangeClean.contains("ALL")) {
            return "TOP PLAYLIST $currentYear"
        }

        // 4. Default to resolved period label
        return "TOP PLAYLIST $labelClean"
    }

    /**
     * Generates a 1000x1000 px bitmap matching Group 11.svg vector layout:
     * - Base rect (primaryColor)
     * - Swooping wave curve (darkColor)
     * - Center vinyl disc (darkColor) with optional center song artwork
     * - SPATIALFLOW brand text in Google Sans Flex (primaryColor)
     * - TOP PLAYLIST [MONTH] in Google Sans Flex (lightTint)
     * - Official SpatialFlow headphones logo in primaryColor
     */
    suspend fun generateCoverArtBitmap(
        context: Context,
        palette: HarmonizedRecapPalette,
        periodSubtitle: String,
        centerTrackUrl: String? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(COVER_SIZE, COVER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Base Canvas Background (primaryColor)
        val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, COVER_SIZE.toFloat(), COVER_SIZE.toFloat(), primaryPaint)

        // 2. Swooping Wave Path (darkColor) — Exact bezier from Group 11.svg
        // M1000 601.99C966.107 793.204 728.714 853.491 0 808.955V1000H1000V601.99Z
        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.darkColor
            style = Paint.Style.FILL
        }
        val wavePath = Path().apply {
            moveTo(1000f, 601.99f)
            cubicTo(
                966.107f, 793.204f,
                728.714f, 853.491f,
                0f, 808.955f
            )
            lineTo(0f, 1000f)
            lineTo(1000f, 1000f)
            close()
        }
        canvas.drawPath(wavePath, darkPaint)

        // 3. Center Vinyl Record Silhouette (darkColor) — Center (500, 399)
        val vinylCenterX = 500.33f
        val vinylCenterY = 399.004f
        val vinylOuterRadius = 333.33f
        val vinylHoleRadius = 33.5f

        // Outer record body
        canvas.drawCircle(vinylCenterX, vinylCenterY, vinylOuterRadius, darkPaint)

        // Concentric record sound grooves (100% solid tonal hairpin rings)
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.grooveColor
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
        }
        val grooveRadii = floatArrayOf(
            vinylOuterRadius * 0.90f,
            vinylOuterRadius * 0.82f,
            vinylOuterRadius * 0.74f,
            vinylOuterRadius * 0.65f,
            vinylOuterRadius * 0.55f,
            vinylOuterRadius * 0.45f
        )
        for (r in grooveRadii) {
            canvas.drawCircle(vinylCenterX, vinylCenterY, r, groovePaint)
        }

        // Spindle center label
        val labelRadius = vinylOuterRadius * 0.33f
        val spindlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(vinylCenterX, vinylCenterY, labelRadius, spindlePaint)

        // Optional: Composite center track artwork if available
        if (!centerTrackUrl.isNullOrBlank()) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(centerTrackUrl)
                    .size(220, 220)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                val artBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    ?: result.drawable?.toBitmap()

                if (artBitmap != null) {
                    val artDiameter = (labelRadius * 1.6f).toInt()
                    val circularArt = getCircularBitmap(artBitmap, artDiameter)
                    val left = vinylCenterX - (artDiameter / 2f)
                    val top = vinylCenterY - (artDiameter / 2f)
                    canvas.drawBitmap(circularArt, left, top, null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load center spindle track art: ${e.message}")
            }
        }

        // Center spindle hole
        canvas.drawCircle(vinylCenterX, vinylCenterY, vinylHoleRadius, darkPaint)

        // 4. Bottom-Left Typography in Google Sans Flex
        val flexTypeface = try {
            ResourcesCompat.getFont(context, R.font.google_sans_flex)
        } catch (e: Exception) {
            null
        }

        // 4A. Brand Name: "SPATIALFLOW" (Heavy Bold Google Sans Flex in primaryColor)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryColor
            textSize = 70f
            isFakeBoldText = true
            letterSpacing = 0.08f
            typeface = flexTypeface
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fontVariationSettings = "'wght' 860, 'wdth' 138, 'ROND' 35"
            }
        }
        canvas.drawText("SPATIALFLOW", 50f, 922f, titlePaint)

        // 4B. Period Subtitle: "TOP PLAYLIST [MONTH]" (lightTint, high-contrast)
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.lightTint
            textSize = 27f
            isFakeBoldText = true
            letterSpacing = 0.12f
            typeface = flexTypeface
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fontVariationSettings = "'wght' 720, 'wdth' 120, 'ROND' 25"
            }
        }
        canvas.drawText(periodSubtitle.uppercase(Locale.ROOT), 52f, 962f, subPaint)

        // 5. Bottom-Right App Logo: Headphones + 5-bar Equalizer (ic_launcher_foreground.xml vector)
        drawAppLogoVector(canvas, palette.primaryColor, targetX = 780f, targetY = 812f, targetSize = 145f)

        bitmap
    }

    /**
     * Renders the SpatialFlow official logo (Headphones + Equalizer bars) at the exact target location.
     */
    private fun drawAppLogoVector(
        canvas: Canvas,
        logoColor: Int,
        targetX: Float,
        targetY: Float,
        targetSize: Float
    ) {
        try {
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = logoColor
                style = Paint.Style.FILL
            }

            // Path 1: 5 Equalizer visualizer bars (scaled from 1024 viewport)
            val eqPathData = "M478.8,649.8V581.9C478.8,580.4 479.4,579.1 480.5,578C481.6,576.9 482.9,576.3 484.5,576.3C486,576.3 487.4,576.9 488.5,578C489.6,579.1 490.2,580.4 490.2,581.9V649.8C490.2,651.4 489.6,652.8 488.5,653.9C487.3,655 486,655.5 484.4,655.5C482.9,655.5 481.5,655 480.5,653.9C479.4,652.8 478.8,651.4 478.8,649.8ZM505.4,675.2V556.6C505.4,555.1 505.9,553.8 507,552.7C508.2,551.6 509.5,551 511,551C512.6,551 513.9,551.6 515.1,552.7C516.2,553.8 516.8,555.1 516.8,556.6V675.2C516.8,676.9 516.2,678.2 515,679.3C513.9,680.4 512.5,681 511,681C509.4,681 508.1,680.4 507,679.3C505.9,678.2 505.4,676.9 505.4,675.2ZM452.3,623.8V608C452.3,606.5 452.8,605.2 453.9,604.1C455.1,603 456.4,602.4 458,602.4C459.6,602.4 460.9,603 462,604.1C463.1,605.2 463.7,606.5 463.7,608V623.8C463.7,625.5 463.1,626.8 462,627.9C460.8,629 459.5,629.6 457.9,629.6C456.3,629.6 455,629 453.9,627.9C452.8,626.8 452.3,625.5 452.3,623.8ZM531.8,649.8V581.9C531.8,580.4 532.3,579.1 533.5,578C534.6,576.9 536,576.3 537.5,576.3C539.1,576.3 540.5,576.9 541.5,578C542.6,579.1 543.2,580.4 543.2,581.9V649.8C543.2,651.4 542.6,652.8 541.5,653.9C540.4,655 539,655.5 537.4,655.5C535.9,655.5 534.5,655 533.4,653.9C532.3,652.8 531.8,651.4 531.8,649.8ZM558.3,623.8V608C558.3,606.5 558.9,605.2 560,604.1C561.2,603 562.5,602.4 564.1,602.4C565.7,602.4 567,603 568.1,604.1C569.2,605.2 569.8,606.5 569.8,608V623.8C569.8,625.5 569.2,626.8 568.1,627.9C566.9,629 565.6,629.6 564,629.6C562.4,629.6 561.1,629 560,627.9C558.9,626.8 558.3,625.5 558.3,623.8Z"
            // Path 2: Headphone arc & earcups (scaled from 1024 viewport)
            val hpPathData = "M371.5,716.6H314.2C301.6,716.6 290.6,711.9 281,702.3C271.5,692.8 266.7,681.7 266.7,669.1V471.8C266.7,438 273.1,406.3 285.9,376.8C298.7,347.3 316.2,321.5 338.3,299.3C360.5,277.2 386.4,259.7 416.1,246.9C445.9,234.1 477.6,227.7 511.5,227.7C545.4,227.7 577.1,234.1 606.9,246.9C636.6,259.7 662.5,277.2 684.7,299.3C706.8,321.5 724.3,347.3 737.1,376.8C749.9,406.3 756.3,438 756.3,471.8V669.1C756.3,681.7 751.5,692.8 742,702.3C732.4,711.9 721.4,716.6 708.8,716.6H651.5C638,716.6 626.7,712.1 617.6,703C608.5,693.9 603.9,682.6 603.9,669.1V555.2C603.9,541.7 608.5,530.4 617.6,521.3C626.7,512.2 638,507.7 651.5,507.7H688.8C699.8,507.7 708.8,498.7 708.8,487.7V471.8C708.8,417.2 689.6,370.7 651.1,332.5C612.7,294.3 566.2,275.2 511.5,275.2C456.8,275.2 410.3,294.3 371.9,332.5C333.4,370.7 314.2,417.2 314.2,471.8V487.7C314.2,498.7 323.2,507.7 334.2,507.7H371.5C385,507.7 396.3,512.2 405.4,521.3C414.5,530.4 419.1,541.7 419.1,555.2V669.1C419.1,682.6 414.5,693.9 405.4,703C396.3,712.1 385,716.6 371.5,716.6Z"

            val eqPath = PathParser.createPathFromPathData(eqPathData)
            val hpPath = PathParser.createPathFromPathData(hpPathData)

            val scale = targetSize / 1024f
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postTranslate(targetX, targetY)
            }
            eqPath.transform(matrix)
            hpPath.transform(matrix)

            canvas.drawPath(hpPath, logoPaint)
            canvas.drawPath(eqPath, logoPaint)
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing vector app logo: ${e.message}")
        }
    }

    /**
     * Circular crop helper for bitmap images.
     */
    private fun getCircularBitmap(src: Bitmap, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        val srcRect = Rect(0, 0, src.width, src.height)
        canvas.drawBitmap(src, srcRect, rect, paint)
        return output
    }

    /**
     * Saves the generated bitmap to internal storage and returns the file:/// URI string.
     */
    suspend fun saveCoverToFile(context: Context, bitmap: Bitmap, fileNamePrefix: String): String =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "recap_covers").apply { mkdirs() }
            val file = File(dir, "${fileNamePrefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(file).toString()
        }

    /**
     * Exports the custom cover art to the user's Gallery (Pictures/SpatialFlow) for easy upload to YouTube Music.
     */
    suspend fun exportToGallery(context: Context, bitmap: Bitmap, title: String): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "${title.replace(' ', '_')}.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "SpatialFlow")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)
                    }
                }
                imageUri
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export cover art to gallery: ${e.message}", e)
                null
            }
        }
}
