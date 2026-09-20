package com.grace.app.data

import com.grace.app.core.GraceConstants
import com.russhwolf.settings.Settings

/**
 * Replaces the injected `SharedPreferences`. The original only persisted one
 * key: `disclaimer_tnc_key` (Constants.java:51).
 */
class SettingsStore(private val settings: Settings) {

    var disclaimerTncAccepted: Boolean
        get() = settings.getBoolean(GraceConstants.DISCLAIMER_TNC_KEY, false)
        set(value) = settings.putBoolean(GraceConstants.DISCLAIMER_TNC_KEY, value)
}
