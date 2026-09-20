package com.grace.app

import android.app.Application
import com.grace.app.di.AndroidPlatformHolder

/**
 * Application entry point. Replaces the Dagger `AppComponent` bootstrap: it only
 * records the application context, which the Koin `platformModule` needs for
 * settings, file storage, image processing and connectivity.
 */
class GraceApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidPlatformHolder.application = this
    }
}
