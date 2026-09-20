package com.grace.app.domain.photo

import com.grace.app.core.GraceConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden tests for the watermark geometry in
 * `custom/BlessPhotoWorker.java:35-44` and `util/GracePhotoUtil.addBlessing`.
 */
class BlessGeometryTest {

    @Test
    fun landscapeWhenStrictlyWiderThanTall() {
        assertEquals(GraceConstants.LANDSCAPE_RESIZE, BlessGeometry.resizeType(4000, 3000))
    }

    @Test
    fun portraitWhenTallerOrSquare() {
        assertEquals(GraceConstants.PORTRAIT_RESIZE, BlessGeometry.resizeType(3000, 4000))
        assertEquals(GraceConstants.PORTRAIT_RESIZE, BlessGeometry.resizeType(3000, 3000))
    }

    @Test
    fun portraitWatermarkIsDrivenByWidth() {
        // width / WATERMARK_DIMENSIONS_FACTOR
        assertEquals(1000, BlessGeometry.watermarkSize(3000, 4000))
    }

    @Test
    fun landscapeWatermarkIsDrivenByHeight() {
        // height / WATERMARK_DIMENSIONS_FACTOR
        assertEquals(1000, BlessGeometry.watermarkSize(4000, 3000))
    }

    @Test
    fun squarePhotoCountsAsPortrait() {
        assertEquals(500, BlessGeometry.watermarkSize(1500, 1500))
    }

    @Test
    fun watermarkCornerMatchesTheOriginalFormula() {
        // photo 1200x1600 (portrait) -> watermark edge 400
        val photoWidth = 1200
        val photoHeight = 1600
        val watermark = BlessGeometry.watermarkSize(photoWidth, photoHeight)
        assertEquals(400, watermark)

        // x = wmW / 12
        assertEquals(400f / 12f, BlessGeometry.watermarkX(watermark))
        // y = H - wmH - wmH / 12
        assertEquals(1600f - 400f - 400f / 12f, BlessGeometry.watermarkY(photoHeight, watermark))
    }

    @Test
    fun watermarkStaysInsideThePhotoBounds() {
        val photoWidth = 1000
        val photoHeight = 2000
        val watermark = BlessGeometry.watermarkSize(photoWidth, photoHeight)
        val x = BlessGeometry.watermarkX(watermark)
        val y = BlessGeometry.watermarkY(photoHeight, watermark)

        assertTrue(x >= 0f)
        assertTrue(y >= 0f)
        assertTrue(x + watermark <= photoWidth)
        assertTrue(y + watermark <= photoHeight)
    }
}
