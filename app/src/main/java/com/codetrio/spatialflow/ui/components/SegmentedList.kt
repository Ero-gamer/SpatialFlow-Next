package com.codetrio.spatialflow.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.ui.theme.concentricInner
import androidx.compose.ui.unit.sp

enum class SegmentedItemPosition {
    Single,
    First,
    Middle,
    Last
}

val LocalSegmentedInGroup = compositionLocalOf { false }
val LocalSegmentedItemPosition = compositionLocalOf<SegmentedItemPosition?> { null }

val SegmentedLargeCorner: Dp = 24.dp
val SegmentedSmallCorner: Dp = 4.dp
val SegmentedItemGap: Dp = 2.dp
val SegmentedHorizontalPadding: Dp = 16.dp

fun segmentedItemShape(
    position: SegmentedItemPosition?,
    large: Dp = SegmentedLargeCorner,
    small: Dp = SegmentedSmallCorner,
): Shape {
    return when (position) {
        null, SegmentedItemPosition.Single -> RoundedCornerShape(large)
        SegmentedItemPosition.First -> RoundedCornerShape(
            topStart = large,
            topEnd = large,
            bottomStart = small,
            bottomEnd = small
        )
        SegmentedItemPosition.Middle -> RoundedCornerShape(small)
        SegmentedItemPosition.Last -> RoundedCornerShape(
            topStart = small,
            topEnd = small,
            bottomStart = large,
            bottomEnd = large
        )
    }
}

class SegmentedGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(visible: Boolean = true, content: @Composable () -> Unit) {
        if (visible) {
            items.add(content)
        }
    }
}

@Composable
fun SegmentedGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    horizontalPadding: Dp = SegmentedHorizontalPadding,
    content: @Composable SegmentedGroupScope.() -> Unit,
) {
    val scope = SegmentedGroupScope()
    scope.content()
    val itemCount = scope.items.size

    if (itemCount == 0) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    ) {
        if (title != null) {
            SegmentedGroupTitle(
                title = title,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SegmentedItemGap)
        ) {
            scope.items.forEachIndexed { index, itemContent ->
                val position = when {
                    itemCount == 1 -> SegmentedItemPosition.Single
                    index == 0 -> SegmentedItemPosition.First
                    index == itemCount - 1 -> SegmentedItemPosition.Last
                    else -> SegmentedItemPosition.Middle
                }
                CompositionLocalProvider(
                    LocalSegmentedInGroup provides true,
                    LocalSegmentedItemPosition provides position,
                ) {
                    itemContent()
                }
            }
        }
    }
}

@Composable
fun SegmentedGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 0.8.sp,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedRow(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    minHeight: Dp = 68.dp,
    contentPadding: Dp = 16.dp,
    leadingShape: Shape? = null,
    leadingBackground: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
) {
    val position = LocalSegmentedItemPosition.current
    val resolvedShape = shape ?: remember(position) { segmentedItemShape(position) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "segmentedRowScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = resolvedShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(
                    if (enabled && onClick != null) {
                        Modifier
                            .focusable()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                enabled = enabled,
                                onClick = onClick
                            )
                    } else {
                        Modifier
                    }
                )
                .alpha(if (enabled) 1f else 0.5f)
                .padding(horizontal = contentPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                val iconCorner = SegmentedLargeCorner.concentricInner(12.dp)
                val resolvedLeadingShape = leadingShape ?: RoundedCornerShape(iconCorner)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(resolvedLeadingShape)
                        .then(
                            if (leadingBackground != Color.Transparent) Modifier.background(leadingBackground) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                        leadingIcon()
                    }
                }
                Spacer(Modifier.width(14.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                ) {
                    title()
                }

                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    ProvideTextStyle(
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        subtitle()
                    }
                }
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(12.dp))
                Box(
                    contentAlignment = Alignment.CenterEnd
                ) {
                    trailingContent()
                }
            }
        }
    }
}
