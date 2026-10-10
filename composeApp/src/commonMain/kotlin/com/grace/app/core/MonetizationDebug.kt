package com.grace.app.core

import com.grace.app.platform.EntitlementState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Debug-only switches for exercising the monetization flows without real store accounts.
 *
 * Everything here is inert in a release build: [enabled] is backed by `isDebugBuild`,
 * which is a compile-time constant on both platforms, so R8 strips the whole surface. The
 * object is also the single place that knows how to *bypass* production rules, so it is
 * kept out of [MonetizationConfig] (which only validates real identifiers) and out of the
 * ViewModels (which must stay pure functions of their state).
 *
 * Precedence rule used by the coordinator: an [entitlementOverride] wins over whatever
 * the purchase backend reports, so a developer can simulate a paid customer even while a
 * real RevenueCat Test Store session is active.
 */
class MonetizationDebug {

    /** `false` in release builds; every mutator below becomes a no-op. */
    val enabled: Boolean get() = isDebugBuild

    private val _forceNextInterstitial = MutableStateFlow(false)

    /**
     * When `true`, the next successful blessing is ad-eligible regardless of the
     * every-second-blessing cadence. Consumed (reset to `false`) by the coordinator so a
     * forced ad cannot leak into subsequent blessings.
     */
    val forceNextInterstitial: StateFlow<Boolean> =
        _forceNextInterstitial

    private val _entitlementOverride =
        MutableStateFlow<EntitlementState?>(null)

    /** Forced entitlement, or `null` to trust the purchase backend. */
    val entitlementOverride: StateFlow<EntitlementState?> =
        _entitlementOverride

    private val _debugGeography = MutableStateFlow(DebugGeography.Off)

    /** UMP debug geography override; consumed by the Android consent service. */
    val debugGeography: StateFlow<DebugGeography> = _debugGeography

    fun requestForceNextInterstitial() {
        if (!enabled) return
        _forceNextInterstitial.value = true
    }

    /**
     * Consumes the forced-ad flag. Returns `true` at most once per
     * [requestForceNextInterstitial] call, and always `false` in release builds.
     */
    fun consumeForceNextInterstitial(): Boolean {
        if (!enabled) return false
        if (!_forceNextInterstitial.value) return false
        _forceNextInterstitial.value = false
        return true
    }

    /** Forces the entitlement the coordinator sees. Pass `null` to defer to the backend. */
    fun setEntitlementOverride(state: EntitlementState?) {
        if (!enabled) return
        _entitlementOverride.value = state
    }

    /** Cycles Off → EEA → US → Off, so one row drives the consent tests. */
    fun cycleDebugGeography() {
        if (!enabled) return
        _debugGeography.value = when (_debugGeography.value) {
            DebugGeography.Off -> DebugGeography.EEA
            DebugGeography.EEA -> DebugGeography.US
            DebugGeography.US -> DebugGeography.Off
        }
    }

    /** Returns every switch to its default. Used by the "Reset all" row. */
    fun reset() {
        if (!enabled) return
        _forceNextInterstitial.value = false
        _entitlementOverride.value = null
        _debugGeography.value = DebugGeography.Off
    }
}

/**
 * UMP debug geographies. Mirrors `com.google.android.ump.model.DebugGeography` without
 * importing the Android SDK, so this file stays in `commonMain`.
 */
enum class DebugGeography {
    Off,
    EEA,
    US
}