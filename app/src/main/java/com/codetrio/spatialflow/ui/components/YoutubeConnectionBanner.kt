package com.codetrio.spatialflow.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.util.ConnectionStatus

/**
 * YouTube-style connection status text (No inset box/banner).
 * Pure dynamic text that animates when offline or back online, lifting the navigation bar.
 */
@Composable
fun YoutubeConnectionBanner(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val isVisible = status != ConnectionStatus.Hidden

    // Dynamic Material 3 Expressive color tokens directly for text (No inset container box)
    val targetTextColor = when (status) {
        ConnectionStatus.Offline -> MaterialTheme.colorScheme.error
        ConnectionStatus.BackOnline -> MaterialTheme.colorScheme.primary
        ConnectionStatus.Hidden -> Color.Transparent
    }

    val animatedTextColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "BannerTextColor"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(
            animationSpec = tween(durationMillis = 200)
        ),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeOut(
            animationSpec = tween(durationMillis = 180)
        ),
        modifier = modifier
    ) {
        val bannerText = if (status == ConnectionStatus.BackOnline) "Back online" else "No connection"

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = bannerText
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    if (targetState == ConnectionStatus.BackOnline) {
                        (slideInVertically(
                            initialOffsetY = { height -> height / 2 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(animationSpec = tween(220)))
                            .togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { height -> -height / 2 },
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                ) + fadeOut(animationSpec = tween(220))
                            )
                    } else {
                        (slideInVertically(
                            initialOffsetY = { height -> -height / 2 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(animationSpec = tween(220)))
                            .togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { height -> height / 2 },
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                ) + fadeOut(animationSpec = tween(220))
                            )
                    }
                },
                label = "ConnectionBannerText"
            ) { currentStatus ->
                val text = if (currentStatus == ConnectionStatus.BackOnline) "Back online" else "No connection"
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = animatedTextColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
