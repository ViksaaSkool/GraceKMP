package com.grace.app.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the shared consent gate.
 *
 * The bug this locks down: Google's demo ad units are not registered with UMP, so UMP can
 * never return a decision for them. Treating "no decision" as "no ads" meant demo interstitials
 * were never requested at all — indistinguishable from a broken SDK. Production IDs must keep
 * the strict behaviour, so both halves are asserted here.
 */
class AdConsentGateTest {

    // ---- Production IDs: unchanged, strict --------------------------------------------

    @Test
    fun productionIdsHonourAGrant() {
        assertTrue(resolveCanRequestAds(reportedCanRequest = true, inconclusive = false, sampleAds = false))
    }

    @Test
    fun productionIdsRefuseWhenConsentIsWithheld() {
        assertFalse(resolveCanRequestAds(reportedCanRequest = false, inconclusive = false, sampleAds = false))
    }

    @Test
    fun productionIdsRefuseWhenUmpIsInconclusive() {
        assertFalse(resolveCanRequestAds(reportedCanRequest = false, inconclusive = true, sampleAds = false))
    }

    // ---- Demo IDs: only an inconclusive answer falls open ------------------------------

    @Test
    fun demoIdsStillHonourAGrant() {
        assertTrue(resolveCanRequestAds(reportedCanRequest = true, inconclusive = true, sampleAds = true))
    }

    @Test
    fun demoIdsFallOpenWhenUmpCannotAnswer() {
        // The demo app has no consent message, so this is its only reachable state.
        assertTrue(resolveCanRequestAds(reportedCanRequest = false, inconclusive = true, sampleAds = true))
    }

    @Test
    fun demoIdsNeverOverrideARealRefusal() {
        // A declined form is a decision, not a failure to reach one.
        assertFalse(resolveCanRequestAds(reportedCanRequest = false, inconclusive = false, sampleAds = true))
    }
}