package com.grace.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.grace.app.di.AndroidPlatformHolder
import com.grace.app.platform.AdMobBootstrap
import com.grace.app.platform.AndroidGraceFileStore
import com.grace.app.platform.AndroidPhotoPicker
import com.grace.app.platform.AndroidShareService
import com.grace.app.platform.AndroidSystemUi
import com.grace.app.platform.BuildConfigHolder

/**
 * Single Android activity hosting the shared Compose Multiplatform UI. Replaces
 * `GraceSplashScreenActivity`, `Main2Activity` and `PhotoDetailsActivity`.
 *
 * Parity notes (PLAN.md §1.4):
 * - Portrait-only (enforced in AndroidManifest).
 * - Draws edge-to-edge so the shared background (and the splash clouds) run
 *   under the system bars, exactly like Compose on iOS. The status bar is
 *   therefore always the colour of whatever the app paints behind it. Light bar
 *   content reads against the blue/black backgrounds the app uses.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Activity-scoped services must be registered before STARTED, so they
        // are created here and handed to the Koin graph via the holder.
        AndroidPlatformHolder.activity = this
        AndroidPlatformHolder.photoPicker = AndroidPhotoPicker(this, AndroidGraceFileStore(this))
        AndroidPlatformHolder.systemUi = AndroidSystemUi(this)
        AndroidPlatformHolder.shareService = AndroidShareService(this)

        // Google Mobile Ads + UMP need an Activity for consent forms and for presenting a
        // full-screen interstitial; both resolve it lazily through the holder.
        BuildConfigHolder.setAppId(
            com.grace.app.di.androidMonetizationConfig().adMobAppId
        )
        AdMobBootstrap.initialize(this)

        setContent {
            App()
        }
    }
}
