package com.grace.app.domain.monetization

import com.grace.app.core.MonetizationDebug
import com.grace.app.data.SettingsStore
import com.grace.app.platform.AdConsentService
import com.grace.app.platform.EntitlementState
import com.grace.app.platform.InterstitialAdService
import com.grace.app.platform.InterstitialResult
import com.grace.app.platform.PurchaseResult
import com.grace.app.platform.PurchasesRepository
import com.grace.app.platform.RemoveAdsProduct
import com.grace.app.platform.RestoreResult
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The debug developer panel's effect on the ad decision.
 *
 * These switches exist so the interstitial cadence and the Remove Ads entitlement can be
 * exercised without blessing photos twice or without a store account. They are gated on
 * `MonetizationDebug.enabled` (= `isDebugBuild`), which is `true` in every unit-test run,
 * so the switches are live here exactly as they are in a debug build.
 */
class MonetizationCoordinatorDebugTest {

    private class FakePurchases(initial: EntitlementState) : PurchasesRepository {
        private val _entitlement = MutableStateFlow(initial)
        override val entitlementState: StateFlow<EntitlementState> = _entitlement.asStateFlow()

        private val _product = MutableStateFlow<RemoveAdsProduct?>(null)
        override val product: StateFlow<RemoveAdsProduct?> = _product.asStateFlow()

        private val _supportId = MutableStateFlow<String?>("TEST-USER")
        override val supportId: StateFlow<String?> = _supportId.asStateFlow()

        var refreshCalls = 0

        fun set(state: EntitlementState) {
            _entitlement.value = state
        }

        override suspend fun purchaseRemoveAds(): PurchaseResult = PurchaseResult.Unavailable
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.Unavailable
        override suspend fun refresh() {
            refreshCalls++
        }
    }

    private class FakeConsent(canRequest: Boolean) : AdConsentService {
        private val _canRequestAds = MutableStateFlow(canRequest)
        override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

        private val _privacy = MutableStateFlow(false)
        override val privacyOptionsRequired: StateFlow<Boolean> = _privacy.asStateFlow()

        override suspend fun refreshConsent() = Unit
        override suspend fun showPrivacyOptions() = Unit
    }

    private class FakeAds(
        private val result: InterstitialResult = InterstitialResult.DisplayedAndDismissed
    ) : InterstitialAdService {
        var showCalls = 0
        var preloadCalls = 0

        override suspend fun preload() {
            preloadCalls++
        }

        override suspend fun showIfReady(): InterstitialResult {
            showCalls++
            return result
        }
    }

    private fun coordinator(
        purchases: PurchasesRepository,
        consent: AdConsentService,
        ads: InterstitialAdService,
        settings: SettingsStore,
        debug: MonetizationDebug
    ) = MonetizationCoordinator(purchases, consent, ads, settings, debug)

    // ---- Force next interstitial --------------------------------------------

    @Test
    fun aForcedAdFiresOnAnOddBlessingThatWouldNormallySkipTheAd() = runTest {
        val purchases = FakePurchases(EntitlementState.Free)
        val ads = FakeAds()
        val debug = MonetizationDebug()

        // Blessing #1 is odd → normally no ad at all.
        val c = coordinator(purchases, FakeConsent(canRequest = true), ads, SettingsStore(MapSettings()), debug)
        assertFalse(c.onBlessingSucceeded())

        debug.requestForceNextInterstitial()
        assertTrue(c.onBlessingSucceeded())
        assertEquals(1, ads.showCalls)
    }

    @Test
    fun aForcedAdDoesNotAdvanceTheNaturalCadence() = runTest {
        val settings = SettingsStore(MapSettings())
        val debug = MonetizationDebug()
        val c = coordinator(
            FakePurchases(EntitlementState.Free),
            FakeConsent(canRequest = true),
            FakeAds(),
            settings,
            debug
        )

        // One real (odd) blessing + one forced ad must together still leave the counter at 1,
        // so the *next* natural blessing is #2 and remains ad-eligible.
        assertFalse(c.onBlessingSucceeded())
        debug.requestForceNextInterstitial()
        assertTrue(c.onBlessingSucceeded())

        assertEquals(1L, settings.freeBlessingCount)

        // #2 is the ad-eligible one, and it still fires.
        assertTrue(c.onBlessingSucceeded())
    }

    @Test
    fun theForceFlagIsConsumedExactlyOnce() = runTest {
        val debug = MonetizationDebug()
        val ads = FakeAds()
        val c = coordinator(
            FakePurchases(EntitlementState.Free),
            FakeConsent(canRequest = true),
            ads,
            SettingsStore(MapSettings()),
            debug
        )

        debug.requestForceNextInterstitial()
        assertTrue(c.onBlessingSucceeded())
        assertEquals(1, ads.showCalls)

        // The flag is gone and left the counter untouched, so the cadence resumes from #1:
        // odd, no ad. A single forced ad must never cascade into the following blessings.
        assertFalse(c.onBlessingSucceeded()) // #1 odd, nothing left to force
        assertEquals(1, ads.showCalls)

        // #2 is the naturally eligible one and it fires exactly once.
        assertTrue(c.onBlessingSucceeded())
        assertEquals(2, ads.showCalls)
    }

