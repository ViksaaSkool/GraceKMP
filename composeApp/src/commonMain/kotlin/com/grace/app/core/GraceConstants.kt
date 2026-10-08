package com.grace.app.core

/**
 * Every business value from the original app, encoded as a named constant.
 *
 * Sources: `constants/Constants.java`, `custom/FoodDetector.java`,
 * `custom/BlessPhotoWorker.java`, `util/GracePhotoUtil.java` (PLAN.md §3.1).
 */
object GraceConstants {

    // Log tags (Constants.java:10-14)
    const val UI_TAG = "UITAG"
    const val API_TAG = "APITAG"
    const val APP_TAG = "APPTAG"
    const val CAMERA_TAG = "CAMERATAG"
    const val BLESS_TAG = "BLESSTAG"

    // Food detection (FoodDetector.java:21-24, 31)
    const val FOOD_CONFIDENCE_THRESHOLD = 0.70f
    val FOOD_LABELS: Set<String> = setOf(
        "food", "dish", "cuisine", "meal", "dessert", "fruit", "vegetable",
        "burger", "pizza", "sandwich", "salad", "pasta", "sushi"
    )

    // Animation timings (Constants.java:42-48, integer.xml)
    const val CROSS_FADE_DURATION = 1000
    const val LOADING_ANIMATION_DURATION = 1100
    const val SCALE_DURATION = 500
    const val TAP_DURATION = 700

    // Splash timeline (`GraceSplashScreenActivity`, `activity_splash_screen.xml`).
    // Retuned into one continuous "Heaven Opens" reveal: the native launch frame
    // and the first Compose frame are the same composition, the clouds open while
    // the badge rises through them, then the scene crossfades into Get Meal.
    //
    // Both platforms hand off from a plain `colorPrimary` frame (iOS launch
    // storyboard, Android starting window), so the clouds fade in as the first
    // beat of the shared animation rather than popping in.
    const val SPLASH_CLOUD_FADE_DURATION = 250
    const val SPLASH_FIRST_FRAME_DURATION = 250
    const val SPLASH_FRAME_DURATION = 85
    const val SPLASH_CLOUD_SETTLE_DURATION = 1200
    const val SPLASH_LOGO_DURATION = 900
    const val SPLASH_HOLD_DURATION = 400
    const val SPLASH_EXIT_FADE_DURATION = 450
    const val DOTS_ANIMATION_DURATION = 300

    /**
     * `clouds_0..clouds_8` (PLAN.md §3.4). The badge only starts moving once the
     * whole sequence has played, so the sun is behind the clouds and the scene
     * has stopped changing before the logo appears.
     */
    const val SPLASH_CLOUD_FRAME_COUNT = 9

    /** When the last cloud frame is on screen and has been held for one frame. */
    const val SPLASH_CLOUD_SEQUENCE_DURATION =
        SPLASH_FIRST_FRAME_DURATION + (SPLASH_CLOUD_FRAME_COUNT - 1) * SPLASH_FRAME_DURATION

    /** First frame that paints the yellow sun (frame 1). */
    const val SPLASH_FIRST_SUN_FRAME_INDEX = 1

    // Files (Constants.java:26-30)
    const val APP_FOLDER = "GraceApp"
    const val GRACE_PHOTO = "Grace_Photo_"
    const val GRACE_BLESSED_PHOTO = "Grace_Blessed_Photo_"
    const val GRACE_PHOTO_JPG = ".jpg"
    const val DATE_PHOTO_FORMAT = "yyyyMMdd_HHmmss"
    const val PICKED_PHOTO_PREFIX = "picked_"

    // Photo processing (Constants.java:55-59)
    const val LANDSCAPE_RESIZE = 0
    const val PORTRAIT_RESIZE = 1
    const val MARGIN_FACTOR = 12
    const val WATERMARK_DIMENSIONS_FACTOR = 3
    const val JPEG_QUALITY = 100

    // Preferences (Constants.java:51)
    const val DISCLAIMER_TNC_KEY = "disclaimer_tnc_key"

    /**
     * Version of the legal documents the customer must accept before entering the app.
     * Bumped when advertising/purchases were introduced so existing installs are shown the
     * material changes again (see `SettingsStore.acceptedPolicyVersion`).
     */
    const val REQUIRED_POLICY_VERSION = 2

    // Monetization identifiers. Conceptual IDs are fixed; the *store records* behind them
    // are configured in App Store Connect / Play Console / the RevenueCat dashboard.
    const val ENTITLEMENT_REMOVE_ADS = "remove_ads"
    const val OFFERING_DEFAULT = "default"
    const val PRODUCT_REMOVE_ADS_LIFETIME = "remove_ads_lifetime"

    const val PRIVACY_POLICY_URL = "https://blessameal.com/privacy_policy.html"

    const val TERMS_AND_CONDITIONS_URL = "https://blessameal.com/terms_and_conditions.html"

    // TODO(product): replace with the real store listing once Grace is published.
    const val INVITE_FRIENDS_URL = "https://play.google.com/store/apps/details?id=com.grace.app"

    // Disclaimer web page
    const val DISCLAIMER_WEB_URL = "https://blessameal.com/disclaimer.html"

    // View ratios (fragment_photo.xml weights)
    const val PHOTO_AREA_WEIGHT = 0.6f
    const val PANEL_AREA_WEIGHT = 0.4f
}
