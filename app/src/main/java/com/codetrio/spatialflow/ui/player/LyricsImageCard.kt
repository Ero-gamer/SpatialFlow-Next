package com.codetrio.spatialflow.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.ui.explore.SpatialImageDefaults
import com.codetrio.spatialflow.ui.explore.resizeCDN
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 100% Authentic Apple Music Lyrics Share Card.
 * Matches official Apple Music design (media_1792073154317.webp):
 * - Single Unified Card: One sleek continuous rounded rectangle with dark frosted glass.
 * - Dynamic Typography: Lyrics scale proportionally based on card width and selected line count.
 * - Integrated Metadata: Squircle album artwork (with SpatialImage innerBorder) + Title + Artist + SpatialFlow badge.
 * - 100% Mathematically Proportional: Every element scales from cardWidth, guaranteeing identical layout
 *   between the in-app preview and exported Canvas bitmap.
 */
@Composable
fun LyricsImageCard(
    currentSong: SongItem?,
    selectedLines: List<LyricLine>,
    options: LyricsShareImageOptions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lyricsText = selectedLines.joinToString("\n") { it.content }

    // Palette Extraction for Static Theme
    var extractedColors by remember(currentSong?.thumbnailUrl) { mutableStateOf(ExtractedPaletteColors.Default) }
    LaunchedEffect(currentSong?.thumbnailUrl) {
        val thumbUrl = currentSong?.thumbnailUrl
        val vId = currentSong?.videoId
        if (!thumbUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val loader = context.imageLoader
                    val req = ImageRequest.Builder(context)
                        .data(thumbUrl.resizeCDN(120))
                        .size(120)
                        .allowHardware(false)
                        .build()
                    val drawable = loader.execute(req).drawable
                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null && vId != null) {
                        extractedColors = PlayerColorExtractor.extractFromBitmap(vId, bitmap)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val composeTextAlign = when (options.textAlign) {
        LyricsShareTextAlign.LEFT -> TextAlign.Left
        LyricsShareTextAlign.CENTER -> TextAlign.Center
        LyricsShareTextAlign.RIGHT -> TextAlign.Right
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(options.aspectRatio.ratioAspect)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (options.theme == LyricsShareTheme.STATIC) {
                    extractedColors.darkMuted
                } else {
                    Color.Black
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth.value
        val isStory = options.aspectRatio == LyricsShareAspectRatio.STORY_9_16

        // Proportional layout tokens
        val cardWidthFactor = if (isStory) 0.82f else 0.86f
        val cardWidthVal = containerWidth * cardWidthFactor
        val cardWidthDp = cardWidthVal.dp

        val cardPadding = (cardWidthVal * 0.065f).dp
        val cardCornerLarge = (cardWidthVal * 0.060f).dp
        val cardCornerSmall = (cardWidthVal * 0.015f).dp
        val connectedGap = (cardWidthVal * 0.008f).dp
        val artworkSize = (cardWidthVal * 0.155f).dp
        val artworkCorner = (cardWidthVal * 0.038f).dp

        val titleFontSize = (cardWidthVal * 0.050f).sp
        val artistFontSize = (cardWidthVal * 0.038f).sp
        val badgeFontSize = (cardWidthVal * 0.032f).sp
        val badgeIconSize = (cardWidthVal * 0.036f).dp

        // Dynamic typography scaling curve based on line count
        val lineCount = selectedLines.size.coerceAtLeast(1)
        val lyricsScale = when {
            lineCount <= 2 -> 0.075f
            lineCount <= 4 -> 0.062f
            lineCount <= 6 -> 0.052f
            lineCount <= 8 -> 0.044f
            lineCount <= 10 -> 0.038f
            else -> 0.034f
        }
        val responsiveFontSize = (cardWidthVal * lyricsScale).sp
        val responsiveLineHeight = (responsiveFontSize.value * 1.34f).sp

        // Fluid Apple Music Background Layer (Ambient Blur)
        if (options.theme == LyricsShareTheme.FLUID) {
            AppleMusicBackground(
                song = currentSong,
                modifier = Modifier.fillMaxSize()
            )
        }

        // M3 Expressive Connected Segment Colors
        val cardBgColor = if (options.theme == LyricsShareTheme.FLUID) {
            Color(0xFF141418).copy(alpha = 0.54f)
        } else {
            extractedColors.dominant.copy(alpha = 0.65f)
        }

        val topShape = RoundedCornerShape(
            topStart = cardCornerLarge,
            topEnd = cardCornerLarge,
            bottomStart = cardCornerSmall,
            bottomEnd = cardCornerSmall
        )

        val botShape = RoundedCornerShape(
            topStart = cardCornerSmall,
            topEnd = cardCornerSmall,
            bottomStart = cardCornerLarge,
            bottomEnd = cardCornerLarge
        )

        // ── M3 EXPRESSIVE CONNECTED SEGMENTED CARD ──
        Column(
            modifier = Modifier
                .width(cardWidthDp)
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(connectedGap)
        ) {
            // ── SEGMENT 1: LYRICS BOX (Connected Leading Shape) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(topShape)
                    .background(cardBgColor)
                    .padding(cardPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (lyricsText.isNotBlank()) lyricsText else "No lyrics selected",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = responsiveFontSize,
                        lineHeight = responsiveLineHeight,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFlexLyrics
                    ),
                    color = Color.White,
                    textAlign = composeTextAlign,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 16,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ── SEGMENT 2: METADATA BOX (Connected Trailing Shape) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(botShape)
                    .background(cardBgColor)
                    .padding(horizontal = cardPadding, vertical = (cardPadding.value * 0.70f).dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((cardWidthVal * 0.034f).dp)
                ) {
                    if (options.showArtwork && !currentSong?.thumbnailUrl.isNullOrBlank()) {
                        SpatialImage(
                            model = currentSong.thumbnailUrl,
                            contentDescription = currentSong.title,
                            size = ImageSemanticSize.ThumbnailSmall,
                            shape = RoundedCornerShape(artworkCorner),
                            innerBorder = SpatialImageDefaults.innerBorder(),
                            modifier = Modifier.size(artworkSize),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy((cardWidthVal * 0.008f).dp)
                    ) {
                        Text(
                            text = currentSong?.title ?: "Unknown Title",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = titleFontSize
                            ),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = currentSong?.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = artistFontSize
                            ),
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // SpatialFlow Branding Badge
                        if (options.showWatermark) {
                            Spacer(Modifier.height((cardWidthVal * 0.008f).dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy((cardWidthVal * 0.012f).dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_applogo),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.70f),
                                    modifier = Modifier.size(badgeIconSize)
                                )
                                Text(
                                    text = "SpatialFlow",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = badgeFontSize,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.70f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
