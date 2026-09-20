package com.grace.app.ui.settings

import com.grace.app.platform.ShareService
import com.grace.app.ui.GraceViewModel
import com.grace.app.ui.components.GraceSnackbarController
import com.grace.app.ui.components.GraceToken
import com.grace.app.ui.navigation.LegalPage
import com.grace.app.ui.navigation.Navigator
import com.grace.app.ui.navigation.Screen

/**
 * Settings actions. The invite copy is injected rather than read from the
 * resource bundle so this stays a plain, testable ViewModel (the same pattern
 * `PhotoViewModel` uses for its chooser title).
 */
class SettingsViewModel(
    private val navigator: Navigator,
    private val shareService: ShareService,
    private val snackbar: GraceSnackbarController,
    private val inviteMessage: String,
    private val inviteChooserTitle: String
) : GraceViewModel() {

    fun onBack() {
        navigator.pop()
    }

    fun onPrivacyPolicy() {
        navigator.push(Screen.Legal(LegalPage.PrivacyPolicy))
    }

    fun onTermsAndConditions() {
        navigator.push(Screen.Legal(LegalPage.TermsAndConditions))
    }

    fun onDisclaimer() {
        navigator.push(Screen.Legal(LegalPage.Disclaimer))
    }

    fun onInviteFriends() {
        try {
            shareService.shareText(inviteMessage, inviteChooserTitle)
            snackbar.show(GraceToken.ShareSuccess)
        } catch (_: Exception) {
            snackbar.show(GraceToken.ShareError)
        }
    }
}
