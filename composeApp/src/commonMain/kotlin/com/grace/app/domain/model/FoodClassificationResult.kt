package com.grace.app.domain.model

/**
 * Replaces the Clarifai-era `MealResponse`. The `status` field was a leftover
 * and is dropped (PLAN.md §5.2), but the raw labels are kept because they are
 * useful for logging and for the Phase 10 classifier-parity run.
 */
data class FoodClassificationResult(
    val labels: List<String>,
    val isMeal: Boolean
)
