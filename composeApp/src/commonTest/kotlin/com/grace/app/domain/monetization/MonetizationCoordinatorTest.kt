package com.grace.app.domain.monetization

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
import kotlin.test.assertTrue

/**
 * The decision that sits between "the photo was blessed" and "show the photo".
 *
 * Everything here is a constructor-injected fake driven by `runTest`, so no Main
 * dispatcher, no timing, and no live ad inventory.
 */
class MonetizationCoordinatorTest {

    private class FakePurchases(
        initial: EntitlementState = EntitlementState.Free
    ) : PurchasesRepository {
        private val _entitlement = MutableStateFlow(initial)
        override val entitlementState: StateFlow<EntitlementState> = _entitlement.asStateFlow()

        private val _product = MutableStateFlow<RemoveAdsProduct?>(null)
        override val product: StateFlow<RemoveAdsProduct?> = _product.asStateFlow()

        private val _supportId = MutableStateFlow<String?>("TEST-USER")
        override val supportId: StateFlow<String?> = _supportId.asStateFlow()

        var refreshCalls = 0
        var entitlementAfterRefresh: EntitlementState? = null

        fun set(state: EntitlementState) {
            _entitlement.value = state
        }

        override suspend fun purchaseRemoveAds(): PurchaseResult = PurchaseResult.Unavailable
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.Unavailable

        override suspend fun refresh() {
            refreshCalls++
            entitlementAfterRefresh?.let { _entitlement.value = it }
        }
    }

    private class FakeConsent(canRequest: Boolean) : AdConsentService {
        private val _canRequestAds = MutableStateFlow(canRequest)
        override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

        private val _privacy = MutableStateFlow(false)
        override val privacyOptionsRequired: StateFlow<Boolean> = _privacy.asStateFlow()

        var refreshCalls = 0

        fun setCanRequestAds(value: Boolean) {
            _canRequestAds.value = value
        }

        override suspend fun refreshConsent() {
            refreshCalls++
        }

        override suspend fun showPrivacyOptions() = Unit
    }

    private class FakeAds(
        private val result: InterstitialResult
    ) : InterstitialAdService {
        var preloadCalls = 0
        var showCalls = 0

        override suspend fun preload() {
            preloadCalls++
        }

        override suspend fun showIfReady(): InterstitialResult {
            showCalls++
            return result
        }
    }

    private class ExplodingAds : InterstitialAdService {
        override suspend fun preload() = Unit
        override suspend fun showIfReady(): InterstitialResult = error("network down")
    }

    private fun coordinator(
        purchases: PurchasesRepository,
        consent: AdConsentService = FakeConsent(canRequest = true),
        ads: InterstitialAdService = FakeAds(InterstitialResult.DisplayedAndDismissed),
        settings: SettingsStore = SettingsStore(MapSettings())
    ) = Triple(
        MonetizationCoordinator(purchases, consent, ads, settings),
        ads,
        settings
    )

    // ---- Entitlement gating -------------------------------------------------

    @Test
    fun aPaidCustomerNeverSeesAnAdAndNeverIncrementsTheCounter() = runTest {
        val (coordinator, ads, settings) = coordinator(
            purchases = FakePurchases(EntitlementState.Purchased)
        )

        repeat(4) { assertFalse(coordinator.onBlessingSucceeded()) }

        assertEquals(0, (ads as FakeAds).showCalls)
        assertEquals(0L, settings.freeBlessingCount)
    }

    @Test
    fun anUnknownEntitlementFailsSafeWithNoAdAndNoCounting() = runTest {
        val purchases = FakePurchases(EntitlementState.Unknown)
        val (coordinator, ads, settings) = coordinator(purchases = purchases)

        repeat(4) { assertFalse(coordinator.onBlessingSucceeded()) }

        assertEquals(0, (ads as FakeAds).showCalls)
        assertEquals(0L, settings.freeBlessingCount)
    }

    @Test
    fun startRefreshesTheEntitlementWhenItIsStillUnknown() = runTest {
        val purchases = FakePurchases(EntitlementState.Unknown).apply {
            entitlementAfterRefresh = EntitlementState.Purchased
        }
        val consent = FakeConsent(canRequest = true)
        val (coordinator, ads, _) = coordinator(purchases = purchases, consent = consent)

        coordinator.start()

        assertEquals(1, purchases.refreshCalls)
        assertEquals(EntitlementState.Purchased, purchases.entitlementState.value)
        assertEquals(1, consent.refreshCalls)
        // A resolved paid customer must not have an ad preloaded for them.
        assertEquals(0, (ads as FakeAds).preloadCalls)
    }

