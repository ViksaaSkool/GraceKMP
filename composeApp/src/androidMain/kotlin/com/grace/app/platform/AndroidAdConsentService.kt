package com.grace.app.platform

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
import com.google.android.ump.ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
import com.grace.app.core.DebugGeography as GraceDebugGeography
import com.grace.app.core.GraceConstants.APP_TAG
import com.grace.app.core.MonetizationDebug
import com.grace.app.core.debugLog
import com.grace.app.core.logError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google UMP consent on Android.
 *
 * Refresh runs on every launch (the coordinator calls it from `MonetizationCoordinator.start`),
 * asks UMP for fresh state rather than reading a cached consent string, presents any
 * required form, and republishes `canRequestAds` / `privacyOptionsRequired` afterwards.
 */
class AndroidAdConsentService(
    private val activityProvider: () -> Activity?,
    private val debug: MonetizationDebug = MonetizationDebug(),
    /**
     * True when this build serves Google's **demo** ad units (`…9942544…`).
     *
     * Those IDs are not registered with UMP, so the consent service can never obtain a
     * decision for them and would otherwise report "cannot request ads" forever — which is
     * exactly what happened: demo interstitials never showed in debug. Demo inventory is
     * neither real nor billable, so in that case only, an inconclusive UMP answer falls open.
     * Production IDs are unaffected and keep the strict gate.
     */
    private val sampleAds: Boolean = false
) : AdConsentService {

    private val _canRequestAds = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> =
        _privacyOptionsRequired.asStateFlow()

    private val consentInformation: ConsentInformation?
        get() = activityProvider()?.let { UserMessagingPlatform.getConsentInformation(it) }

    override suspend fun refreshConsent() {
        // UMP reads state owned by the Mobile Ads SDK, so never ask it to answer before
        // AdMobBootstrap has finished. Bounded, so a wedged init cannot stall the launch.
        AdMobBootstrap.awaitInitialized()

        val activity = activityProvider()
        val information = activity?.let { UserMessagingPlatform.getConsentInformation(it) }
        if (activity == null || information == null) {
            publish(information, inconclusive = true)
            return
        }

        val updated = runCatching {
            information.awaitRequestConsentInfoUpdate(activity)
        }.onFailure {
            logError(APP_TAG, "UMP consent refresh failed: ${it.message}")
        }.getOrDefault(false)

        if (!updated) {
            publish(information, inconclusive = true)
            return
        }

        // Present the form if UMP still needs a decision before ads can be requested.
        if (!information.canRequestAds() && information.isConsentFormAvailable) {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (error != null) {
                    logError(APP_TAG, "UMP consent form failed: ${error.message}")
                }
                publish(information, inconclusive = error != null)
            }
        } else {
            // No form to show and still "cannot request ads" means UMP had nothing to ask —
            // the demo-app-ID case, not a withdrawn consent.
            publish(information, inconclusive = !information.canRequestAds())
        }
    }

    override suspend fun showPrivacyOptions() {
        val activity = activityProvider() ?: return
        val information = UserMessagingPlatform.getConsentInformation(activity)
        if (information.privacyOptionsRequirementStatus !=
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ) {
            publish(information, inconclusive = false)
            return
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                logError(APP_TAG, "UMP privacy options failed: ${error.message}")
            }
            publish(information, inconclusive = false)
        }
    }

    /**
     * @param inconclusive `true` when UMP failed to produce an answer at all (not initialized,
     * network error, demo app ID with no consent message) rather than when it positively
     * reported a withheld decision. Only an inconclusive answer may fall open, and only for
     * demo ad units — see [sampleAds].
     */
    private fun publish(information: ConsentInformation?, inconclusive: Boolean) {
        val reported = information?.canRequestAds() ?: false
        val canRequest = resolveCanRequestAds(reported, inconclusive, sampleAds)
        val privacyRequired = information?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        _canRequestAds.value = canRequest
        _privacyOptionsRequired.value = privacyRequired
        debugLog(APP_TAG) {
            "UMP state: canRequestAds=$canRequest privacyRequired=$privacyRequired " +
                "(reported=$reported inconclusive=$inconclusive sampleAds=$sampleAds)"
        }
    }

    /**
     * Suspends until UMP has refreshed its consent info. Returns `false` when the update
     * failed, so the caller can tell "no decision" apart from "consent withheld".
     */
    private suspend fun ConsentInformation.awaitRequestConsentInfoUpdate(
        activity: Activity
    ): Boolean = suspendCancellableCoroutine { cont ->
        // UMP 4.x moved debug geography off ConsentRequestParameters.Builder onto
        // ConsentDebugSettings; the constants live on the DebugGeography annotation.
        fun debugSettings(): ConsentDebugSettings? {
            if (!debug.enabled) return null
            val context = activityProvider()?.applicationContext ?: return null
            val geography = when (debug.debugGeography.value) {
                GraceDebugGeography.EEA -> DEBUG_GEOGRAPHY_EEA
                GraceDebugGeography.US -> DEBUG_GEOGRAPHY_REGULATED_US_STATE
                GraceDebugGeography.Off -> return null
            }
            return ConsentDebugSettings.Builder(context)
                .setDebugGeography(geography)
                .build()
        }
        val parameters = ConsentRequestParameters.Builder()
            .apply { debugSettings()?.let { setConsentDebugSettings(it) } }
            .build()
        requestConsentInfoUpdate(
            activity,
            parameters,
            { if (cont.isActive) cont.resume(true) },
            { error: FormError ->
                logError(APP_TAG, "UMP requestConsentInfoUpdate failed: ${error.message}")
                if (cont.isActive) cont.resume(false)
            }
        )
    }
}
