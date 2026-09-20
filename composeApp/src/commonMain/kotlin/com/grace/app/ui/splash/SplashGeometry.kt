package com.grace.app.ui.splash

/**
 * Cloud/badge geometry for the "Heaven Opens" splash.
 *
 * `clouds_0..8` are 540x767dp vectors drawn full width and pinned to the bottom
 * of the window, so the artwork's top edge sits at
 * `windowHeight - windowWidth * 767/540` and an asset coordinate maps to
 * `cloudTop + windowWidth * assetY / 540`.
 *
 * In the final frame the yellow sun occupies asset y 24.2..182.2. Below
 * [WhiteBoundaryAssetY] the white cloud mass covers the full width of the
 * artwork, so a badge resting under it can never cover the sun.
 */
object SplashGeometry {

    const val CloudViewportWidth = 540f
    const val CloudViewportHeight = 767f

    /** Displayed height divided by displayed width of the cloud artwork. */
    const val CloudAspectRatio = CloudViewportHeight / CloudViewportWidth

    /** Lowest asset-y reached by the sun in `clouds_8`. */
    const val SunBottomAssetY = 182.2f

    /**
     * Conservative white-cloud boundary: below the sun and below every ridge in
     * the foreground cloud paths, so it is white across the whole width.
     */
    const val WhiteBoundaryAssetY = 210f

    fun cloudTopPx(windowHeightPx: Float, windowWidthPx: Float): Float =
        windowHeightPx - windowWidthPx * CloudAspectRatio

    fun whiteBoundaryPx(windowHeightPx: Float, windowWidthPx: Float): Float =
        cloudTopPx(windowHeightPx, windowWidthPx) +
            windowWidthPx * WhiteBoundaryAssetY / CloudViewportWidth

    /** Where the badge's top edge comes to rest: inside the white cloud. */
    fun logoFinalTopPx(
        windowHeightPx: Float,
        windowWidthPx: Float,
        clearancePx: Float
    ): Float = whiteBoundaryPx(windowHeightPx, windowWidthPx) + clearancePx

    /**
     * Interpolates the badge from fully offscreen (`rise == 0`) to its resting
     * position (`rise == 1`). The travel is monotonic, so the badge never
     * overshoots upwards into the sun.
     */
    fun logoTopPx(
        windowHeightPx: Float,
        windowWidthPx: Float,
        clearancePx: Float,
        rise: Float
    ): Float {
        val finalTop = logoFinalTopPx(windowHeightPx, windowWidthPx, clearancePx)
        return windowHeightPx + (finalTop - windowHeightPx) * rise
    }
}
