package com.grace.app.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Replaces `com.lb.auto_fit_textview.AutoResizeTextView`.
 *
 * The original shrank the text until it fit its view. We start at
 * [maxFontSize] and step down to [minFontSize] while the measured layout
 * overflows. `BasicText`'s `autoSize` would be the modern equivalent, but the
 * explicit loop keeps the behaviour obvious and portable.
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    maxFontSize: TextUnit = 18.sp,
    minFontSize: TextUnit = 10.sp,
    textAlign: TextAlign = TextAlign.Center
) {
    var fontSizeSp by remember(text) { mutableFloatStateOf(maxFontSize.value) }

    BasicText(
        text = text,
        modifier = modifier,
        overflow = TextOverflow.Clip,
        maxLines = Int.MAX_VALUE,
        onTextLayout = { result ->
            // AutoResizeTextView shrank until the text fit its view; we shrink
            // on either axis so the label never clips.
            if ((result.didOverflowHeight || result.didOverflowWidth) &&
                fontSizeSp > minFontSize.value
            ) {
                fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(minFontSize.value)
            }
        },
        style = TextStyle(
            color = color,
            fontSize = fontSizeSp.sp,
            textAlign = textAlign
        )
    )
}
