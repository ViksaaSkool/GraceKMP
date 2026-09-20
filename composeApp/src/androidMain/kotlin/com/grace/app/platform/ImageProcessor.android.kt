package com.grace.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import com.grace.app.core.GraceConstants.BLESS_TAG
import com.grace.app.core.debugLog
import com.grace.app.domain.photo.BlessGeometry
import com.grace.app.domain.photo.ExifOrientation
import com.grace.app.domain.photo.ImageTransforms
import com.grace.app.domain.photo.JpegExifReader
import com.grace.app.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Android image pipeline: `BitmapFactory` + `Canvas`, mirroring
 * `GracePhotoUtil.getHandledBitmap` / `addBlessing` / `getResizedBitmap` and
 * `BlessPhotoWorker.bless`.
 */
class AndroidImageProcessor(private val context: Context) : ImageProcessor {

    class AndroidDecodedImage(val bitmap: Bitmap) : DecodedImage {
        override val width: Int get() = bitmap.width
        override val height: Int get() = bitmap.height
    }

    override suspend fun decodeOriented(path: String): DecodedImage? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null

        val orientation = runCatching {
            JpegExifReader.readOrientation(file.readBytes())
        }.getOrDefault(ExifOrientation.Undefined)
        val transform = ImageTransforms.transformFor(orientation)

        val options = BitmapFactory.Options().apply { inMutable = true }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return@withContext null

        val transformed = applyTransform(decoded, transform.rotationDegrees, transform.flipHorizontal, transform.flipVertical)
        AndroidDecodedImage(transformed)
    }

    override suspend fun decodeWatermark(sizePx: Int): DecodedImage? = withContext(Dispatchers.IO) {
        if (sizePx <= 0) return@withContext null
        val bytes = Res.readBytes("drawable/watermark_blessed_transparent.png")
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val resized = Bitmap.createScaledBitmap(decoded, sizePx, sizePx, false)
        if (resized !== decoded) decoded.recycle()
        AndroidDecodedImage(resized)
    }

    override suspend fun compositeAndEncodeJpeg(
        photo: DecodedImage,
        watermark: DecodedImage,
        marginFactor: Int,
        outputPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val photoBitmap = (photo as? AndroidDecodedImage)?.bitmap ?: return@withContext false
        val watermarkBitmap = (watermark as? AndroidDecodedImage)?.bitmap ?: return@withContext false

        try {
            // GracePhotoUtil.addBlessing
            val canvas = Canvas(photoBitmap)
            canvas.drawBitmap(photoBitmap, Matrix(), null)
            canvas.drawBitmap(
                watermarkBitmap,
                BlessGeometry.watermarkX(watermarkBitmap.width, marginFactor),
                BlessGeometry.watermarkY(photoBitmap.height, watermarkBitmap.height, marginFactor),
                null
            )

            val outputFile = File(outputPath).apply { parentFile?.mkdirs() }
            FileOutputStream(outputFile).use { stream ->
                photoBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                stream.flush()
            }
            true
        } catch (e: IOException) {
            debugLog(BLESS_TAG) { "bless() | exception = ${e.message}" }
            false
        }
    }

    override fun release(image: DecodedImage) {
        (image as? AndroidDecodedImage)?.bitmap?.takeIf { !it.isRecycled }?.recycle()
    }

    private fun applyTransform(
        source: Bitmap,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): Bitmap {
        if (rotationDegrees == 0 && !flipHorizontal && !flipVertical) return source

        val matrix = Matrix()
        if (flipHorizontal) matrix.preScale(-1.0f, 1.0f)
        if (flipVertical) matrix.preScale(1.0f, -1.0f)
        if (rotationDegrees != 0) matrix.postRotate(rotationDegrees.toFloat())

        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result !== source) source.recycle()
        return result
    }
}
