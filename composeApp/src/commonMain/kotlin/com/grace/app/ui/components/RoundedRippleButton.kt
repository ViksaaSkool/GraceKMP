package com.grace.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import com.grace.app.ui.theme.GraceDimens

/**
 * Replaces `MaterialRippleLayout` + `Button` with
 * `@drawable/rounded_button_white` / `@drawable/rounded_button_pink`:
 * a 30dp-radius pill, 4dp text padding, 14sp all-caps label, and a black
 * 10%-alpha ripple.
 */
@Composable
fun RoundedRippleButton(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = GraceDimens.TextButtonsPadding)
) {
    val shape = RoundedCornerShape(GraceDimens.ButtonRadius)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(backgroundColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.Black.copy(alpha = 0.1f)),
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = GraceDimens.ButtonTextSize,
            textAlign = TextAlign.Center
        )
    }
}
