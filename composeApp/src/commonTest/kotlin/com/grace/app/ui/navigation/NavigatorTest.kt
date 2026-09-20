package com.grace.app.ui.navigation

import com.grace.app.domain.model.MealContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the navigation rules from `Main2Activity` + `ChangeFragmentHelper`
 * (PLAN.md §Phase 9, task 2).
 */
class NavigatorTest {

    @Test
    fun replaceSwapsTheCurrentScreenAndClearsTheStack() {
        val navigator = Navigator(Screen.Splash)
        navigator.replace(Screen.GetMeal)
        assertEquals(Screen.GetMeal, navigator.current.value)

        navigator.replace(Screen.Loading("/tmp/a.jpg", LoadingMessage.LetMeSee))
        assertEquals(Screen.Loading("/tmp/a.jpg", LoadingMessage.LetMeSee), navigator.current.value)

        // Nothing to pop after a replace.
        assertFalse(navigator.pop())
    }

    @Test
    fun pushKeepsThePreviousScreenUnderneath() {
        val navigator = Navigator(Screen.Photo(MealContent.Asks, "/tmp/a.jpg"))
        navigator.push(Screen.PhotoDetails("/tmp/a.jpg"))
        assertEquals(Screen.PhotoDetails("/tmp/a.jpg"), navigator.current.value)

        assertTrue(navigator.pop())
        assertEquals(Screen.Photo(MealContent.Asks, "/tmp/a.jpg"), navigator.current.value)
    }

    @Test
    fun backToGetMealReturnsTrueFromAnyOtherScreen() {
        val navigator = Navigator(Screen.Photo(MealContent.Angered, null))
        assertTrue(navigator.backToGetMeal())
        assertEquals(Screen.GetMeal, navigator.current.value)
    }

    @Test
    fun backToGetMealReturnsFalseOnGetMealSoTheSystemExits() {
        val navigator = Navigator(Screen.GetMeal)
        assertFalse(navigator.backToGetMeal())
        assertEquals(Screen.GetMeal, navigator.current.value)
    }

    @Test
    fun backToGetMealReturnsFalseOnSplash() {
        val navigator = Navigator(Screen.Splash)
        assertFalse(navigator.backToGetMeal())
    }

    @Test
    fun popReturnsFalseWhenTheStackIsEmpty() {
        assertFalse(Navigator(Screen.GetMeal).pop())
    }

    @Test
    fun settingsStacksOverGetMealAndPopsBack() {
        val navigator = Navigator(Screen.GetMeal)
        navigator.push(Screen.Settings)

        assertEquals(Screen.Settings, navigator.current.value)
        assertTrue(navigator.pop())
        assertEquals(Screen.GetMeal, navigator.current.value)
    }

    @Test
    fun legalPageStacksOverSettingsAndPopsBackToIt() {
        val navigator = Navigator(Screen.GetMeal)
        navigator.push(Screen.Settings)
        navigator.push(Screen.Legal(LegalPage.PrivacyPolicy))

        assertEquals(Screen.Legal(LegalPage.PrivacyPolicy), navigator.current.value)
        assertTrue(navigator.pop())
        assertEquals(Screen.Settings, navigator.current.value)
        assertTrue(navigator.pop())
        assertEquals(Screen.GetMeal, navigator.current.value)
    }

    @Test
    fun loadingMessageDistinguishesClassifyFromBless() {
        val classify = Screen.Loading("/tmp/a.jpg", LoadingMessage.LetMeSee)
        val bless = Screen.Loading("/tmp/blessed.jpg", LoadingMessage.BlessingPhoto)
        assertTrue(classify != bless)
        assertEquals(LoadingMessage.LetMeSee, classify.message)
        assertEquals(LoadingMessage.BlessingPhoto, bless.message)
    }
}
