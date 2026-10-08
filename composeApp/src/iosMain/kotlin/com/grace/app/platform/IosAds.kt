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
class IosAdConsentService : AdConsentService {

    private val _canRequestAds = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> =
        _privacyOptionsRequired.asStateFlow()

    override suspend fun refreshConsent() {
        val bridge = IosMonetizationBridgeHolder.bridge ?: return
        apply(bridge.awaitRefreshConsent())
    }

    override suspend fun showPrivacyOptions() {
        val bridge = IosMonetizationBridgeHolder.bridge ?: return
        apply(bridge.awaitShowPrivacyOptions())
    }

    private fun apply(state: ConsentState) {
        _canRequestAds.value = state.canRequestAds
        _privacyOptionsRequired.value = state.privacyRequired
        IosAdConsentBridgeState.canRequestAds = state.canRequestAds
    }

    private data class ConsentState(val canRequestAds: Boolean, val privacyRequired: Boolean)

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
        bridge.start(appId) { }
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
}

/**
 * Mirrors the last consent state the Swift bridge reported, so the interstitial service can
 * refuse to present before UMP has granted ad requests.
 */
object IosAdConsentBridgeState {
    @Volatile
    var canRequestAds: Boolean = false
}
