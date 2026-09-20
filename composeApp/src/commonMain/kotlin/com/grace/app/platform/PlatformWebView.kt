package com.grace.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A WebView with no shared Compose API (ADR-7).
 *
 * [onLoaded] is invoked when the page finishes loading *and* when a load error
 * occurs — in both cases the original hid the typing indicator and revealed the
 * web view. On error the implementation loads the bundled `empty_page.html`
 * fallback (`Constants.ERROR_WEB_PAGE`).
 */
@Composable
expect fun PlatformWebView(
    url: String,
    modifier: Modifier,
    onLoaded: () -> Unit
)
