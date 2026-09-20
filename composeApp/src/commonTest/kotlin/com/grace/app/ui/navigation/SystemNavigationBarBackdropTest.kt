package com.grace.app.ui.navigation

import androidx.compose.ui.graphics.Color
import com.grace.app.domain.model.MealContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The photo result screens all carry a black panel to the bottom edge
 * (a black photo area for Asks/Approves, a black panel for Angered), so
 * the strip behind the system navigation bar is painted black on them;
 * every other screen keeps the window background.
 */
class SystemNavigationBarBackdropTest {

    @Test
    fun photoResultPaintsTheNavigationBarBlack() {
        assertEquals(Color.Black, systemNavigationBarBackdrop(
            Screen.Photo(MealContent.Asks, "/tmp/a.jpg")))
        assertEquals(Color.Black, systemNavigationBarBackdrop(
            Screen.Photo(MealContent.Approves, "/tmp/b.jpg")))
        assertEquals(Color.Black, systemNavigationBarBackdrop(
            Screen.Photo(MealContent.Angered, "/tmp/c.jpg")))
    }

    @Test
    fun everyOtherScreenKeepsTheWindowBackground() {
        assertNull(systemNavigationBarBackdrop(Screen.Splash))
        assertNull(systemNavigationBarBackdrop(Screen.GetMeal))
        assertNull(systemNavigationBarBackdrop(Screen.Settings))
        assertNull(systemNavigationBarBackdrop(Screen.Legal(LegalPage.PrivacyPolicy)))
        assertNull(systemNavigationBarBackdrop(Screen.Loading("/tmp/a.jpg", LoadingMessage.LetMeSee)))
        assertNull(systemNavigationBarBackdrop(Screen.PhotoDetails("/tmp/a.jpg")))
    }
}
