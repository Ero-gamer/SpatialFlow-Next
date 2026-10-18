package com.codetrio.spatialflow.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexLyricsPlain

/**
 * Renders the lyrics-mode content area, dispatching to the appropriate sub-state:
 *
 * 1. **Synced lyrics** — [SyncedLyricsCompose] with seek & long-press-to-share.
 * 2. **Plain lyrics** — scrollable [Text] column with a [LyricsMetadataFooter].
 * 3. **Loading** — [LinearWavyProgressIndicator] + descriptive label.
 * 4. **Error** — [LyricsErrorState] with a retry callback.
 * 5. **Not loaded** — [LyricsErrorState] with a fetch callback.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerLyricsView(
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLyricsLoading: Boolean,
    lyricsError: Throwable?,
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    currentSong: SongItem?,
    contentColor: Color,
    dynamicAccentColor: Color,
    isPlaying: Boolean,
    effectivePositionProvider: () -> Int,
    onSeekTo: (Int) -> Unit,
    onRetryLyrics: () -> Unit,
    onFetchLyrics: () -> Unit,
    onLineLongClick: (LyricLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !syncedLyrics.isNullOrEmpty() -> {
            SyncedLyricsCompose(
                onSeekTo = onSeekTo,
                lyrics = syncedLyrics,
                currentPositionProvider = effectivePositionProvider,
                contentColor = contentColor,
                dynamicAccentColor = dynamicAccentColor,
                currentSong = currentSong,
                selectedProvider = selectedProvider,
                providerResults = providerResults,
                isPlayingProvider = { isPlaying },
                playbackSpeedProvider = { 1f },
                onLineLongClick = onLineLongClick,
                modifier = modifier.fillMaxSize()
            )
        }

        !plainLyrics.isNullOrBlank() -> {
            val isScriptRtl = androidx.compose.runtime.remember(plainLyrics) { isRtlText(plainLyrics) }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides (if (isScriptRtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr)
            ) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val h = size.height
                            if (h > 0f) {
                                val topFadePx = 54.dp.toPx().coerceAtMost(h * 0.15f)
                                val bottomFadePx = 76.dp.toPx().coerceAtMost(h * 0.22f)
                                val topRatio = (topFadePx / h).coerceIn(0f, 0.35f)
                                val bottomRatio = (1f - (bottomFadePx / h)).coerceIn(topRatio, 1f)
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        topRatio to Color.Black,
                                        bottomRatio to Color.Black,
                                        1.0f to Color.Transparent,
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 28.dp)
                    ) {
                        Text(
                            text = plainLyrics,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = GoogleSansFlexLyricsPlain,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 44.sp,
                                letterSpacing = (-0.2).sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            color = contentColor.copy(alpha = 0.9f)
                        )
                        LyricsMetadataFooter(
                            currentSong = currentSong,
                            selectedProvider = selectedProvider,
                            providerResults = providerResults,
                            contentColor = contentColor
                        )
                    }
                }
            }
        }

        isLyricsLoading -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(12.dp),
                    color = dynamicAccentColor
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Searching lyrics across providers...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }

        lyricsError != null -> {
            LyricsErrorState(
                message = lyricsError.message ?: "Lyrics not found",
                onRetry = onRetryLyrics,
            )
        }

        else -> {
            LyricsErrorState(
                message = "Lyrics are not loaded yet",
                onRetry = onFetchLyrics,
            )
        }
    }
}
