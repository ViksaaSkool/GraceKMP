package com.grace.app.platform

import androidx.compose.ui.Modifier

/**
 * Insets the app content out of the system bars.
 *
 * On Android the activity is deliberately **not** edge-to-edge (the original
 * drew below a coloured status bar), so the content view is already inset and
 * this is a no-op. On iOS Compose draws edge-to-edge, so the safe-area insets
 * have to be applied explicitly to reproduce the same layout — otherwise the
 * TnC title sits under the Dynamic Island.
 *
 * The background is painted outside the inset, so the area behind the status
 * bar still shows the screen colour, matching the original.
 */
expect fun Modifier.platformScreenInsets(): Modifier
