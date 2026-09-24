package com.grace.app.domain.monetization

import com.grace.app.data.SettingsStore
import com.grace.app.platform.AdConsentService
import com.grace.app.platform.EntitlementState
import com.grace.app.platform.InterstitialAdService
import com.grace.app.platform.InterstitialResult
import com.grace.app.platform.PurchasesRepository

/**
 * Owns the whole "did the customer just earn an ad?" decision.
 *
 * Kept out of `LoadingViewModel` so it is a plain suspend object with constructor-injected
 * fakes — no `viewModelScope`, no Main dispatcher, fully covered by `runTest`.
 *
 * Ordering, per the monetization plan:
 * 1. resolve entitlement  2. paid → straight to result
 * 3. unresolved → straight to result (fail safe, do not count)
 * 4. free → count the blessing
 * 5. odd → result   6. even → preloaded interstitial
 * 7. any ad problem → result
 */
class MonetizationCoordinator(
    private val purchases: PurchasesRepository,
    private val consent: AdConsentService,
    private val ads: InterstitialAdService,
    private val settings: SettingsStore
) {

    private var started = false

    /**
     * One-shot app-launch work: refresh the entitlement, refresh UMP consent, and start
     * preloading an ad only once both say it is allowed. Safe to call repeatedly.
     */
    suspend fun start() {
        if (started) return
        started = true
        purchases.refresh()
        consent.refreshConsent()
        preloadIfAllowed()
    }

    /**
     * Called once per successful bless, immediately before navigating to the Approves
     * result. Returns `true` only when an interstitial was actually presented and closed,
     * which is the sole trigger for the Remove Ads prompt.
     *
     * Never throws and never delays the result: every failure path returns `false`.
     */
    suspend fun onBlessingSucceeded(): Boolean {
        // 1–3. Paid customers and unresolved entitlements never see an ad.
        when (resolveEntitlement()) {
            EntitlementState.Purchased -> return false
            EntitlementState.Unknown -> return false
            EntitlementState.Free -> Unit
        }

        // 4. Count only confirmed-free blessings.
        val blessingCount = settings.incrementFreeBlessingCount()

        // 5–6. Every second one is ad-eligible.
        if (AdFrequencyPolicy.decideAfterBlessing(blessingCount) !=
            BlessingAdDecision.ShowInterstitial
        ) return false

        // 7. Consent, availability and load failures all fall open to the result.
        if (!consent.canRequestAds.value) return false

        val result = runCatching { ads.showIfReady() }.getOrElse { InterstitialResult.Failed }
        if (result == InterstitialResult.DisplayedAndDismissed) {
            // Refill while the result screen is up so the next eligible blessing is warm.
            runCatching { ads.preload() }
            return true
        }
        return false
    }

    private suspend fun resolveEntitlement(): EntitlementState {
        val current = purchases.entitlementState.value
        if (current != EntitlementState.Unknown) return current
        return runCatching { purchases.refresh() }
            .map { purchases.entitlementState.value }
            .getOrElse { EntitlementState.Unknown }
    }

    private suspend fun preloadIfAllowed() {
        if (purchases.entitlementState.value == EntitlementState.Purchased) return
        if (!consent.canRequestAds.value) return
        runCatching { ads.preload() }
    }
}
