package com.grace.app.ui.photo

import com.grace.app.domain.model.MealContent
import com.grace.app.platform.AdConsentService
import com.grace.app.platform.EntitlementState
import com.grace.app.platform.PurchaseResult
import com.grace.app.platform.PurchasesRepository
import com.grace.app.platform.RemoveAdsProduct
import com.grace.app.platform.RestoreResult
import com.grace.app.platform.ShareService
import com.grace.app.ui.components.GraceSnackbarController
import com.grace.app.ui.navigation.Navigator
import com.grace.app.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Remove Ads prompt is one-shot route metadata: it may only appear for the Approves
 * result that followed a displayed ad, and dismissing it must be a plain synchronous state
 * change so it is safe to call from composition and from tests.
 */
class PhotoViewModelTest {

    private class FakePurchases : PurchasesRepository {
        private val _entitlement = MutableStateFlow(EntitlementState.Free)
        override val entitlementState: StateFlow<EntitlementState> = _entitlement.asStateFlow()

        private val _product = MutableStateFlow<RemoveAdsProduct?>(null)
        override val product: StateFlow<RemoveAdsProduct?> = _product.asStateFlow()

        private val _supportId = MutableStateFlow<String?>(null)
        override val supportId: StateFlow<String?> = _supportId.asStateFlow()

        override suspend fun purchaseRemoveAds(): PurchaseResult = PurchaseResult.Unavailable
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.Unavailable
        override suspend fun refresh() = Unit
    }

    private class NoopShareService : ShareService {
        override fun sharePhoto(path: String, chooserTitle: String) = Unit
        override fun shareText(text: String, chooserTitle: String) = Unit
    }

    private fun viewModel(
        content: MealContent = MealContent.Approves,
        showRemoveAdsPrompt: Boolean = false
    ) = PhotoViewModel(
        content = content,
        photoUri = "/tmp/blessed.jpg",
        showRemoveAdsPrompt = showRemoveAdsPrompt,
        navigator = Navigator(com.grace.app.ui.navigation.Screen.GetMeal),
        shareService = NoopShareService(),
        snackbar = GraceSnackbarController(),
        purchases = FakePurchases(),
        shareChooserTitle = "Share blessed meal via…"
    )

    @Test
    fun thePromptIsHiddenWhenTheRouteDidNotRequestIt() {
        assertFalse(viewModel(showRemoveAdsPrompt = false).showRemoveAdsPrompt.value)
    }

    @Test
    fun thePromptAppearsWhenAnInterstitialWasDisplayedAndDismissed() {
        assertTrue(viewModel(showRemoveAdsPrompt = true).showRemoveAdsPrompt.value)
    }

    @Test
    fun thePromptIsIgnoredForNonApprovesResults() {
        assertFalse(
            viewModel(
                content = MealContent.Asks,
                showRemoveAdsPrompt = true
            ).showRemoveAdsPrompt.value
        )
        assertFalse(
            viewModel(
                content = MealContent.Angered,
                showRemoveAdsPrompt = true
            ).showRemoveAdsPrompt.value
        )
    }

    @Test
    fun dismissingThePromptHidesItPermanentlyForThisViewModel() {
        val model = viewModel(showRemoveAdsPrompt = true)

        model.onDismissRemoveAdsPrompt()

        assertFalse(model.showRemoveAdsPrompt.value)
    }

    @Test
    fun dismissingTwiceIsHarmless() {
        val model = viewModel(showRemoveAdsPrompt = true)

        model.onDismissRemoveAdsPrompt()
        model.onDismissRemoveAdsPrompt()

        assertFalse(model.showRemoveAdsPrompt.value)
    }

    @Test
    fun thePromptStartsIdleAndNotBusy() {
        val model = viewModel(showRemoveAdsPrompt = true)

        assertFalse(model.purchaseInProgress.value)
        assertNull(model.removeAdsProduct.value)
    }
}
