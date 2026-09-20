package com.grace.app.di

import com.grace.app.platform.ConnectivityObserver
import com.grace.app.platform.FoodClassifier
import com.grace.app.platform.GraceFileStore
import com.grace.app.platform.ImageProcessor
import com.grace.app.platform.IosConnectivityObserver
import com.grace.app.platform.IosGraceFileStore
import com.grace.app.platform.IosImageProcessor
import com.grace.app.platform.IosPhotoPicker
import com.grace.app.platform.IosShareService
import com.grace.app.platform.IosSystemUi
import com.grace.app.platform.PhotoPicker
import com.grace.app.platform.ShareService
import com.grace.app.platform.SystemUi
import com.grace.app.platform.VisionFoodClassifier
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val platformModule: Module = module {

    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single<GraceFileStore> { IosGraceFileStore() }
    single<ImageProcessor> { IosImageProcessor() }
    single<FoodClassifier> { VisionFoodClassifier() }
    single<ConnectivityObserver> { IosConnectivityObserver() }
    single<PhotoPicker> { IosPhotoPicker(get()) }
    single<SystemUi> { IosSystemUi() }
    single<ShareService> { IosShareService() }
}
