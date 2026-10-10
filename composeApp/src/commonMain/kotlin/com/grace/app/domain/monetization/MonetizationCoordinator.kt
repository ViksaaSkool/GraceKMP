package com.grace.app.domain.monetization

import com.grace.app.core.GraceConstants.APP_TAG
import com.grace.app.core.MonetizationDebug
import com.grace.app.core.debugLog
import com.grace.app.data.SettingsStore
import com.grace.app.platform.AdConsentService
import com.grace.app.platform.EntitlementState
import com.grace.app.platform.InterstitialAdService
import com.grace.app.platform.InterstitialResult
import com.grace.app.platform.PurchasesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Why the last ad-eligible blessing did or did not present an interstitial.
 *
 * Every path through [MonetizationCoordinator.onBlessingSucceeded] used to collapse to
 * `false`, so a skipped ad because the cadence was odd was indistinguishable from a skipped
 * ad because the SDK never loaded one. This enum is the difference; the debug panel shows it.
 */
enum class AdOutcome {
    /** No ad-eligible blessing has happened yet this session. */
    None,

    /** A paid customer — ads are suppressed by definition. */
    SkippedPaid,

    /** Entitlement unresolved — fails safe, and does not count the blessing. */
    SkippedUnresolved,

    /** Ad was eligible by cadence but consent has not been granted. */
    ConsentRequired,

    /** Cadence said "no ad this time" (an odd-numbered blessing). */
    SkippedCadence,

    /** Eligible, but the SDK had no ad to give even after an on-demand load. */
    Unavailable,

    /** Eligible, but loading or presenting the ad failed. */
    Failed,

    /** Ad was presented and dismissed. */
    Shown,

    /** Presented because of the debug "Force next interstitial" switch. */
    Forced
}

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
    private val settings: SettingsStore,
    private val debug: MonetizationDebug = MonetizationDebug()
) {

    private var started = false

    private val _lastAdOutcome = MutableStateFlow(AdOutcome.None)

    /**
     * Why the last ad-eligible blessing did or did not present an interstitial.
     *
     * Purely a diagnostic: before this existed, every failure path returned `false`
     * indistinguishably, so "no ad because the cadence was odd" and "no ad because the SDK
     * never loaded one" looked identical in the app — which is how a permanently empty ad
     * cache went unnoticed. Surfaced in the debug panel; harmless in release.
     */
    val lastAdOutcome: StateFlow<AdOutcome> = _lastAdOutcome.asStateFlow()

    /**
     * Optional scope used to preload when consent arrives after launch. Left `null` by
     * default so unit tests keep driving this class as a plain suspend object with no Main
     * dispatcher; [AppNavHost] supplies one in the running app.
     */
    private var scope: CoroutineScope? = null

    fun attachScope(scope: CoroutineScope) {
        this.scope = scope
    }

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
        watchConsent()
    }

    /**
     * Preloads as soon as consent turns true, whenever that happens.
     *
     * Consent can arrive *after* the launch-time preload was skipped — a form the customer
     * had to answer, or a slow UMP round trip. Without this the cache stayed cold for the
     * entire session and no ad was ever requested. `drop(1)` skips the emission handled
     * synchronously above, so nothing is preloaded twice at launch.
     */
    private fun watchConsent() {
        val scope = scope ?: return
        scope.launch {
            consent.canRequestAds.drop(1).collect { allowed ->
                if (allowed) preloadIfAllowed()
            }
        }
    }

    /**
     * Re-reads the entitlement from the backend and republishes it to the UI.
     *
     * Used by the debug panel after an override change or a simulated reinstall. Unlike
     * [start] this is *not* idempotent — a developer action is expected to have an effect —
     * but it never throws, so a failing backend cannot break the Settings screen.
     */
    suspend fun refresh() {
        runCatching { purchases.refresh() }
            .onFailure { debugLog(APP_TAG) { "Monetization refresh failed: ${it.message}" } }
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
            EntitlementState.Purchased -> return record(AdOutcome.SkippedPaid)
            EntitlementState.Unknown -> return record(AdOutcome.SkippedUnresolved)
            EntitlementState.Free -> Unit
        }

        // Debug escape hatch: make the next blessing ad-eligible regardless of the cadence,
        // and consume the flag so it cannot leak into a later blessing. Deliberately does
        // NOT increment the counter, so the real cadence is untouched afterwards.
        if (debug.consumeForceNextInterstitial()) return presentInterstitial(AdOutcome.Forced)

        // 4. Count only confirmed-free blessings.
        val blessingCount = settings.incrementFreeBlessingCount()

        // 5–6. Every second one is ad-eligible.
        if (AdFrequencyPolicy.decideAfterBlessing(blessingCount) !=
            BlessingAdDecision.ShowInterstitial
        ) return record(AdOutcome.SkippedCadence)

        return presentInterstitial()
    }

    /**
     * Steps 6–7: consent, availability and load failures all fall open to the result, so
     * the blessed photo is never withheld by an ad problem. Returns `true` only when an ad
     * was actually presented and closed — the sole trigger for the Remove Ads prompt.
     */
    private suspend fun presentInterstitial(shown: AdOutcome = AdOutcome.Shown): Boolean {
        // Consent, availability and load failures all fall open to the result.
        if (!consent.canRequestAds.value) return record(AdOutcome.ConsentRequired)

        val result = runCatching { ads.showIfReady() }.getOrElse { InterstitialResult.Failed }
        if (result == InterstitialResult.DisplayedAndDismissed) {
            // Refill while the result screen is up so the next eligible blessing is warm.
            runCatching { ads.preload() }
            return record(shown, true)
        }
        return record(
            when (result) {
                InterstitialResult.Unavailable -> AdOutcome.Unavailable
                InterstitialResult.ConsentRequired -> AdOutcome.ConsentRequired
                else -> AdOutcome.Failed
            }
        )
    }

    /** Publishes [outcome] and returns [shown], so callers can `return record(...)` directly. */
    private fun record(outcome: AdOutcome, shown: Boolean = false): Boolean {
        _lastAdOutcome.value = outcome
        debugLog(APP_TAG) { "Ad outcome: $outcome (shown=$shown)" }
        return shown
    }

    private suspend fun resolveEntitlement(): EntitlementState {
        // Debug override wins over the backend, so a paid/free customer can be simulated
        // against the dummy repository, a Test Store session, or a real one alike.
        debug.entitlementOverride.value?.let { return it }

        val current = purchases.entitlementState.value
        if (current != EntitlementState.Unknown) return current
        return runCatching { purchases.refresh() }
            .map { purchases.entitlementState.value }
            .getOrElse { EntitlementState.Unknown }
    }

    private suspend fun preloadIfAllowed() {
        if (debug.entitlementOverride.value == EntitlementState.Purchased) return
        if (purchases.entitlementState.value == EntitlementState.Purchased) return
        if (!consent.canRequestAds.value) return
        runCatching { ads.preload() }
    }
}
