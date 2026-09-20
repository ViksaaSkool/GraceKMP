package com.grace.app.platform

/**
 * iOS has no tintable status bar: it is drawn by the system over the app and
 * its appearance is controlled by the view controller, not by a colour value.
 * `exitApp` is a documented no-op — Apple's HIG forbids programmatically
 * terminating the app, so the TnC "Exit" button simply leaves the dialog up on
 * iOS (the user can background the app).
 */
class IosSystemUi : SystemUi {

    override fun exitApp() = Unit
}