    // ---- Ad cadence ---------------------------------------------------------

    @Test
    fun freeCustomersSeeAnAdOnlyOnEverySecondBlessing() = runTest {
        val (coordinator, ads, settings) = coordinator(
            purchases = FakePurchases(EntitlementState.Free)
        )
        val shown = (1..4).map { coordinator.onBlessingSucceeded() }

        // Blessings 1 and 3 are odd → straight to the result, no ad requested at all.
        // Blessings 2 and 4 are even → the interstitial is shown.
        assertEquals(listOf(false, true, false, true), shown)
        assertEquals(2, (ads as FakeAds).showCalls)
        assertEquals(4L, settings.freeBlessingCount)
    }

    @Test
    fun startPreloadsAnAdForAFreeCustomerWhenConsentAllowsIt() = runTest {
        val (coordinator, ads, _) = coordinator(
            purchases = FakePurchases(EntitlementState.Free),
            consent = FakeConsent(canRequest = true)
        )

        coordinator.start()

        assertEquals(1, (ads as FakeAds).preloadCalls)
    }

    // ---- Consent and availability fall open ---------------------------------

    @Test
    fun missingConsentSkipsTheAdButStillCountsTheBlessing() = runTest {
        val (coordinator, ads, settings) = coordinator(
            purchases = FakePurchases(EntitlementState.Free),
            consent = FakeConsent(canRequest = false)
        )

        assertFalse(coordinator.onBlessingSucceeded())

        assertEquals(0, (ads as FakeAds).showCalls)
        assertEquals(1L, settings.freeBlessingCount)
    }

    @Test
    fun anUnavailableAdNeverBlocksTheResult() = runTest {
        val (coordinator, ads, _) = coordinator(
            purchases = FakePurchases(EntitlementState.Free),
            ads = FakeAds(InterstitialResult.Unavailable)
        )

        // Blessing #1 is odd and never reaches the ad service; #2 is the ad-eligible one.
        assertFalse(coordinator.onBlessingSucceeded())
        assertFalse(coordinator.onBlessingSucceeded())
        assertEquals(1, (ads as FakeAds).showCalls)
    }

    @Test
    fun aFailedAdNeverBlocksTheResult() = runTest {
        val (coordinator, _, _) = coordinator(
            purchases = FakePurchases(EntitlementState.Free),
            ads = FakeAds(InterstitialResult.Failed)
        )

        assertFalse(coordinator.onBlessingSucceeded()) // #1 odd
        assertFalse(coordinator.onBlessingSucceeded()) // #2 even, ad fails
    }

    @Test
    fun anExceptionFromTheAdServiceIsSwallowedAndTheResultIsShown() = runTest {
        val (coordinator, _, _) = coordinator(
            purchases = FakePurchases(EntitlementState.Free),
            ads = ExplodingAds()
        )

        assertFalse(coordinator.onBlessingSucceeded()) // #1 odd
        assertFalse(coordinator.onBlessingSucceeded()) // #2 even, service throws
    }

    // ---- Prompt trigger -----------------------------------------------------

    @Test
    fun onlyAnActuallyDismissedAdTriggersTheRemoveAdsPrompt() = runTest {
        val (coordinator, _, _) = coordinator(
            purchases = FakePurchases(EntitlementState.Free),
            ads = FakeAds(InterstitialResult.DisplayedAndDismissed)
        )

        assertFalse(coordinator.onBlessingSucceeded()) // #1 odd — no ad, no prompt
        assertTrue(coordinator.onBlessingSucceeded())  // #2 even — shown and dismissed
    }

    @Test
    fun startIsIdempotentAcrossRecompositions() = runTest {
        val purchases = FakePurchases(EntitlementState.Free)
        val consent = FakeConsent(canRequest = true)
        val (coordinator, ads, _) = coordinator(purchases = purchases, consent = consent)

        coordinator.start()
        coordinator.start()
        coordinator.start()

        assertEquals(1, purchases.refreshCalls)
        assertEquals(1, consent.refreshCalls)
        assertEquals(1, (ads as FakeAds).preloadCalls)
    }

    @Test
    fun aDisplayedAdRefillsTheCacheForTheNextEligibleBlessing() = runTest {
        val (coordinator, ads, _) = coordinator(
            purchases = FakePurchases(EntitlementState.Free)
        )

        coordinator.onBlessingSucceeded() // #1 odd — no ad, no refill
        val preloadsAfterFirst = (ads as FakeAds).preloadCalls

        coordinator.onBlessingSucceeded() // #2 even — shown, then refilled

        assertTrue(ads.preloadCalls > preloadsAfterFirst)
    }
}
