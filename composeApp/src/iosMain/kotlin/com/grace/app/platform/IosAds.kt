package com.grace.app.platform

import kotlin.concurrent.Volatile

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume

/**
 * [AdConsentService] backed by the Swift UMP bridge.
 *
 * When the Google User Messaging Platform Swift package is not linked (or the bridge has
 * not been installed) the service reports "cannot request ads", which makes the shared
 * coordinator skip advertising entirely instead of guessing.
 */
class IosAdConsentService(
    private val appId: String,
    /**
     * True when this build serves Google's **demo** ad units. Those IDs are not registered
     * with UMP, so it can never return a decision for them and the strict gate would keep
     * every demo interstitial from ever being requested. Demo inventory is neither real nor
     * billable, so only here does an inconclusive answer fall open. Production IDs are
     * unaffected.
     */
    private val sampleAds: Boolean = false
) : AdConsentService {

    private val _canRequestAds = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> =
        _privacyOptionsRequired.asStateFlow()

    override suspend fun refreshConsent() {
        val bridge = IosMonetizationBridgeHolder.bridge ?: return
        // MobileAds.shared.start() must complete before UMP is asked anything. It used to be
        // called only from preload(), which is itself gated on consent — so it could never
        // run first and consent requests raced an unstarted SDK.
        bridge.awaitStart(appId)
        apply(bridge.awaitRefreshConsent())
    }

    override suspend fun showPrivacyOptions() {
        val bridge = IosMonetizationBridgeHolder.bridge ?: return
        apply(bridge.awaitShowPrivacyOptions())
    }

    private fun apply(state: ConsentState) {
        // UMP either answered or produced nothing to ask; with demo IDs the latter is the
        // normal case. See resolveCanRequestAds for why that is safe only for demo inventory.
        val canRequest = resolveCanRequestAds(
            reportedCanRequest = state.canRequestAds,
            inconclusive = !state.canRequestAds && !state.privacyRequired,
            sampleAds = sampleAds
        )
        _canRequestAds.value = canRequest
        _privacyOptionsRequired.value = state.privacyRequired
        IosAdConsentBridgeState.canRequestAds = canRequest
    }

    private data class ConsentState(val canRequestAds: Boolean, val privacyRequired: Boolean)

    private suspend fun IosAdBridge.awaitStart(appId: String): Unit =
        suspendCancellableCoroutine { cont ->
            start(appId) { if (cont.isActive) cont.resume(Unit) }
        }

    private suspend fun IosAdBridge.awaitRefreshConsent(): ConsentState =
        suspendCancellableCoroutine { cont ->
            refreshConsent { can, privacy ->
                if (cont.isActive) cont.resume(ConsentState(can, privacy))
            }
        }

    private suspend fun IosAdBridge.awaitShowPrivacyOptions(): ConsentState =
        suspendCancellableCoroutine { cont ->
            showPrivacyOptions { can, privacy ->
                if (cont.isActive) cont.resume(ConsentState(can, privacy))
            }
        }
}

/**
 * [InterstitialAdService] backed by the Swift Google Mobile Ads bridge.
 *
 * [showIfReady] suspends until the native side reports the ad was dismissed, failed, or
 * was never available — every non-dismissal result falls open to the blessed photo.
 */
class IosInterstitialAdService(
    private val appId: String,
    private val unitId: String
) : InterstitialAdService {

    override suspend fun preload() {
        val bridge = IosMonetizationBridgeHolder.bridge ?: return
        bridge.awaitStart(appId)
        bridge.preloadInterstitial(unitId)
    }

    override suspend fun showIfReady(): InterstitialResult {
        val bridge = IosMonetizationBridgeHolder.bridge ?: return InterstitialResult.Unavailable
        if (!IosAdConsentBridgeState.canRequestAds) return InterstitialResult.ConsentRequired

        return suspendCancellableCoroutine { cont ->
            bridge.showInterstitial(unitId) { code ->
                if (cont.isActive) cont.resume(code.toInterstitialResult())
            }
        }
    }

    private fun Int.toInterstitialResult(): InterstitialResult = when (this) {
        0 -> InterstitialResult.DisplayedAndDismissed
        1 -> InterstitialResult.Unavailable
        else -> InterstitialResult.Failed
    }

    private suspend fun IosAdBridge.awaitStart(appId: String): Unit =
        suspendCancellableCoroutine { cont ->
            start(appId) { if (cont.isActive) cont.resume(Unit) }
        }
}

/**
 * Mirrors the last consent state the Swift bridge reported, so the interstitial service can
 * refuse to present before UMP has granted ad requests.
 */
object IosAdConsentBridgeState {
    @Volatile
    var canRequestAds: Boolean = false
}
