package com.grace.app.ui.settings

import com.grace.app.core.MonetizationConfig
import com.grace.app.core.MonetizationDebug
import com.grace.app.data.SettingsStore
import com.grace.app.domain.monetization.MonetizationCoordinator
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
    private val monetization: MonetizationCoordinator,
    private val settings: SettingsStore,
    private val config: MonetizationConfig,
    private val debug: MonetizationDebug,
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

    // ---- Debug developer panel ----------------------------------------------
    // Every action below is inert in release builds: `MonetizationDebug` is gated on
    // `isDebugBuild`, so this whole surface is compiled out of production.

    /** `false` in release builds — the panel is never rendered. */
    val debugVisible: Boolean get() = debug.enabled

    /**
     * Read-only snapshot for the panel's diagnostics rows: which backend this build
     * selected, the persisted blessing counter, and the live consent state.
     */
    val debugState: StateFlow<MonetizationDebugUiState> = combine(
        combine(
            purchases.entitlementState,
            debug.entitlementOverride
        ) { entitlement, override -> entitlement to override },
        combine(
            debug.forceNextInterstitial,
            debug.debugGeography
        ) { forceAd, geography -> forceAd to geography },
        combine(
            consent.canRequestAds,
            monetization.lastAdOutcome
        ) { canRequestAds, outcome -> canRequestAds to outcome }
    ) { (entitlement, override), (forceAd, geography), (canRequestAds, outcome) ->
        MonetizationDebugUiState(
            backend = when {
                config.usesTestStoreKey -> "RevenueCat Test Store"
                config.purchasesAvailable -> "RevenueCat (${MonetizationDebugUiState.PLATFORM})"
                else -> "Dummy (no RevenueCat key)"
            },
            entitlement = entitlement,
            entitlementOverridden = override != null,
            blessingCount = settings.freeBlessingCount,
            forceNextAd = forceAd,
            debugGeography = geography,
            canRequestAds = canRequestAds,
            lastAdOutcome = outcome
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MonetizationDebugUiState()
    )

    /** Makes the next blessing ad-eligible regardless of the every-second cadence. */
    fun onDebugForceNextAd() {
        debug.requestForceNextInterstitial()
    }

    fun onDebugForceFree() = applyOverride(EntitlementState.Free)

    fun onDebugForcePurchased() = applyOverride(EntitlementState.Purchased)

    /** Clears the override and re-reads the real backend so the UI matches reality. */
    fun onDebugClearOverride() = applyOverride(null)

    fun onDebugResetCounter() {
        settings.freeBlessingCount = 0
    }

    /**
     * Simulates a reinstall: drops the locally cached purchased hint and forces a real
     * refresh, so Restore can be exercised without clearing app data.
     */
    fun onDebugClearCachedEntitlement() {
        settings.removeAdsEntitlementCached = false
        debug.setEntitlementOverride(null)
        scope.launch { monetization.refresh() }
    }

    fun onDebugCycleGeography() {
        debug.cycleDebugGeography()
    }

    fun onDebugResetAll() {
        debug.reset()
        settings.freeBlessingCount = 0
        settings.removeAdsEntitlementCached = false
        scope.launch { monetization.refresh() }
    }

    private fun applyOverride(state: EntitlementState?) {
        debug.setEntitlementOverride(state)
        // The dummy backend renders the premium rows from its own state, so push the
        // override through a refresh rather than leaving the screen half-updated.
        scope.launch { monetization.refresh() }
    }
}

/** Diagnostics shown in the debug panel. All fields are developer-facing only. */
data class MonetizationDebugUiState(
    val backend: String = "",
    val entitlement: EntitlementState = EntitlementState.Unknown,
    val entitlementOverridden: Boolean = false,
    val blessingCount: Long = 0,
    val forceNextAd: Boolean = false,
    val debugGeography: com.grace.app.core.DebugGeography =
        com.grace.app.core.DebugGeography.Off,
    val canRequestAds: Boolean = false,
    /**
     * Why the last ad-eligible blessing did or did not show an interstitial. The single most
     * useful row when an ad "is not showing": it separates an odd-blessing skip from an
     * SDK that never loaded anything.
     */
    val lastAdOutcome: com.grace.app.domain.monetization.AdOutcome =
        com.grace.app.domain.monetization.AdOutcome.None
) {
    companion object {
        /** Only used to label the live platform in the backend row. */
        const val PLATFORM = "platform store"
    }
}
