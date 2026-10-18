package com.codetrio.spatialflow.util

import android.graphics.Matrix
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Utility to accurately convert Jetpack Compose [Shape]s and M3 Expressive [RoundedPolygon]s
 * into Android Canvas [android.graphics.Path] instances positioned and scaled precisely.
 */
object CanvasShapeProvider {

    /**
     * Converts an M3 Expressive [RoundedPolygon] (e.g. MaterialShapes.Cookie12Sided, MaterialShapes.Sunny)
     * into an [android.graphics.Path] positioned at [left], [top] with dimensions [width] x [height].
     * Completely non-composable and thread-safe for background Canvas rendering.
     */
    fun getPolygonPath(
        polygon: RoundedPolygon,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ): AndroidPath {
        val androidPath = polygon.toPath()

        val bounds = RectF()
        androidPath.computeBounds(bounds, true)

        val pathWidth = bounds.width()
        val pathHeight = bounds.height()

        if (pathWidth <= 0f || pathHeight <= 0f) {
            return androidPath
        }

        val pathCx = bounds.centerX()
        val pathCy = bounds.centerY()

        val targetCx = left + (width / 2f)
        val targetCy = top + (height / 2f)

        val scale = minOf(width / pathWidth, height / pathHeight)

        val matrix = Matrix()
        matrix.postTranslate(-pathCx, -pathCy)
        matrix.postScale(scale, scale)
        matrix.postTranslate(targetCx, targetCy)

        androidPath.transform(matrix)
        return androidPath
    }

    /**
     * Converts a Compose [Shape] into an [android.graphics.Path] positioned at [left], [top]
     * with dimensions [width] x [height].
     */
    fun getShapePath(
        shape: Shape,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        density: Density = Density(1f)
    ): AndroidPath {
        val outline = shape.createOutline(
            size = Size(width, height),
            layoutDirection = LayoutDirection.Ltr,
            density = density
        )

        val androidPath = AndroidPath()
        when (outline) {
            is Outline.Generic -> {
                androidPath.set(outline.path.asAndroidPath())
            }
            is Outline.Rounded -> {
                val rr = outline.roundRect
                val rectF = RectF(rr.left, rr.top, rr.right, rr.bottom)
                val radii = floatArrayOf(
                    rr.topLeftCornerRadius.x, rr.topLeftCornerRadius.y,
                    rr.topRightCornerRadius.x, rr.topRightCornerRadius.y,
                    rr.bottomRightCornerRadius.x, rr.bottomRightCornerRadius.y,
                    rr.bottomLeftCornerRadius.x, rr.bottomLeftCornerRadius.y
                )
                androidPath.addRoundRect(rectF, radii, AndroidPath.Direction.CW)
            }
            is Outline.Rectangle -> {
                val r = outline.rect
                androidPath.addRect(r.left, r.top, r.right, r.bottom, AndroidPath.Direction.CW)
            }
        }

        if (left != 0f || top != 0f) {
            val matrix = Matrix()
            matrix.postTranslate(left, top)
            androidPath.transform(matrix)
        }

        return androidPath
    }
}
