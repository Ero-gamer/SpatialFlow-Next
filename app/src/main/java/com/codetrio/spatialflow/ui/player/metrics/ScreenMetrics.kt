package com.codetrio.spatialflow.ui.player.metrics

import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

/**
 * Responsive Screen Metrics data class calculating dynamic UI dimensions, font scales,
 * and layout gap multipliers across portrait AND landscape, for phones, tablets & foldables.
 *
 * Device size buckets are keyed on smallestScreenWidthDp so a phone stays classified as a
 * phone in landscape (widthDp would otherwise balloon past the tablet threshold). Two-pane
 * layouts are driven by [isLandscape] || [isTablet], not by isTablet alone.
 */
@Immutable
data class ScreenMetrics(
    val screenWidth: Dp,
    val screenHeight: Dp,
    val isLandscape: Boolean,      // orientation == landscape
    val isSmallPhone: Boolean,     // < 360dp
    val isNormalPhone: Boolean,    // 360-412dp
    val isLargePhone: Boolean,     // 412-480dp
    val isTablet: Boolean,         // > 480dp / tablet
    
    val marginHorizontal: Dp,      // 12dp, 16dp, 18dp, 20dp
    val albumArtSize: Dp,          // 240dp..360dp
    val topGap: Dp,                // 12dp..20dp
    val albumGap: Dp,              // 16dp..32dp
    val metadataGap: Dp,           // 8dp..16dp
    val chipGap: Dp,               // 12dp..20dp
    val seekbarGap: Dp,            // 12dp..20dp
    val buttonGap: Dp,             // 8dp..16dp
    
    val buttonHeight: Dp,          // 64dp, 72dp, 80dp
    val playIconSize: Dp,          // 36dp, 40dp, 44dp
    val skipIconSize: Dp,          // 28dp, 32dp, 36dp
    
    val titleFontSize: TextUnit,   // 18sp, 20sp, 22sp
    val artistFontSize: TextUnit,  // 12sp, 13sp, 14sp
    
    val chipHeight: Dp,            // 28dp, 32dp, 36dp
    val chipSpacing: Dp,           // 6dp, 8dp, 10dp
    val maxVisibleChips: Int       // 3, 5, 6
)

@Composable
fun computeScreenMetrics(): ScreenMetrics {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
    val cutoutHeightPx = WindowInsets.displayCutout.getTop(density)
    val navBarHeightPx = WindowInsets.navigationBars.getBottom(density)
    
    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
        configuration.orientation,
        density.density,
        density.fontScale,
        statusBarHeightPx,
        cutoutHeightPx,
        navBarHeightPx
    ) {
        val widthDp = configuration.screenWidthDp.dp
        val heightDp = configuration.screenHeightDp.dp
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Device class is based on the *smallest* width so a phone stays a phone in landscape
        // (screenWidthDp would otherwise balloon past the tablet threshold when rotated).
        val swDp = configuration.smallestScreenWidthDp
        val isSmall = swDp < 360
        val isNormal = swDp >= 360 && swDp < 412
        val isLarge = swDp >= 412 && swDp < 600
        val isTablet = swDp >= 600

        val scaleFactor = ((widthDp.value - 360f) / 120f).coerceIn(0f, 1f)
        val spacingMultiplier = 0.80f + (scaleFactor * 0.55f)
        
        val marginH = when {
            isSmall -> 12.dp
            isNormal -> 16.dp
            isLarge -> 18.dp
            else -> 20.dp
        }
        
        val statusBarHeight = with(density) { statusBarHeightPx.toDp() }
        val cutoutHeight = with(density) { cutoutHeightPx.toDp() }
        val navBarHeight = with(density) { navBarHeightPx.toDp() }
        val safeTop = maxOf(statusBarHeight, cutoutHeight) + 8.dp
        
        val reservedControlsHeight = heightDp * 0.40f
        val maxArtworkWidth = widthDp - (marginH * 2)
        val twoPane = isLandscape
        val computedAlbumArtSize = if (twoPane) {
            // Two-pane (landscape): artwork lives in the LEFT pane, vertically centered across
            // the full area of the screen (header was removed in landscape).
            // Budget leaves room for status/nav bars + vertical margins (8dp * 2 = 16dp) and breathing room.
            val paneArtworkHeight = (heightDp - statusBarHeight - navBarHeight - 24.dp).coerceAtLeast(240.dp)
            val paneArtworkWidth = (widthDp * 0.5f) - (marginH * 2)
            min(paneArtworkHeight, paneArtworkWidth).coerceIn(240.dp, 420.dp)
        } else {
            val availableArtworkHeight = (heightDp - safeTop - navBarHeight - reservedControlsHeight - 60.dp).coerceAtLeast(240.dp)
            min(availableArtworkHeight, maxArtworkWidth).coerceIn(240.dp, if (isTablet) 460.dp else 400.dp)
        }
        
        // Landscape phones are height-constrained: the right pane must fit metadata + seek bar +
        // buttons without overflowing, so cap button height lower than the portrait/tablet sizes.
        val btnHeight = when {
            isLandscape && !isTablet -> 56.dp
            isSmall -> 64.dp
            isNormal -> 72.dp
            else -> 80.dp
        }
        
        val playIcon = when {
            isSmall -> 36.dp
            isNormal -> 40.dp
            else -> 44.dp
        }
        
        val skipIcon = when {
            isSmall -> 28.dp
            isNormal -> 32.dp
            else -> 36.dp
        }
        
        val titleSize = when {
            isSmall -> 26.sp
            isNormal -> 28.sp
            else -> 26.sp
        }
        
        val artistSize = when {
            isSmall -> 20.sp
            isNormal -> 24.sp
            else -> 22.sp
        }
        
        val (chipH, chipSpace, maxChips) = when {
            isSmall -> Triple(28.dp, 6.dp, 3)
            isNormal -> Triple(32.dp, 8.dp, 5)
            else -> Triple(36.dp, 10.dp, 6)
        }
        
        ScreenMetrics(
            screenWidth = widthDp,
            screenHeight = heightDp,
            isLandscape = isLandscape,
            isSmallPhone = isSmall,
            isNormalPhone = isNormal,
            isLargePhone = isLarge,
            isTablet = isTablet,
            marginHorizontal = marginH,
            albumArtSize = computedAlbumArtSize,
            topGap = (16.dp * spacingMultiplier).coerceIn(12.dp, 20.dp),
            albumGap = (24.dp * spacingMultiplier).coerceIn(16.dp, 32.dp),
            metadataGap = (12.dp * spacingMultiplier).coerceIn(8.dp, 16.dp),
            chipGap = (16.dp * spacingMultiplier).coerceIn(12.dp, 20.dp),
            seekbarGap = (16.dp * spacingMultiplier).coerceIn(12.dp, 20.dp),
            buttonGap = (12.dp * spacingMultiplier).coerceIn(8.dp, 16.dp),
            buttonHeight = btnHeight,
            playIconSize = playIcon,
            skipIconSize = skipIcon,
            titleFontSize = titleSize,
            artistFontSize = artistSize,
            chipHeight = chipH,
            chipSpacing = chipSpace,
            maxVisibleChips = maxChips
        )
    }
}

val LocalScreenMetrics = compositionLocalOf<ScreenMetrics> {
    error("LocalScreenMetrics not provided")
}
