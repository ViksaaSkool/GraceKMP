package com.grace.app.core

/**
 * Build-time monetization identifiers.
 *
 * Debug builds ship Google's official **test** AdMob IDs so development traffic is
 * always billed as test inventory. Release builds read the production values from
 * `local.properties` / the environment (see `docs/monetization-setup.md`) and are
 * refused by a Gradle guard while any value is still a placeholder.
 *
 * RevenueCat has no generic public test key, so its absence is represented by
 * [REVENUECAT_PLACEHOLDER] rather than an invented value.
 */
data class MonetizationConfig(
    /** `ca-app-pub-…~…` for the platform this build targets. */
    val adMobAppId: String,
    /** Interstitial unit for this platform. */
    val interstitialUnitId: String,
    /** RevenueCat public SDK key, or [REVENUECAT_PLACEHOLDER]. */
    val revenueCatApiKey: String
) {
    /** True while RevenueCat is still an unset placeholder. */
    val revenueCatPlaceholder: Boolean
        get() = !revenueCatApiKey.isValidRevenueCatKey

    /**
     * True when the key is a RevenueCat **Test Store** key (`test_…`).
     *
     * Test Store exercises the real `RevenueCatPurchasesRepository` end to end — real
     * `CustomerInfo`, real entitlements, real dashboard rows — with no App Store Connect
     * and no Play Console account. It is development-only: the SDK crashes on purpose when
     * it finds one in a release build, and the Gradle guard refuses one even earlier.
     */
    val usesTestStoreKey: Boolean
        get() = revenueCatApiKey.startsWith(TEST_STORE_KEY_PREFIX)

    /**
     * True when the ad identifiers are still Google's sample values. Sample IDs are
     * always valid to *run*, so this only matters for release validation.
     */
    val usesSampleAdIds: Boolean
        get() = adMobAppId.isGoogleSampleAdMobId || interstitialUnitId.isGoogleSampleAdUnitId

    /** True when purchases can actually be attempted in this build. */
    val purchasesAvailable: Boolean get() = !revenueCatPlaceholder

    companion object {
        const val REVENUECAT_PLACEHOLDER = "REPLACE_ME"

        /** RevenueCat Test Store keys are `test_…`; real platform keys are `appl_`/`goog_`. */
        const val TEST_STORE_KEY_PREFIX = "test_"

        /** Google's sample AdMob app IDs. */
        const val SAMPLE_ANDROID_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        const val SAMPLE_IOS_APP_ID = "ca-app-pub-3940256099942544~1458002511"

        /** Google's sample interstitial unit ID (valid on both platforms). */
        const val SAMPLE_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

        /**
         * Resolves a RevenueCat key, treating blank/unset values as the placeholder so a
         * clean checkout never silently configures the SDK with garbage.
         */
        fun revenueCatKey(raw: String?): String =
            raw?.trim()?.takeIf { it.isNotEmpty() } ?: REVENUECAT_PLACEHOLDER
    }
}

private val String.isValidRevenueCatKey: Boolean
    get() = this != MonetizationConfig.REVENUECAT_PLACEHOLDER && length > 20 && contains('_')

private val String.isGoogleSampleAdMobId: Boolean
    get() = this == MonetizationConfig.SAMPLE_ANDROID_APP_ID ||
        this == MonetizationConfig.SAMPLE_IOS_APP_ID

private val String.isGoogleSampleAdUnitId: Boolean
    get() = this == MonetizationConfig.SAMPLE_INTERSTITIAL_UNIT_ID
