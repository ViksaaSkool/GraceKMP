package com.grace.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.grace.app.platform.PlatformWebView
import com.grace.app.ui.theme.GraceColors

/**
 * A white [PlatformWebView] with the loading indicator the original showed until
 * the page reported back (`DisclaimerTermsAndConditionsFragment`). Shared by the
 * first-run dialog pages and the Settings legal pages.
 */
@Composable
fun WebPage(
    url: String,
    modifier: Modifier = Modifier
) {
    var loaded by remember(url) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        PlatformWebView(
            url = url,
            modifier = Modifier.fillMaxSize(),
            onLoaded = { loaded = true }
        )
        if (!loaded) {
            TypingIndicator(
                dotColor = GraceColors.Accent,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
