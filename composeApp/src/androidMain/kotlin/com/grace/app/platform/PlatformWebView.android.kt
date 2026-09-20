package com.grace.app.platform

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.grace.app.resources.Res

/**
 * `DisclaimerTermsAndConditionsFragment`: JS enabled, load-with-overview-mode,
 * scrollbars outside overlay, a plain `WebChromeClient`, and a `WebViewClient`
 * that reveals the web view on `onPageFinished` and loads the bundled
 * `empty_page.html` fallback on `onReceivedError`.
 */
@SuppressLint("SetJavaScriptEnabled")
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

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                visibility = View.INVISIBLE
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webChromeClient = WebChromeClient()
                scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        super.onPageFinished(view, finishedUrl)
                        view.visibility = View.VISIBLE
                        onLoaded()
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        super.onReceivedError(view, request, error)
                        view.visibility = View.VISIBLE
                        view.loadDataWithBaseURL(
                            null,
                            errorHtml ?: DEFAULT_ERROR_HTML,
                            "text/html",
                            "utf-8",
                            null
                        )
                        onLoaded()
                    }
                }
                loadUrl(url)
            }
        }
    )
}

private const val DEFAULT_ERROR_HTML = "<html><body>ERROR!</body></html>"
