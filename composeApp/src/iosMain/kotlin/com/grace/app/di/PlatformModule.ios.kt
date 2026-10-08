package com.grace.app.di

import kotlin.concurrent.Volatile

import com.grace.app.core.MonetizationConfig
import com.grace.app.platform.AdConsentService
import com.grace.app.platform.ConnectivityObserver
import com.grace.app.platform.FoodClassifier
import com.grace.app.platform.GraceFileStore
import com.grace.app.platform.ImageProcessor
import com.grace.app.platform.InterstitialAdService
import com.grace.app.platform.IosAdConsentService
import com.grace.app.platform.IosConnectivityObserver
import com.grace.app.platform.IosGraceFileStore
import com.grace.app.platform.IosImageProcessor
import com.grace.app.platform.IosInterstitialAdService
import com.grace.app.platform.IosPhotoPicker
import com.grace.app.platform.IosShareService
import com.grace.app.platform.IosSystemUi
import com.grace.app.platform.PhotoPicker
import com.grace.app.platform.PurchasesRepository
import com.grace.app.platform.ShareService
import com.grace.app.platform.SystemUi
import com.grace.app.platform.VisionFoodClassifier
import com.grace.app.platform.createPurchasesRepository
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

/**
 * iOS AdMob identifiers. Debug uses Google's sample IDs; release reads the production
 * values from the Xcode build configuration (see docs/monetization-setup.md and
 * `iosApp/Configuration/Config.xcconfig`).
 */
fun iosMonetizationConfig(): MonetizationConfig =
    MonetizationConfig(
        adMobAppId = IosMonetizationBuildConfig.adMobAppId,
        interstitialUnitId = IosMonetizationBuildConfig.interstitialUnitId,
        revenueCatApiKey = MonetizationConfig.revenueCatKey(IosMonetizationBuildConfig.revenueCatApiKey)
    )

/**
 * Values injected from Xcode via `SWIFT_ACTIVE_COMPILATION_CONDITIONS`-style build settings
 * are not available to Kotlin, so they are handed in by the Swift host through
 * [IosMonetizationBuildConfig.configure] before `MainViewController()` is created.
 * Defaults are Google's sample IDs, which are always safe to run with.
 */
object IosMonetizationBuildConfig {
    @Volatile var adMobAppId: String = MonetizationConfig.SAMPLE_IOS_APP_ID
    @Volatile var interstitialUnitId: String = MonetizationConfig.SAMPLE_INTERSTITIAL_UNIT_ID
    @Volatile var revenueCatApiKey: String = MonetizationConfig.REVENUECAT_PLACEHOLDER

    fun configure(appId: String, interstitialUnitId: String, revenueCatApiKey: String?) {
        this.adMobAppId = appId.ifBlank { MonetizationConfig.SAMPLE_IOS_APP_ID }
        this.interstitialUnitId =
            interstitialUnitId.ifBlank { MonetizationConfig.SAMPLE_INTERSTITIAL_UNIT_ID }
        this.revenueCatApiKey = MonetizationConfig.revenueCatKey(revenueCatApiKey)
    }
}

actual val platformModule: Module = module {

    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single<GraceFileStore> { IosGraceFileStore() }
    single<ImageProcessor> { IosImageProcessor() }
    single<FoodClassifier> { VisionFoodClassifier() }
    single<ConnectivityObserver> { IosConnectivityObserver() }
    single<PhotoPicker> { IosPhotoPicker(get()) }
    single<SystemUi> { IosSystemUi() }
    single<ShareService> { IosShareService() }

    single<MonetizationConfig> { iosMonetizationConfig() }
    single<AdConsentService> { IosAdConsentService() }
    single<InterstitialAdService> {
        val config = get<MonetizationConfig>()
        IosInterstitialAdService(
            appId = config.adMobAppId,
            unitId = config.interstitialUnitId
        )
    }
    single<PurchasesRepository> {
        createPurchasesRepository(config = get(), settings = get())
    }
}
