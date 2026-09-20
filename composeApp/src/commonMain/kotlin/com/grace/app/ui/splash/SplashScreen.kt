package com.grace.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.grace.app.core.GraceConstants
import com.grace.app.resources.Res
import com.grace.app.resources.clouds_0
import com.grace.app.resources.clouds_1
import com.grace.app.resources.clouds_2
import com.grace.app.resources.clouds_3
import com.grace.app.resources.clouds_4
import com.grace.app.resources.clouds_5
import com.grace.app.resources.clouds_6
import com.grace.app.resources.clouds_7
import com.grace.app.resources.clouds_8
import com.grace.app.resources.grace_main
import com.grace.app.ui.navigation.Navigator
import com.grace.app.ui.navigation.Screen
import com.grace.app.ui.theme.GraceColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

private val cloudFrames = listOf(
    Res.drawable.clouds_0,
    Res.drawable.clouds_1,
    Res.drawable.clouds_2,
    Res.drawable.clouds_3,
    Res.drawable.clouds_4,
    Res.drawable.clouds_5,
    Res.drawable.clouds_6,
    Res.drawable.clouds_7,
    Res.drawable.clouds_8
)

/** Intrinsic size of `grace_main`; pinned explicitly so the geometry is exact. */
private val LogoWidth = 260.dp
private val LogoHeight = 220.dp

/**
 * Gap kept between the white-cloud boundary and the badge's resting top edge,
 * wide enough to absorb the transient scale/rotation overshoot.
 */
private val LogoClearance = 24.dp

/** Entrance styling: the badge starts small, tilted and transparent, then settles. */
private const val LogoStartScale = 0.82f
private const val LogoStartRotationDeg = -3f

/** Soft warm halo drawn behind the badge as it arrives. */
private val GlowColor = Color(0x59FFF4D6)

/**
 * The single, continuous "Heaven Opens" splash.
 *
 * Both platforms hand off from the same native frame — a plain `colorPrimary`
 * field (the iOS `LaunchScreen.storyboard` and the Android starting window,
 * including the Android 12+ system splash) — so the shared animation starts on
 * the same beat everywhere. From there:
 *
 *  - the clouds fade in (`clouds_0`) and then open (`clouds_0..8`), revealing
 *    the sun behind them, while the cloud layer settles for depth;
 *  - only once the whole cloud sequence has played does the `grace_main` badge
 *    rise from below, coming to rest inside the white cloud mass below the sun;
 *  - a soft halo fades in behind the badge;
 *  - the finished composition holds briefly, then crossfades into Get Meal.
 *
 * The original (`GraceSplashScreenActivity` + `activity_splash_screen.xml`) ran
 * the clouds for 1200ms, waited another 1200ms for the logo, then held 1100ms —
 * ~3.8s across two visibly separate splashes. This keeps the same assets and
 * story but overlaps the beats into one scene.
 */
