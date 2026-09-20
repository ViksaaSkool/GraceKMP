package com.grace.app.platform

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

/**
 * On iOS Compose renders edge-to-edge, so the safe-area insets are applied to
 * keep content clear of the notch/Dynamic Island and the home indicator.
 */
actual fun Modifier.platformScreenInsets(): Modifier = this.safeDrawingPadding()
