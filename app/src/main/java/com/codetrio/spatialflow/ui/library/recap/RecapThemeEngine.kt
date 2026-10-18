package com.codetrio.spatialflow.ui.library.recap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encapsulates the fully resolved Material 3 Expressive theme tokens for a single Recap slide.
 */
@Immutable
data class RecapSlideTheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val colorScheme: ColorScheme
) {
    val dynamicAccentColor: Color get() = primary

    companion object {
        val Default: RecapSlideTheme = run {
            val primary = Color(0xFFD0BCFF)
            val background = Color(0xFF101216)
            val surface = Color(0xFF1B1D22)
            val surfaceContainer = Color(0xFF22242A)
            val surfaceContainerHigh = Color(0xFF2B2D34)
            val surfaceVariant = Color(0xFF2E313A)
            val primaryContainer = Color(0xFF4F378B)

            val cs = darkColorScheme(
                primary = primary,
                onPrimary = Color(0xFF381E72),
                primaryContainer = primaryContainer,
                onPrimaryContainer = Color(0xFFEADDFF),
                secondary = Color(0xFFCCC2DC),
                onSecondary = Color(0xFF332D41),
                background = background,
                onBackground = Color(0xFFE6E1E5),
                surface = surface,
                onSurface = Color(0xFFE6E1E5),
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = Color(0xFFCAC4D0),
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh
            )

            RecapSlideTheme(
                primary = primary,
                onPrimary = Color(0xFF381E72),
                primaryContainer = primaryContainer,
                onPrimaryContainer = Color(0xFFEADDFF),
                secondary = Color(0xFFCCC2DC),
                onSecondary = Color(0xFF332D41),
                secondaryContainer = Color(0xFF4A4458),
                onSecondaryContainer = Color(0xFFE8DEF8),
                tertiary = Color(0xFFEFB8C8),
                onTertiary = Color(0xFF492532),
                background = background,
                onBackground = Color(0xFFE6E1E5),
                surface = surface,
                onSurface = Color(0xFFE6E1E5),
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = Color(0xFFCAC4D0),
                surfaceContainerLowest = Color(0xFF0C0E12),
                surfaceContainerLow = surface,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                surfaceContainerHighest = Color(0xFF33353D),
                colorScheme = cs
            )
        }
    }
}

/**
 * Backward compatibility container for legacy library and sheet callers.
 */
@Immutable
data class RecapDynamicTheme(
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val tertiaryAccent: Color,
    val onPrimaryAccent: Color,
    val onSecondaryAccent: Color,
    val cardSurface: Color,
    val cardSurfaceHigh: Color,
    val doodleStroke: Color = Color.Transparent,
    val genreColors: List<Color> = emptyList(),
    val genreTextColors: List<Color> = emptyList(),
    val romanticMoodColor: Color = primaryAccent,
    val dramaticMoodColor: Color = secondaryAccent,
    val sadMoodColor: Color = tertiaryAccent,
    val onTertiaryAccent: Color = if (tertiaryAccent.luminance() > 0.45f) Color.Black else Color.White,
    val artistSurface: Color = cardSurface,
    val artistChip: Color = secondaryAccent.copy(alpha = 0.35f)
) {
    fun colorSchemeForSlide(slideType: StorySlideType): ColorScheme {
        val slideAccent = accentForSlide(slideType)
        val onSlideAccent = onAccentForSlide(slideType)
        return darkColorScheme(
            primary = slideAccent,
            onPrimary = onSlideAccent,
            primaryContainer = slideAccent.copy(alpha = 0.28f),
            onPrimaryContainer = if (slideAccent.luminance() > 0.45f) Color(0xFF101216) else Color.White,
            secondary = secondaryAccent,
            onSecondary = onSecondaryAccent,
            secondaryContainer = secondaryAccent.copy(alpha = 0.25f),
            onSecondaryContainer = if (secondaryAccent.luminance() > 0.45f) Color(0xFF101216) else Color.White,
            tertiary = tertiaryAccent,
            onTertiary = onTertiaryAccent,
            tertiaryContainer = tertiaryAccent.copy(alpha = 0.22f),
            onTertiaryContainer = if (tertiaryAccent.luminance() > 0.45f) Color(0xFF101216) else Color.White,
            background = Color(0xFF101216),
            onBackground = Color(0xFFF1F0F4),
            surface = cardSurface,
            onSurface = Color(0xFFF1F0F4),
            surfaceVariant = cardSurfaceHigh,
            onSurfaceVariant = Color(0xFFF1F0F4).copy(alpha = 0.78f),
            surfaceContainer = cardSurface,
            surfaceContainerHigh = cardSurfaceHigh,
            surfaceContainerHighest = cardSurfaceHigh
        )
    }

    fun accentForSlide(slideType: StorySlideType): Color = when (slideType) {
        StorySlideType.INTRO -> primaryAccent
        StorySlideType.LISTENING_TIME -> secondaryAccent
        StorySlideType.TOP_ARTIST -> primaryAccent
        StorySlideType.TOP_TRACK -> secondaryAccent
        StorySlideType.TOP_PLAYLIST -> tertiaryAccent
        StorySlideType.TOP_ARTISTS_RANKED -> primaryAccent
        StorySlideType.TOP_GENRES -> secondaryAccent
        StorySlideType.GRAND_SUMMARY -> primaryAccent
    }

    fun onAccentForSlide(slideType: StorySlideType): Color =
        if (accentForSlide(slideType).luminance() > 0.45f) Color.Black else Color.White

    fun doodleStrokeForSlide(slideType: StorySlideType): Color = doodleStroke
}

