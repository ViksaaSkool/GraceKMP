package com.grace.app.platform

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.grace.app.core.GraceConstants.APP_TAG
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
    private val activityProvider: () -> Activity?
) : AdConsentService {

    private val _canRequestAds = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> =
        _privacyOptionsRequired.asStateFlow()

    private val consentInformation: ConsentInformation?
        get() = activityProvider()?.let { UserMessagingPlatform.getConsentInformation(it) }

    override suspend fun refreshConsent() {
        val activity = activityProvider()
        val information = activity?.let { UserMessagingPlatform.getConsentInformation(it) }
        if (activity == null || information == null) {
            publish(information)
            return
        }

        runCatching {
            information.awaitRequestConsentInfoUpdate(activity)
            // Present the form if UMP still needs a decision before ads can be requested.
            if (!information.canRequestAds() && information.isConsentFormAvailable) {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) {
                        logError(APP_TAG, "UMP consent form failed: ${error.message}")
                    }
                    publish(information)
                }
            } else {
                publish(information)
            }
        }.onFailure {
            logError(APP_TAG, "UMP consent refresh failed: ${it.message}")
            publish(information)
        }
    }

    override suspend fun showPrivacyOptions() {
        val activity = activityProvider() ?: return
        val information = UserMessagingPlatform.getConsentInformation(activity)
        if (information.privacyOptionsRequirementStatus !=
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ) {
            publish(information)
            return
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                logError(APP_TAG, "UMP privacy options failed: ${error.message}")
            }
            publish(information)
        }
    }

    private fun publish(information: ConsentInformation?) {
        val canRequest = information?.canRequestAds() ?: false
        val privacyRequired = information?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        _canRequestAds.value = canRequest
        _privacyOptionsRequired.value = privacyRequired
        debugLog(APP_TAG) { "UMP state: canRequestAds=$canRequest privacyRequired=$privacyRequired" }
    }

    private suspend fun ConsentInformation.awaitRequestConsentInfoUpdate(
        activity: Activity
    ): Unit = suspendCancellableCoroutine { cont ->
        val parameters = ConsentRequestParameters.Builder().build()
        requestConsentInfoUpdate(
            activity,
            parameters,
            { if (cont.isActive) cont.resume(Unit) },
            { error: FormError ->
                logError(APP_TAG, "UMP requestConsentInfoUpdate failed: ${error.message}")
                if (cont.isActive) cont.resume(Unit)
            }
        )
    }
}
