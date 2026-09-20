package com.grace.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grace.app.core.GraceConstants
import com.grace.app.ui.theme.GraceColors
import com.grace.app.ui.theme.GraceDimens

/**
 * Replaces `com.udevel.widgetlab.TypingIndicatorView`
 * (dotCount 3, Grow, sequence, dotColor colorAccent, dotSize 10dp,
 * dotHorizontalSpacing 10dp, dotAnimationDuration 300ms).
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = GraceColors.Accent,
    backgroundColor: Color = Color.Transparent,
    dotSize: Dp = GraceDimens.DotsSize,
    spacing: Dp = GraceDimens.DotsSpacing
) {
    val duration = GraceConstants.DOTS_ANIMATION_DURATION
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val scale by transition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = duration,
                        delayMillis = index * duration / 2,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(scale)
                    .background(dotColor, CircleShape)
                    .then(
                        if (backgroundColor == Color.Transparent) Modifier
                        else Modifier.padding(0.dp)
                    )
            )
        }
    }
}
