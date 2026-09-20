package com.grace.app.domain.food

import com.grace.app.platform.DecodedImage
import com.grace.app.platform.FoodClassifier
import com.grace.app.platform.ImageProcessor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the orchestration the original spread across
 * `LoadingPresenterImpl.convertPhotoAndValidate` + `FoodDetector`.
 */
class IsPhotoOfMealUseCaseTest {

    private class FakeImage(override val width: Int = 100, override val height: Int = 100) : DecodedImage

    private class FakeImageProcessor(
        private val image: DecodedImage? = FakeImage(),
        private val throwOnDecode: Throwable? = null
    ) : ImageProcessor {
        var released = false
            private set

        override suspend fun decodeOriented(path: String): DecodedImage? {
            throwOnDecode?.let { throw it }
            return image
        }

        override suspend fun decodeWatermark(sizePx: Int): DecodedImage? = null

        override suspend fun compositeAndEncodeJpeg(
            photo: DecodedImage,
            watermark: DecodedImage,
            marginFactor: Int,
            outputPath: String
        ): Boolean = false

        override fun release(image: DecodedImage) {
            released = true
        }
    }

    private class FakeClassifier(
        private val labels: List<String> = emptyList(),
        private val throwOnClassify: Throwable? = null
    ) : FoodClassifier {
        override suspend fun classify(image: DecodedImage): List<String> {
            throwOnClassify?.let { throw it }
            return labels
        }
    }

    @Test
    fun emptyPathIsAFailure() = runTest {
        val outcome = IsPhotoOfMealUseCase(FakeImageProcessor(), FakeClassifier())("")
        val failure = assertIs<ClassificationOutcome.Failure>(outcome)
        assertEquals("photoUri is NULL or EMPTY!", failure.error)
    }

    @Test
    fun undecodablePhotoIsAFailure() = runTest {
        val outcome = IsPhotoOfMealUseCase(FakeImageProcessor(image = null), FakeClassifier())("/tmp/x.jpg")
        assertIs<ClassificationOutcome.Failure>(outcome)
    }

    @Test
    fun decodeExceptionIsAFailure() = runTest {
        val outcome = IsPhotoOfMealUseCase(
            FakeImageProcessor(throwOnDecode = IllegalStateException("boom")),
            FakeClassifier()
        )("/tmp/x.jpg")
        assertEquals("boom", assertIs<ClassificationOutcome.Failure>(outcome).error)
    }

    @Test
    fun classifierExceptionIsAFailure() = runTest {
        val outcome = IsPhotoOfMealUseCase(
            FakeImageProcessor(),
            FakeClassifier(throwOnClassify = IllegalStateException("vision down"))
        )("/tmp/x.jpg")
        assertEquals("vision down", assertIs<ClassificationOutcome.Failure>(outcome).error)
    }

    @Test
    fun foodLabelsProduceASuccessfulMealDecision() = runTest {
        val outcome = IsPhotoOfMealUseCase(
            FakeImageProcessor(),
            FakeClassifier(listOf("Food", "Cuisine"))
        )("/tmp/x.jpg")
        val success = assertIs<ClassificationOutcome.Success>(outcome)
        assertTrue(success.result.isMeal)
        assertEquals(listOf("Food", "Cuisine"), success.result.labels)
    }

    @Test
    fun nonFoodLabelsProduceASuccessfulNegativeDecision() = runTest {
        val outcome = IsPhotoOfMealUseCase(
            FakeImageProcessor(),
            FakeClassifier(listOf("car", "sky"))
        )("/tmp/x.jpg")
        val success = assertIs<ClassificationOutcome.Success>(outcome)
        assertTrue(!success.result.isMeal)
    }

    @Test
    fun theDecodedImageIsAlwaysReleased() = runTest {
        val processor = FakeImageProcessor()
        IsPhotoOfMealUseCase(processor, FakeClassifier(listOf("pizza")))("/tmp/x.jpg")
        assertTrue(processor.released, "the decoded bitmap must be recycled")
    }
}
