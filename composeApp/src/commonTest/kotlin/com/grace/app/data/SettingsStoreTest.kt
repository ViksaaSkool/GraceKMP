package com.grace.app.data

import com.grace.app.core.GraceConstants
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the three values monetization added to the originally single-key preferences
 * store: versioned legal acceptance (with its Boolean migration), the free-blessing
 * counter, and the last-known purchased hint.
 */
class SettingsStoreTest {

    private fun store(settings: MapSettings = MapSettings()) = SettingsStore(settings)

    // ---- Versioned policy acceptance ---------------------------------------

    @Test
    fun aFreshInstallHasAcceptedNothing() {
        val store = store()

        assertEquals(SettingsStore.NO_POLICY_VERSION, store.acceptedPolicyVersion)
        assertFalse(store.hasAcceptedCurrentPolicy)
        assertFalse(store.disclaimerTncAccepted)
    }

    @Test
    fun anInstallThatOnlyStoredTheLegacyBooleanReadsAsVersionOne() {
        val settings = MapSettings()
        settings.putBoolean(GraceConstants.DISCLAIMER_TNC_KEY, true)
        val store = store(settings)

        assertEquals(SettingsStore.LEGACY_POLICY_VERSION, store.acceptedPolicyVersion)
        // Version 1 predates the advertising/purchase terms, so it must re-prompt.
        assertFalse(store.hasAcceptedCurrentPolicy)
    }

    @Test
    fun acceptingTheCurrentDocumentsPersistsBothTheVersionAndTheLegacyBoolean() {
        val store = store()

        store.acceptedPolicyVersion = GraceConstants.REQUIRED_POLICY_VERSION

        assertEquals(GraceConstants.REQUIRED_POLICY_VERSION, store.acceptedPolicyVersion)
        assertTrue(store.disclaimerTncAccepted)
        assertTrue(store.hasAcceptedCurrentPolicy)
    }

    @Test
    fun aNewInstallIsRePromptedUntilTheCurrentVersionIsAccepted() {
        val settings = MapSettings()
        val store = store(settings)

        assertFalse(store.hasAcceptedCurrentPolicy)

        store.acceptedPolicyVersion = GraceConstants.REQUIRED_POLICY_VERSION

        assertTrue(store.hasAcceptedCurrentPolicy)
    }

    // ---- Free-blessing counter ---------------------------------------------

    @Test
    fun theCounterStartsAtZeroAndIncrementsMonotonically() {
        val store = store()

        assertEquals(0L, store.freeBlessingCount)
        assertEquals(1L, store.incrementFreeBlessingCount())
        assertEquals(2L, store.incrementFreeBlessingCount())
        assertEquals(2L, store.freeBlessingCount)
    }

    @Test
    fun theCounterSurvivesARecreatedStoreOverTheSameBacking() {
        val settings = MapSettings()
        store(settings).incrementFreeBlessingCount()
        store(settings).incrementFreeBlessingCount()

        assertEquals(2L, store(settings).freeBlessingCount)
    }

    // ---- Purchased hint -----------------------------------------------------

    @Test
    fun thePurchasedHintDefaultsToFalseAndPersistsPositives() {
        val settings = MapSettings()
        assertFalse(store(settings).removeAdsEntitlementCached)

        store(settings).removeAdsEntitlementCached = true

        assertTrue(store(settings).removeAdsEntitlementCached)
    }

    @Test
    fun keysDoNotCollideWithTheLegacyPreference() {
        val settings = MapSettings()
        val store = store(settings)

        store.acceptedPolicyVersion = GraceConstants.REQUIRED_POLICY_VERSION

        assertEquals(
            GraceConstants.REQUIRED_POLICY_VERSION,
            settings.getInt(SettingsStore.POLICY_VERSION_KEY, -1)
        )
        assertTrue(settings.getBoolean(GraceConstants.DISCLAIMER_TNC_KEY, false))
    }
}
