package com.grace.app.platform

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.grace.app.core.GraceConstants.APP_TAG
import com.grace.app.core.debugLog
import com.grace.app.core.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Google Mobile Ads Next-Gen interstitial on Android.
 *
 * Uses the SDK's preloader, which keeps a small warm cache and refills automatically when
 * an ad is polled. [showIfReady] suspends until the customer closes the ad (or until the
 * presentation fails), and never throws — so the blessed photo is never withheld by an ad
 * problem.
 *
 * **The cache is an optimisation, not the only source.** If nothing is preloaded — first
 * launch after a fresh install, a preload that failed, consent that only arrived after
 * [preload] was gated off — [showIfReady] loads an ad on demand and presents that instead.
 * Polling alone was the reason no ad ever appeared: an empty cache returned
 * [InterstitialResult.Unavailable] on every single blessing, forever, with nothing logged.
 */
class AndroidInterstitialAdService(
    private val activityProvider: () -> Activity?,
    private val consent: AdConsentService,
    private val unitId: String
) : InterstitialAdService {

    override suspend fun preload() {
        if (!consent.canRequestAds.value) return
        if (!AdMobBootstrap.isInitialized) {
            // Every SDK call below throws in this state, so say so once and loudly instead of
            // letting the failure look like "no ad available".
            debugLog(APP_TAG) { "Skipping preload: Google Mobile Ads is not initialized" }
            return
        }
        runCatching {
            val request = AdRequest.Builder(unitId).build()
            val started = InterstitialAdPreloader.start(unitId, PreloadConfiguration(request))
            debugLog(APP_TAG) {
                "Interstitial preload for $unitId: started=$started " +
                    "available=${InterstitialAdPreloader.isAdAvailable(unitId)}"
            }
        }.onFailure { logError(APP_TAG, "Interstitial preload failed: ${it.message}") }
    }

    override suspend fun showIfReady(): InterstitialResult {
        if (!consent.canRequestAds.value) return InterstitialResult.ConsentRequired
        if (!AdMobBootstrap.isInitialized) return InterstitialResult.Failed

        val activity = activityProvider() ?: return InterstitialResult.Failed

        val cached = runCatching { InterstitialAdPreloader.pollAd(unitId) }.getOrNull()
        if (cached == null) {
            debugLog(APP_TAG) { "Interstitial cache empty for $unitId, loading on demand" }
            // Nothing warm — do not skip the ad, go and get one. This is the path that makes
            // the first ad-eligible blessing show anything at all.
            val loaded = loadOnDemand(activity)
            if (loaded == null) {
                preload()
                return InterstitialResult.Unavailable
            }
            return present(activity, loaded).also { preload() }
        }

        return present(activity, cached).also { preload() }
    }

    /**
     * One-shot `InterstitialAd.load`. Resolves to `null` when the ad server has nothing to
     * offer (no network, no Play services, invalid ad unit) — the caller treats that as
     * [InterstitialResult.Unavailable] and shows the photo regardless.
     */
    private suspend fun loadOnDemand(activity: Activity): InterstitialAd? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val request = AdRequest.Builder(unitId).build()
                runCatching {
                    InterstitialAd.load(
                        request,
                        object : AdLoadCallback<InterstitialAd> {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                debugLog(APP_TAG) { "Interstitial loaded on demand for $unitId" }
                                if (cont.isActive) cont.resume(ad)
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                logError(
                                    APP_TAG,
                                    "Interstitial on-demand load failed " +
                                        "(${error.code}): ${error.message}"
                                )
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    )
                }.onFailure {
                    logError(APP_TAG, "Interstitial load threw: ${it.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

    /**
     * Presents [ad] and suspends until it is dismissed or fails. Resumes `true` only for a
     * genuine dismissal, which is the sole trigger for the Remove Ads prompt.
     */
    private suspend fun present(activity: Activity, ad: InterstitialAd): InterstitialResult =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                var settled = false
                fun settle(result: InterstitialResult) {
                    if (settled || !cont.isActive) return
                    settled = true
                    cont.resume(result)
                }

                ad.adEventCallback = object : InterstitialAdEventCallback {
                    override fun onAdDismissedFullScreenContent() {
                        settle(InterstitialResult.DisplayedAndDismissed)
                    }

                    override fun onAdFailedToShowFullScreenContent(
                        fullScreenContentError: FullScreenContentError
                    ) {
                        logError(
                            APP_TAG,
                            "Interstitial failed to show: ${fullScreenContentError.message}"
                        )
                        settle(InterstitialResult.Failed)
                    }

                    override fun onAdShowedFullScreenContent() {
                        debugLog(APP_TAG) { "Interstitial shown" }
                    }

                    override fun onAdImpression() = Unit
                    override fun onAdClicked() = Unit
                }

                runCatching { ad.show(activity) }.onFailure {
                    logError(APP_TAG, "Interstitial show() threw: ${it.message}")
                    settle(InterstitialResult.Failed)
                }
            }
        }
}