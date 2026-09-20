package com.grace.app.domain.photo

import com.grace.app.core.GraceConstants

/**
 * Watermark geometry, ported from `custom/BlessPhotoWorker.java:35-44` and
 * `util/GracePhotoUtil.java:129-136, 251-253`.
 */
object BlessGeometry {

    /**
     * `getResizeType`: landscape when the photo is strictly wider than tall,
     * portrait otherwise (a square counts as portrait).
     */
    fun resizeType(photoWidth: Int, photoHeight: Int): Int =
        if (photoWidth > photoHeight) GraceConstants.LANDSCAPE_RESIZE else GraceConstants.PORTRAIT_RESIZE

    /**
     * Square watermark edge length:
     *  - portrait  → `photoWidth / WATERMARK_DIMENSIONS_FACTOR`
     *  - landscape → `photoHeight / WATERMARK_DIMENSIONS_FACTOR`
     */
    fun watermarkSize(photoWidth: Int, photoHeight: Int): Int =
        if (resizeType(photoWidth, photoHeight) == GraceConstants.PORTRAIT_RESIZE) {
            photoWidth / GraceConstants.WATERMARK_DIMENSIONS_FACTOR
        } else {
            photoHeight / GraceConstants.WATERMARK_DIMENSIONS_FACTOR
        }

    /** `addBlessing`: `watermarkWidth / marginFactor`. */
    fun watermarkX(watermarkWidth: Int, marginFactor: Int = GraceConstants.MARGIN_FACTOR): Float =
        watermarkWidth.toFloat() / marginFactor

    /** `addBlessing`: `photoHeight - watermarkHeight - watermarkHeight / marginFactor`. */
    fun watermarkY(
        photoHeight: Int,
        watermarkHeight: Int,
        marginFactor: Int = GraceConstants.MARGIN_FACTOR
    ): Float = photoHeight - (watermarkHeight + watermarkHeight.toFloat() / marginFactor)
}
