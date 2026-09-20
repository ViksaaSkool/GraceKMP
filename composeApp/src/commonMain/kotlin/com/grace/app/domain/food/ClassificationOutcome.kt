package com.grace.app.domain.food

import com.grace.app.domain.model.FoodClassificationResult

/**
 * Distinguishes "the classifier ran" from "we could not even decode the photo".
 * The original surfaced this as `onIsMealResponseFailure(String error)`, whose
 * message was shown raw in debug builds and replaced by
 * `something_went_wrong_text` in release builds.
 */
sealed interface ClassificationOutcome {
    data class Success(val result: FoodClassificationResult) : ClassificationOutcome
    data class Failure(val error: String) : ClassificationOutcome
}
