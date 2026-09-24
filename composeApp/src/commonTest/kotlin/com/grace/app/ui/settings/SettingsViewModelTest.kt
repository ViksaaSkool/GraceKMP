package com.grace.app.ui.settings

import com.grace.app.platform.AdConsentService
import com.grace.app.platform.PurchaseResult
import com.grace.app.platform.PurchasesRepository
import com.grace.app.platform.RemoveAdsProduct
import com.grace.app.platform.RestoreResult
import com.grace.app.platform.EntitlementState
import com.grace.app.platform.ShareService
import com.grace.app.ui.components.GraceSnackbarController
import com.grace.app.ui.navigation.LegalPage
import com.grace.app.ui.navigation.Navigator
import com.grace.app.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private class FakePurchasesRepository : PurchasesRepository {
        private val _entitlement = MutableStateFlow(EntitlementState.Free)
        override val entitlementState: StateFlow<EntitlementState> = _entitlement.asStateFlow()

        private val _product = MutableStateFlow<RemoveAdsProduct?>(null)
        override val product: StateFlow<RemoveAdsProduct?> = _product.asStateFlow()

        private val _supportId = MutableStateFlow<String?>(null)
        override val supportId: StateFlow<String?> = _supportId.asStateFlow()

        var purchaseCalls = 0
        var restoreCalls = 0
        var nextPurchase: PurchaseResult = PurchaseResult.Success
        var nextRestore: RestoreResult = RestoreResult.Success

        fun setEntitlement(state: EntitlementState) {
            _entitlement.value = state
        }

        fun setProduct(product: RemoveAdsProduct?) {
            _product.value = product
        }

        override suspend fun purchaseRemoveAds(): PurchaseResult {
            purchaseCalls++
            if (nextPurchase == PurchaseResult.Success) setEntitlement(EntitlementState.Purchased)
            return nextPurchase
        }

        override suspend fun restorePurchases(): RestoreResult {
            restoreCalls++
            return nextRestore
        }

        override suspend fun refresh() = Unit
    }

    private class FakeConsentService : AdConsentService {
        private val _canRequestAds = MutableStateFlow(false)
        override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

        private val _privacyRequired = MutableStateFlow(false)
        override val privacyOptionsRequired: StateFlow<Boolean> = _privacyRequired.asStateFlow()

        var showPrivacyOptionsCalls = 0

        override suspend fun refreshConsent() = Unit

        override suspend fun showPrivacyOptions() {
            showPrivacyOptionsCalls++
        }
    }

    private fun viewModel(
        navigator: Navigator,
        shareService: ShareService = FakeShareService(),
        purchases: PurchasesRepository = FakePurchasesRepository(),
        consent: AdConsentService = FakeConsentService()
    ) = SettingsViewModel(
        navigator = navigator,
        shareService = shareService,
        snackbar = GraceSnackbarController(),
        purchases = purchases,
        consent = consent,
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
