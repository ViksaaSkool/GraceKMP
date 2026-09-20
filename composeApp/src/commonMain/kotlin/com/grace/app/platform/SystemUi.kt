package com.grace.app.platform

/**
 * Platform system-UI actions.
 *
 * The status bar is left entirely to the platform: both apps draw edge-to-edge
 * (see `MainActivity` on Android), so the bar is transparent and simply shows
 * whatever the app paints behind it. The original tinted the status bar per
 * screen (`colorPrimaryDark` on GetMeal, black on the photo states — PLAN.md
 * §1.4); that tint is what produced a seam against the app background, so it is
 * gone.
 */
interface SystemUi {
    /**
     * TnC "Exit". Android finishes the task. iOS has no sanctioned way to
     * terminate the app, so the actual is a documented no-op there.
     */
    fun exitApp()
}
