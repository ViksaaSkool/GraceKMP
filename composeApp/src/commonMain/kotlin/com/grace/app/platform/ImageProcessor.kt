package com.grace.app.platform

/**
 * A decoded, orientation-corrected image. The concrete type is platform
 * specific (`android.graphics.Bitmap` on Android, `UIImage` on iOS) but the
 * shared layer only ever needs the dimensions and an opaque handle.
 */
interface DecodedImage {
    val width: Int
    val height: Int
}

/**
 * Decode / orient / composite / encode. Replaces the bitmap half of
 * `util/GracePhotoUtil.java` and `custom/BlessPhotoWorker.java`.
 *
 * ADR-4 originally proposed Skiko for this. Skiko is not on the Android
 * classpath (Compose on Android draws through the platform canvas), so the
 * platform-native pipeline is used instead: `Bitmap`/`Canvas` on Android and
 * `UIImage`/`CoreGraphics` on iOS. All the *maths* (orientation mapping,
 * watermark size/placement, crop rect) stays shared and unit-tested in
 * `domain/photo`.
 */
interface ImageProcessor {
    /** Decodes [path] and applies the EXIF transform. Null when undecodable. */
    suspend fun decodeOriented(path: String): DecodedImage?

    /** Decodes the bundled blessed watermark, resized to [sizePx] x [sizePx]. */
    suspend fun decodeWatermark(sizePx: Int): DecodedImage?

    /**
     * Draws [watermark] onto [photo] using [BlessGeometry] and writes a JPEG
     * at quality 100 to [outputPath]. Returns true on success.
     */
    suspend fun compositeAndEncodeJpeg(
        photo: DecodedImage,
        watermark: DecodedImage,
        marginFactor: Int,
        outputPath: String
    ): Boolean

    fun release(image: DecodedImage)
}
