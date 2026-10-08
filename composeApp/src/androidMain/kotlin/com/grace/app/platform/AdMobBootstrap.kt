package com.grace.app.platform

import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.grace.app.core.GraceConstants.APP_TAG
import com.grace.app.core.debugLog
import com.grace.app.core.logError
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot Google Mobile Ads Next-Gen bootstrap.
 *
 * Runs on a background thread (required — a synchronous call can ANR) and installs the
 * request configuration **before** initialization so no ad can be requested ahead of it:
 * publisher privacy personalization is `DISABLED`, which asks for contextual /
 * non-personalized inventory. Child-directed treatment stays unset because Grace is a
 * general-audience app; the Play/App Store age ratings must agree with that.
 */
object AdMobBootstrap {

    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        val requestConfiguration = RequestConfiguration.Builder()
            .setPublisherPrivacyPersonalizationState(
                RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED
            )
            .build()

        Thread {
            runCatching {
                MobileAds.setRequestConfiguration(requestConfiguration)
                MobileAds.initialize(
                    context.applicationContext,
                    InitializationConfig.Builder(BuildConfigHolder.adMobAppId)
                        .setRequestConfiguration(requestConfiguration)
                        .build()
                ) { debugLog(APP_TAG) { "Google Mobile Ads initialized" } }
            }.onFailure {
                initialized.set(false)
                logError(APP_TAG, "Google Mobile Ads init failed: ${it.message}")
            }
        }.start()
    }
}

/**
 * Holds the AdMob application ID for [AdMobBootstrap]. It is supplied by the DI graph
 * through [setAppId], which `MainActivity` calls before initialization so the value comes
 * from the same `MonetizationConfig` the rest of the app uses.
 */
object BuildConfigHolder {
    @Volatile
    var adMobAppId: String = MonetizationDefaults.SAMPLE_ANDROID_APP_ID

    fun setAppId(appId: String) {
        adMobAppId = appId
    }
}

private object MonetizationDefaults {
    const val SAMPLE_ANDROID_APP_ID =
        com.grace.app.core.MonetizationConfig.SAMPLE_ANDROID_APP_ID
}
