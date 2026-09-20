@file:OptIn(ExperimentalForeignApi::class)

package com.grace.app.platform

import com.grace.app.domain.photo.BlessGeometry
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the real iOS image pipeline (`UIImage` + `CoreGraphics`) on the
 * simulator. This covers the platform half of `BlessPhotoUseCase` — the maths
 * itself is covered by `BlessGeometryTest` in `commonTest`.
 */
class IosImageProcessorTest {

    private val processor = IosImageProcessor()

    @Test
    fun decodesAFileWithoutOrientation() = runTest {
        val path = writeSolidJpeg(width = 300, height = 200)

        val decoded = processor.decodeOriented(path)
        assertNotNull(decoded, "decoding a plain JPEG should succeed")
        assertEquals(300, decoded.width)
        assertEquals(200, decoded.height)
        processor.release(decoded)
    }

    @Test
    fun appliesExifRotation90() = runTest {
        // ROTATE_90 is the common "camera held sideways" tag.
        val path = writeSolidJpeg(width = 300, height = 200, orientation = 6)

        val decoded = processor.decodeOriented(path)
        assertNotNull(decoded)
        // 90° rotation swaps the axes.
        assertEquals(200, decoded.width)
        assertEquals(300, decoded.height)
        processor.release(decoded)
    }

    @Test
    fun appliesExifRotate180WithoutSwappingAxes() = runTest {
        val path = writeSolidJpeg(width = 300, height = 200, orientation = 3)

        val decoded = processor.decodeOriented(path)
        assertNotNull(decoded)
        assertEquals(300, decoded.width)
        assertEquals(200, decoded.height)
        processor.release(decoded)
    }

    @Test
    fun treatsTransposeAsANoOpLikeTheOriginal() = runTest {
        val path = writeSolidJpeg(width = 300, height = 200, orientation = 5)

        val decoded = processor.decodeOriented(path)
        assertNotNull(decoded)
        assertEquals(300, decoded.width)
        assertEquals(200, decoded.height)
        processor.release(decoded)
    }

    @Test
    fun returnsNullForAMissingFile() = runTest {
        assertEquals(null, processor.decodeOriented("/tmp/grace-does-not-exist.jpg"))
    }

    @Test
    fun blessesAPhotoAtTheExpectedGeometry() = runTest {
        val sourcePath = writeSolidJpeg(width = 600, height = 400)
        val photo = assertNotNull(processor.decodeOriented(sourcePath))

        // Landscape (600 > 400) -> watermark edge = height / 3
        val expectedWatermark = BlessGeometry.watermarkSize(600, 400)
        assertEquals(133, expectedWatermark)

        val watermark = assertNotNull(processor.decodeWatermark(expectedWatermark))
        assertEquals(expectedWatermark, watermark.width)
        assertEquals(expectedWatermark, watermark.height)

        val outputPath = "${NSTemporaryDirectory()}grace-blessed-test.jpg"
        val ok = processor.compositeAndEncodeJpeg(
            photo = photo,
            watermark = watermark,
            marginFactor = 12,
            outputPath = outputPath
        )
        assertTrue(ok, "compositing should succeed")
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(outputPath), "output should exist")

        val result = assertNotNull(UIImage.imageWithContentsOfFile(outputPath))
        assertEquals(600.0, result.size.useContents { width })
        assertEquals(400.0, result.size.useContents { height })

        processor.release(photo)
        processor.release(watermark)
    }
}

/** Renders a solid-colour JPEG, optionally tagged with an EXIF orientation. */
private fun writeSolidJpeg(width: Int, height: Int, orientation: Int? = null): String {
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width.toDouble(), height.toDouble()), true, 1.0)
    UIColor.redColor.setFill()
    UIRectFill(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    var data = UIImageJPEGRepresentation(requireNotNull(image), 1.0)!!
    if (orientation != null) {
        data = data.withExifOrientation(orientation)
    }

    val path = "${NSTemporaryDirectory()}grace-test-$width-$height-${orientation ?: 0}.jpg"
    data.writeToFile(path, true)
    return path
}

/**
 * Inserts an APP1/Exif segment carrying `orientation` immediately after the SOI
 * marker of an existing JPEG, so the platform decoder sees a genuine EXIF tag.
 */
private fun NSData.withExifOrientation(orientation: Int): NSData {
    val original = toByteArray()
    val tiff = ByteArray(26)
    tiff[0] = 0x49; tiff[1] = 0x49                       // "II" little-endian
    tiff[2] = 0x2A; tiff[3] = 0x00                       // 42
    tiff[4] = 0x08                                       // IFD0 at offset 8
    tiff[8] = 0x01                                       // 1 entry
    tiff[10] = 0x12; tiff[11] = 0x01                     // tag 0x0112 (Orientation)
    tiff[12] = 0x03; tiff[13] = 0x00                     // type SHORT
    tiff[14] = 0x01                                      // count 1
    tiff[18] = (orientation and 0xFF).toByte()

    val exifPayload = "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff
    val segmentLength = exifPayload.size + 2

    val out = ByteArray(original.size + exifPayload.size + 4)
    out[0] = original[0]; out[1] = original[1]           // SOI
    out[2] = 0xFF.toByte(); out[3] = 0xE1.toByte()       // APP1
    out[4] = ((segmentLength shr 8) and 0xFF).toByte()
    out[5] = (segmentLength and 0xFF).toByte()
    exifPayload.copyInto(out, destinationOffset = 6)
    original.copyInto(out, destinationOffset = 6 + exifPayload.size, startIndex = 2)
    return out.toNSData()
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData.create(bytes = null, length = 0u)
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
