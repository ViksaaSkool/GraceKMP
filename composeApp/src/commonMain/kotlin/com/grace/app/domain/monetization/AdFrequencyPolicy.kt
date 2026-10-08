package com.grace.app.domain.monetization

/** What should happen immediately after a photo is successfully blessed. */
enum class BlessingAdDecision {
    /** Go straight to the Approves result. */
    ShowResult,

    /** Show a preloaded interstitial first, then the Approves result. */
    ShowInterstitial
}

/**
 * "An interstitial after every second successful blessing."
 *
 * [freeBlessingCount] is the persisted counter **after** the current blessing has been
 * counted, so the first blessing is odd and shows no ad; the second is even and does.
 * Only ever called for a confirmed-**free** customer — paid and unresolved entitlements
 * never reach this policy.
 */
object AdFrequencyPolicy {

    fun decideAfterBlessing(freeBlessingCount: Long): BlessingAdDecision =
        if (freeBlessingCount > 0 && freeBlessingCount % 2 == 0L) {
            BlessingAdDecision.ShowInterstitial
        } else {
            BlessingAdDecision.ShowResult
        }
}
