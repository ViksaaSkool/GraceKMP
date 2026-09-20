package com.grace.app.ui.photo

import com.grace.app.domain.model.MealContent
import com.grace.app.platform.ShareService
import com.grace.app.ui.GraceViewModel
import com.grace.app.ui.components.GraceSnackbarController
import com.grace.app.ui.components.GraceToken
import com.grace.app.ui.navigation.LoadingMessage
import com.grace.app.ui.navigation.Navigator
import com.grace.app.ui.navigation.Screen

/**
 * `PhotoFragment`: the result screen for all three [MealContent] states.
 */
class PhotoViewModel(
    val content: MealContent,
    val photoUri: String?,
    private val navigator: Navigator,
    private val shareService: ShareService,
    private val snackbar: GraceSnackbarController,
    private val shareChooserTitle: String
) : GraceViewModel() {

    /** PhotoFragment.handleLeftButtonClick */
    fun onLeftButton() {
        when {
            content.leftIsDone || content.leftIsNo -> navigator.replace(Screen.GetMeal)
            content.leftIsFeelWraith -> Unit // handled as local UI state in the screen
        }
    }

    /** PhotoFragment.handleRightButtonClick */
    fun onRightButton() {
        when {
            content.rightIsYes ->
                navigator.replace(Screen.Loading(photoUri.orEmpty(), LoadingMessage.BlessingPhoto))

            content.rightIsShare -> photoUri?.let {
                try {
                    shareService.sharePhoto(it, shareChooserTitle)
                    snackbar.show(GraceToken.ShareSuccess)
                } catch (_: Exception) {
                    snackbar.show(GraceToken.ShareError)
                }
            }
            content.rightIsAnotherTry -> navigator.replace(Screen.GetMeal)
        }
    }

    /** PhotoFragment.handlePhotoClick: opens the viewer unless the state is Angered. */
    fun onPhotoClick() {
        if (content.photoIsZoomable && !photoUri.isNullOrEmpty()) {
            navigator.push(Screen.PhotoDetails(photoUri))
        }
    }
}
