package com.grace.app.ui.settings

import com.grace.app.platform.AdConsentService
import com.grace.app.platform.EntitlementState
import com.grace.app.platform.PurchaseResult
import com.grace.app.platform.PurchasesRepository
import com.grace.app.platform.RemoveAdsProduct
import com.grace.app.platform.RestoreResult
import com.grace.app.platform.ShareService
import com.grace.app.ui.GraceViewModel
import com.grace.app.ui.components.GraceSnackbarController
import com.grace.app.ui.components.GraceToken
import com.grace.app.ui.navigation.LegalPage
import com.grace.app.ui.navigation.Navigator
import com.grace.app.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Grace Premium section of Settings needs to render. */
data class PremiumUiState(
    val entitlement: EntitlementState = EntitlementState.Unknown,
    val product: RemoveAdsProduct? = null,
    val busy: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val supportId: String? = null
) {
    val purchased: Boolean get() = entitlement == EntitlementState.Purchased

    /** `false` when the build has no store backend (placeholder RevenueCat key). */
    val unavailable: Boolean get() = entitlement == EntitlementState.Unknown && product == null
}

/**
 * Settings actions, plus the Grace Premium section: Remove Ads, Restore Purchases and the
 * UMP privacy-options entry point.
 *
 * Entitlement/product/consent are read straight from the platform seams and combined into
 * a single immutable state, so the screen stays a pure function of [premiumState].
 */
class SettingsViewModel(
    private val navigator: Navigator,
    private val shareService: ShareService,
    private val snackbar: GraceSnackbarController,
    private val purchases: PurchasesRepository,
    private val consent: AdConsentService,
    private val inviteMessage: String,
    private val inviteChooserTitle: String
) : GraceViewModel() {

    private val busy = MutableStateFlow(false)

    val premiumState: StateFlow<PremiumUiState> = combine(
        purchases.entitlementState,
        purchases.product,
        busy,
        consent.privacyOptionsRequired,
        purchases.supportId
    ) { entitlement, product, isBusy, privacyRequired, supportId ->
        PremiumUiState(
            entitlement = entitlement,
            product = product,
            busy = isBusy,
            privacyOptionsRequired = privacyRequired,
            supportId = supportId
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PremiumUiState()
    )

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

    fun onRemoveAds() {
        if (busy.value) return
        busy.value = true
        scope.launch {
            try {
                when (purchases.purchaseRemoveAds()) {
                    PurchaseResult.Success -> snackbar.show(GraceToken.RemoveAdsPurchased)
                    PurchaseResult.Cancelled,
                    PurchaseResult.Unavailable -> Unit
                    is PurchaseResult.Failure -> snackbar.show(GraceToken.PurchaseFailed)
                }
            } finally {
                busy.value = false
            }
        }
    }

    fun onRestorePurchases() {
        if (busy.value) return
        busy.value = true
        scope.launch {
            try {
                when (purchases.restorePurchases()) {
                    RestoreResult.Success -> snackbar.show(GraceToken.RestoreSuccess)
                    RestoreResult.NothingToRestore -> snackbar.show(GraceToken.RestoreNothing)
                    RestoreResult.Unavailable -> Unit
                    is RestoreResult.Failure -> snackbar.show(GraceToken.RestoreFailed)
                }
            } finally {
                busy.value = false
            }
        }
    }

    /** Opens the UMP privacy-options form, only when UMP requires the entry point. */
    fun onPrivacyChoices() {
        scope.launch { consent.showPrivacyOptions() }
    }
}
