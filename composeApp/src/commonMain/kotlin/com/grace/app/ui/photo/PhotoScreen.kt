package com.grace.app.ui.photo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.grace.app.core.GraceConstants
import com.grace.app.domain.model.MealContent
import com.grace.app.resources.Res
import com.grace.app.resources.another_try_text
import com.grace.app.resources.do_you_want_to_text
import com.grace.app.resources.done_text
import com.grace.app.resources.error_god
import com.grace.app.resources.error_god_top
import com.grace.app.resources.feel_wraith_text
import com.grace.app.resources.his_grace_angered_text
import com.grace.app.resources.his_grace_approves_text
import com.grace.app.resources.his_grace_asks_text
import com.grace.app.resources.no_text
import com.grace.app.resources.not_a_meal_text
import com.grace.app.resources.share_text
import com.grace.app.resources.tap_for_full_text
import com.grace.app.resources.yes_text
import com.grace.app.resources.your_meal_is_blessed_text
import com.grace.app.ui.components.AutoSizeText
import com.grace.app.ui.components.RoundedRippleButton
import com.grace.app.ui.components.TapFullStrip
import com.grace.app.ui.theme.GraceColors
import com.grace.app.ui.theme.GraceDimens
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * `PhotoFragment` + `fragment_photo.xml`.
 *
 * 60% photo area (black, bottom-anchored crop, 70%-alpha top-down black
 * gradient, error-god composite for Angered, "Tap for full photo" strip) over a
 * 40% black panel (accent all-caps title, auto-sizing subtitle, two rounded
 * ripple buttons that scale in over 500ms).
 */
@Composable
fun PhotoScreen(
    viewModel: PhotoViewModel,
    modifier: Modifier = Modifier
) {
    val content = viewModel.content
    val photoUri = viewModel.photoUri

    var wraithPulseStarted by remember { mutableStateOf(false) }
    var tapStripVisible by remember { mutableStateOf(content.hasPhoto) }

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Photo area (60%) ----
        Box(
            modifier = Modifier
                .weight(GraceConstants.PHOTO_AREA_WEIGHT)
                .fillMaxWidth()
                .background(
                    if (content.hasPhoto) Color.Black else GraceColors.Primary
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.onPhotoClick()
                    tapStripVisible = false
                }
        ) {
            if (content.hasPhoto && photoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(photoUri.toPath())
                        .crossfade(GraceConstants.CROSS_FADE_DURATION)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxSize()
                )

                // black_gradient overlay at 70% alpha (top black → bottom clear)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(GraceDimens.AlphaPhotoOverlay)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent)
                            )
                        )
                )
            } else {
                ErrorGodView(pulsing = wraithPulseStarted)
            }

            if (content.hasPhoto && tapStripVisible) {
                TapFullStrip(
                    text = stringResource(Res.string.tap_for_full_text),
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // ---- Panel (40%) ----
        Box(
            modifier = Modifier
                .weight(GraceConstants.PANEL_AREA_WEIGHT)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(GraceDimens.BottomButtonsMargin)
                    .padding(bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(content.titleRes()),
                    color = GraceColors.Accent,
                    fontSize = GraceDimens.LoadingTitleTextSize,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
                // fragment_photo.xml gives the AutoResizeTextView match_parent
                // height with gravity="center", so it is centred in the space
                // between the title and the buttons.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AutoSizeText(
                        text = stringResource(content.subtitleRes()),
                        color = Color.White,
                        maxFontSize = GraceDimens.ResultSubTitleTextSize,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(GraceDimens.BottomButtonsMargin),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScaleIn(modifier = Modifier.weight(1f)) {
                    RoundedRippleButton(
                        text = stringResource(content.leftButtonRes()),
                        backgroundColor = Color.White,
                        textColor = GraceColors.Accent,
                        onClick = {
                            if (content.leftIsFeelWraith) wraithPulseStarted = true
                            else viewModel.onLeftButton()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.size(GraceDimens.BottomBetweenButtonsMargin * 2))
                ScaleIn(modifier = Modifier.weight(1f)) {
                    RoundedRippleButton(
                        text = stringResource(content.rightButtonRes()),
                        backgroundColor = GraceColors.Accent,
                        textColor = Color.White,
                        onClick = viewModel::onRightButton,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** `UiUtil.setAndStartScaleAnimation(view, 0f, 1f, 500)`. */
@Composable
private fun ScaleIn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(GraceConstants.SCALE_DURATION))
    }
    Box(modifier = modifier.scale(scale.value)) { content() }
}

/**
 * `error_relative_layout`: `error_god` with `error_god_top` layered over it,
 * inset by 32dp. The base image is the one the "Feel the wraith" animation
 * pulses (alpha 1.0 → 0.1, 1100ms, reverse, infinite).
 */
@Composable
private fun ErrorGodView(
    pulsing: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "wraith")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 0.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(GraceConstants.LOADING_ANIMATION_DURATION),
            repeatMode = RepeatMode.Reverse
        ),
        label = "errorGodAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(GraceDimens.LoadingFragmentMargin)
    ) {
        Image(
            painter = painterResource(Res.drawable.error_god),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .alpha(pulse)
        )
        Image(
            painter = painterResource(Res.drawable.error_god_top),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun MealContent.titleRes() = when (this) {
    MealContent.Asks -> Res.string.his_grace_asks_text
    MealContent.Approves -> Res.string.his_grace_approves_text
    MealContent.Angered -> Res.string.his_grace_angered_text
}

private fun MealContent.subtitleRes() = when (this) {
    MealContent.Asks -> Res.string.do_you_want_to_text
    MealContent.Approves -> Res.string.your_meal_is_blessed_text
    MealContent.Angered -> Res.string.not_a_meal_text
}

private fun MealContent.leftButtonRes() = when (this) {
    MealContent.Asks -> Res.string.no_text
    MealContent.Approves -> Res.string.done_text
    MealContent.Angered -> Res.string.feel_wraith_text
}

private fun MealContent.rightButtonRes() = when (this) {
    MealContent.Asks -> Res.string.yes_text
    MealContent.Approves -> Res.string.share_text
    MealContent.Angered -> Res.string.another_try_text
}
