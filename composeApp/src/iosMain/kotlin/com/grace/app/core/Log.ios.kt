package com.grace.app.core

import platform.Foundation.NSLog
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean get() = kotlin.native.Platform.isDebugBinary

actual fun logDebug(tag: String, message: String) {
    if (isDebugBuild) NSLog("%s: %s", tag, message)
}

actual fun logError(tag: String, message: String) {
    if (isDebugBuild) NSLog("%s: %s", tag, message)
}
