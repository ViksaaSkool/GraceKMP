package com.grace.app.core

import android.util.Log
import com.grace.app.BuildConfig

actual val isDebugBuild: Boolean get() = BuildConfig.DEBUG

actual fun logDebug(tag: String, message: String) {
    if (isDebugBuild) Log.d(tag, message)
}

actual fun logError(tag: String, message: String) {
    if (isDebugBuild) Log.e(tag, message)
}