/**
 * High-performance, LRU-cached theme extraction engine modeled directly after PlayerColorExtractor.
 */
object RecapThemeEngine {

    private val slideCache = LruCache<String, RecapSlideTheme>(50)
    private val paletteCache = LruCache<String, Palette>(50)

    fun getCachedSlideTheme(key: String?): RecapSlideTheme? {
        if (key.isNullOrBlank()) return null
        return slideCache.get(key)
    }

    /**
     * Resolves the artwork URL associated with the active Recap slide.
     */
    fun resolveArtworkUrlForSlide(
        slide: StorySlideType,
        recap: PlayerSharedViewModel.ListeningRecap,
        viewModel: PlayerSharedViewModel
    ): String? {
        return when (slide) {
            StorySlideType.INTRO -> {
                recap.topSongs.firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.thumbnailUrl
                    ?: recap.topArtists.firstOrNull()?.let { viewModel.getArtistProfileImage(it.artist) ?: it.thumbnailUrl }
            }
            StorySlideType.LISTENING_TIME -> {
                recap.topSongs.firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.thumbnailUrl
            }
            StorySlideType.TOP_ARTIST -> {
                recap.topArtists.firstOrNull()?.let { viewModel.getArtistProfileImage(it.artist) ?: it.thumbnailUrl }
            }
            StorySlideType.TOP_TRACK -> {
                recap.topSongs.firstOrNull()?.thumbnailUrl
            }
            StorySlideType.TOP_PLAYLIST -> {
                recap.topSongs.drop(1).firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.thumbnailUrl
                    ?: recap.topSongs.firstOrNull()?.thumbnailUrl
            }
            StorySlideType.TOP_ARTISTS_RANKED -> {
                recap.topArtists.firstOrNull()?.let { viewModel.getArtistProfileImage(it.artist) ?: it.thumbnailUrl }
            }
            StorySlideType.TOP_GENRES -> {
                recap.topSongs.firstOrNull()?.thumbnailUrl
            }
            StorySlideType.GRAND_SUMMARY -> {
                recap.topSongs.firstOrNull()?.thumbnailUrl
                    ?: recap.topArtists.firstOrNull()?.let { viewModel.getArtistProfileImage(it.artist) ?: it.thumbnailUrl }
            }
        }
    }

