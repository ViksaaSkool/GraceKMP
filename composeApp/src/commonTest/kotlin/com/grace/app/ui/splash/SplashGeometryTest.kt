package com.grace.app.ui.splash

import com.grace.app.core.GraceConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The badge must come to rest inside the white cloud mass and below the sun, on
 * every supported window size, and must never travel above its resting position.
 */
class SplashGeometryTest {

    /** Portrait windows the app ships to: small phones through iPad. */
    private val windows = listOf(
        320f to 568f,
        360f to 640f,
        375f to 667f,
        393f to 852f,
        430f to 932f,
        768f to 1024f,
        820f to 1180f
    )

    private val logoHeightPx = 220f
    private val clearancePx = 24f

    private fun sunBottomPx(windowWidthPx: Float, windowHeightPx: Float) =
        SplashGeometry.cloudTopPx(windowHeightPx, windowWidthPx) +
            windowWidthPx * SplashGeometry.SunBottomAssetY / SplashGeometry.CloudViewportWidth

    @Test
    fun cloudsAreBottomAnchoredAtTheirAspectRatio() {
        val windowWidth = 400f
        val windowHeight = 900f
        val cloudHeight = windowWidth * SplashGeometry.CloudAspectRatio

        assertEquals(
            windowHeight - cloudHeight,
            SplashGeometry.cloudTopPx(windowHeight, windowWidth),
            absoluteTolerance = 0.001f
        )
    }

    @Test
    fun badgeRestsBelowTheSunOnEveryWindow() {
        windows.forEach { (width, height) ->
            val restTop = SplashGeometry.logoFinalTopPx(height, width, clearancePx)
            val sunBottom = sunBottomPx(width, height)

            assertTrue(
                restTop > sunBottom,
                "badge top $restTop must be below the sun ($sunBottom) at ${width}x$height"
            )
            assertTrue(
                restTop >= SplashGeometry.whiteBoundaryPx(height, width),
                "badge must rest inside the white cloud at ${width}x$height"
            )
        }
    }

    @Test
    fun badgeStaysInsideTheWindowOnEveryWindow() {
        windows.forEach { (width, height) ->
            val restTop = SplashGeometry.logoFinalTopPx(height, width, clearancePx)
            assertTrue(
                restTop + logoHeightPx <= height,
                "badge bottom ${restTop + logoHeightPx} must fit in $height at ${width}x$height"
            )
        }
    }

    @Test
    fun badgeTravelIsMonotonicAndStartsOffscreen() {
        val windowWidth = 393f
        val windowHeight = 852f

        assertEquals(
            windowHeight,
            SplashGeometry.logoTopPx(windowHeight, windowWidth, clearancePx, rise = 0f),
            absoluteTolerance = 0.001f
        )
        assertEquals(
            SplashGeometry.logoFinalTopPx(windowHeight, windowWidth, clearancePx),
            SplashGeometry.logoTopPx(windowHeight, windowWidth, clearancePx, rise = 1f),
            absoluteTolerance = 0.001f
        )

        var previous = Float.POSITIVE_INFINITY
        var rise = 0f
        while (rise <= 1f) {
            val top = SplashGeometry.logoTopPx(windowHeight, windowWidth, clearancePx, rise)
            assertTrue(top <= previous, "travel must never reverse at rise=$rise")
            assertTrue(top >= SplashGeometry.logoFinalTopPx(windowHeight, windowWidth, clearancePx))
            previous = top
            rise += 0.02f
        }
    }

    @Test
    fun cloudSequenceEndsAfterTheSunIsBehindTheClouds() {
        // The badge is only released once the whole cloud sequence has played, so
        // the first frame that paints the sun is always in the past by then.
        val firstSunFrameEnd =
            GraceConstants.SPLASH_FIRST_FRAME_DURATION +
                GraceConstants.SPLASH_FIRST_SUN_FRAME_INDEX * GraceConstants.SPLASH_FRAME_DURATION

        assertTrue(GraceConstants.SPLASH_CLOUD_SEQUENCE_DURATION > firstSunFrameEnd)
    }
}
