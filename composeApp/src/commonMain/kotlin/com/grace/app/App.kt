package com.grace.app

import androidx.compose.runtime.Composable
import com.grace.app.di.appModule
import com.grace.app.di.platformModule
import com.grace.app.ui.navigation.AppNavHost
import com.grace.app.ui.theme.GraceTheme
import org.koin.compose.KoinApplication

/**
 * Root composable for the shared Grace UI.
 *
 * Starts the Koin graph (ADR-3) and hands off to the navigator, which drives
 * every screen: Splash → GetMeal → Loading → Photo → PhotoDetails, plus the
 * first-run Disclaimer/TnC dialog over GetMeal.
 */
@Composable
fun App() {
    KoinApplication(application = { modules(appModule, platformModule) }) {
        GraceTheme {
            AppNavHost()
        }
    }
}
