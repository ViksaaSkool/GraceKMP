package com.grace.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.grace.app.resources.Res
import com.grace.app.resources.disclaimer_title
import com.grace.app.resources.grace_premium_title
import com.grace.app.resources.ic_chevron_right
import com.grace.app.resources.invite_friends_text
import com.grace.app.resources.privacy_choices_text
import com.grace.app.resources.privacy_policy_title
import com.grace.app.resources.premium_unavailable_text
import com.grace.app.resources.purchased_text
import com.grace.app.resources.remove_ads_with_price
import com.grace.app.resources.restore_purchases_text
import com.grace.app.resources.settings_title
import com.grace.app.resources.support_id_label
import com.grace.app.resources.terms_conditions_title
import com.grace.app.ui.components.RoundedRippleButton
import com.grace.app.ui.components.GraceTopBar
import com.grace.app.ui.theme.GraceColors
import com.grace.app.ui.theme.GraceDimens
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The settings screen: a back bar over a white rounded panel with the app's
 * legal pages and the invite action.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val premium by viewModel.premiumState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GraceColors.Primary)
    ) {
        GraceTopBar(
            title = stringResource(Res.string.settings_title),
            onBack = viewModel::onBack
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GraceDimens.ScreenHorizontalMargin)
                .clip(RoundedCornerShape(GraceDimens.SettingsPanelRadius))
                .background(Color.White)
        ) {
            SettingsRow(
                label = stringResource(Res.string.disclaimer_title),
                onClick = viewModel::onDisclaimer
            )
            SettingsDivider()
            SettingsRow(
                label = stringResource(Res.string.privacy_policy_title),
                onClick = viewModel::onPrivacyPolicy
            )
            SettingsDivider()
            SettingsRow(
                label = stringResource(Res.string.terms_conditions_title),
                onClick = viewModel::onTermsAndConditions
            )
        }

        GracePremiumSection(
            state = premium,
            onRemoveAds = viewModel::onRemoveAds,
            onRestore = viewModel::onRestorePurchases,
            onPrivacyChoices = viewModel::onPrivacyChoices,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GraceDimens.ScreenHorizontalMargin)
                .padding(top = GraceDimens.BottomBetweenButtonsMargin)
        )

        RoundedRippleButton(
            text = stringResource(Res.string.invite_friends_text),
            backgroundColor = GraceColors.Accent,
            textColor = Color.White,
            onClick = viewModel::onInviteFriends,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GraceDimens.ScreenHorizontalMargin)
                .padding(top = GraceDimens.BottomBetweenButtonsMargin)
        )
    }
}

/**
 * The Grace Premium panel: Remove Ads with the store-localised price, a purchased state,
 * Restore Purchases, the UMP privacy-options entry point (only when UMP requires it), and
 * the anonymous RevenueCat support ID for troubleshooting.
 */
@Composable
private fun GracePremiumSection(
    state: PremiumUiState,
    onRemoveAds: () -> Unit,
    onRestore: () -> Unit,
    onPrivacyChoices: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(GraceDimens.SettingsPanelRadius))
            .background(Color.White)
    ) {
        SettingsSectionHeader(text = stringResource(Res.string.grace_premium_title))

        when {
            state.purchased -> SettingsRow(
                label = stringResource(Res.string.purchased_text),
                onClick = null
            )

            state.product != null -> SettingsRow(
                label = stringResource(
                    Res.string.remove_ads_with_price,
                    state.product!!.localizedPrice
                ),
                onClick = if (state.busy) null else onRemoveAds
            )

            else -> SettingsRow(
                label = stringResource(Res.string.premium_unavailable_text),
                onClick = null
            )
        }

        SettingsDivider()
        SettingsRow(
            label = stringResource(Res.string.restore_purchases_text),
            onClick = if (state.busy) null else onRestore
        )

        if (state.privacyOptionsRequired) {
            SettingsDivider()
            SettingsRow(
                label = stringResource(Res.string.privacy_choices_text),
                onClick = onPrivacyChoices
            )
        }

        state.supportId?.let { id ->
            SettingsDivider()
            SettingsRow(
                label = stringResource(Res.string.support_id_label, id),
                onClick = null
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = GraceDimens.ScreenHorizontalMargin)
            .height(GraceDimens.SettingsRowHeight / 2 + 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = Color(0xFF757575),
            fontSize = GraceDimens.ButtonTextSize
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GraceDimens.SettingsRowHeight)
            .then(
                // `null` renders an informational row: same metrics, no ripple, no action.
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = Color.Black.copy(alpha = 0.1f)),
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = GraceDimens.ScreenHorizontalMargin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.Black,
            fontSize = GraceDimens.DialogTextSize,
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            Image(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(GraceDimens.SettingsIconSize)
            )
        }
    }
}

@Composable
private fun SettingsDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GraceDimens.ScreenHorizontalMargin)
            .height(GraceDimens.DividerThickness)
            .background(Color(0xFFE0E0E0))
    )
}
