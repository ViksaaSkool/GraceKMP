package com.grace.app.platform

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.grace.app.core.GraceConstants.APP_TAG
import com.grace.app.core.debugLog
import com.grace.app.core.logError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Mobile Ads Next-Gen interstitial on Android.
 *
 * Uses the SDK's preloader, which keeps a small warm cache and refills automatically when
 * an ad is polled. [showIfReady] suspends until the customer closes the ad (or until the
 * presentation fails), and never throws — a cold cache simply reports
 * [InterstitialResult.Unavailable] so the blessed result is shown immediately.
 */
class AndroidInterstitialAdService(
    private val activityProvider: () -> Activity?,
    private val consent: AdConsentService,
    private val unitId: String
) : InterstitialAdService {

    override suspend fun preload() {
        if (!consent.canRequestAds.value) return
        runCatching {
            val request = AdRequest.Builder(unitId).build()
            InterstitialAdPreloader.start(unitId, PreloadConfiguration(request))
            debugLog(APP_TAG) { "Interstitial preloading started for $unitId" }
        }.onFailure { logError(APP_TAG, "Interstitial preload failed: ${it.message}") }
    }

    override suspend fun showIfReady(): InterstitialResult {
        if (!consent.canRequestAds.value) return InterstitialResult.ConsentRequired

        val activity = activityProvider() ?: return InterstitialResult.Failed

        val ad = runCatching { InterstitialAdPreloader.pollAd(unitId) }.getOrNull()
            ?: return InterstitialResult.Unavailable

        val result = suspendCancellableCoroutine { cont ->
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

                override fun onAdShowedFullScreenContent() = Unit
                override fun onAdImpression() = Unit
                override fun onAdClicked() = Unit
            }

            runCatching { ad.show(activity) }.onFailure {
                logError(APP_TAG, "Interstitial show() threw: ${it.message}")
                settle(InterstitialResult.Failed)
            }
        }

        // Refill once the customer is back in the app, so the next eligible blessing
        // has a warm ad waiting. `pollAd` also triggers the preloader's auto-reload;
        // this call is idempotent and keeps the state correct across cold starts.
        preload()
        return result
    }
}
