package com.grace.app.platform

import android.app.Activity

/**
 * `Exit` handling for Android.
 *
 * The system bars are configured once in `MainActivity` (edge-to-edge,
 * transparent), so there is no per-screen status-bar tint here any more — the
 * bar always shows the app background behind it.
 */
class AndroidSystemUi(private val activity: Activity) : SystemUi {

    override fun exitApp() {
        activity.finishAffinity()
    }
}