    /**
     * Extracts a dynamic Material 3 Expressive [RecapSlideTheme] from an image URL.
     * Uses downscaled Coil bitmap decoding and area-constrained AndroidX Palette generation (<5ms).
     */
    suspend fun extractSlideTheme(
        context: Context,
        artworkUrl: String?
    ): RecapSlideTheme {
        if (artworkUrl.isNullOrBlank()) return RecapSlideTheme.Default

        slideCache.get(artworkUrl)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(160, 160)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                val bitmap: Bitmap? = (result.drawable as? BitmapDrawable)?.bitmap
                    ?: result.drawable?.toBitmap()

                if (bitmap == null) return@withContext RecapSlideTheme.Default

                val palette = Palette.from(bitmap)
                    .maximumColorCount(24)
                    .resizeBitmapArea(6400)
                    .generate()

                val dominantInt = palette.getDominantColor(0xFF1A1A1A.toInt())

                // 1. Vibrant punchy accent
                val vibrantInt = palette.vibrantSwatch?.rgb
                    ?: palette.lightVibrantSwatch?.rgb
                    ?: palette.getVibrantColor(dominantInt)

                // 2. Dark Vibrant deep container tone
                val darkVibrantInt = palette.darkVibrantSwatch?.rgb
                    ?: palette.vibrantSwatch?.rgb?.let { rgb ->
                        val hsl = FloatArray(3)
                        ColorUtils.colorToHSL(rgb, hsl)
                        hsl[2] = (hsl[2] * 0.45f).coerceIn(0.12f, 0.32f)
                        ColorUtils.HSLToColor(hsl)
                    }
                    ?: palette.getDarkVibrantColor(dominantInt)

                // 3. Dark Muted
                val darkMutedInt = palette.darkMutedSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: dominantInt

                // Refine with HSL for impeccable readability & contrast
                val primaryHsl = FloatArray(3).apply { ColorUtils.colorToHSL(vibrantInt, this) }

                // Midnight obsidian background subtly tinted with dominant hue
                val bgHsl = floatArrayOf(
                    primaryHsl[0],
                    (primaryHsl[1] * 0.20f).coerceIn(0.06f, 0.14f),
                    0.10f
                )
                val background = Color(ColorUtils.HSLToColor(bgHsl))

                // Tonal surfaces
                val surfaceHsl = floatArrayOf(primaryHsl[0], (primaryHsl[1] * 0.22f).coerceIn(0.08f, 0.16f), 0.155f)
                val surface = Color(ColorUtils.HSLToColor(surfaceHsl))

                val surfaceContainerHsl = floatArrayOf(primaryHsl[0], (primaryHsl[1] * 0.24f).coerceIn(0.10f, 0.18f), 0.195f)
                val surfaceContainer = Color(ColorUtils.HSLToColor(surfaceContainerHsl))

                val surfaceContainerHighHsl = floatArrayOf(primaryHsl[0], (primaryHsl[1] * 0.26f).coerceIn(0.12f, 0.20f), 0.245f)
                val surfaceContainerHigh = Color(ColorUtils.HSLToColor(surfaceContainerHighHsl))

                val surfaceVariantHsl = floatArrayOf(primaryHsl[0], (primaryHsl[1] * 0.22f).coerceIn(0.09f, 0.16f), 0.225f)
                val surfaceVariant = Color(ColorUtils.HSLToColor(surfaceVariantHsl))

                // Primary vibrant color tuned for dark mode
                val vibrantPrimaryHsl = floatArrayOf(
                    primaryHsl[0],
                    primaryHsl[1].coerceAtLeast(0.70f),
                    0.68f
                )
                val primary = Color(ColorUtils.HSLToColor(vibrantPrimaryHsl))
                val onPrimary = if (primary.luminance() > 0.45f) Color(0xFF101216) else Color.White

                val primaryContainerHsl = floatArrayOf(
                    primaryHsl[0],
                    (primaryHsl[1] * 0.85f).coerceIn(0.55f, 0.90f),
                    0.28f
                )
                val primaryContainer = Color(ColorUtils.HSLToColor(primaryContainerHsl))
                val onPrimaryContainer = Color(ColorUtils.HSLToColor(floatArrayOf(primaryHsl[0], 0.85f, 0.94f)))

                val secondary = Color(darkVibrantInt)
                val onSecondary = if (secondary.luminance() > 0.45f) Color.Black else Color.White

                val tertiary = Color(darkMutedInt)
                val onTertiary = if (tertiary.luminance() > 0.45f) Color.Black else Color.White

                val cs = darkColorScheme(
                    primary = primary,
                    onPrimary = onPrimary,
                    primaryContainer = primaryContainer,
                    onPrimaryContainer = onPrimaryContainer,
                    secondary = secondary,
                    onSecondary = onSecondary,
                    tertiary = tertiary,
                    onTertiary = onTertiary,
                    background = background,
                    onBackground = Color(0xFFF1F0F4),
                    surface = surface,
                    onSurface = Color(0xFFF1F0F4),
                    surfaceVariant = surfaceVariant,
                    onSurfaceVariant = Color(0xFFF1F0F4).copy(alpha = 0.80f),
                    surfaceContainerLowest = Color(ColorUtils.HSLToColor(floatArrayOf(primaryHsl[0], 0.08f, 0.08f))),
                    surfaceContainerLow = surface,
                    surfaceContainer = surfaceContainer,
                    surfaceContainerHigh = surfaceContainerHigh,
                    surfaceContainerHighest = Color(ColorUtils.HSLToColor(floatArrayOf(primaryHsl[0], 0.18f, 0.28f))),
                    outline = Color.White.copy(alpha = 0.18f),
                    outlineVariant = Color.White.copy(alpha = 0.10f)
                )

                val theme = RecapSlideTheme(
                    primary = primary,
                    onPrimary = onPrimary,
                    primaryContainer = primaryContainer,
                    onPrimaryContainer = onPrimaryContainer,
                    secondary = secondary,
                    onSecondary = onSecondary,
                    secondaryContainer = surfaceContainer,
                    onSecondaryContainer = onPrimaryContainer,
                    tertiary = tertiary,
                    onTertiary = onTertiary,
                    background = background,
                    onBackground = Color(0xFFF1F0F4),
                    surface = surface,
                    onSurface = Color(0xFFF1F0F4),
                    surfaceVariant = surfaceVariant,
                    onSurfaceVariant = Color(0xFFF1F0F4).copy(alpha = 0.80f),
                    surfaceContainerLowest = Color(ColorUtils.HSLToColor(floatArrayOf(primaryHsl[0], 0.08f, 0.08f))),
                    surfaceContainerLow = surface,
                    surfaceContainer = surfaceContainer,
                    surfaceContainerHigh = surfaceContainerHigh,
                    surfaceContainerHighest = Color(ColorUtils.HSLToColor(floatArrayOf(primaryHsl[0], 0.18f, 0.28f))),
                    colorScheme = cs
                )

                slideCache.put(artworkUrl, theme)
                theme
            } catch (_: Exception) {
                RecapSlideTheme.Default
            }
        }
    }

    // =========================================================================
    // Legacy Compatibility API for LibraryScreen and RecapShareSheet
    // =========================================================================

    suspend fun extractSongPalette(context: Context, thumbnailUrl: String?, songId: String?): Palette? {
        if (thumbnailUrl.isNullOrBlank()) return null
        val cacheKey = songId ?: thumbnailUrl
        paletteCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(160, 160)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: result.drawable?.toBitmap()
                if (bitmap != null) {
                    val p = Palette.from(bitmap).resizeBitmapArea(6400).generate()
                    paletteCache.put(cacheKey, p)
                    p
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun extractArtistPalette(context: Context, artistImageUrl: String?): Palette? {
        return extractSongPalette(context, artistImageUrl, artistImageUrl)
    }

    fun create(
        songPalette: Palette? = null,
        artistPalette: Palette? = null,
        playlistPalette: Palette? = null,
        recap: PlayerSharedViewModel.ListeningRecap,
        timeRangeLabel: String = ""
    ): RecapDynamicTheme {
        val vibrant = songPalette?.vibrantSwatch?.rgb
            ?: artistPalette?.vibrantSwatch?.rgb
            ?: 0xFFD0BCFF.toInt()
        val primary = Color(vibrant)
        val secondary = Color(songPalette?.darkVibrantSwatch?.rgb ?: 0xFF381E72.toInt())
        val tertiary = Color(songPalette?.darkMutedSwatch?.rgb ?: 0xFF4A4458.toInt())

        return RecapDynamicTheme(
            primaryAccent = primary,
            secondaryAccent = secondary,
            tertiaryAccent = tertiary,
            onPrimaryAccent = if (primary.luminance() > 0.45f) Color.Black else Color.White,
            onSecondaryAccent = Color.White,
            cardSurface = Color(0xFF1B1D22),
            cardSurfaceHigh = Color(0xFF2B2D34)
        )
    }

    fun create(
        songPalette: Palette? = null,
        artistPalette: Palette? = null,
        seed: String = ""
    ): RecapDynamicTheme {
        val vibrant = songPalette?.vibrantSwatch?.rgb
            ?: artistPalette?.vibrantSwatch?.rgb
            ?: 0xFFD0BCFF.toInt()
        val primary = Color(vibrant)
        val secondary = Color(songPalette?.darkVibrantSwatch?.rgb ?: 0xFF381E72.toInt())
        val tertiary = Color(songPalette?.darkMutedSwatch?.rgb ?: 0xFF4A4458.toInt())

        return RecapDynamicTheme(
            primaryAccent = primary,
            secondaryAccent = secondary,
            tertiaryAccent = tertiary,
            onPrimaryAccent = if (primary.luminance() > 0.45f) Color.Black else Color.White,
            onSecondaryAccent = Color.White,
            cardSurface = Color(0xFF1B1D22),
            cardSurfaceHigh = Color(0xFF2B2D34)
        )
    }
}

/**
 * Composable hook to extract and remember the dynamic [RecapSlideTheme] for the active slide.
 */
@Composable
fun rememberRecapSlideTheme(
    slide: StorySlideType,
    recap: PlayerSharedViewModel.ListeningRecap,
    viewModel: PlayerSharedViewModel
): RecapSlideTheme {
    val context = LocalContext.current
    val artworkUrl = remember(slide, recap) {
        RecapThemeEngine.resolveArtworkUrlForSlide(slide, recap, viewModel)
    }
    var theme by remember(artworkUrl) {
        mutableStateOf(RecapThemeEngine.getCachedSlideTheme(artworkUrl) ?: RecapSlideTheme.Default)
    }

    LaunchedEffect(artworkUrl) {
        if (!artworkUrl.isNullOrBlank()) {
            theme = RecapThemeEngine.extractSlideTheme(context, artworkUrl)
        }
    }

    return theme
}
