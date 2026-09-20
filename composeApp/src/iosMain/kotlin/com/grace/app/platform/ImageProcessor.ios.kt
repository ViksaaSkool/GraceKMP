@file:OptIn(ExperimentalForeignApi::class)

package com.grace.app.platform

import com.grace.app.core.GraceConstants.BLESS_TAG
import com.grace.app.core.GraceConstants
import com.grace.app.core.debugLog
import com.grace.app.domain.photo.BlessGeometry
import com.grace.app.domain.photo.ExifOrientation
import com.grace.app.domain.photo.ImageTransforms
import com.grace.app.domain.photo.JpegExifReader
import com.grace.app.resources.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.drawInRect
import kotlin.math.PI

/**
 * iOS image pipeline: `UIImage` + `CoreGraphics`, mirroring
 * `GracePhotoUtil.getHandledBitmap` / `addBlessing` / `getResizedBitmap` and
 * `BlessPhotoWorker.bless`.
 */
class IosImageProcessor : ImageProcessor {

    class IosDecodedImage(val image: UIImage) : DecodedImage {
        override val width: Int
            get() = image.size.useContents { width }.toInt()
        override val height: Int
            get() = image.size.useContents { height }.toInt()
    }

    override suspend fun decodeOriented(path: String): DecodedImage? = withContext(Dispatchers.Default) {
        val data = NSData.dataWithContentsOfFile(path) ?: return@withContext null

        // ObjC initialisers are imported as non-null, so a nil return surfaces
        // as a NullPointerException rather than `null`. Wrapping the call keeps
        // an undecodable/corrupt file on the normal failure path instead of
        // crashing the app.
        val decoded = runCatching { UIImage(data = data) }.getOrNull()
            ?: return@withContext null

        // `UIImage(data:)` keeps the EXIF orientation in `imageOrientation` and
        // reports an orientation-corrected `size`. Re-wrapping the underlying
        // CGImage gives us raw pixels with `imageOrientation == Up`, which is
        // what `BitmapFactory.decodeFile` produces on Android. Without this the
        // rotation below would be applied on top of an already-corrected size.
        val rawImage = decoded.CGImage
            ?.let { cgImage -> runCatching { UIImage.imageWithCGImage(cgImage) }.getOrNull() }
            ?: decoded

        val orientation = runCatching {
            JpegExifReader.readOrientation(data.toByteArray())
        }.getOrDefault(ExifOrientation.Undefined)
        val transform = ImageTransforms.transformFor(orientation)

        IosDecodedImage(applyTransform(rawImage, transform.rotationDegrees, transform.flipHorizontal, transform.flipVertical))
    }

    override suspend fun decodeWatermark(sizePx: Int): DecodedImage? = withContext(Dispatchers.Default) {
        if (sizePx <= 0) return@withContext null
        val bytes = Res.readBytes("drawable/watermark_blessed_transparent.png")
        val data = bytes.toNSData()
        val image = UIImage(data = data) ?: return@withContext null

        val resized = render(sizePx.toDouble(), sizePx.toDouble()) { ctx ->
            image.drawInRect(CGRectMake(0.0, 0.0, sizePx.toDouble(), sizePx.toDouble()))
        }
        IosDecodedImage(resized ?: image)
    }

    override suspend fun compositeAndEncodeJpeg(
        photo: DecodedImage,
        watermark: DecodedImage,
        marginFactor: Int,
        outputPath: String
    ): Boolean = withContext(Dispatchers.Default) {
        val photoImage = (photo as? IosDecodedImage)?.image ?: return@withContext false
        val watermarkImage = (watermark as? IosDecodedImage)?.image ?: return@withContext false

        val photoWidth = photoImage.size.useContents { width }
        val photoHeight = photoImage.size.useContents { height }
        val wmWidth = watermarkImage.size.useContents { width }
        val wmHeight = watermarkImage.size.useContents { height }

        val x = BlessGeometry.watermarkX(wmWidth.toInt(), marginFactor).toDouble()
        val y = BlessGeometry.watermarkY(photoHeight.toInt(), wmHeight.toInt(), marginFactor).toDouble()

        val composed = render(photoWidth, photoHeight) { _ ->
            photoImage.drawInRect(CGRectMake(0.0, 0.0, photoWidth, photoHeight))
            watermarkImage.drawInRect(CGRectMake(x, y, wmWidth, wmHeight))
        } ?: return@withContext false

        val data = UIImageJPEGRepresentation(composed, 1.0) ?: run {
            debugLog(BLESS_TAG) { "bless() | failed to encode JPEG" }
            return@withContext false
        }
        data.writeToFile(outputPath, true)
    }

    override fun release(image: DecodedImage) {
        // ARC manages UIImage lifetimes; nothing to release.
    }

    private inline fun render(
        width: Double,
        height: Double,
        draw: (platform.CoreGraphics.CGContextRef?) -> Unit
    ): UIImage? {
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), false, 1.0)
        try {
            val context = UIGraphicsGetCurrentContext()
            draw(context)
            return UIGraphicsGetImageFromCurrentImageContext()
        } finally {
            UIGraphicsEndImageContext()
        }
    }

    private fun applyTransform(
        image: UIImage,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): UIImage {
        if (rotationDegrees == 0 && !flipHorizontal && !flipVertical) return image

        val width = image.size.useContents { width }
        val height = image.size.useContents { height }
        val rotated = rotationDegrees == 90 || rotationDegrees == 270
        val outputWidth = if (rotated) height else width
        val outputHeight = if (rotated) width else height

        return render(outputWidth, outputHeight) { context ->
            context?.let { ctx ->
                CGContextTranslateCTM(ctx, outputWidth / 2.0, outputHeight / 2.0)
                if (rotationDegrees != 0) {
                    CGContextRotateCTM(ctx, rotationDegrees * PI / 180.0)
                }
                val scaleX = if (flipHorizontal) -1.0 else 1.0
                val scaleY = if (flipVertical) -1.0 else 1.0
                if (scaleX != 1.0 || scaleY != 1.0) {
                    CGContextScaleCTM(ctx, scaleX, scaleY)
                }
                CGContextTranslateCTM(ctx, -width / 2.0, -height / 2.0)
            }
            image.drawInRect(CGRectMake(0.0, 0.0, width, height))
        } ?: image
    }
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData.create(bytes = null, length = 0u)
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
