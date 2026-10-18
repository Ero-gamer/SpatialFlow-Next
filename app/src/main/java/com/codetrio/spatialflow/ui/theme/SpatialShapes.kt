package com.codetrio.spatialflow.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.UnevenRoundedRectangle

/**
 * SpatialShapes provides iOS-inspired G²-continuous smooth corner shapes (Squircles).
 * These continuous curves seamlessly blend into straight edges, creating an organic,
 * high-fidelity liquid glass aesthetic across SpatialFlow.
 */
@Stable
object SpatialShapes {
    /** Smooth pill / capsule shape for floating action buttons and tabs */
    val Pill = Capsule(style = RoundedCornerStyle.Continuous)

    /** Extra-small squircle for badges, tags, and chips (6dp) */
    val ExtraSmall = RoundedRectangle(6.dp, style = RoundedCornerStyle.Continuous)

    /** Small squircle for small icons, mini badges, and compact items (8dp) */
    val Small = RoundedRectangle(8.dp, style = RoundedCornerStyle.Continuous)

    /** Medium squircle for standard list thumbnails and category tiles (14dp) */
    val Medium = RoundedRectangle(14.dp, style = RoundedCornerStyle.Continuous)

    /** Medium-Large squircle for grid items, album art, and buttons (16dp) */
    val MediumLarge = RoundedRectangle(16.dp, style = RoundedCornerStyle.Continuous)

    /** Large squircle for cards, dialog options, and hero components (20dp) */
    val Large = RoundedRectangle(20.dp, style = RoundedCornerStyle.Continuous)

    /** Extra-Large squircle for main album artwork and hero cards (28dp) */
    val ExtraLarge = RoundedRectangle(28.dp, style = RoundedCornerStyle.Continuous)

    /** Floating navigation bar capsule shape (32dp) */
    val FloatingBar = RoundedRectangle(32.dp, style = RoundedCornerStyle.Continuous)

    /** Top sheet / modal bottom sheet continuous rounded shape (28dp) */
    val SheetTop = UnevenRoundedRectangle(topStart = 28.dp, topEnd = 28.dp, style = RoundedCornerStyle.Continuous)

    /** Helper function to create custom continuous squircle shapes */
    fun squircle(size: Dp) = RoundedRectangle(size, style = RoundedCornerStyle.Continuous)

    /** Helper function for custom asymmetric squircle corners */
    fun squircle(
        topStart: Dp = 0.dp,
        topEnd: Dp = 0.dp,
        bottomEnd: Dp = 0.dp,
        bottomStart: Dp = 0.dp
    ) = UnevenRoundedRectangle(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart,
        style = RoundedCornerStyle.Continuous
    )

    /**
     * Concentric Corner Radius rule (Material Design 3 & Geometry):
     * Inner Radius = Outer Radius - Padding
     *
     * When two nested shapes have concentric corners, their corner arcs share the
     * exact same center of curvature, eliminating awkward visual pinching.
     * Clamps to 0.dp if the padding exceeds the outer radius.
     */
    fun concentric(outerRadius: Dp, padding: Dp): Dp = (outerRadius - padding).coerceAtLeast(0.dp)

    /**
     * Creates a continuous concentric squircle shape for nested content inside an outer container.
     */
    fun concentricSquircle(outerRadius: Dp, padding: Dp) = squircle(concentric(outerRadius, padding))

    /**
     * Creates a standard concentric RoundedCornerShape for nested content inside an outer container.
     */
    fun concentricRounded(outerRadius: Dp, padding: Dp) = androidx.compose.foundation.shape.RoundedCornerShape(concentric(outerRadius, padding))
}

/**
 * Extension on Dp to easily derive concentric inner radii anywhere in Compose:
 * `val innerCorner = outerCorner.concentricInner(padding)`
 */
fun Dp.concentricInner(padding: Dp): Dp = (this - padding).coerceAtLeast(0.dp)

