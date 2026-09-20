package com.grace.app.ui.policy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.backhandler.BackHandler
import com.grace.app.core.GraceConstants
import com.grace.app.resources.Res
import com.grace.app.resources.disclaimer_title
import com.grace.app.resources.enter_app_text
import com.grace.app.resources.exit_app_text
import com.grace.app.resources.i_agree_text
import com.grace.app.resources.ic_dummy_forward
import com.grace.app.resources.ic_forward
import com.grace.app.resources.next_step_text
import com.grace.app.resources.terms_conditions_title
import com.grace.app.resources.terms_and_conditions_title
import com.grace.app.ui.components.WebPage
import com.grace.app.ui.theme.GraceColors
import com.grace.app.ui.theme.GraceDimens
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val PAGE_COUNT = 3

/**
 * Startup policy gate (was `TncDialog` + `dialog_disclaimer_tnc.xml`).
 *
 * Full-screen, non-cancelable dialog shown after the splash until the user
 * agrees: a 3-page pager (Disclaimer, Privacy Policy, Terms and Conditions),
 * dot indicator, checkbox gating and an action that reads `Next` (with an
 * accent arrow) on pages 0-1 and `OK` (disabled until all pages have been
 * read and the box is ticked, with a white arrow) on page 2. `Exit` finishes
 * the app; `OK` persists the acceptance (`disclaimer_tnc_key`, never shown
 * again).
 *
 * The "OK" button becomes enabled only after the user has scrolled
 * through all [PAGE_COUNT] pages, ensuring every legal document is
 * read before acknowledgment.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PolicyDialog(
    onAccepted: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    var checked by remember { mutableStateOf(false) }
    val visitedPages = remember { mutableStateOf(setOf<Int>()) }

    val currentPage = pagerState.currentPage
    val allPagesRead = visitedPages.value.size == PAGE_COUNT
    val actionEnabled = allPagesRead && checked
    val isLastPage = currentPage == PAGE_COUNT - 1

    LaunchedEffect(pagerState.currentPage) {
        visitedPages.value = visitedPages.value + pagerState.currentPage
    }

    BackHandler(enabled = true) {
        onExit()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(GraceDimens.ButtonRadius))
                .background(Color.White)
        ) {
            // Top bar: title + Exit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(GraceDimens.DialogTitlePadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        when (currentPage) {
                            0 -> Res.string.disclaimer_title
                            1 -> Res.string.terms_and_conditions_title
                            else -> Res.string.terms_conditions_title
                        }
                    ),
                    color = Color.Black,
                    fontSize = GraceDimens.DialogTitleSize,
                    modifier = Modifier
                        .weight(1f)
                        .padding(GraceDimens.DialogTitlePadding)
                )
                Text(
                    text = stringResource(Res.string.exit_app_text),
                    color = GraceColors.Accent,
                    fontSize = GraceDimens.DialogTextSize,
                    modifier = Modifier
                        .clickable(onClick = onExit)
                        .padding(GraceDimens.DialogTitlePadding)
                )
            }

            // Pager + dots
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    WebPage(
                        url = when (page) {
                            0 -> "https://blessameal.com/disclaimer.html"
                            1 -> GraceConstants.PRIVACY_POLICY_URL
                            else -> GraceConstants.TERMS_AND_CONDITIONS_URL
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                PagerDots(
                    pageCount = PAGE_COUNT,
                    currentPage = currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(GraceColors.TransparentBlack)
                        .padding(vertical = 8.dp)
                )
            }

            // Bottom bar: checkbox + action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(GraceDimens.DialogTitlePadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLastPage) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { checked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = GraceColors.Accent,
                                uncheckedColor = GraceColors.Accent,
                                checkmarkColor = Color.White
                            )
                        )
                        Text(
                            text = stringResource(Res.string.i_agree_text),
                            color = Color.Black,
                            fontSize = GraceDimens.DialogTextSize
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clickable(enabled = if (!isLastPage) true else actionEnabled) {
                            if (!isLastPage) {
                                scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                            } else {
                                onAccepted()
                            }
                        }
                        .padding(GraceDimens.DialogTitlePadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (!isLastPage) Res.string.next_step_text
                            else Res.string.enter_app_text
                        ),
                        color = if (!isLastPage || actionEnabled) GraceColors.Accent else Color.DarkGray,
                        fontSize = GraceDimens.DialogTextSize
                    )
                    Image(
                        painter = painterResource(
                            if (!isLastPage) Res.drawable.ic_forward else Res.drawable.ic_dummy_forward
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Replaces the `TabLayout` used purely as a dot indicator
 * (`tab_indicator_selected` = 10dp accent ring, `tab_indicator_default` =
 * 10dp darker-grey ring, `dots_spacing` 10dp).
 */
@Composable
private fun PagerDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = GraceDimens.DotsSpacing / 2)
                    .size(GraceDimens.DotsSize)
                    .clip(CircleShape)
                    .background(if (selected) GraceColors.Accent else Color.DarkGray)
            )
            if (index != pageCount - 1) {
                Spacer(modifier = Modifier.size(GraceDimens.DotsSpacing))
            }
        }
    }
}
