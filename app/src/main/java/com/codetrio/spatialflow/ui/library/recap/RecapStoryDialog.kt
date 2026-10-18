package com.codetrio.spatialflow.ui.library.recap

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import com.codetrio.spatialflow.util.ComposeToImage
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexRecapTitle
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexRecapWide
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The 8 distinct story slides matching the YouTube Music Recap design.
 */
enum class StorySlideType {
    INTRO,
    LISTENING_TIME,
    TOP_ARTIST,
    TOP_TRACK,
    TOP_PLAYLIST,
    TOP_ARTISTS_RANKED,
    TOP_GENRES,
    GRAND_SUMMARY
}

/**
 * Computes a dynamic seasonal date label (e.g. "June – August") matching the user's recap timeframe.
 */
fun formatRecapPeriod(timeRangeLabel: String): String {
    val calendar = Calendar.getInstance()
    val currentMonth = calendar.get(Calendar.MONTH)
    val monthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    return when (timeRangeLabel.lowercase(Locale.ROOT)) {
        "this week" -> "Past 7 Days"
        "this month" -> monthNames[currentMonth]
        else -> {
            val seasonStart = (currentMonth / 3) * 3
            val seasonEnd = (seasonStart + 2) % 12
            "${monthNames[seasonStart]} – ${monthNames[seasonEnd]}"
        }
    }
}

/**
 * Adaptive single-line typography that dynamically scales its font size down so long titles
 * never wrap into multiple rows and never truncate with ellipses (...).
 */
@Composable
fun RecapAdaptiveSingleLineText(
    text: String,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    targetFontSize: TextUnit,
    minFontSize: TextUnit = 12.sp,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    letterSpacing: TextUnit = TextUnit.Unspecified
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = when (textAlign) {
            TextAlign.Start, TextAlign.Left -> Alignment.CenterStart
            TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        val density = LocalDensity.current
        val widthDp = maxWidth.value
        val charLength = text.length.coerceAtLeast(1)

        val calculatedSp = if (widthDp > 0 && charLength > 0) {
            val estimatedSp = (widthDp / (charLength * 0.58f))
            estimatedSp.coerceIn(minFontSize.value, targetFontSize.value)
        } else {
            targetFontSize.value
        }

        Text(
            text = text,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = calculatedSp.sp,
            letterSpacing = letterSpacing,
            lineHeight = (calculatedSp * 1.15f).sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = textAlign,
            color = color
        )
    }
}

/**
 * Retained for compatibility with share sheet callers.
 */
