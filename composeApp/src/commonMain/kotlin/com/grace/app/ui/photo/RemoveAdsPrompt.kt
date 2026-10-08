package com.grace.app.ui.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grace.app.resources.Res
import com.grace.app.resources.not_now_text
import com.grace.app.resources.premium_unavailable_text
import com.grace.app.resources.remove_ads_prompt_body
import com.grace.app.resources.remove_ads_prompt_title
import com.grace.app.resources.remove_ads_with_price
import com.grace.app.resources.restore_purchases_text
import com.grace.app.ui.components.RoundedRippleButton
import com.grace.app.ui.theme.GraceColors
import com.grace.app.ui.theme.GraceDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Dismissible bottom sheet over the blessed result, shown only after an interstitial was
 * actually presented and closed.
 *
 * It is layered *on top of* the existing 60/40 layout rather than inset into it, so the
 * photo-result parity recorded in `docs/parity/REPORT.md` is untouched once the prompt is
 * dismissed.
 */
@Composable
fun RemoveAdsPrompt(
    viewModel: PhotoViewModel,
    modifier: Modifier = Modifier
) {
    val visible by viewModel.showRemoveAdsPrompt.collectAsState()
    val product by viewModel.removeAdsProduct.collectAsState()
    val busy by viewModel.purchaseInProgress.collectAsState()

    if (!visible) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = viewModel::onDismissRemoveAdsPrompt
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                // Consume taps so the scrim dismiss does not fire through the sheet.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = GraceDimens.ScreenHorizontalMargin)
                .padding(top = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.remove_ads_prompt_title),
                color = Color.Black,
                fontSize = GraceDimens.DialogTitleSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.remove_ads_prompt_body),
                color = Color(0xFF555555),
                fontSize = GraceDimens.DialogTextSize,
                textAlign = TextAlign.Center
            )

            val price = product?.localizedPrice
            RoundedRippleButton(
                text = if (price != null) {
                    stringResource(Res.string.remove_ads_with_price, price)
                } else {
                    stringResource(Res.string.premium_unavailable_text)
                },
                backgroundColor = GraceColors.Accent,
                textColor = Color.White,
                // Busy / unpriced taps are swallowed rather than changing the component's
                // resting appearance, which the parity screenshots depend on.
                onClick = { if (!busy && price != null) viewModel.onRemoveAds() },
                modifier = Modifier.fillMaxWidth()
            )

            PromptTextAction(
                text = stringResource(Res.string.not_now_text),
                color = Color.Black,
                onClick = viewModel::onDismissRemoveAdsPrompt
            )
            PromptTextAction(
                text = stringResource(Res.string.restore_purchases_text),
                color = GraceColors.PrimaryDark,
                onClick = { if (!busy) viewModel.onRestorePurchases() }
            )
        }
    }
}

@Composable
private fun PromptTextAction(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GraceDimens.ButtonRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.Black.copy(alpha = 0.08f)),
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = GraceDimens.ButtonTextSize)
    }
}
