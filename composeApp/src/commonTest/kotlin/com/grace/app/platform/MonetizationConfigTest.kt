package com.grace.app.platform

import com.grace.app.core.MonetizationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Placeholder policy: debug must always be runnable with Google's sample IDs, and the
 * release guard must be able to tell a real configuration from a placeholder one.
 */
class MonetizationConfigTest {

    private fun config(
        appId: String = MonetizationConfig.SAMPLE_ANDROID_APP_ID,
        unitId: String = MonetizationConfig.SAMPLE_INTERSTITIAL_UNIT_ID,
        rcKey: String = MonetizationConfig.REVENUECAT_PLACEHOLDER
    ) = MonetizationConfig(
        adMobAppId = appId,
        interstitialUnitId = unitId,
        revenueCatApiKey = rcKey
    )

    @Test
    fun theSampleIdsAreRecognisedAsSamples() {
        assertTrue(config().usesSampleAdIds)
        assertTrue(
            config(appId = MonetizationConfig.SAMPLE_IOS_APP_ID).usesSampleAdIds
        )
    }

    @Test
    fun productionIdsAreNotReportedAsSamples() {
        assertFalse(
            config(
                appId = "ca-app-pub-1111111111111111~2222222222",
                unitId = "ca-app-pub-1111111111111111/3333333333"
            ).usesSampleAdIds
        )
    }

    @Test
    fun thePlaceholderRevenueCatKeyMeansPurchasesAreUnavailable() {
        assertTrue(config().revenueCatPlaceholder)
        assertFalse(config().purchasesAvailable)
    }

    @Test
    fun aRealisticRevenueCatKeyEnablesPurchases() {
        val c = config(
            appId = "ca-app-pub-1111111111111111~2222222222",
            unitId = "ca-app-pub-1111111111111111/3333333333",
            rcKey = "appl_this_is_a_long_public_sdk_key_value"
        )

        assertFalse(c.revenueCatPlaceholder)
        assertTrue(c.purchasesAvailable)
        assertFalse(c.usesSampleAdIds)
    }

    @Test
    fun blankOrPlaceholderRevenueCatKeysNormaliseToThePlaceholder() {
        assertEquals(
            MonetizationConfig.REVENUECAT_PLACEHOLDER,
            MonetizationConfig.revenueCatKey(null)
        )
        assertEquals(
            MonetizationConfig.REVENUECAT_PLACEHOLDER,
            MonetizationConfig.revenueCatKey("   ")
        )
        assertEquals(
            MonetizationConfig.REVENUECAT_PLACEHOLDER,
            MonetizationConfig.revenueCatKey(MonetizationConfig.REVENUECAT_PLACEHOLDER)
        )
    }

    @Test
    fun aRealKeyIsPassedThroughUnchanged() {
        val key = "appl_aaaaaaaaaaaaaaaaaaaa"
        assertEquals(key, MonetizationConfig.revenueCatKey(key))
    }

    @Test
    fun sampleIdsAreValidLookingAdMobIdentifiers() {
        assertTrue(
            MonetizationConfig.SAMPLE_ANDROID_APP_ID.startsWith("ca-app-pub-")
        )
        assertTrue(
            MonetizationConfig.SAMPLE_INTERSTITIAL_UNIT_ID.startsWith("ca-app-pub-")
        )
    }
}
