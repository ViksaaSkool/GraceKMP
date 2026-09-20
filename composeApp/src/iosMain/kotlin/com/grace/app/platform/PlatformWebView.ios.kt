@file:OptIn(ExperimentalForeignApi::class)

package com.grace.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.grace.app.resources.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * `DisclaimerTermsAndConditionsFragment` on iOS: `WKWebView` that reports
 * `onLoaded` on `didFinish` and loads the bundled `empty_page.html` fallback
 * when a navigation fails.
 */
@Composable
actual fun PlatformWebView(
    url: String,
    modifier: Modifier,
    onLoaded: () -> Unit
) {
    var errorHtml by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        errorHtml = runCatching {
            Res.readBytes("files/empty_page.html").decodeToString()
        }.getOrNull()
    }

    // WKWebView holds its navigationDelegate weakly, so the delegate must be
    // retained for the lifetime of the view.
    val delegateHolder = remember { mutableStateOf<NSObject?>(null) }

    UIKitView(
        modifier = modifier,
        factory = {
            val webView = WKWebView(
                frame = CGRectZero.readValue(),
                configuration = WKWebViewConfiguration()
            )
            val delegate = object : NSObject(), WKNavigationDelegateProtocol {
                override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                    onLoaded()
                }

                @ObjCSignatureOverride
                override fun webView(
                    webView: WKWebView,
                    didFailNavigation: WKNavigation?,
                    withError: NSError
                ) {
                    loadFallback(webView, errorHtml)
                    onLoaded()
                }

                @ObjCSignatureOverride
                override fun webView(
                    webView: WKWebView,
                    didFailProvisionalNavigation: WKNavigation?,
                    withError: NSError
                ) {
                    loadFallback(webView, errorHtml)
                    onLoaded()
                }
            }
            delegateHolder.value = delegate
            webView.navigationDelegate = delegate
            NSURL.URLWithString(url)?.let { nsUrl ->
                webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
            }
            webView
        }
    )
}

private fun loadFallback(webView: WKWebView, html: String?) {
    webView.loadHTMLString(html ?: "<html><body>ERROR!</body></html>", baseURL = null)
}
