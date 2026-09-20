package com.grace.app.domain.food

import com.grace.app.core.GraceConstants.API_TAG
import com.grace.app.core.debugLog
import com.grace.app.platform.FoodClassifier
import com.grace.app.platform.ImageProcessor

/**
 * Replaces `LoadingPresenterImpl.convertPhotoAndValidate` + `FoodDetector`.
 *
 * The original decoded and EXIF-oriented the photo on a background executor,
 * then handed the bitmap to ML Kit and matched the returned labels against the
 * whitelist.
 */
class IsPhotoOfMealUseCase(
    private val imageProcessor: ImageProcessor,
    private val foodClassifier: FoodClassifier
) {
    suspend operator fun invoke(photoPath: String): ClassificationOutcome {
        if (photoPath.isEmpty()) {
            debugLog(API_TAG) { "isPhotoOfMeal() | photoUri is NULL or EMPTY!" }
            return ClassificationOutcome.Failure("photoUri is NULL or EMPTY!")
        }
        val image = try {
            imageProcessor.decodeOriented(photoPath)
        } catch (t: Throwable) {
            return ClassificationOutcome.Failure(t.message ?: "Failed to decode photo")
        } ?: return ClassificationOutcome.Failure("Failed to decode photo")

        return try {
            val labels = foodClassifier.classify(image)
            debugLog(API_TAG) { "isPhotoOfMeal() | labels = $labels" }
            ClassificationOutcome.Success(FoodLabelMatcher.classify(labels))
        } catch (t: Throwable) {
            ClassificationOutcome.Failure(t.message ?: "Failed to classify photo")
        } finally {
            imageProcessor.release(image)
        }
    }
}
