package com.grace.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.grace.app.core.GraceConstants
import com.grace.app.ui.theme.GraceColors
import com.grace.app.ui.theme.GraceDimens

/**
 * The "Tap for full photo" strip (fragment_photo.xml:49-67):
 * `colorAccent` background with `alpha="0.3"` on the container (so the white
 * label is faded too), 8dp padding, 18sp label, and a `scaleY` 0→1 reveal over
 * 700ms (`Constants.TAP_DURATION`).
 */
@Composable
fun TapFullStrip(
    text: String,
    modifier: Modifier = Modifier
) {
    val scaleY = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scaleY.animateTo(1f, animationSpec = tween(GraceConstants.TAP_DURATION))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scaleX = 1f, scaleY = scaleY.value)
            .alpha(0.3f)
            .background(GraceColors.Accent)
            .padding(GraceDimens.DialogTitlePadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}
