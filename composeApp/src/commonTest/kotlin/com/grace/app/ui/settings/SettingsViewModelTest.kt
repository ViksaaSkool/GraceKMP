package com.grace.app.ui.settings

import com.grace.app.platform.ShareService
import com.grace.app.ui.components.GraceSnackbarController
import com.grace.app.ui.navigation.LegalPage
import com.grace.app.ui.navigation.Navigator
import com.grace.app.ui.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsViewModelTest {

    private class FakeShareService : ShareService {
        var sharedText: String? = null
        var chooserTitle: String? = null
        var sharedPhotos = 0

        override fun sharePhoto(path: String, chooserTitle: String) {
            sharedPhotos++
        }

        override fun shareText(text: String, chooserTitle: String) {
            sharedText = text
            this.chooserTitle = chooserTitle
        }
    }

    private fun viewModel(
        navigator: Navigator,
        shareService: ShareService
    ) = SettingsViewModel(
        navigator = navigator,
        shareService = shareService,
        snackbar = GraceSnackbarController(),
        inviteMessage = "Bless your meals with Grace. Get it here: https://example.test/grace",
        inviteChooserTitle = "Invite friends via…"
    )

    @Test
    fun privacyPolicyPushesThePrivacyPageOverSettings() {
        val navigator = Navigator(Screen.Settings)
        viewModel(navigator, FakeShareService()).onPrivacyPolicy()

        assertEquals(Screen.Legal(LegalPage.PrivacyPolicy), navigator.current.value)

        // Settings is still underneath, so back returns to it rather than Get Meal.
        navigator.pop()
        assertEquals(Screen.Settings, navigator.current.value)
    }

    @Test
    fun termsPushesTheTermsPageOverSettings() {
        val navigator = Navigator(Screen.Settings)
        viewModel(navigator, FakeShareService()).onTermsAndConditions()

        assertEquals(Screen.Legal(LegalPage.TermsAndConditions), navigator.current.value)
    }

    @Test
    fun disclaimerPushesTheDisclaimerPageOverSettings() {
        val navigator = Navigator(Screen.Settings)
        viewModel(navigator, FakeShareService()).onDisclaimer()

        assertEquals(Screen.Legal(LegalPage.Disclaimer), navigator.current.value)
    }

    @Test
    fun backFromALegalPageReturnsToSettings() {
        val navigator = Navigator(Screen.Settings)
        val model = viewModel(navigator, FakeShareService())

        model.onPrivacyPolicy()
        model.onBack()

        assertEquals(Screen.Settings, navigator.current.value)
    }

    @Test
    fun backFromSettingsReturnsToGetMeal() {
        val navigator = Navigator(Screen.GetMeal)
        navigator.push(Screen.Settings)

        viewModel(navigator, FakeShareService()).onBack()

        assertEquals(Screen.GetMeal, navigator.current.value)
    }

    @Test
    fun inviteFriendsSharesTheLocalisedMessage() {
        val shareService = FakeShareService()
        viewModel(Navigator(Screen.Settings), shareService).onInviteFriends()

        assertEquals(
            "Bless your meals with Grace. Get it here: https://example.test/grace",
            shareService.sharedText
        )
        assertEquals("Invite friends via…", shareService.chooserTitle)
        assertEquals(0, shareService.sharedPhotos)
    }

    @Test
    fun inviteDoesNotShareAnythingElse() {
        val shareService = FakeShareService()
        val model = viewModel(Navigator(Screen.Settings), shareService)

        model.onPrivacyPolicy()

        assertNull(shareService.sharedText)
        assertEquals(0, shareService.sharedPhotos)
    }
}
