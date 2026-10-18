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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.ui.explore.SpatialImageDefaults
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexDisplay

/**
 * 100% Mathematically Proportional Song Story Card Composable.
 * Matches user reference (IMG_20261015_201149_331.webp & IMG_20261015_201235_896.webp):
 * - 9:16 Vertical layout on fluid ambient blurred artwork background.
 * - Centered floating card with LOW OPACITY frosted glass (~0.52 alpha) instead of harsh solid black.
 * - Proportional scaling: All paddings, typography, artwork size, gaps, and corner radiuses
 *   scale strictly as a percentage of card width, guaranteeing 100% identical layout across
 *   any preview viewport and the 1080x1920 export canvas.
 */
@Composable
fun SongStoryCard(
    song: SongItem?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth.value
        val cardWidthDp = (containerWidth * 0.78f).dp
        val cardPadding = (containerWidth * 0.78f * 0.055f).dp
        val cardCorner = (containerWidth * 0.78f * 0.055f).dp
        val artworkCorner = (containerWidth * 0.78f * 0.040f).dp

        val titleFontSize = (containerWidth * 0.78f * 0.065f).sp
        val titleLineHeight = (titleFontSize.value * 1.22f).sp
        val artistFontSize = (containerWidth * 0.78f * 0.045f).sp
        val badgeFontSize = (containerWidth * 0.78f * 0.038f).sp
        val badgeIconSize = (containerWidth * 0.78f * 0.042f).dp

        val artToTitleGap = (containerWidth * 0.78f * 0.045f).dp
        val titleToArtistGap = (containerWidth * 0.78f * 0.015f).dp
        val artistToBadgeGap = (containerWidth * 0.78f * 0.032f).dp

        // Ambient Fluid Blur Background Layer
        AppleMusicBackground(
            song = song,
            modifier = Modifier.fillMaxSize()
        )

        // Centered Floating Card — Translucent Frosted Glass (Lower Opacity with Ambient Blur)
        val cardShape = RoundedCornerShape(cardCorner)
        val cardBgColor = Color(0xFF16161E).copy(alpha = 0.52f)

        Box(
            modifier = Modifier
                .width(cardWidthDp)
                .wrapContentHeight()
                .clip(cardShape)
                .background(cardBgColor)
                .padding(cardPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // 1:1 Album Artwork using SpatialImage with signature innerBorder
                SpatialImage(
                    model = song?.thumbnailUrl,
                    contentDescription = song?.title,
                    size = ImageSemanticSize.Cover,
                    shape = RoundedCornerShape(artworkCorner),
                    innerBorder = SpatialImageDefaults.innerBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(artToTitleGap))

                // Song Title (Proportionally Scaled with 2-line wrap protection)
                Text(
                    text = song?.title ?: "Unknown Track",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = titleFontSize,
                        lineHeight = titleLineHeight,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFlexDisplay
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(titleToArtistGap))

                // Artist Name (Proportionally Scaled)
                Text(
                    text = song?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = artistFontSize,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(artistToBadgeGap))

                // SpatialFlow Branding Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((containerWidth * 0.78f * 0.016f).dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_applogo),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(badgeIconSize)
                    )
                    Text(
                        text = "SpatialFlow",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = badgeFontSize,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
