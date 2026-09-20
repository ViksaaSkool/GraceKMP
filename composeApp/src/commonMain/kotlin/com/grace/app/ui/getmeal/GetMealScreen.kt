package com.grace.app.ui.getmeal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grace.app.core.GraceConstants
import com.grace.app.resources.Res
import com.grace.app.resources.capture_meal_text
import com.grace.app.resources.from_file_meal_text
import com.grace.app.resources.ic_settings
import com.grace.app.resources.open_settings_text
import com.grace.app.resources.take_photo_of_meal
import com.grace.app.resources.upload_meal
import com.grace.app.ui.theme.GraceColors
import com.grace.app.ui.theme.GraceDimens
import com.grace.app.ui.policy.PolicyDialog
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun GetMealScreen(
    viewModel: GetMealViewModel,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { onStart() }

    val showTnc by viewModel.showTnc.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GraceColors.Primary)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val spacing = GraceDimens.CardSpacing
                val maxCardWidth = minOf(
                    maxWidth - GraceDimens.CardMargin * 2,
                    GraceDimens.CardMaxWidth
                )
                val availableForCards = (maxHeight - GraceDimens.SettingsBarHeight).coerceAtLeast(1.dp)
                val maxCardHeight = ((availableForCards - spacing) / 2).coerceAtLeast(1.dp)
                val cardWidth = minOf(
                    maxCardWidth,
                    maxCardHeight * GraceDimens.CardAspectRatio
                ).coerceAtLeast(1.dp)
                val cardHeight = cardWidth / GraceDimens.CardAspectRatio
                val horizontalInset = (maxWidth - cardWidth) / 2

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(GraceDimens.SettingsBarHeight)
                            .padding(start = horizontalInset),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        SettingsButton(onClick = viewModel::onSettingsClick)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MealCard(
                            icon = Res.drawable.take_photo_of_meal,
                            label = stringResource(Res.string.capture_meal_text),
                            onClick = viewModel::onCaptureMeal,
                            modifier = Modifier.size(cardWidth, cardHeight)
                        )
                        Spacer(modifier = Modifier.height(spacing))
                        MealCard(
                            icon = Res.drawable.upload_meal,
                            label = stringResource(Res.string.from_file_meal_text),
                            onClick = viewModel::onMealFromGallery,
                            modifier = Modifier.size(cardWidth, cardHeight)
                        )
                    }
                }
            }
        }

        if (showTnc) {
            PolicyDialog(
                onAccepted = viewModel::onTncAccepted,
                onExit = viewModel::onTncExit
            )
        }
    }
}

@Composable
private fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            painter = painterResource(Res.drawable.ic_settings),
            contentDescription = stringResource(Res.string.open_settings_text),
            tint = Color.White,
            modifier = Modifier.size(GraceDimens.SettingsIconSize)
        )
    }
}

@Composable
private fun MealCard(
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(GraceConstants.SCALE_DURATION))
    }

    val shape = RoundedCornerShape(GraceDimens.CardRadius)
    Card(
        modifier = modifier,
        shape = shape,
        elevation = CardDefaults.cardElevation(
            defaultElevation = GraceDimens.CardElevation,
            pressedElevation = GraceDimens.CardMargin
        ),
        colors = CardDefaults.cardColors(containerColor = GraceColors.Primary)
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(GraceColors.Primary)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.Black.copy(alpha = 0.1f)),
                    onClick = onClick
                )
                .scale(scale.value)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(GraceDimens.CardContentPadding)
                    .padding(bottom = GraceDimens.CardImageBottomPadding)
            )
            Text(
                text = label.uppercase(),
                color = Color.White,
                fontSize = GraceDimens.CardLabelTextSize,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(GraceDimens.CardContentPadding)
            )
        }
    }
}
