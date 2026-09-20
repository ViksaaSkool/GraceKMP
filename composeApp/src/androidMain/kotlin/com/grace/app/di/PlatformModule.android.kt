package com.grace.app.di

import android.app.Application
import android.content.Context
import com.grace.app.platform.AndroidConnectivityObserver
import com.grace.app.platform.AndroidGraceFileStore
import com.grace.app.platform.AndroidImageProcessor
import com.grace.app.platform.ConnectivityObserver
import com.grace.app.platform.FoodClassifier
import com.grace.app.platform.GraceFileStore
import com.grace.app.platform.ImageProcessor
import com.grace.app.platform.MlKitFoodClassifier
import com.grace.app.platform.PhotoPicker
import com.grace.app.platform.ShareService
import com.grace.app.platform.SystemUi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Holds the pieces that need an Activity (and therefore cannot be created by
 * the Koin graph, which is started from the Compose tree). `MainActivity`
 * populates these in `onCreate`; the module resolves them lazily on first use,
 * which always happens after that.
 */
object AndroidPlatformHolder {
    lateinit var application: Application

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
}
