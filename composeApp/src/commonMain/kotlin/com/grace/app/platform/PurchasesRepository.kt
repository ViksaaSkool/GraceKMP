package com.grace.app.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * The customer's Remove Ads entitlement.
 *
 * [RevenueCat's `CustomerInfo` is the source of truth][1]; a locally persisted Boolean is
 * only ever used to avoid flashing an ad at somebody who already paid while a refresh is
 * in flight. [Unknown] therefore means "not resolved yet" and must fail **safe** by
 * showing no ad — never by assuming the customer is free.
 *
 * [1]: https://www.revenuecat.com/docs/customers/customer-info
 */
enum class EntitlementState {
    /** Entitlement has not been resolved yet (startup, offline, SDK unavailable). */
    Unknown,

    /** Confirmed to have no active `remove_ads` entitlement. */
    Free,

    /** Confirmed active `remove_ads` entitlement. */
    Purchased
}

/** The one-time Remove Ads product, priced by the store. */
data class RemoveAdsProduct(
    /** Store product ID, e.g. `remove_ads_lifetime`. */
    val productId: String,
    /** Store-localised, store-formatted price. Never hardcode `$0.99` here. */
    val localizedPrice: String
)

sealed interface PurchaseResult {
    /** Entitlement is now active. */
    data object Success : PurchaseResult

    /** The customer dismissed the store sheet. Not an error. */
    data object Cancelled : PurchaseResult

    data class Failure(val message: String) : PurchaseResult

    /** No product/SDK available in this build (placeholder configuration). */
    data object Unavailable : PurchaseResult
}

sealed interface RestoreResult {
    /** An entitlement was found and activated. */
    data object Success : RestoreResult

    /** Restore completed but the store account owns nothing. */
    data object NothingToRestore : RestoreResult

    data class Failure(val message: String) : RestoreResult

    data object Unavailable : RestoreResult
}

/**
 * SDK-independent purchase seam. The shared layer only ever talks to this interface, so
 * RevenueCat can be swapped or faked without touching ViewModels or UI.
 */
interface PurchasesRepository {
    val entitlementState: StateFlow<EntitlementState>

    /** Store-provided product, or `null` while unknown / unavailable. */
    val product: StateFlow<RemoveAdsProduct?>

    /** Anonymous RevenueCat App User ID, shown only as a support hint. */
    val supportId: StateFlow<String?>

    suspend fun purchaseRemoveAds(): PurchaseResult

    suspend fun restorePurchases(): RestoreResult

    /** Re-reads `CustomerInfo` and the `default` offering from the store. */
    suspend fun refresh()
}
