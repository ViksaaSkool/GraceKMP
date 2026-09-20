package com.grace.app.domain.photo

import com.grace.app.platform.DecodedImage
import com.grace.app.platform.GraceFileStore
import com.grace.app.platform.ImageProcessor
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers `custom/BlessPhotoWorker.bless()`, whose contract is "return the output
 * path, or an empty string on any failure" — `LoadingFragment.onPhotoBlessed`
 * branches on exactly that.
 */
class BlessPhotoUseCaseTest {

    private class FakeImage(override val width: Int, override val height: Int) : DecodedImage

    private class FakeFileStore(private val path: String = "/tmp/blessed.jpg") : GraceFileStore {
        override fun newCameraFile(now: LocalDateTime): String = "/tmp/camera.jpg"
        override fun newBlessedFile(now: LocalDateTime): String = path
        override fun newPickedFile(millis: Long, extension: String): String = "/tmp/picked.$extension"
    }

    private class FakeImageProcessor(
        private val photo: DecodedImage? = FakeImage(600, 400),
        private val watermark: DecodedImage? = FakeImage(133, 133),
        private val compositeSucceeds: Boolean = true
    ) : ImageProcessor {
        var requestedWatermarkSize: Int? = null
            private set
        var compositeArgs: Triple<Int, Int, String>? = null
            private set
        var photoReleased = false
            private set
        var watermarkReleased = false
            private set

        override suspend fun decodeOriented(path: String): DecodedImage? = photo

        override suspend fun decodeWatermark(sizePx: Int): DecodedImage? {
            requestedWatermarkSize = sizePx
            return watermark
        }

        override suspend fun compositeAndEncodeJpeg(
            photo: DecodedImage,
            watermark: DecodedImage,
            marginFactor: Int,
            outputPath: String
        ): Boolean {
            compositeArgs = Triple(photo.width, marginFactor, outputPath)
            return compositeSucceeds
        }

        override fun release(image: DecodedImage) {
            if (image === photo) photoReleased = true
            if (image === watermark) watermarkReleased = true
        }
    }

    @Test
    fun emptyPathReturnsAnEmptyString() = runTest {
        assertEquals("", BlessPhotoUseCase(FakeImageProcessor(), FakeFileStore())(""))
    }

    @Test
    fun undecodablePhotoReturnsAnEmptyString() = runTest {
        val processor = FakeImageProcessor(photo = null)
        assertEquals("", BlessPhotoUseCase(processor, FakeFileStore())("/tmp/x.jpg"))
    }

    @Test
    fun missingWatermarkReturnsAnEmptyString() = runTest {
        val processor = FakeImageProcessor(watermark = null)
        assertEquals("", BlessPhotoUseCase(processor, FakeFileStore())("/tmp/x.jpg"))
    }

    @Test
    fun failedEncodingReturnsAnEmptyString() = runTest {
        val processor = FakeImageProcessor(compositeSucceeds = false)
        assertEquals("", BlessPhotoUseCase(processor, FakeFileStore())("/tmp/x.jpg"))
    }

    @Test
    fun successReturnsTheOutputPath() = runTest {
        val processor = FakeImageProcessor()
        val result = BlessPhotoUseCase(processor, FakeFileStore("/tmp/blessed.jpg"))("/tmp/x.jpg")
        assertEquals("/tmp/blessed.jpg", result)
    }

    @Test
    fun theWatermarkIsSizedFromTheOrientedPhoto() = runTest {
        // Landscape 600x400 -> height / 3 = 133, per BlessPhotoWorker.
        val processor = FakeImageProcessor(photo = FakeImage(600, 400))
        BlessPhotoUseCase(processor, FakeFileStore())("/tmp/x.jpg")
        assertEquals(133, processor.requestedWatermarkSize)
    }

    @Test
    fun portraitPhotoDrivesTheWatermarkFromItsWidth() = runTest {
        val processor = FakeImageProcessor(photo = FakeImage(300, 900), watermark = FakeImage(100, 100))
        BlessPhotoUseCase(processor, FakeFileStore())("/tmp/x.jpg")
        assertEquals(100, processor.requestedWatermarkSize)
    }

    @Test
    fun bothBitmapsAreReleased() = runTest {
        val processor = FakeImageProcessor()
        BlessPhotoUseCase(processor, FakeFileStore())("/tmp/x.jpg")
        assertTrue(processor.photoReleased, "photo should be recycled")
        assertTrue(processor.watermarkReleased, "watermark should be recycled")
    }
}
