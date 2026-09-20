package com.grace.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.grace.app.resources.Res
import com.grace.app.resources.back_text
import com.grace.app.resources.ic_back
import com.grace.app.ui.theme.GraceDimens
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * White-on-blue bar with a back action and a title, used by the screens that
 * stack over Get Meal (Settings, legal documents).
 */
@Composable
fun GraceTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GraceDimens.TopBarHeight)
            .padding(horizontal = GraceDimens.DialogTitlePadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(Res.drawable.ic_back),
                contentDescription = stringResource(Res.string.back_text),
                tint = Color.White,
                modifier = Modifier.size(GraceDimens.SettingsIconSize)
            )
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = GraceDimens.DialogTitleSize
        )
    }
}