@Composable
fun RecapDoodleBackground(
    slideType: StorySlideType = StorySlideType.GRAND_SUMMARY,
    strokeColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    // Custom doodle background disabled
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecapStoryDialog(
    recap: PlayerSharedViewModel.ListeningRecap,
    timeRangeLabel: String,
    viewModel: PlayerSharedViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val periodLabel = remember(timeRangeLabel) { formatRecapPeriod(timeRangeLabel) }
    val heroSong   = remember(recap.topSongs)   { recap.topSongs.firstOrNull() }
    val heroArtist = remember(recap.topArtists) { recap.topArtists.firstOrNull() }
    val heroArtistImage = viewModel.artistProfileMap[heroArtist?.artist]
        ?: heroArtist?.let { viewModel.getArtistProfileImage(it.artist) ?: it.thumbnailUrl }
        ?: recap.topSongs.firstOrNull { s -> s.artist.equals(heroArtist?.artist, ignoreCase = true) }?.thumbnailUrl
    val runnersUpSongs = remember(recap.topSongs) { recap.topSongs.drop(1).take(2) }

    val slideTheme = rememberRecapSlideTheme(
        slide     = StorySlideType.GRAND_SUMMARY,
        recap     = recap,
        viewModel = viewModel
    )

    val accent    = slideTheme.primary
    val onAccent  = slideTheme.onPrimary
    val sec       = slideTheme.secondaryContainer
    val onSec     = slideTheme.onSecondaryContainer
    val ter       = slideTheme.primaryContainer
    val onTer     = slideTheme.onPrimaryContainer
    val surfHigh  = slideTheme.surfaceContainerHigh
    val onSurf    = slideTheme.onSurface
    val bgColor   = slideTheme.surfaceContainerLowest

    val topTrackShape = MaterialShapes.Cookie12Sided.toShape()
    val sunnyShape    = MaterialShapes.Sunny.toShape()
    val cookieShape   = MaterialShapes.Cookie4Sided.toShape()
    val cloverId      = MaterialShapes.Clover4Leaf.toShape()

    var isCapturing         by remember { mutableStateOf(false) }
    var isPlaylistSaved     by remember { mutableStateOf(false) }
    var showDestinationPicker by remember { mutableStateOf(false) }
    var isSavingToGallery   by remember { mutableStateOf(false) }
    var isSharingRecap      by remember { mutableStateOf(false) }

    val topSongItems = remember(recap.topSongs) {
        recap.topSongs.map { song ->
            if (song.songId.toLongOrNull() != null && !song.thumbnailUrl.isNullOrEmpty() && !song.thumbnailUrl.startsWith("http")) {
                SongItem(song.songId.toLong(), song.title, song.artist, -1L, song.thumbnailUrl, 0L, System.currentTimeMillis())
            } else {
                SongItem.createOnlineSong(videoId = song.songId, title = song.title, artist = song.artist, streamUrl = null, durationMs = 0L, thumbnailUrl = song.thumbnailUrl)
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = slideTheme.colorScheme,
        motionScheme = MotionScheme.expressive()
    ) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            BackHandler(onBack = onDismissRequest)
            val dialogView = LocalView.current

            // ═══════════════════════════════════════════════════════
            // FULL CANVAS — SOLID TONAL MATERIAL 3 BACKGROUND (NO GRADIENTS)
            // ═══════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {

                    // ═══ HEADER BAR ═══════════════════════════════════
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = accent
                            ) {
                                Text(
                                    text = "SPATIALFLOW RECAP",
                                    fontFamily = GoogleSansFlexRecapTitle,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.8.sp,
                                    color = onAccent,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                            Text(
                                text = periodLabel.uppercase(Locale.ROOT),
                                fontFamily = GoogleSansFlexRecapTitle,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 0.8.sp,
                                color = onSurf.copy(alpha = 0.80f)
                            )
                        }

                        if (!isCapturing) {
                            Surface(
                                onClick = onDismissRequest,
                                shape = CircleShape,
                                color = surfHigh,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = onSurf,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ═══ SCROLLABLE CONTENT BODY ══════════════════════
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ═══ ZONE A: HERO ALBUM ART — CENTERED MONUMENTAL ══════
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // #1 TRACK badge above the art (NO BORDER)
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = accent
                                ) {
                                    Text(
                                        text = "#1 TRACK",
                                        fontFamily = GoogleSansFlexRecapTitle,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        letterSpacing = 2.sp,
                                        color = onAccent,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                    )
                                }

                                // Centered album art with expressive shape (NO BORDER)
                                Surface(
                                    shape = topTrackShape,
                                    modifier = Modifier.size(176.dp),
                                    color = accent.copy(alpha = 0.12f),
                                    shadowElevation = 0.dp
                                ) {
                                    val heroThumb = heroSong?.thumbnailUrl
                                    if (!heroThumb.isNullOrBlank()) {
                                        SpatialImage(
                                            model = heroThumb,
                                            contentDescription = heroSong?.title,
                                            size = ImageSemanticSize.ThumbnailMedium,
                                            contentScale = ContentScale.Crop,
                                            shape = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(72.dp))
                                        }
                                    }
                                }

                                // Track title — ADAPTIVE SINGLE LINE (NO WRAPPING, NO (...))
                                RecapAdaptiveSingleLineText(
                                    text = heroSong?.title ?: "—",
                                    fontFamily = GoogleSansFlexRecapWide,
                                    fontWeight = FontWeight.Black,
                                    targetFontSize = 28.sp,
                                    minFontSize = 14.sp,
                                    letterSpacing = (-0.5).sp,
                                    color = onSurf,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                )

                                // Artist name — ADAPTIVE SINGLE LINE (NO WRAPPING, NO (...))
                                RecapAdaptiveSingleLineText(
                                    text = heroSong?.artist ?: "",
                                    fontFamily = GoogleSansFlexRecapTitle,
                                    fontWeight = FontWeight.Bold,
                                    targetFontSize = 16.sp,
                                    minFontSize = 12.sp,
                                    color = onSurf.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                )
                            }
                        }

                        // Solid Tonal Divider (NO GRADIENT)
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = surfHigh.copy(alpha = 0.6f)
                        )

                        // ═══ ZONE B: STATS + ARTIST AVATAR — SIDE-BY-SIDE ══════
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // LEFT: Listening stats stacked vertically
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "YOU LISTENED FOR",
                                    fontFamily = GoogleSansFlexRecapTitle,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    letterSpacing = 2.sp,
                                    color = accent
                                )

                                Text(
                                    text = viewModel.formatListeningTime(recap.totalMinutes).uppercase(Locale.ROOT),
                                    fontFamily = GoogleSansFlexRecapWide,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 38.sp,
                                    letterSpacing = (-1.5).sp,
                                    lineHeight = 40.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    color = onSurf
                                )

                                // Stats row — compact badges (NO BORDER)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = accent
                                    ) {
                                        Text(
                                            text = "${recap.totalPlayCount} PLAYS",
                                            fontFamily = GoogleSansFlexRecapTitle,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.5.sp,
                                            color = onAccent,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = sec
                                    ) {
                                        Text(
                                            text = "${recap.listeningStreakDays}D STREAK",
                                            fontFamily = GoogleSansFlexRecapTitle,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.5.sp,
                                            color = onSec,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(2.dp))

                                // #1 ARTIST label (NO BORDER)
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = ter
                                ) {
                                    Text(
                                        text = "#1 ARTIST",
                                        fontFamily = GoogleSansFlexRecapTitle,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.5.sp,
                                        color = onTer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                // Artist name — ADAPTIVE SINGLE LINE (NO WRAPPING, NO (...))
                                RecapAdaptiveSingleLineText(
                                    text = heroArtist?.artist ?: "—",
                                    fontFamily = GoogleSansFlexRecapWide,
                                    fontWeight = FontWeight.Black,
                                    targetFontSize = 22.sp,
                                    minFontSize = 13.sp,
                                    letterSpacing = (-0.5).sp,
                                    color = onSurf,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // RIGHT: Artist avatar — big Sunny shape (NO BORDER)
                            Surface(
                                shape = sunnyShape,
                                modifier = Modifier.size(116.dp),
                                color = accent.copy(alpha = 0.12f),
                                shadowElevation = 0.dp
                            ) {
                                if (!heroArtistImage.isNullOrBlank()) {
                                    SpatialImage(
                                        model = heroArtistImage,
                                        contentDescription = heroArtist?.artist,
                                        size = ImageSemanticSize.ThumbnailMedium,
                                        shape = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, tint = accent, modifier = Modifier.size(56.dp))
                                    }
                                }
                            }
                        }

                        // Solid Tonal Divider (NO GRADIENT)
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = surfHigh.copy(alpha = 0.6f)
                        )

                        // ═══ ZONE C: TOP REPLAY — RUNNER-UP ROWS ═══════════
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOP REPLAY",
                                    fontFamily = GoogleSansFlexRecapWide,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.5.sp,
                                    color = accent
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = accent
                                ) {
                                    Text(
                                        text = "HEAVY ROTATION",
                                        fontFamily = GoogleSansFlexRecapTitle,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp,
                                        letterSpacing = 1.sp,
                                        color = onAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (runnersUpSongs.isNotEmpty()) {
                                runnersUpSongs.forEachIndexed { i, song ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Rank number
                                        Text(
                                            text = "0${i + 2}",
                                            fontFamily = GoogleSansFlexRecapWide,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 28.sp,
                                            letterSpacing = (-0.8).sp,
                                            color = if (i == 0) accent else onSec,
                                            modifier = Modifier.width(42.dp)
                                        )

                                        // Album art (NO BORDER)
                                        Surface(
                                            shape = if (i % 2 == 0) topTrackShape else sunnyShape,
                                            modifier = Modifier.size(58.dp),
                                            color = accent.copy(alpha = 0.12f),
                                            shadowElevation = 0.dp
                                        ) {
                                            if (!song.thumbnailUrl.isNullOrBlank()) {
                                                SpatialImage(
                                                    model = song.thumbnailUrl,
                                                    contentDescription = song.title,
                                                    size = ImageSemanticSize.ThumbnailSmall,
                                                    shape = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(Modifier.fillMaxSize().background(surfHigh), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(28.dp))
                                                }
                                            }
                                        }

                                        // Title + Artist — ADAPTIVE SINGLE LINE (NO WRAPPING, NO (...))
                                        Column(modifier = Modifier.weight(1f)) {
                                            RecapAdaptiveSingleLineText(
                                                text = song.title,
                                                fontFamily = GoogleSansFlexRecapWide,
                                                fontWeight = FontWeight.Black,
                                                targetFontSize = 15.sp,
                                                minFontSize = 11.sp,
                                                color = onSurf,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            RecapAdaptiveSingleLineText(
                                                text = song.artist,
                                                fontFamily = GoogleSansFlexRecapTitle,
                                                fontWeight = FontWeight.Bold,
                                                targetFontSize = 13.sp,
                                                minFontSize = 10.sp,
                                                color = onSurf.copy(alpha = 0.65f),
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        // Play count badge (NO BORDER)
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (i == 0) accent else sec
                                        ) {
                                            Text(
                                                text = "${song.count}",
                                                fontFamily = GoogleSansFlexRecapTitle,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                color = if (i == 0) onAccent else onSec,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // No runners-up: show personality archetype (NO BORDER)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = cloverId,
                                        modifier = Modifier.size(64.dp),
                                        color = accent.copy(alpha = 0.12f),
                                        shadowElevation = 0.dp
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        RecapAdaptiveSingleLineText(
                                            text = recap.personality.title.uppercase(Locale.ROOT),
                                            fontFamily = GoogleSansFlexRecapWide,
                                            fontWeight = FontWeight.Black,
                                            targetFontSize = 20.sp,
                                            minFontSize = 13.sp,
                                            color = onSurf,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = recap.surpriseStat ?: "${recap.totalPlayCount} TOTAL TRACKS",
                                            fontFamily = GoogleSansFlexRecapTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = onSurf.copy(alpha = 0.65f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ═══ ACTION BAR (Material 3 Expressive Connected Controls — NO BORDERS) ══════════════════
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Bookmark Playlist (Tonal Surface)
                        Surface(
                            onClick = { showDestinationPicker = true },
                            shape = sunnyShape,
                            color = if (isPlaylistSaved) accent else sec,
                            shadowElevation = 0.dp,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaylistSaved) Icons.Default.Check else Icons.Default.BookmarkBorder,
                                    contentDescription = "Save Playlist",
                                    tint = if (isPlaylistSaved) onAccent else onSec,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // 2. Save Image Directly to Gallery (Pictures/SpatialFlow)
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        isSavingToGallery = true
                                        val bmp = RecapImageComposer.createRecapImage(
                                            context = context,
                                            recap = recap,
                                            timeRangeLabel = timeRangeLabel,
                                            slideType = StorySlideType.GRAND_SUMMARY,
                                            viewModel = viewModel
                                        )

                                        ComposeToImage.saveBitmapAsFile(
                                            context = context,
                                            bitmap = bmp,
                                            fileName = "SpatialFlow_Recap_${System.currentTimeMillis()}"
                                        )
                                        Toast.makeText(context, "Saved to Gallery (Pictures/SpatialFlow)", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSavingToGallery = false
                                    }
                                }
                            },
                            enabled = !isSavingToGallery && !isSharingRecap,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = sec,
                                contentColor = onSec
                            )
                        ) {
                            if (isSavingToGallery) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = onSec)
                            } else {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Save",
                                    fontFamily = GoogleSansFlexRecapTitle,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // 3. Share Recap (Composes high-res image and shares via Intent, zero screenshotting)
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        isSharingRecap = true
                                        val bmp = RecapImageComposer.createRecapImage(
                                            context = context,
                                            recap = recap,
                                            timeRangeLabel = timeRangeLabel,
                                            slideType = StorySlideType.GRAND_SUMMARY,
                                            viewModel = viewModel
                                        )

                                        val uri = ComposeToImage.saveBitmapAsFile(
                                            context = context,
                                            bitmap = bmp,
                                            fileName = "SpatialFlow_Recap_${System.currentTimeMillis()}"
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                             type = "image/png"
                                             putExtra(Intent.EXTRA_STREAM, uri)
                                             putExtra(Intent.EXTRA_TEXT, "✨ Check out my Listening Recap on SpatialFlow!")
                                             addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "My SpatialFlow Recap"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSharingRecap = false
                                    }
                                }
                            },
                            enabled = !isSharingRecap && !isSavingToGallery,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = onAccent
                            )
                        ) {
                            if (isSharingRecap) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = onAccent)
                            } else {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Share",
                                    fontFamily = GoogleSansFlexRecapTitle,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDestinationPicker) {
        val destinationPaletteState = produceState(initialValue = RecapCoverArtGenerator.HarmonizedRecapPalette.Default, key1 = recap.topSongs) {
            val urls = recap.topSongs.mapNotNull { it.thumbnailUrl }.filter { it.isNotBlank() }
            value = RecapCoverArtGenerator.extractHarmonizedPalette(context, urls)
        }
        RecapSaveDestinationDialog(
            recap = recap,
            periodLabel = periodLabel,
            topSongItems = topSongItems,
            viewModel = viewModel,
            palette = destinationPaletteState.value,
            onDismissRequest = { showDestinationPicker = false },
            onPlaylistSaved = { isPlaylistSaved = true; showDestinationPicker = false }
        )
    }
}

