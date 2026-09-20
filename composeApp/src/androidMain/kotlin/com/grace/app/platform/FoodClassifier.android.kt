package com.grace.app.platform

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.grace.app.core.GraceConstants.API_TAG
import com.grace.app.core.GraceConstants
import com.grace.app.core.debugLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * `custom/FoodDetector` on ML Kit (`image-labeling:17.0.9`), using the original
 * 0.70 confidence threshold. The whitelist matching is shared
 * (`FoodLabelMatcher`), so only the label vocabulary is platform-specific.
 */
class MlKitFoodClassifier : FoodClassifier {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(GraceConstants.FOOD_CONFIDENCE_THRESHOLD)
            .build()
    )

    override suspend fun classify(image: DecodedImage): List<String> {
        val bitmap = (image as? AndroidImageProcessor.AndroidDecodedImage)?.bitmap
            ?: return emptyList()
        val input = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            labeler.process(input)
                .addOnSuccessListener { labels ->
                    val texts = labels.map { it.text }
                    debugLog(API_TAG) { "classify() | labels = $texts" }
                    continuation.resume(texts)
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }
    }
}
