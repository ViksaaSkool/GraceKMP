package com.grace.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.grace.app.ui.components.GraceTopBar
import com.grace.app.ui.components.WebPage
import com.grace.app.ui.theme.GraceColors
import com.grace.app.ui.theme.GraceDimens

/**
 * A legal document from Settings, rendered in the same in-app web view the
 * first-run dialog uses.
 */
@Composable
fun LegalScreen(
    title: String,
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GraceColors.Primary)
    ) {
        GraceTopBar(title = title, onBack = onBack)

        WebPage(
            url = url,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = GraceDimens.ScreenHorizontalMargin)
                .clip(
                    RoundedCornerShape(
                        topStart = GraceDimens.SettingsPanelRadius,
                        topEnd = GraceDimens.SettingsPanelRadius
                    )
                )
        )
    }
}
