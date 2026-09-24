package com.grace.app.platform

/**
 * Outcome of an attempted interstitial presentation.
 *
 * Every case other than [DisplayedAndDismissed] must let the caller proceed straight to the
 * blessed result: a network failure, a missing consent grant or an expired ad must never
 * withhold the photo the customer just produced.
 */
sealed interface InterstitialResult {
    /** The ad covered the screen and the customer closed it. Prompt may be shown. */
    data object DisplayedAndDismissed : InterstitialResult

    /** No ad was cached / no fill / not preloaded yet. */
    data object Unavailable : InterstitialResult

    /** UMP has not granted permission to request ads. */
    data object ConsentRequired : InterstitialResult

    /** The ad was cached but could not be presented. */
    data object Failed : InterstitialResult
}

/**
 * Platform interstitial seam. Implementations keep at most one ad in flight, consume it
 * exactly once, and immediately begin refilling afterwards.
 */
interface InterstitialAdService {
    /** Starts (or resumes) preloading. Never blocks; safe to call repeatedly. */
    suspend fun preload()

    /**
     * Presents a preloaded ad if one is ready, suspending until it is dismissed.
     * Returns a non-[InterstitialResult.DisplayedAndDismissed] result rather than throwing
     * whenever the ad could not be shown.
     */
    suspend fun showIfReady(): InterstitialResult
}
