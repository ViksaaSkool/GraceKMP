package com.grace.app.platform

import com.grace.app.core.GraceConstants.APP_TAG
import com.grace.app.core.GraceConstants
import com.grace.app.core.debugLog
import com.grace.app.core.logError
import com.grace.app.data.SettingsStore
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import com.revenuecat.purchases.kmp.PurchasesDelegate
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offerings
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesException
import com.revenuecat.purchases.kmp.models.PurchasesTransactionException
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RevenueCat-backed [PurchasesRepository].
 *
 * Configured with RevenueCat's **anonymous** App User ID — no custom `appUserId`, no
 * device identifier, no IDFA — so ownership sits with the Apple/Google store account and
 * the explicit Restore action re-attaches it after a reinstall.
 *
 * `CustomerInfo` is authoritative; [SettingsStore.removeAdsEntitlementCached] is only ever
 * written on a positive result so a stale `false` cannot outlive a refresh.
 */
class RevenueCatPurchasesRepository(
    apiKey: String,
    private val settings: SettingsStore
) : PurchasesRepository {

    private val _entitlementState =
        MutableStateFlow(
            if (settings.removeAdsEntitlementCached) EntitlementState.Purchased
            else EntitlementState.Unknown
        )
    override val entitlementState: StateFlow<EntitlementState> = _entitlementState.asStateFlow()

    private val _product = MutableStateFlow<RemoveAdsProduct?>(null)
    override val product: StateFlow<RemoveAdsProduct?> = _product.asStateFlow()

    private val _supportId = MutableStateFlow<String?>(null)
    override val supportId: StateFlow<String?> = _supportId.asStateFlow()

    private val delegate = object : PurchasesDelegate {
        override fun onPurchasePromoProduct(
            product: StoreProduct,
            startPurchase: (
                onError: (PurchasesError, Boolean) -> Unit,
                onSuccess: (StoreTransaction, CustomerInfo) -> Unit
            ) -> Unit
        ) = Unit

        override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) =
            applyEntitlement(customerInfo)
    }

    init {
        if (Purchases.isConfigured) {
            attachDelegate()
        } else {
            runCatching {
                Purchases.configure(PurchasesConfiguration.Builder(apiKey).build())
                attachDelegate()
                debugLog(APP_TAG) { "RevenueCat configured with an anonymous app user id" }
            }.onFailure {
                logError(APP_TAG, "RevenueCat configure failed: ${it.message}")
                _entitlementState.value = EntitlementState.Unknown
            }
        }
    }

    private fun attachDelegate() {
        runCatching { Purchases.sharedInstance.delegate = delegate }
            .onFailure { logError(APP_TAG, "RevenueCat delegate attach failed: ${it.message}") }
    }

    override suspend fun refresh() {
        if (!Purchases.isConfigured) {
            _entitlementState.value = EntitlementState.Unknown
            return
        }
        runCatching {
            val purchases = Purchases.sharedInstance
            _supportId.value = purchases.appUserID
            val info = purchases.awaitCustomerInfo()
            applyEntitlement(info)
            loadProduct(purchases.awaitOfferings())
        }.onFailure {
            debugLog(APP_TAG) { "RevenueCat refresh failed: ${it.message}" }
            // Leave the last known state alone; a paid customer must not flip to Free
            // just because the network blipped.
        }
    }

    override suspend fun purchaseRemoveAds(): PurchaseResult {
        if (!Purchases.isConfigured) return PurchaseResult.Unavailable
        val pkg = runCatching { currentPackage() }.getOrNull()
            ?: return PurchaseResult.Unavailable

        return try {
            val result = Purchases.sharedInstance.awaitPurchase(pkg)
            applyEntitlement(result.customerInfo)
            if (isActive(result.customerInfo)) PurchaseResult.Success
            else PurchaseResult.Failure("Purchase completed without an active entitlement")
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) PurchaseResult.Cancelled
            else {
                logError(APP_TAG, "Remove Ads purchase failed: ${e.message}")
                PurchaseResult.Failure(e.message ?: "Purchase failed")
            }
        } catch (e: PurchasesException) {
            logError(APP_TAG, "Remove Ads purchase failed: ${e.message}")
            PurchaseResult.Failure(e.message ?: "Purchase failed")
        } catch (e: Exception) {
            logError(APP_TAG, "Remove Ads purchase failed: ${e.message}")
            PurchaseResult.Failure(e.message ?: "Purchase failed")
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        if (!Purchases.isConfigured) return RestoreResult.Unavailable

        return try {
            val info = Purchases.sharedInstance.awaitRestore()
            applyEntitlement(info)
            if (isActive(info)) RestoreResult.Success else RestoreResult.NothingToRestore
        } catch (e: PurchasesException) {
            logError(APP_TAG, "Restore failed: ${e.message}")
            RestoreResult.Failure(e.message ?: "Restore failed")
        } catch (e: Exception) {
            logError(APP_TAG, "Restore failed: ${e.message}")
            RestoreResult.Failure(e.message ?: "Restore failed")
        }
    }

    private suspend fun currentPackage() =
        Purchases.sharedInstance.awaitOfferings().let { offerings ->
            val offering = offerings.current
                ?: offerings.getOffering(GraceConstants.OFFERING_DEFAULT)
                ?: return@let null
            offering.getPackage(GraceConstants.PRODUCT_REMOVE_ADS_LIFETIME)
                ?: offering.availablePackages.firstOrNull()
        }

    private suspend fun loadProduct(offerings: Offerings) {
        val offering = offerings.current
            ?: offerings.getOffering(GraceConstants.OFFERING_DEFAULT)
        val storeProduct = (offering?.getPackage(GraceConstants.PRODUCT_REMOVE_ADS_LIFETIME)
            ?: offering?.availablePackages?.firstOrNull())?.storeProduct

        _product.value = storeProduct?.let {
            RemoveAdsProduct(productId = it.id, localizedPrice = it.price.formatted)
        }
    }

    private fun applyEntitlement(info: CustomerInfo) {
        val active = isActive(info)
        _entitlementState.value =
            if (active) EntitlementState.Purchased else EntitlementState.Free
        if (active) settings.removeAdsEntitlementCached = true
    }

    private fun isActive(info: CustomerInfo): Boolean =
        info.entitlements[GraceConstants.ENTITLEMENT_REMOVE_ADS]?.isActive == true
}