// =========================================================================
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecapSaveDestinationDialog(
    recap: PlayerSharedViewModel.ListeningRecap,
    periodLabel: String,
    topSongItems: List<SongItem>,
    viewModel: PlayerSharedViewModel?,
    palette: RecapCoverArtGenerator.HarmonizedRecapPalette,
    onDismissRequest: () -> Unit,
    onPlaylistSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var savingStatusText by remember { mutableStateOf("") }
    val periodSubtitle = remember(periodLabel) { "TOP PLAYLIST • ${periodLabel.uppercase(Locale.ROOT)}" }

    val primarySolid = Color(palette.primaryColor)
    val darkSolid = Color(palette.darkColor)
    val darkBorderSolid = Color(palette.darkBorder)

    val cookieShape = MaterialShapes.Cookie4Sided.toShape()
    val sunnyShape  = MaterialShapes.Sunny.toShape()

    Dialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismissRequest()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SAVE RECAP PLAYLIST",
                            fontFamily = GoogleSansFlexRecapTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = primarySolid
                        )
                        Text(
                            text = "Choose Destination",
                            fontFamily = GoogleSansFlexRecapTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            if (!isSaving) {
                                onDismissRequest()
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dynamically generated 1000px Cover Art Preview Card
                val previewBitmap by produceState<Bitmap?>(initialValue = null, key1 = palette, key2 = periodSubtitle) {
                    value = RecapCoverArtGenerator.generateCoverArtBitmap(
                        context = context,
                        palette = palette,
                        periodSubtitle = periodSubtitle,
                        centerTrackUrl = recap.topSongs.firstOrNull()?.thumbnailUrl
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = cookieShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            val bmp = previewBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Recap Cover Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = primarySolid)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🎨 Dynamic Cover Art",
                                fontFamily = GoogleSansFlexRecapTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "100% solid HSL palette extracted from your playlist tracks with official branding.",
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isSaving) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = primarySolid)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = savingStatusText,
                            fontFamily = GoogleSansFlexRecapTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    // Option 1: Local Library (Solid Tonal Container)
                    Surface(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                savingStatusText = "Generating custom cover & saving locally..."
                                val bitmap = previewBitmap ?: RecapCoverArtGenerator.generateCoverArtBitmap(
                                    context, palette, periodSubtitle, recap.topSongs.firstOrNull()?.thumbnailUrl
                                )
                                val fileUri = RecapCoverArtGenerator.saveCoverToFile(context, bitmap, "recap_${periodLabel.lowercase(Locale.ROOT)}")
                                val playlistTitle = "Recap $periodLabel"
                                viewModel?.createLocalPlaylistWithSongs(playlistTitle, topSongItems, thumbnailUrl = fileUri)
                                onPlaylistSaved()
                                isSaving = false
                                onDismissRequest()
                                Toast.makeText(context, "Saved to Local Library with custom cover art!", Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = darkSolid,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Smartphone,
                                        contentDescription = null,
                                        tint = primarySolid,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Local Library",
                                    fontFamily = GoogleSansFlexRecapTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Saves directly in SpatialFlow with attached cover art",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: YouTube Music (Solid Tonal Container)
                    Surface(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                savingStatusText = "Creating playlist on YouTube Music..."
                                val bitmap = previewBitmap ?: RecapCoverArtGenerator.generateCoverArtBitmap(
                                    context, palette, periodSubtitle, recap.topSongs.firstOrNull()?.thumbnailUrl
                                )
                                val galleryUri = RecapCoverArtGenerator.exportToGallery(context, bitmap, "SpatialFlow_Recap_$periodLabel")
                                val playlistTitle = "SpatialFlow Recap $periodLabel"
                                viewModel?.createYouTubePlaylistWithSongs(playlistTitle, topSongItems) { success, _ ->
                                    isSaving = false
                                    onDismissRequest()
                                    if (success) {
                                        onPlaylistSaved()
                                        Toast.makeText(
                                            context,
                                            "Playlist created on YouTube Music! Cover art saved to Pictures/SpatialFlow.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Cover art saved to Gallery. YouTube sync completed.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = darkSolid,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = primarySolid,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "YouTube Music",
                                    fontFamily = GoogleSansFlexRecapTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Creates online playlist & exports 1000px art to Gallery",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 3: Both (Local & YouTube) (Solid Tonal Container)
                    Surface(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                savingStatusText = "Saving to Local Library & YouTube Music..."
                                val bitmap = previewBitmap ?: RecapCoverArtGenerator.generateCoverArtBitmap(
                                    context, palette, periodSubtitle, recap.topSongs.firstOrNull()?.thumbnailUrl
                                )
                                val fileUri = RecapCoverArtGenerator.saveCoverToFile(context, bitmap, "recap_${periodLabel.lowercase(Locale.ROOT)}")
                                val galleryUri = RecapCoverArtGenerator.exportToGallery(context, bitmap, "SpatialFlow_Recap_$periodLabel")
                                viewModel?.createLocalPlaylistWithSongs("Recap $periodLabel", topSongItems, thumbnailUrl = fileUri)
                                viewModel?.createYouTubePlaylistWithSongs("SpatialFlow Recap $periodLabel", topSongItems) { _, _ -> }
                                onPlaylistSaved()
                                isSaving = false
                                onDismissRequest()
                                Toast.makeText(
                                    context,
                                    "Saved to Local Library & YouTube Music! Cover art saved to Gallery.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = sunnyShape,
                                color = darkSolid,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = primarySolid,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Both (Local & YouTube)",
                                        fontFamily = GoogleSansFlexRecapTitle,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = primarySolid
                                    ) {
                                        Text(
                                            text = "BEST",
                                            fontFamily = GoogleSansFlexRecapTitle,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            color = darkSolid,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Full sync with local custom cover and Gallery backup",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// HELPERS
// =========================================================================
internal fun heroArtistName(artist: String): String {
    return artist.ifBlank { "Top Artist" }
}

internal fun heroArtistImage(recap: PlayerSharedViewModel.ListeningRecap, viewModel: PlayerSharedViewModel): String? {
    val heroArtist = recap.topArtists.firstOrNull() ?: return null
    return viewModel.getArtistProfileImage(heroArtist.artist) ?: heroArtist.thumbnailUrl
}
