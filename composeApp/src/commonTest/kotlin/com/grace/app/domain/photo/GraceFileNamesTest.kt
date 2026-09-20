package com.grace.app.domain.photo

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the file-naming rules from `util/GracePhotoUtil.java:39-67` and `:101`.
 */
class GraceFileNamesTest {

    private val fixedTime = LocalDateTime(2026, 9, 16, 7, 5, 3)

    @Test
    fun timestampUsesTheOriginalPattern() {
        assertEquals("20260916_070503", GraceFileNames.timestamp(fixedTime))
    }

    @Test
    fun timestampZeroPadsEveryField() {
        assertEquals("20240101_000000", GraceFileNames.timestamp(LocalDateTime(2024, 1, 1, 0, 0, 0)))
    }

    @Test
    fun cameraFileMatchesTheOriginalPrefixAndExtension() {
        assertEquals("Grace_Photo_20260916_070503.jpg", GraceFileNames.cameraFileName(fixedTime))
    }

    @Test
    fun blessedFileMatchesTheOriginalPrefixAndExtension() {
        assertEquals(
            "Grace_Blessed_Photo_20260916_070503.jpg",
            GraceFileNames.blessedFileName(fixedTime)
        )
    }

    @Test
    fun pickedFileUsesMillisAndExtension() {
        assertEquals("picked_1758000000000.jpg", GraceFileNames.pickedFileName(1758000000000L, "jpg"))
        assertEquals("picked_1758000000000.png", GraceFileNames.pickedFileName(1758000000000L, "png"))
    }

    @Test
    fun pickedFileFallsBackToJpgForAnEmptyExtension() {
        assertEquals("picked_42.jpg", GraceFileNames.pickedFileName(42L, ""))
    }

    @Test
    fun namesAreSortedChronologicallyAsStrings() {
        val earlier = GraceFileNames.cameraFileName(LocalDateTime(2026, 1, 2, 3, 4, 5))
        val later = GraceFileNames.cameraFileName(LocalDateTime(2026, 1, 2, 3, 4, 6))
        assertTrue(earlier < later)
    }
}
