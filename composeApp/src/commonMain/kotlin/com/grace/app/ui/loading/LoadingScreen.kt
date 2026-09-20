package com.grace.app.ui.loading

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grace.app.core.GraceConstants
import com.grace.app.resources.Res
import com.grace.app.resources.blessing_photo_text
import com.grace.app.resources.let_me_see_text
import com.grace.app.resources.loading_god
import com.grace.app.resources.loading_god_no_circles
import com.grace.app.ui.components.TypingIndicator
import com.grace.app.ui.navigation.LoadingMessage
import com.grace.app.ui.theme.GraceColors
import com.grace.app.ui.theme.GraceDimens
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * `LoadingFragment` + `fragment_loading.xml`.
 *
 * Note: the plan's prose said "black background", but the layout uses
 * `@color/colorPrimary` — the layout is the source of truth (PLAN.md §0).
 *
 * The god image pulses alpha 0.1 ↔ 1.0 every 1100ms (reverse, infinite) and the
 * static `loading_god_no_circles` sits on top, so only the circles visibly
 * pulse. The typing indicator is centred under the message.
 */
@Composable
fun LoadingScreen(
    viewModel: LoadingViewModel,
    message: LoadingMessage,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { onStart() }

    val transition = rememberInfiniteTransition(label = "loadingPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(GraceConstants.LOADING_ANIMATION_DURATION),
            repeatMode = RepeatMode.Reverse
        ),
        label = "godAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GraceColors.Primary)
    ) {
        Box(
            modifier = Modifier
                .weight(0.75f)
                .fillMaxSize()
                .padding(GraceDimens.LoadingFragmentMargin)
        ) {
            Image(
                painter = painterResource(Res.drawable.loading_god),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(pulse)
            )
            Image(
                painter = painterResource(Res.drawable.loading_god_no_circles),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .weight(0.25f)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(
                        when (message) {
                            LoadingMessage.LetMeSee -> Res.string.let_me_see_text
                            LoadingMessage.BlessingPhoto -> Res.string.blessing_photo_text
                        }
                    ),
                    color = Color.White,
                    fontSize = GraceDimens.LoadingTitleTextSize,
                    textAlign = TextAlign.Center
                )
                Box(modifier = Modifier.padding(top = 16.dp)) {
                    TypingIndicator()
                }
            }
        }
    }
}
