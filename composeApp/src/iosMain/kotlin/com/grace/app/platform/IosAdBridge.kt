package com.grace.app.platform

import kotlin.concurrent.Volatile

/**
 * Native Google Mobile Ads + UMP bridge, implemented in Swift inside the Xcode host.
 *
 * The method shapes are deliberately simple (no suspending functions, plain `Boolean` /
 * `Int` results) so they cross the Kotlin/Native ↔ Swift boundary without wrappers. Result
 * codes for [showInterstitial]:
 * `0` displayed-and-dismissed · `1` unavailable · `2` failed.
 *
 * The Swift side is compiled behind `#if canImport(GoogleMobileAds)` /
 * `#if canImport(UserMessagingPlatform)`, so until those packages are added the host
 * installs nothing and [IosMonetizationBridgeHolder.bridge] stays `null`.
 */
interface IosAdBridge {
    fun start(appId: String, onDone: (Boolean) -> Unit)

    /** Requests fresh consent info, then presents any required form. */
    fun refreshConsent(onDone: (canRequestAds: Boolean, privacyRequired: Boolean) -> Unit)

    fun showPrivacyOptions(onDone: (canRequestAds: Boolean, privacyRequired: Boolean) -> Unit)

    fun preloadInterstitial(unitId: String)

    fun showInterstitial(unitId: String, onResult: (code: Int) -> Unit)
}

/** Populated by the Swift host before the Compose UI is created. */
object IosMonetizationBridgeHolder {
    @Volatile
    var bridge: IosAdBridge? = null
}
