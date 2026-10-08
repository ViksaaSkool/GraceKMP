package com.grace.app.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Google UMP consent seam.
 *
 * Consent state is never read from a locally stored string — [refreshConsent] asks the SDK
 * for the current status on every launch, and [canRequestAds] is only trusted as reported
 * by UMP. The app requests contextual/non-personalized ads, so a denied grant simply means
 * no ad is requested at all.
 */
interface AdConsentService {
    /** `true` only once UMP reports ads may be requested. */
    val canRequestAds: StateFlow<Boolean>

    /** `true` when a publisher-rendered "Privacy Choices" entry point must be shown. */
    val privacyOptionsRequired: StateFlow<Boolean>

    /** Requests fresh consent info and presents any required form. Call on every launch. */
    suspend fun refreshConsent()

    /** Opens the UMP privacy-options form from Settings. */
    suspend fun showPrivacyOptions()
}
