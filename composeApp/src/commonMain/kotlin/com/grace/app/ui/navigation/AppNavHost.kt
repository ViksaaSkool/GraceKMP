package com.grace.app.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.grace.app.core.GraceConstants
import com.grace.app.domain.model.MealContent
import com.grace.app.platform.ConnectivityObserver
import com.grace.app.platform.platformScreenInsets
import com.grace.app.resources.Res
import com.grace.app.resources.disclaimer_title
import com.grace.app.resources.invite_friends_chooser_text
import com.grace.app.resources.invite_friends_message
import com.grace.app.resources.privacy_policy_title
import com.grace.app.resources.share_meal_text
import com.grace.app.resources.terms_conditions_title
import com.grace.app.ui.components.GraceSnackbarController
import com.grace.app.ui.components.GraceSnackbarHost
import com.grace.app.ui.components.GraceToken
import com.grace.app.ui.components.graceMessageText
import com.grace.app.ui.getmeal.GetMealScreen
import com.grace.app.ui.getmeal.GetMealViewModel
import com.grace.app.ui.loading.LoadingScreen
import com.grace.app.ui.loading.LoadingViewModel
import com.grace.app.ui.photo.PhotoScreen
import com.grace.app.ui.photo.PhotoViewModel
import com.grace.app.ui.photodetails.PhotoDetailsScreen
import com.grace.app.ui.settings.LegalScreen
import com.grace.app.ui.settings.SettingsScreen
import com.grace.app.ui.settings.SettingsViewModel
import com.grace.app.ui.splash.SplashScreen
import com.grace.app.ui.theme.GraceColors
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The colour the strip behind the system navigation bar should be painted, or
 * `null` to leave the window background showing.
 *
 * Every screen but the splash is inset out of the system bars, so that strip
 * normally shows `GraceColors.Primary`. The photo result screens all carry a
 * black panel down to the bottom edge (black photo area for Asks/Approves,
 * black panel for Angered), so the strip is painted black on those screens.
 */
internal fun systemNavigationBarBackdrop(screen: Screen): Color? =
    if (screen is Screen.Photo) Color.Black else null

/**
 * Replaces `Main2Activity`'s fragment container plus `ChangeFragmentHelper` and
 * `ChangeActivityHelper` (ADR-5).
 *
 * Transitions: the original had no animation for fragment `replace()` calls, a
 * fade (`android.R.anim.fade_in/fade_out`) for splash → main and for the
 * photo-details activity. Only those are animated here; the splash fade was
 * lengthened so the "Heaven Opens" scene dissolves rather than cuts.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navigator: Navigator = koinInject()
    val snackbarController: GraceSnackbarController = koinInject()
    val connectivityObserver: ConnectivityObserver = koinInject()

    val snackbarHostState = remember { SnackbarHostState() }
    val current by navigator.current.collectAsState()

    // Main2Activity.showSnackBarMessage + BaseActivity.NetworkStateReceiver
    LaunchedEffect(Unit) {
        snackbarController.messages.collect { message ->
            snackbarHostState.showSnackbar(graceMessageText(message))
        }
    }
    LaunchedEffect(Unit) {
        connectivityObserver.observe()
            .distinctUntilChanged()
            .collect { connected ->
                if (!connected) snackbarController.show(GraceToken.NoInternet)
            }
    }

    // Main2Activity.onBackPressed: anything pushed over another screen (photo
    // details, Settings, a legal page) pops back to it; any other screen returns
    // to Get Meal; on GetMeal the press falls through and exits the app.
    BackHandler(enabled = current !is Screen.Splash && current !is Screen.GetMeal) {
        if (!navigator.pop()) navigator.backToGetMeal()
    }

    val navigationBarBackdrop = systemNavigationBarBackdrop(current)
    val navigationBarInset =
        WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GraceColors.Primary)
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val isSplash = initialState is Screen.Splash || targetState is Screen.Splash
                val isPhotoDetails =
                    initialState is Screen.PhotoDetails || targetState is Screen.PhotoDetails
                when {
                    // Splash dissolves into Get Meal; both share the primary blue,
                    // so the clouds and badge melt away as the cards arrive.
                    isSplash -> fadeIn(tween(GraceConstants.SPLASH_EXIT_FADE_DURATION)) togetherWith
                        fadeOut(tween(GraceConstants.SPLASH_EXIT_FADE_DURATION))

                    isPhotoDetails -> fadeIn(tween(300)) togetherWith fadeOut(tween(300))

                    else -> EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "graceNav",
            modifier = Modifier.fillMaxSize()
        ) { screen ->
            // The splash is full-bleed so its clouds run to the very bottom edge,
            // under the navigation bar / home indicator; every other screen keeps
            // the safe-drawing insets.
            val screenInsets =
                if (screen is Screen.Splash) Modifier else Modifier.platformScreenInsets()
            ScreenScope(screen) {
                Box(modifier = Modifier.fillMaxSize().then(screenInsets)) {
                    when (screen) {
                        Screen.Splash -> SplashScreen(navigator = navigator)

                        Screen.GetMeal -> {
                            val viewModel: GetMealViewModel = koinViewModel()
                            GetMealScreen(viewModel = viewModel, onStart = viewModel::onStart)
                        }

                        is Screen.Loading -> {
                            val viewModel: LoadingViewModel = koinViewModel {
                                parametersOf(screen.photoUri, screen.message)
                            }
                            LoadingScreen(
                                viewModel = viewModel,
                                message = screen.message,
                                onStart = viewModel::start
                            )
                        }

                        is Screen.Photo -> {
                            val shareChooserTitle = stringResource(Res.string.share_meal_text)
                            val viewModel: PhotoViewModel = koinViewModel {
                                parametersOf(screen.content, screen.photoUri, shareChooserTitle)
                            }
                            PhotoScreen(viewModel = viewModel)
                        }

                        is Screen.PhotoDetails -> PhotoDetailsScreen(
                            photoUri = screen.photoUri,
                            onClose = { navigator.pop() }
                        )

                        Screen.Settings -> {
                            val inviteMessage = stringResource(
                                Res.string.invite_friends_message,
                                GraceConstants.INVITE_FRIENDS_URL
                            )
                            val inviteChooserTitle =
                                stringResource(Res.string.invite_friends_chooser_text)
                            val viewModel: SettingsViewModel = koinViewModel {
                                parametersOf(inviteMessage, inviteChooserTitle)
                            }
                            SettingsScreen(viewModel = viewModel)
                        }

                        is Screen.Legal -> {
                            val title = stringResource(
                                when (screen.page) {
                                    LegalPage.PrivacyPolicy -> Res.string.privacy_policy_title
                                    LegalPage.TermsAndConditions ->
                                        Res.string.terms_conditions_title
                                    LegalPage.Disclaimer -> Res.string.disclaimer_title
                                }
                            )
                            val url = when (screen.page) {
                                LegalPage.PrivacyPolicy -> GraceConstants.PRIVACY_POLICY_URL
                                LegalPage.TermsAndConditions ->
                                    GraceConstants.TERMS_AND_CONDITIONS_URL
                                LegalPage.Disclaimer -> "https://blessameal.com/disclaimer.html"
                            }
                            LegalScreen(
                                title = title,
                                url = url,
                                onBack = { navigator.pop() }
                            )
                        }
                    }
                }
            }
        }

        if (navigationBarBackdrop != null && navigationBarInset > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navigationBarInset)
                    .background(navigationBarBackdrop)
            )
        }

        GraceSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(Modifier.platformScreenInsets())
        )
    }
}
