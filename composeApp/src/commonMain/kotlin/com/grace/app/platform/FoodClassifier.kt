package com.grace.app.platform

/**
 * On-device image labelling. ML Kit `ImageLabeling` on Android, Vision
 * `VNClassifyImageRequest` on iOS (ADR-6). Only the label vocabulary differs
 * between platforms; the decision logic lives in
 * `domain/food/FoodLabelMatcher` so it is identical everywhere.
 *
 * The confidence threshold (0.70) is applied by the platform implementation so
 * that the returned list matches what the original fed into the matcher.
 */
interface FoodClassifier {
    suspend fun classify(image: DecodedImage): List<String>
}
