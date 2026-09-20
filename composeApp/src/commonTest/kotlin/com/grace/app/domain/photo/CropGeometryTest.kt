package com.grace.app.domain.photo

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests `custom/BitmapCropTransformation.java:37-51`.
 */
class CropGeometryTest {

    @Test
    fun bottomAnchoredCropWhenViewportIsShorterThanBitmap() {
        // bitmap 1000x2000, viewport 1080x1200 -> width min(1080,1000)=1000,
        // height 1200, y = 2000-1200 = 800
        val rect = CropGeometry.cropRect(
            bitmapWidth = 1000,
            bitmapHeight = 2000,
            layoutWidth = 1080,
            layoutHeight = 1200
        )
        assertEquals(CropRect(x = 0, y = 800, width = 1000, height = 1200), rect)
    }

    @Test
    fun fullHeightKeptWhenViewportIsTallerThanBitmap() {
        val rect = CropGeometry.cropRect(
            bitmapWidth = 800,
            bitmapHeight = 600,
            layoutWidth = 1080,
            layoutHeight = 1200
        )
        assertEquals(CropRect(x = 0, y = 0, width = 800, height = 600), rect)
    }

    @Test
    fun widthNeverExceedsTheBitmap() {
        val rect = CropGeometry.cropRect(
            bitmapWidth = 400,
            bitmapHeight = 900,
            layoutWidth = 1080,
            layoutHeight = 500
        )
        assertEquals(400, rect.width)
        assertEquals(500, rect.height)
        assertEquals(400, rect.y)
    }

    @Test
    fun equalHeightsDoNotCrop() {
        val rect = CropGeometry.cropRect(
            bitmapWidth = 1000,
            bitmapHeight = 800,
            layoutWidth = 1000,
            layoutHeight = 800
        )
        assertEquals(CropRect(x = 0, y = 0, width = 1000, height = 800), rect)
    }
}
