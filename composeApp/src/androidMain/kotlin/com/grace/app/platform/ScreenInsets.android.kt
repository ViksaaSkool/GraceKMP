package com.grace.app.platform

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

/**
 * `MainActivity` draws edge-to-edge (see [com.grace.app.MainActivity]) so the app
 * background runs under the system bars, matching Compose on iOS. Content is
 * inset with the safe-drawing insets so it stays clear of the status bar, the
 * navigation bar and any display cutout.
 */
actual fun Modifier.platformScreenInsets(): Modifier = this.safeDrawingPadding()
