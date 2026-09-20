package com.grace.app.domain.photo

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the pure-Kotlin replacement for the `ExifInterface` orientation read in
 * `util/GracePhotoUtil.java:145-156`.
 *
 * Each fixture is a minimal but structurally valid JPEG: SOI, one APP1/Exif
 * segment holding a single IFD0 entry (tag 0x0112), then EOI.
 */
class JpegExifReaderTest {

    @Test
    fun readsEveryDefinedOrientationValue() {
        val expected = mapOf(
            1 to ExifOrientation.Normal,
            2 to ExifOrientation.FlipHorizontal,
            3 to ExifOrientation.Rotate180,
            4 to ExifOrientation.FlipVertical,
            5 to ExifOrientation.Transpose,
            6 to ExifOrientation.Rotate90,
            7 to ExifOrientation.Transverse,
            8 to ExifOrientation.Rotate270
        )
        expected.forEach { (value, orientation) ->
            assertEquals(
                orientation,
                JpegExifReader.readOrientation(jpegWithOrientation(value)),
                "orientation value $value"
            )
        }
    }

    @Test
    fun readsBigEndianExif() {
        assertEquals(
            ExifOrientation.Rotate90,
            JpegExifReader.readOrientation(jpegWithOrientation(6, bigEndian = true))
        )
    }

    @Test
    fun unknownOrientationValueIsUndefined() {
        assertEquals(
            ExifOrientation.Undefined,
            JpegExifReader.readOrientation(jpegWithOrientation(99))
        )
    }

    @Test
    fun missingExifIsUndefined() {
        val noExif = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        assertEquals(ExifOrientation.Undefined, JpegExifReader.readOrientation(noExif))
    }

    @Test
    fun emptyAndTruncatedInputIsUndefined() {
        assertEquals(ExifOrientation.Undefined, JpegExifReader.readOrientation(ByteArray(0)))
        assertEquals(
            ExifOrientation.Undefined,
            JpegExifReader.readOrientation(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte())
            )
        )
    }

    @Test
    fun nonJpegInputIsUndefined() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertEquals(ExifOrientation.Undefined, JpegExifReader.readOrientation(png))
    }

    /**
     * `getHandledBitmap` behaviour matrix: only rotations and mirrors map to a
     * transform, and TRANSPOSE/TRANSVERSE are deliberate no-ops.
     */
    @Test
    fun transformMatrixMatchesTheOriginalSwitch() {
        assertEquals(OrientationTransform.None, ImageTransforms.transformFor(ExifOrientation.Undefined))
        assertEquals(OrientationTransform.None, ImageTransforms.transformFor(ExifOrientation.Normal))
        assertEquals(OrientationTransform(0, true, false), ImageTransforms.transformFor(ExifOrientation.FlipHorizontal))
        assertEquals(OrientationTransform(180, false, false), ImageTransforms.transformFor(ExifOrientation.Rotate180))
        assertEquals(OrientationTransform(0, false, true), ImageTransforms.transformFor(ExifOrientation.FlipVertical))
        assertEquals(OrientationTransform.None, ImageTransforms.transformFor(ExifOrientation.Transpose))
        assertEquals(OrientationTransform(90, false, false), ImageTransforms.transformFor(ExifOrientation.Rotate90))
        assertEquals(OrientationTransform.None, ImageTransforms.transformFor(ExifOrientation.Transverse))
        assertEquals(OrientationTransform(270, false, false), ImageTransforms.transformFor(ExifOrientation.Rotate270))
    }
}

private fun jpegWithOrientation(orientation: Int, bigEndian: Boolean = false): ByteArray {
    val tiff = tiffWithOrientation(orientation, bigEndian)
    val exifPayload = "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff
    val segmentLength = exifPayload.size + 2

    return buildList {
        add(0xFF.toByte()); add(0xD8.toByte())                       // SOI
        add(0xFF.toByte()); add(0xE1.toByte())                       // APP1
        add(((segmentLength shr 8) and 0xFF).toByte())
        add((segmentLength and 0xFF).toByte())
        addAll(exifPayload.toList())
        add(0xFF.toByte()); add(0xD9.toByte())                       // EOI
    }.toByteArray()
}

private fun tiffWithOrientation(orientation: Int, bigEndian: Boolean): ByteArray {
    val bytes = ByteArray(26)
    if (bigEndian) {
        bytes[0] = 0x4D; bytes[1] = 0x4D
        bytes[2] = 0x00; bytes[3] = 0x2A
        bytes[4] = 0x00; bytes[5] = 0x00; bytes[6] = 0x00; bytes[7] = 0x08
        bytes[8] = 0x00; bytes[9] = 0x01                                 // 1 entry
        bytes[10] = 0x01; bytes[11] = 0x12                              // tag 0x0112
        bytes[12] = 0x00; bytes[13] = 0x03                              // SHORT
        bytes[14] = 0x00; bytes[15] = 0x00; bytes[16] = 0x00; bytes[17] = 0x01
        bytes[18] = ((orientation shr 8) and 0xFF).toByte()
        bytes[19] = (orientation and 0xFF).toByte()
    } else {
        bytes[0] = 0x49; bytes[1] = 0x49
        bytes[2] = 0x2A; bytes[3] = 0x00
        bytes[4] = 0x08; bytes[5] = 0x00; bytes[6] = 0x00; bytes[7] = 0x00
        bytes[8] = 0x01; bytes[9] = 0x00                                 // 1 entry
        bytes[10] = 0x12; bytes[11] = 0x01                              // tag 0x0112
        bytes[12] = 0x03; bytes[13] = 0x00                              // SHORT
        bytes[14] = 0x01; bytes[15] = 0x00; bytes[16] = 0x00; bytes[17] = 0x00
        bytes[18] = (orientation and 0xFF).toByte()
        bytes[19] = ((orientation shr 8) and 0xFF).toByte()
    }
    return bytes
}
