package com.codetrio.spatialflow.ui.explore

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.codetrio.spatialflow.ui.components.LiquidGlassActionPill
import com.codetrio.spatialflow.ui.components.LiquidGlassPillIconButton
import com.codetrio.spatialflow.ui.components.rememberThrottledBackdrop
import com.codetrio.spatialflow.ui.components.throttledLayerBackdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val GenreCardBorderColors = listOf(
    Color.White.copy(alpha = 0.15f),
    Color.White.copy(alpha = 0.03f)
)

internal val GenreCardRadialColors = listOf(
    Color.White.copy(alpha = 0.10f),
    Color.Transparent
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    genresUiModels: List<GenreUiModel>,
    onBack: () -> Unit,
    onGenreClick: (title: String, browseId: String, params: String?) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 4 else 2

    val chunkedItems = remember(genresUiModels, columns) {
        genresUiModels.chunked(columns)
    }

    val surfaceColor = MaterialTheme.colorScheme.background
    val genresBackdrop = rememberThrottledBackdrop(surfaceColor)
    val topBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 68.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        if (genresUiModels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .throttledLayerBackdrop(genresBackdrop),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topBarPadding,
                    bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = chunkedItems,
                    key = { index, row -> "${index}_${row.firstOrNull()?.id ?: row.hashCode()}" }
                ) { _, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { genre ->
                            GenreCard(
                                genre = genre,
                                onClick = {
                                    onGenreClick(genre.title, genre.browseId, genre.params)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size < columns) {
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }

        // Floating Liquid Glass Action Pill Header (Sibling on top)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassActionPill(backdrop = genresBackdrop) {
                LiquidGlassPillIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GenreCard(
    genre: GenreUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = genre.title
    val imageUrl = genre.imageUrl
    val gradientColors = genre.gradientColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "GenreCardPressScale"
    )

    val cardGradientBrush = remember(gradientColors) {
        Brush.linearGradient(
            colors = listOf(
                gradientColors.getOrElse(0) { Color(0xFF1E1E1E) },
                gradientColors.getOrElse(1) { Color(0xFF121212) }
            )
        )
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .aspectRatio(1.6f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = cardGradientBrush)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = GenreCardRadialColors,
                            radius = 220f
                        )
                    )
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 18.sp
                ),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 14.dp, end = 64.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
                    .graphicsLayer {
                        rotationZ = -14f
                        shadowElevation = 12f
                        shape = RoundedCornerShape(10.dp)
                        clip = true
                    }
                    .size(68.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                SpatialImage(
                    model = imageUrl,
                    contentDescription = null,
                    size = ImageSemanticSize.GenreArtwork,
                    shape = RoundedCornerShape(10.dp),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
