package com.grace.app.platform

import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.grace.app.core.GraceConstants.APP_TAG
import com.grace.app.core.debugLog
import com.grace.app.core.logError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot Google Mobile Ads Next-Gen bootstrap.
 *
 * Runs on a background thread (required — a synchronous call can ANR).
 *
 * The request configuration is handed to [InitializationConfig.Builder.setRequestConfiguration],
 * which is the only supported way to install it. Calling the static
 * `MobileAds.setRequestConfiguration(...)` first is **not** a way of getting ahead of
 * initialization — the SDK throws `MobileAds.initialize must be called before using the
 * Google Mobile Ads SDK`, initialization never happens, and every later ad request then fails
 * with that same error. That single call was why no ad ever appeared on Android. Because the
 * configuration rides along with the initialization call itself, no ad can be requested ahead
 * of it anyway.
 *
 * Publisher privacy personalization is `DISABLED`, which asks for contextual /
 * non-personalized inventory. Child-directed treatment stays unset because Grace is a
 * general-audience app; the Play/App Store age ratings must agree with that.
 */
object AdMobBootstrap {

    private val initialized = AtomicBoolean(false)

    /**
     * Completes once [MobileAds.initialize] has returned (successfully or not).
     *
     * UMP and the ad preloader must not be touched before the SDK is up:
     * `MonetizationCoordinator.start()` runs from the Compose tree right after
     * `MainActivity.onCreate`, so without this they race the background init and fail. The
     * outcome is *not* raised to the caller — see [isInitialized] for diagnostics.
     */
    private val ready = CompletableDeferred<Unit>()

    /**
     * Whether [MobileAds.initialize] actually completed. Only ever read by diagnostics:
     * every failure path in the app falls open to showing the blessed photo.
     */
    @Volatile
    var isInitialized: Boolean = false
        private set

    /**
     * Suspends until initialization finished, capped at [timeoutMillis] so a wedged SDK can
     * never stall the launch. Never throws: on timeout or failure the caller simply proceeds
     * and reports whatever the SDK says.
     */
    suspend fun awaitInitialized(timeoutMillis: Long = 10_000L) {
        withTimeoutOrNull(timeoutMillis) { ready.await() }
    }

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        val requestConfiguration = RequestConfiguration.Builder()
            .setPublisherPrivacyPersonalizationState(
                RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED
            )
            .build()

        Thread {
            runCatching {
                MobileAds.initialize(
                    context.applicationContext,
                    InitializationConfig.Builder(BuildConfigHolder.adMobAppId)
                        .setRequestConfiguration(requestConfiguration)
                        .build()
                ) { debugLog(APP_TAG) { "Google Mobile Ads adapters initialized" } }
                // The call above returns once the SDK itself is initialized; the listener is
                // only for mediation adapters, which we do not need before an ad request.
                isInitialized = true
                debugLog(APP_TAG) { "Google Mobile Ads initialized (appId=${BuildConfigHolder.adMobAppId})" }
            }.onFailure {
                // Retriable: a transient init failure must not leave the app ad-less forever.
                initialized.set(false)
                isInitialized = false
                logError(APP_TAG, "Google Mobile Ads init failed: ${it.message}")
            }
            // Always release waiters, otherwise awaitInitialized() burns its full timeout.
            ready.complete(Unit)
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
