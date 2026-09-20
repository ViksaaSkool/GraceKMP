package com.grace.app.domain.photo

/**
 * Minimal, dependency-free JPEG APP1/Exif orientation reader.
 *
 * Replaces `ExifInterface` usage in `util/GracePhotoUtil.java:145-156` so the
 * same parsing runs on Android and iOS (and is unit-testable). Returns
 * [ExifOrientation.Undefined] for anything malformed or missing, matching the
 * original's `ORIENTATION_UNDEFINED` fallback.
 */
object JpegExifReader {

    private const val MARKER_SOI = 0xD8
    private const val MARKER_APP1 = 0xE1
    private const val MARKER_SOS = 0xDA
    private const val EXIF_HEADER = "Exif"
    private const val TAG_ORIENTATION = 0x0112

    fun readOrientation(bytes: ByteArray): ExifOrientation {
        val tiffStart = findTiffHeader(bytes) ?: return ExifOrientation.Undefined
        return readOrientationFromTiff(bytes, tiffStart)
    }

    /** Locates the first byte of the TIFF header (just past "Exif\0\0"). */
    private fun findTiffHeader(bytes: ByteArray): Int? {
        if (bytes.size < 4) return null
        if (u8(bytes, 0) != 0xFF || u8(bytes, 1) != MARKER_SOI) return null

        var offset = 2
        while (offset + 3 < bytes.size) {
            if (u8(bytes, offset) != 0xFF) return null
            val marker = u8(bytes, offset + 1)
            if (marker == MARKER_SOS) return null
            if (marker == 0xFF) {
                offset++
                continue
            }
            val length = u16(bytes, offset + 2, bigEndian = true)
            if (length < 2) return null
            val payload = offset + 4
            if (marker == MARKER_APP1 && payload + 6 <= bytes.size) {
                val isExif = (0 until EXIF_HEADER.length).all { i ->
                    bytes[payload + i].toInt().toChar() == EXIF_HEADER[i]
                }
                if (isExif) return payload + 6
            }
            offset = payload + (length - 2)
        }
        return null
    }

    private fun readOrientationFromTiff(bytes: ByteArray, tiffStart: Int): ExifOrientation {
        if (tiffStart + 8 > bytes.size) return ExifOrientation.Undefined

        val bigEndian = when {
            u8(bytes, tiffStart) == 0x4D && u8(bytes, tiffStart + 1) == 0x4D -> true
            u8(bytes, tiffStart) == 0x49 && u8(bytes, tiffStart + 1) == 0x49 -> false
            else -> return ExifOrientation.Undefined
        }
        if (u16(bytes, tiffStart + 2, bigEndian) != 42) return ExifOrientation.Undefined

        val ifdOffset = tiffStart + u32(bytes, tiffStart + 4, bigEndian)
        if (ifdOffset + 2 > bytes.size) return ExifOrientation.Undefined

        val entryCount = u16(bytes, ifdOffset, bigEndian)
        var entry = ifdOffset + 2
        repeat(entryCount) {
            if (entry + 12 > bytes.size) return ExifOrientation.Undefined
            val tag = u16(bytes, entry, bigEndian)
            if (tag == TAG_ORIENTATION) {
                // SHORT, count 1 → the value lives in the first two value bytes.
                val value = u16(bytes, entry + 8, bigEndian)
                return ExifOrientation.fromExifValue(value)
            }
            entry += 12
        }
        return ExifOrientation.Undefined
    }

    private fun u8(bytes: ByteArray, index: Int): Int = bytes[index].toInt() and 0xFF

    private fun u16(bytes: ByteArray, index: Int, bigEndian: Boolean): Int =
        if (bigEndian) {
            (u8(bytes, index) shl 8) or u8(bytes, index + 1)
        } else {
            (u8(bytes, index + 1) shl 8) or u8(bytes, index)
        }

    private fun u32(bytes: ByteArray, index: Int, bigEndian: Boolean): Int {
        val b0 = u8(bytes, index)
        val b1 = u8(bytes, index + 1)
        val b2 = u8(bytes, index + 2)
        val b3 = u8(bytes, index + 3)
        return if (bigEndian) {
            (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        } else {
            (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        }
    }
}
