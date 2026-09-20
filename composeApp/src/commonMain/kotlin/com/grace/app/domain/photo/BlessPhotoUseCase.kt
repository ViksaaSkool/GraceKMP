package com.grace.app.domain.photo

import com.grace.app.core.GraceConstants.BLESS_TAG
import com.grace.app.core.GraceConstants
import com.grace.app.core.debugLog
import com.grace.app.platform.GraceFileStore
import com.grace.app.platform.ImageProcessor
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Replaces `custom/BlessPhotoWorker.bless()`:
 *  1. decode + EXIF-orient the meal photo
 *  2. decode `watermark_blessed_transparent.png`
 *  3. resize the watermark to a square of `min(w,h)/3` (orientation decides
 *     whether width or height drives it)
 *  4. draw it at `(wmW/12, H - wmH - wmH/12)`
 *  5. write a JPEG at quality 100 named `Grace_Blessed_Photo_yyyyMMdd_HHmmss.jpg`
 *
 * Returns the output path, or an empty string when anything fails — exactly
 * like the original, whose caller checks `uri != null && !uri.isEmpty()`.
 */
class BlessPhotoUseCase(
    private val imageProcessor: ImageProcessor,
    private val fileStore: GraceFileStore
) {
    suspend operator fun invoke(photoPath: String): String {
        if (photoPath.isEmpty()) {
            debugLog(BLESS_TAG) { "blessPhoto() | photoUri is NULL or EMPTY!" }
            return ""
        }

        val photo = imageProcessor.decodeOriented(photoPath) ?: return ""
        var watermark: com.grace.app.platform.DecodedImage? = null
        return try {
            val size = BlessGeometry.watermarkSize(photo.width, photo.height)
            debugLog(BLESS_TAG) { "bless() | watermark size = $size" }
            watermark = imageProcessor.decodeWatermark(size) ?: return ""

            val outputPath = fileStore.newBlessedFile(
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            )
            val ok = imageProcessor.compositeAndEncodeJpeg(
                photo = photo,
                watermark = watermark,
                marginFactor = GraceConstants.MARGIN_FACTOR,
                outputPath = outputPath
            )
            if (ok) outputPath else ""
        } catch (t: Throwable) {
            debugLog(BLESS_TAG) { "bless() | exception = ${t.message}" }
            ""
        } finally {
            imageProcessor.release(photo)
            watermark?.let { imageProcessor.release(it) }
        }
    }
}
