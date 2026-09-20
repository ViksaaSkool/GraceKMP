@file:OptIn(ExperimentalForeignApi::class)

package com.grace.app.platform

import com.grace.app.domain.food.FoodLabelMatcher
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

/**
 * Smoke test for the Vision half of ADR-6: it proves the
 * `VNClassifyImageRequest` pipeline (handler → request → completion handler →
 * coroutine resume) completes and hands a label list to the shared
 * `FoodLabelMatcher`.
 *
 * A synthetic image has no meaningful subject, so the returned labels may be
 * empty — the assertion is on the pipeline, not the classification.
 */
class VisionFoodClassifierTest {

    @Test
    fun classificationPipelineCompletesAndFeedsTheSharedMatcher() = runTest {
        val path = writeTestJpeg(size = 224)
        val processor = IosImageProcessor()
        val image = assertNotNull(processor.decodeOriented(path))

        val labels = VisionFoodClassifier().classify(image)
        assertTrue(labels.all { it.isNotEmpty() }, "Vision labels should be non-blank strings")

        // Whatever Vision returns must be safe to feed through the shared matcher.
        val result = FoodLabelMatcher.classify(labels)
        assertTrue(result.isMeal == FoodLabelMatcher.isMeal(labels))

        processor.release(image)
    }
}

private fun writeTestJpeg(size: Int): String {
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(size.toDouble(), size.toDouble()), true, 1.0)
    UIColor.orangeColor.setFill()
    UIRectFill(CGRectMake(0.0, 0.0, size.toDouble(), size.toDouble()))
    UIColor.whiteColor.setFill()
    UIRectFill(CGRectMake(0.0, 0.0, size.toDouble(), (size / 3).toDouble()))
    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    val data = UIImageJPEGRepresentation(requireNotNull(image), 1.0)!!
    val path = "${NSTemporaryDirectory()}grace-vision-test.jpg"
    data.writeToFile(path, true)
    return path
}
