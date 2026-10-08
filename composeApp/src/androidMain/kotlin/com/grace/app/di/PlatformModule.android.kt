package com.grace.app.di

import android.app.Application
import android.content.Context
import com.grace.app.core.MonetizationConfig
import com.grace.app.platform.AdConsentService
import com.grace.app.platform.AndroidAdConsentService
import com.grace.app.platform.AndroidConnectivityObserver
import com.grace.app.platform.AndroidGraceFileStore
import com.grace.app.platform.AndroidImageProcessor
import com.grace.app.platform.AndroidInterstitialAdService
import com.grace.app.platform.ConnectivityObserver
import com.grace.app.platform.FoodClassifier
import com.grace.app.platform.GraceFileStore
import com.grace.app.platform.ImageProcessor
import com.grace.app.platform.InterstitialAdService
import com.grace.app.platform.MlKitFoodClassifier
import com.grace.app.platform.PhotoPicker
import com.grace.app.platform.PurchasesRepository
import com.grace.app.platform.ShareService
import com.grace.app.platform.SystemUi
import com.grace.app.platform.createPurchasesRepository
import com.grace.app.BuildConfig
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Monetization identifiers for an Android build. Debug uses Google's official sample IDs;
 * release reads production values injected by Gradle from `local.properties` / the
 * environment, and refuses to assemble while any of them is still a placeholder.
 */
fun androidMonetizationConfig(): MonetizationConfig =
    MonetizationConfig(
        adMobAppId = BuildConfig.ADMOB_APP_ID,
        interstitialUnitId = BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
        revenueCatApiKey = MonetizationConfig.revenueCatKey(BuildConfig.REVENUECAT_API_KEY)
    )

/**
 * Holds the pieces that need an Activity (and therefore cannot be created by
 * the Koin graph, which is started from the Compose tree). `MainActivity`
 * populates these in `onCreate`; the module resolves them lazily on first use,
 * which always happens after that.
 */
object AndroidPlatformHolder {
    lateinit var application: Application

    var activity: android.app.Activity? = null
    var photoPicker: PhotoPicker? = null
    var systemUi: SystemUi? = null
    var shareService: ShareService? = null
}

actual val platformModule: Module = module {

    single<Settings> {
        val context = AndroidPlatformHolder.application
        SharedPreferencesSettings(
            context.getSharedPreferences(
                "${context.packageName}_preferences",
                Context.MODE_PRIVATE
            )
        )
    }

    single<GraceFileStore> { AndroidGraceFileStore(AndroidPlatformHolder.application) }
    single<ImageProcessor> { AndroidImageProcessor(AndroidPlatformHolder.application) }
    single<FoodClassifier> { MlKitFoodClassifier() }
    single<ConnectivityObserver> { AndroidConnectivityObserver(AndroidPlatformHolder.application) }

    single<PhotoPicker> {
        requireNotNull(AndroidPlatformHolder.photoPicker) {
            "AndroidPlatformHolder.photoPicker was not initialised by MainActivity"
        }
    }
    single<SystemUi> {
        requireNotNull(AndroidPlatformHolder.systemUi) {
            "AndroidPlatformHolder.systemUi was not initialised by MainActivity"
        }
    }
    single<ShareService> {
        requireNotNull(AndroidPlatformHolder.shareService) {
            "AndroidPlatformHolder.shareService was not initialised by MainActivity"
        }
    }

    // Monetization. Config comes from the manifest placeholder / BuildConfig-style Gradle
    // properties; see docs/monetization-setup.md.
    single<MonetizationConfig> { androidMonetizationConfig() }

    single<AdConsentService> {
        AndroidAdConsentService(activityProvider = { AndroidPlatformHolder.activity })
    }
    single<InterstitialAdService> {
        AndroidInterstitialAdService(
            activityProvider = { AndroidPlatformHolder.activity },
            consent = get(),
            unitId = get<MonetizationConfig>().interstitialUnitId
        )
    }
    single<PurchasesRepository> {
        createPurchasesRepository(config = get(), settings = get())
    }
}
