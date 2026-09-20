package com.grace.app.domain.food

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Parity tests for `custom/FoodDetector.java:42-69`.
 *
 * The original matched with `text.toLowerCase(Locale.US)`, first an exact hit
 * against the whitelist, then substring containment.
 */
class FoodLabelMatcherTest {

    @Test
    fun exactWhitelistMatchIsFood() {
        assertTrue(FoodLabelMatcher.isMeal(listOf("food")))
        assertTrue(FoodLabelMatcher.isMeal(listOf("Pizza")))
        assertTrue(FoodLabelMatcher.isMeal(listOf("SUSHI")))
    }

    @Test
    fun substringMatchIsFood() {
        // "fast food" and "food product" are the examples named in the original.
        assertTrue(FoodLabelMatcher.isMeal(listOf("fast food")))
        assertTrue(FoodLabelMatcher.isMeal(listOf("food product")))
        assertTrue(FoodLabelMatcher.isMeal(listOf("seafood")))
        assertTrue(FoodLabelMatcher.isMeal(listOf("desserts")))
    }

    @Test
    fun caseInsensitiveMatch() {
        assertTrue(FoodLabelMatcher.isMeal(listOf("BuRgEr")))
        assertTrue(FoodLabelMatcher.isMeal(listOf("FAST FOOD")))
    }

    @Test
    fun nonFoodLabelsAreNotFood() {
        assertFalse(FoodLabelMatcher.isMeal(listOf("car", "sky", "person")))
        assertFalse(FoodLabelMatcher.isMeal(emptyList()))
    }

    @Test
    fun onlyOneLabelNeedsToMatch() {
        assertTrue(FoodLabelMatcher.isMeal(listOf("car", "sky", "salad")))
    }

    @Test
    fun emptyLabelIsNotFood() {
        assertFalse(FoodLabelMatcher.isFoodLabel(""))
    }

    @Test
    fun classifyKeepsLabelsAndDecision() {
        val result = FoodLabelMatcher.classify(listOf("car", "pizza"))
        assertEquals(listOf("car", "pizza"), result.labels)
        assertTrue(result.isMeal)

        val negative = FoodLabelMatcher.classify(listOf("car"))
        assertEquals(listOf("car"), negative.labels)
        assertFalse(negative.isMeal)
    }

    @Test
    fun whitelistMatchesTheOriginalThirteenEntries() {
        val expected = setOf(
            "food", "dish", "cuisine", "meal", "dessert", "fruit", "vegetable",
            "burger", "pizza", "sandwich", "salad", "pasta", "sushi"
        )
        expected.forEach { assertTrue(FoodLabelMatcher.isFoodLabel(it), "expected $it to match") }
    }
}