    @Test
    fun aForcedAdStillRespectsTheConsentGate() = runTest {
        val debug = MonetizationDebug()
        val ads = FakeAds()
        val c = coordinator(
            FakePurchases(EntitlementState.Free),
            FakeConsent(canRequest = false),
            ads,
            SettingsStore(MapSettings()),
            debug
        )

        debug.requestForceNextInterstitial()

        // Consent withheld → no ad, and the photo still appears.
        assertFalse(c.onBlessingSucceeded())
        assertEquals(0, ads.showCalls)
    }

    @Test
    fun aForcedAdReportsFailureTheSameWayTheCadencePathDoes() = runTest {
        val debug = MonetizationDebug()
        val c = coordinator(
            FakePurchases(EntitlementState.Free),
            FakeConsent(canRequest = true),
            FakeAds(InterstitialResult.Unavailable),
            SettingsStore(MapSettings()),
            debug
        )

        debug.requestForceNextInterstitial()
        assertFalse(c.onBlessingSucceeded())
    }

    // ---- Entitlement override ------------------------------------------------

    @Test
    fun anOverrideToPurchasedSuppressesAdsEvenWhenTheBackendSaysFree() = runTest {
        val debug = MonetizationDebug()
        val ads = FakeAds()
        val settings = SettingsStore(MapSettings())
        val c = coordinator(
            FakePurchases(EntitlementState.Free),
            FakeConsent(canRequest = true),
            ads,
            settings,
            debug
        )

        debug.setEntitlementOverride(EntitlementState.Purchased)

        repeat(4) { assertFalse(c.onBlessingSucceeded()) }

        assertEquals(0, ads.showCalls)
        assertEquals(0L, settings.freeBlessingCount)
    }

    @Test
    fun anOverrideToFreeRestoresTheCadenceOnAPurchasedBackend() = runTest {
        val debug = MonetizationDebug()
        val ads = FakeAds()
        val c = coordinator(
            FakePurchases(EntitlementState.Purchased),
            FakeConsent(canRequest = true),
            ads,
            SettingsStore(MapSettings()),
            debug
        )

        debug.setEntitlementOverride(EntitlementState.Free)

        assertFalse(c.onBlessingSucceeded()) // #1 odd
        assertTrue(c.onBlessingSucceeded())  // #2 even
    }

    @Test
    fun clearingTheOverrideDefersBackToTheBackend() = runTest {
        val purchases = FakePurchases(EntitlementState.Purchased)
        val debug = MonetizationDebug()
        val ads = FakeAds()
        val c = coordinator(
            purchases,
            FakeConsent(canRequest = true),
            ads,
            SettingsStore(MapSettings()),
            debug
        )

        debug.setEntitlementOverride(EntitlementState.Free)
        assertFalse(c.onBlessingSucceeded()) // #1 odd
        assertTrue(c.onBlessingSucceeded())  // #2 even, ad-eligible via the override

        debug.setEntitlementOverride(null)
        purchases.set(EntitlementState.Purchased)

        repeat(3) { assertFalse(c.onBlessingSucceeded()) }
    }

    @Test
    fun anOverrideToPurchasedSkipsPreloadingAtStartup() = runTest {
        val debug = MonetizationDebug()
        debug.setEntitlementOverride(EntitlementState.Purchased)

        val ads = FakeAds()
        val c = coordinator(
            FakePurchases(EntitlementState.Free),
            FakeConsent(canRequest = true),
            ads,
            SettingsStore(MapSettings()),
            debug
        )

        c.start()
        assertEquals(0, ads.preloadCalls)
    }

    // ---- Reset ---------------------------------------------------------------

    @Test
    fun resetClearsEverySwitch() = runTest {
        val debug = MonetizationDebug()
        debug.requestForceNextInterstitial()
        debug.setEntitlementOverride(EntitlementState.Purchased)
        debug.cycleDebugGeography()

        debug.reset()

        assertFalse(debug.forceNextInterstitial.value)
        assertNull(debug.entitlementOverride.value)
        assertEquals(
            com.grace.app.core.DebugGeography.Off,
            debug.debugGeography.value
        )
    }

    @Test
    fun theDebugGeographyCycleWalksOffEeaUs() {
        val debug = MonetizationDebug()

        assertEquals(com.grace.app.core.DebugGeography.Off, debug.debugGeography.value)
        debug.cycleDebugGeography()
        assertEquals(com.grace.app.core.DebugGeography.EEA, debug.debugGeography.value)
        debug.cycleDebugGeography()
        assertEquals(com.grace.app.core.DebugGeography.US, debug.debugGeography.value)
        debug.cycleDebugGeography()
        assertEquals(com.grace.app.core.DebugGeography.Off, debug.debugGeography.value)
    }

    // ---- refresh -------------------------------------------------------------

    @Test
    fun refreshPushesAReadThroughToTheBackend() = runTest {
        val purchases = FakePurchases(EntitlementState.Unknown)
        val c = coordinator(
            purchases,
            FakeConsent(canRequest = true),
            FakeAds(),
            SettingsStore(MapSettings()),
            MonetizationDebug()
        )

        c.refresh()

        assertEquals(1, purchases.refreshCalls)
    }

    @Test
    fun refreshSwallowsABackendFailure() = runTest {
        val failing = object : PurchasesRepository by FakePurchases(EntitlementState.Free) {
            override suspend fun refresh(): Unit = error("network down")
        }
        val c = coordinator(
            failing,
            FakeConsent(canRequest = true),
            FakeAds(),
            SettingsStore(MapSettings()),
            MonetizationDebug()
        )

        // Must not throw — a failing refresh cannot be allowed to break the Settings screen.
        c.refresh()
    }
}