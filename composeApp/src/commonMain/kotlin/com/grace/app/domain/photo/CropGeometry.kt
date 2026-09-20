package com.grace.app.domain.photo

import kotlin.math.min

/**
 * Crop geometry, ported from `custom/BitmapCropTransformation.java:37-51`.
 *
 * `width = min(layoutWidth, bitmapWidth)`; if the viewport is shorter than the
 * bitmap the crop is bottom-anchored (`y = bitmapHeight - layoutHeight`),
 * otherwise the full height is kept.
 */
data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)

object CropGeometry {

    fun cropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        layoutWidth: Int,
        layoutHeight: Int
    ): CropRect {
        val width = min(layoutWidth, bitmapWidth)
        return if (layoutHeight < bitmapHeight) {
            CropRect(x = 0, y = bitmapHeight - layoutHeight, width = width, height = layoutHeight)
        } else {
            CropRect(x = 0, y = 0, width = width, height = bitmapHeight)
        }
    }
}
