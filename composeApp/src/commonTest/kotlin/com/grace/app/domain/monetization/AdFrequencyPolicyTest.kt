package com.grace.app.domain.monetization

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * "An interstitial after every second successful blessing."
 *
 * The counter passed in is the value **after** the current blessing was counted, which is
 * how `SettingsStore.incrementFreeBlessingCount()` hands it over.
 */
class AdFrequencyPolicyTest {

    @Test
    fun firstBlessingShowsTheResultImmediately() {
        assertEquals(
            BlessingAdDecision.ShowResult,
            AdFrequencyPolicy.decideAfterBlessing(1)
        )
    }

    @Test
    fun secondBlessingIsEligibleForAnInterstitial() {
        assertEquals(
            BlessingAdDecision.ShowInterstitial,
            AdFrequencyPolicy.decideAfterBlessing(2)
        )
    }

    @Test
    fun thePatternAlternatesForEverySubsequentPair() {
        val decisions = (1L..8L).map { AdFrequencyPolicy.decideAfterBlessing(it) }
        assertEquals(
            listOf(
                BlessingAdDecision.ShowResult,
                BlessingAdDecision.ShowInterstitial,
                BlessingAdDecision.ShowResult,
                BlessingAdDecision.ShowInterstitial,
                BlessingAdDecision.ShowResult,
                BlessingAdDecision.ShowInterstitial,
                BlessingAdDecision.ShowResult,
                BlessingAdDecision.ShowInterstitial
            ),
            decisions
        )
    }

    @Test
    fun aZeroOrNegativeCounterNeverEarnsAnAd() {
        assertEquals(BlessingAdDecision.ShowResult, AdFrequencyPolicy.decideAfterBlessing(0))
        assertEquals(BlessingAdDecision.ShowResult, AdFrequencyPolicy.decideAfterBlessing(-2))
    }

    @Test
    fun aLargeCounterKeepsAlternating() {
        assertEquals(BlessingAdDecision.ShowResult, AdFrequencyPolicy.decideAfterBlessing(101))
        assertEquals(
            BlessingAdDecision.ShowInterstitial,
            AdFrequencyPolicy.decideAfterBlessing(1000)
        )
    }
}