@Composable
fun SplashScreen(
    navigator: Navigator,
    modifier: Modifier = Modifier
) {
    var frameIndex by remember { mutableIntStateOf(0) }

    // Cloud reveal: both platforms hand off from a plain `colorPrimary` native
    // frame, so the clouds fade in as the first beat of the shared animation.
    val cloudAlpha = remember { Animatable(0f) }
    // Cloud layer settle (depth).
    val cloudSettle = remember { Animatable(0f) }
    // Badge entrance channels.
    val rise = remember { Animatable(0f) }
    val logoScale = remember { Animatable(LogoStartScale) }
    val logoRotation = remember { Animatable(LogoStartRotationDeg) }
    val logoAlpha = remember { Animatable(0f) }
    val halo = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Honour the system "remove animations" duration scale (Android). On iOS
        // Compose does not surface Reduce Motion, so this is a no-op there.
        val reducedMotion = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f
        if (reducedMotion) {
            frameIndex = cloudFrames.lastIndex
            cloudAlpha.snapTo(1f)
            cloudSettle.snapTo(1f)
            rise.snapTo(1f)
            logoScale.snapTo(1f)
            logoRotation.snapTo(0f)
            logoAlpha.snapTo(1f)
            halo.snapTo(1f)
            delay(GraceConstants.SPLASH_HOLD_DURATION.toLong())
            navigator.replace(Screen.GetMeal)
            return@LaunchedEffect
        }

        launch {
            cloudAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = GraceConstants.SPLASH_CLOUD_FADE_DURATION,
                    easing = LinearOutSlowInEasing
                )
            )
        }
        val clouds = launch {
            cloudFrames.forEachIndexed { index, _ ->
                frameIndex = index
                delay(
                    if (index == 0) GraceConstants.SPLASH_FIRST_FRAME_DURATION.toLong()
                    else GraceConstants.SPLASH_FRAME_DURATION.toLong()
                )
            }
        }
        val settle = launch {
            cloudSettle.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = GraceConstants.SPLASH_CLOUD_SETTLE_DURATION,
                    easing = LinearOutSlowInEasing
                )
            )
        }

        // Wait for the whole cloud sequence: the sun is now behind the clouds and
        // the last frame has been held for a frame interval before the badge
        // starts moving. `coroutineScope` also makes the hold below wait for
        // every badge channel to finish.
        clouds.join()

        val logoDuration = GraceConstants.SPLASH_LOGO_DURATION
        coroutineScope {
            launch {
                rise.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = logoDuration
                        0f at 0 using FastOutLinearInEasing
                        0.88f at (logoDuration * 0.62f).toInt() using LinearOutSlowInEasing
                        1f at logoDuration
                    }
                )
            }
            launch {
                logoScale.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = logoDuration
                        LogoStartScale at 0
                        1.045f at (logoDuration * 0.78f).toInt()
                        1f at logoDuration
                    }
                )
            }
            launch {
                logoRotation.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = logoDuration
                        LogoStartRotationDeg at 0
                        1.2f at (logoDuration * 0.8f).toInt()
                        0f at logoDuration
                    }
                )
            }
            launch {
                logoAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = (logoDuration * 0.7f).toInt(),
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                halo.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = logoDuration,
                        easing = LinearOutSlowInEasing
                    )
                )
            }
        }

        settle.join()
        delay(GraceConstants.SPLASH_HOLD_DURATION.toLong())
        navigator.replace(Screen.GetMeal)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(GraceColors.Primary)
    ) {
        val windowWidthPx = constraints.maxWidth.toFloat()
        val windowHeightPx = constraints.maxHeight.toFloat()
        val clearancePx = with(LocalDensity.current) { LogoClearance.toPx() }

        // The badge comes to rest inside the white cloud mass, below the sun, so
        // it can never cover the sun even though it is painted on top of it.
        val logoFinalTopPx =
            SplashGeometry.logoFinalTopPx(windowHeightPx, windowWidthPx, clearancePx)
        val logoTopPx = SplashGeometry.logoTopPx(
            windowHeightPx = windowHeightPx,
            windowWidthPx = windowWidthPx,
            clearancePx = clearancePx,
            rise = rise.value
        )

        // Clouds are full-bleed: sized to the artwork's aspect ratio and pinned to
        // the very bottom of the window so their white mass runs under the
        // navigation bar / home indicator instead of leaving a blue strip.
        val cloudScale = 1.035f - 0.035f * cloudSettle.value
        Image(
            painter = painterResource(cloudFrames[frameIndex]),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .height(maxWidth * SplashGeometry.CloudAspectRatio)
                .align(Alignment.BottomCenter)
                .alpha(cloudAlpha.value)
                .graphicsLayer {
                    scaleX = cloudScale
                    scaleY = cloudScale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
        )

        // Halo: a soft warm light that blooms behind the badge's resting position.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(halo.value)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(GlowColor, Color.Transparent),
                            center = Offset(
                                x = size.width / 2f,
                                y = logoFinalTopPx + LogoHeight.toPx() / 2f
                            ),
                            radius = size.width * 0.95f
                        )
                    )
                }
        )

        Image(
            painter = painterResource(Res.drawable.grace_main),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(x = 0, y = logoTopPx.roundToInt()) }
                .size(LogoWidth, LogoHeight)
                .alpha(logoAlpha.value)
                .graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    rotationZ = logoRotation.value
                }
        )
    }
}
