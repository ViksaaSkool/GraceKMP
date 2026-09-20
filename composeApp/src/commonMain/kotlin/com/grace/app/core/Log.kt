package com.grace.app.core

/**
 * `LogUtil` in the original gated every call on `BuildConfig.DEBUG`.
 * The multiplatform equivalent is a platform-provided debug flag.
 */
expect val isDebugBuild: Boolean

expect fun logDebug(tag: String, message: String)

expect fun logError(tag: String, message: String)

inline fun debugLog(tag: String, message: () -> String) {
    if (isDebugBuild) logDebug(tag, message())
}
