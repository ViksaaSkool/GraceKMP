package com.grace.app.ui.loading

import com.grace.app.core.isDebugBuild
import com.grace.app.domain.food.ClassificationOutcome
import com.grace.app.domain.food.IsPhotoOfMealUseCase
import com.grace.app.domain.model.MealContent
import com.grace.app.domain.photo.BlessPhotoUseCase
import com.grace.app.ui.GraceViewModel
import com.grace.app.ui.components.GraceSnackbarController
import com.grace.app.ui.components.GraceToken
import com.grace.app.ui.navigation.LoadingMessage
import com.grace.app.ui.navigation.Navigator
import com.grace.app.ui.navigation.Screen
import kotlinx.coroutines.launch

/**
 * `LoadingFragment` + `LoadingPresenterImpl`.
 *
 * The screen runs exactly one action when it appears:
 *  - [LoadingMessage.LetMeSee] → classify the photo, then show Asks/Angered
 *  - [LoadingMessage.BlessingPhoto] → bless the photo, then show Approves
 *
 * `working` mirrors `LoadingFragment.working`, which stopped a second
 * `onPresenterReady()` (e.g. after a configuration change) from re-running the
 * task.
 */
class LoadingViewModel(
    private val photoUri: String,
    private val message: LoadingMessage,
    private val isPhotoOfMeal: IsPhotoOfMealUseCase,
    private val blessPhoto: BlessPhotoUseCase,
    private val navigator: Navigator,
    private val snackbar: GraceSnackbarController
) : GraceViewModel() {

    private var working = false

    /** Set once the task has produced its result, so [onCleared] can tell a
     *  cancellation apart from a normal hand-off to the next screen. */
    private var completed = false

    fun start() {
        if (working) return
        working = true
        scope.launch {
            when (message) {
                LoadingMessage.LetMeSee -> classify()
                LoadingMessage.BlessingPhoto -> bless()
            }
            completed = true
        }
    }

    /**
     * `LoadingPresenterImpl.onViewDetached`: if a task was still running when
     * the screen went away, the original posted to EventBus and Main2Activity
     * showed `interrupted_text`.
     */
    override fun onCleared() {
        if (working && !completed) {
            snackbar.show(GraceToken.Interrupted)
        }
        super.onCleared()
    }

    private suspend fun classify() {
        when (val outcome = isPhotoOfMeal(photoUri)) {
            is ClassificationOutcome.Success -> {
                val content =
                    if (outcome.result.isMeal) MealContent.Asks else MealContent.Angered
                navigator.replace(
                    Screen.Photo(
                        content = content,
                        photoUri = if (outcome.result.isMeal) photoUri else null
                    )
                )
            }

            is ClassificationOutcome.Failure -> {
                // onIsMealResponseFailure: raw error in debug, generic in release.
                if (isDebugBuild) snackbar.showRaw(outcome.error)
                else snackbar.show(GraceToken.SomethingWentWrong)
                navigator.replace(Screen.GetMeal)
            }
        }
    }

    private suspend fun bless() {
        val blessedUri = blessPhoto(photoUri)
        if (blessedUri.isNotEmpty()) {
            navigator.replace(Screen.Photo(MealContent.Approves, blessedUri))
        } else {
            snackbar.show(GraceToken.SomethingWentWrong)
            navigator.replace(Screen.GetMeal)
        }
    }
}
