package com.grace.app.domain.food

import com.grace.app.core.GraceConstants
import com.grace.app.domain.model.FoodClassificationResult

/**
 * Ported verbatim from `custom/FoodDetector.java:42-69`.
 *
 * For every label returned by the platform classifier (ML Kit on Android,
 * Vision on iOS) we lowercase with `Locale.US` and accept either an exact match
 * against [GraceConstants.FOOD_LABELS] or a substring containment
 * (e.g. "fast food", "food product").
 */
object FoodLabelMatcher {

    fun isMeal(labels: List<String>): Boolean = labels.any { isFoodLabel(it) }

    fun classify(labels: List<String>): FoodClassificationResult =
        FoodClassificationResult(labels = labels, isMeal = isMeal(labels))

    fun isFoodLabel(rawLabel: String): Boolean {
        val text = rawLabel.lowercase()
        if (GraceConstants.FOOD_LABELS.contains(text)) return true
        return GraceConstants.FOOD_LABELS.any { key -> key.isNotEmpty() && text.contains(key) }
    }
}
