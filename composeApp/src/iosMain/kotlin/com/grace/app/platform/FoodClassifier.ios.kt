@file:OptIn(ExperimentalForeignApi::class)

package com.grace.app.platform

import com.grace.app.core.GraceConstants.API_TAG
import com.grace.app.core.GraceConstants
import com.grace.app.core.debugLog
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import platform.CoreGraphics.CGImageRef
import platform.Vision.VNClassifyImageRequest
import platform.Vision.VNClassificationObservation
import platform.Vision.VNImageRequestHandler
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * `VNClassifyImageRequest` on Vision, the iOS counterpart of ML Kit
 * `ImageLabeling` (ADR-6). The original applied a 0.70 confidence threshold
 * before matching; Vision is filtered the same way so the shared
 * `FoodLabelMatcher` sees an equivalent label set.
 */
class VisionFoodClassifier : FoodClassifier {

    override suspend fun classify(image: DecodedImage): List<String> = withContext(Dispatchers.Default) {
        val uiImage = (image as? IosImageProcessor.IosDecodedImage)?.image ?: return@withContext emptyList()
        val cgImage: CGImageRef = uiImage.CGImage ?: return@withContext emptyList()

        suspendCancellableCoroutine { continuation ->
            val request = VNClassifyImageRequest { completedRequest, error ->
                if (error != null) {
                    continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                    return@VNClassifyImageRequest
                }
                val observations = completedRequest?.results
                    ?.filterIsInstance<VNClassificationObservation>()
                    .orEmpty()
                val labels = observations
                    .filter { it.confidence >= GraceConstants.FOOD_CONFIDENCE_THRESHOLD }
                    .map { it.identifier }
                debugLog(API_TAG) { "classify() | labels = $labels" }
                continuation.resume(labels)
            }

            val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
                handler.performRequests(listOf(request), null)
            }
        }
    }
}
