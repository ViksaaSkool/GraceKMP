package com.grace.app.platform

import com.grace.app.core.GraceConstants
import com.grace.app.core.MonetizationConfig
import com.grace.app.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Placeholder purchase backend used while RevenueCat keys are unset.
 *
 * It stands in for the real store so the whole flow — ad cadence, prompt, Settings
 * premium section, restore — can be exercised with **dummy data** before the production
 * keys exist. Release builds refuse to assemble with a placeholder key (see
 * `docs/monetization-setup.md`), so this can never ship.
 *
 * Note it reports [EntitlementState.Free] rather than [EntitlementState.Unknown]: the
 * point of the placeholder is to demonstrate ads, and the real repository is what enforces
 * the "unresolved entitlement suppresses ads" rule.
 */
class DummyPurchasesRepository(
    private val settings: SettingsStore
) : PurchasesRepository {

    private val _entitlementState = MutableStateFlow(EntitlementState.Free)
    override val entitlementState: StateFlow<EntitlementState> = _entitlementState.asStateFlow()

    private val _product = MutableStateFlow(
        RemoveAdsProduct(
            productId = GraceConstants.PRODUCT_REMOVE_ADS_LIFETIME,
            // Dummy price. The real value comes from `StoreProduct.price.formatted`.
            localizedPrice = DUMMY_LOCALIZED_PRICE
        )
    )
    override val product: StateFlow<RemoveAdsProduct?> = _product.asStateFlow()

    private val _supportId = MutableStateFlow(DUMMY_SUPPORT_ID)
    override val supportId: StateFlow<String?> = _supportId.asStateFlow()

    init {
        if (settings.removeAdsEntitlementCached) _entitlementState.value = EntitlementState.Purchased
    }

    override suspend fun purchaseRemoveAds(): PurchaseResult {
        if (_entitlementState.value == EntitlementState.Purchased) return PurchaseResult.Success
        _entitlementState.value = EntitlementState.Purchased
        settings.removeAdsEntitlementCached = true
        return PurchaseResult.Success
    }

    override suspend fun restorePurchases(): RestoreResult =
        if (settings.removeAdsEntitlementCached) {
            _entitlementState.value = EntitlementState.Purchased
            RestoreResult.Success
        } else {
            RestoreResult.NothingToRestore
        }

    override suspend fun refresh() {
        _entitlementState.value =
            if (settings.removeAdsEntitlementCached) EntitlementState.Purchased
            else EntitlementState.Free
    }

    companion object {
        const val DUMMY_LOCALIZED_PRICE = "$0.99"
        const val DUMMY_SUPPORT_ID = "GRACE-DEV-0001"
    }
}

/** Chooses the purchase backend for this build. */
fun createPurchasesRepository(
    config: MonetizationConfig,
    settings: SettingsStore
): PurchasesRepository =
    if (config.purchasesAvailable) {
        RevenueCatPurchasesRepository(config.revenueCatApiKey, settings)
    } else {
        DummyPurchasesRepository(settings)
    }
