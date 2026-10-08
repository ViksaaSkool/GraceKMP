package com.grace.app.data

import com.grace.app.core.GraceConstants
import com.russhwolf.settings.Settings


/**
 * Replaces the injected `SharedPreferences`.
 *
 * Original key (Constants.java:51): a single `disclaimer_tnc_key` Boolean. Monetization
 * adds three more local values — the free-blessing counter, a last-known **purchased**
 * hint, and a versioned legal-acceptance record.
 *
 * Nothing here is authoritative for entitlement: [removeAdsEntitlementCached] may only be
 * trusted to suppress an ad while RevenueCat is refreshing, and a `false` never proves the
 * customer is free.
 */
class SettingsStore(private val settings: Settings) {

    /**
     * Legacy acceptance flag. Kept in sync with [acceptedPolicyVersion] so an install that
     * only knows the Boolean still sees the user as having accepted version 1.
     */
    var disclaimerTncAccepted: Boolean
        get() = settings.getBoolean(GraceConstants.DISCLAIMER_TNC_KEY, false)
        set(value) = settings.putBoolean(GraceConstants.DISCLAIMER_TNC_KEY, value)

    /**
     * The legal-document version this install has accepted.
     *
     * Migration: an install that predates versioning has the Boolean set but no stored
     * number, so it reads as version [LEGACY_POLICY_VERSION] (1). Because the advertising /
     * purchase terms are a material change, [REQUIRED_POLICY_VERSION] is higher and those
     * customers are asked to accept again.
     */
    var acceptedPolicyVersion: Int
        get() = settings.getInt(POLICY_VERSION_KEY, NO_POLICY_VERSION)
            .takeIf { it != NO_POLICY_VERSION }
            ?: if (disclaimerTncAccepted) LEGACY_POLICY_VERSION else NO_POLICY_VERSION
        set(value) {
            settings.putInt(POLICY_VERSION_KEY, value)
            if (value >= LEGACY_POLICY_VERSION) disclaimerTncAccepted = true
        }

    /** `true` once the customer has accepted the currently shipped documents. */
    val hasAcceptedCurrentPolicy: Boolean
        get() = acceptedPolicyVersion >= GraceConstants.REQUIRED_POLICY_VERSION

    /**
     * Successful blessings by confirmed-free customers, used by the ad-frequency policy.
     * Paid and unresolved blessings never touch this counter.
     */
    var freeBlessingCount: Long
        get() = settings.getLong(FREE_BLESSING_COUNT_KEY, 0L)
        set(value) = settings.putLong(FREE_BLESSING_COUNT_KEY, value)

    /** Increments and returns the new count. */
    fun incrementFreeBlessingCount(): Long =
        (freeBlessingCount + 1).also { freeBlessingCount = it }

    /**
     * Last-known purchased state. `true` may suppress an ad while RevenueCat refreshes;
     * `false` is never treated as proof of a free customer.
     */
    var removeAdsEntitlementCached: Boolean
        get() = settings.getBoolean(REMOVE_ADS_CACHED_KEY, false)
        set(value) = settings.putBoolean(REMOVE_ADS_CACHED_KEY, value)

    companion object {
        const val POLICY_VERSION_KEY = "policy_version_key"
        const val FREE_BLESSING_COUNT_KEY = "free_blessing_count_key"
        const val REMOVE_ADS_CACHED_KEY = "remove_ads_entitlement_cached_key"

        /** Version reported for installs that only stored the legacy Boolean. */
        const val LEGACY_POLICY_VERSION = 1

        /** No acceptance recorded at all. */
        const val NO_POLICY_VERSION = 0
    }
}
