package com.grace.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dimensions ported verbatim from the original `res/values/dimens.xml`
 * and `res/values/integer.xml`.
 */
object GraceDimens {
    val LoadingFragmentMargin = 32.dp
    val BottomButtonsMargin = 18.dp
    val CardMargin = 24.dp
    val CardElevation = 8.dp

    /**
     * The meal cards are compact, rounded tiles that keep their illustration
     * inside the card. The final size is the largest rectangle with
     * [CardAspectRatio] that fits two cards plus [CardSpacing] in the available
     * height, so the bottom card is never cut off.
     */
    val CardRadius = 20.dp
    val CardMaxWidth = 340.dp
    val CardSpacing = 40.dp
    val CardContentPadding = 20.dp
    val CardImageBottomPadding = 48.dp
    const val CardAspectRatio = 1.0f

    val BottomBetweenButtonsMargin = 8.dp
    val TextButtonsPadding = 4.dp

    val ButtonRadius = 30.dp

    /** Settings screen. */
    val TopBarHeight = 56.dp

    /** Get Meal's settings bar: taller, with the gear pushed down and out. */
    val SettingsBarHeight = 68.dp
    val ScreenHorizontalMargin = 16.dp
    val SettingsRowHeight = 56.dp
    val SettingsPanelRadius = 20.dp
    val SettingsIconSize = 24.dp
    val DividerThickness = 1.dp

    val LoadingTitleTextSize = 24.sp
    val ButtonTextSize = 14.sp
    val ResultSubTitleTextSize = 18.sp
    val DialogTitlePadding = 8.dp
    val DialogTitleSize = 20.sp
    val DialogTextSize = 16.sp
    val CardLabelTextSize = 18.sp

    val DotsSize = 10.dp
    val DotsSpacing = 10.dp
    val ClosePadding = 12.dp

    const val AlphaPhotoOverlay = 0.7f

    // Timing lives in `GraceConstants` (single source of truth).
    const val DotsAnimationDurationMs = 300
    const val CrossFadeMs = 1000
    const val LoadingPulseMs = 1100
    const val ScaleInMs = 500
    const val TapStripMs = 700
}
