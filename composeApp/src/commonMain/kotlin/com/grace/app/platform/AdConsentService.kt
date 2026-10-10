package com.grace.app.platform

import com.grace.app.core.MonetizationConfig
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

/**
 * Resolves the shared consent gate from what the platform UMP bridge reported.
 *
 * Kept in `commonMain` so both platforms apply the identical rule and it can be pinned by
 * tests instead of drifting apart.
 *
 * The distinction that matters is **withheld** vs **inconclusive**:
 * - *withheld* — UMP positively said no, or the user was presented a form and declined.
 *   Honoured on every build; no ad is requested.
 * - *inconclusive* — UMP never produced an answer at all (SDK not ready, network error, or
 *   no consent message exists for this app id). Google's **demo** ad units (`…9942544…`) are
 *   not registered with UMP, so this is the *only* state they can ever reach — and without
 *   this rule the strict gate keeps every demo interstitial from ever being requested.
 *   Demo inventory is neither real nor billable, so falling open there costs nothing and is
 *   scoped by [MonetizationConfig.usesSampleAdIds].
 *
 * With production IDs [sampleAds] is `false` and inconclusive stays closed, exactly as before.
 */
fun resolveCanRequestAds(
    reportedCanRequest: Boolean,
    inconclusive: Boolean,
    sampleAds: Boolean
): Boolean = reportedCanRequest || (inconclusive && sampleAds)
