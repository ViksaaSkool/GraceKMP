package com.grace.app.ui.getmeal

import com.grace.app.data.SettingsStore
import com.grace.app.platform.PickResult
import com.grace.app.platform.PhotoPicker
import com.grace.app.platform.SystemUi
import com.grace.app.ui.GraceViewModel
import com.grace.app.ui.components.GraceSnackbarController
import com.grace.app.ui.components.GraceToken
import com.grace.app.ui.navigation.LoadingMessage
import com.grace.app.ui.navigation.Navigator
import com.grace.app.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * `GetMealFromFragment` + `Main2Activity` camera/gallery handling.
 */
class GetMealViewModel(
    private val photoPicker: PhotoPicker,
    private val settings: SettingsStore,
    private val navigator: Navigator,
    private val snackbar: GraceSnackbarController,
    private val systemUi: SystemUi
) : GraceViewModel() {

    private val _showTnc = MutableStateFlow(false)
    val showTnc: StateFlow<Boolean> = _showTnc.asStateFlow()

    /** `GetMealFromFragment.disclaimerIsShown`, reset whenever the screen is recreated. */
    private var tncShown = false

    /** `GetMealFromFragment.onStart()`: show the TnC dialog on first entry. */
    fun onStart() {
        if (!settings.disclaimerTncAccepted && !tncShown) {
            tncShown = true
            _showTnc.value = true
        }
    }

    fun onCaptureMeal() {
        scope.launch {
            when (val result = photoPicker.takePhoto()) {
                is PickResult.Success ->
                    navigator.replace(Screen.Loading(result.path, LoadingMessage.LetMeSee))

                PickResult.PermissionDenied -> snackbar.show(GraceToken.PermissionNotGranted)
                PickResult.Cancelled, is PickResult.Failed -> snackbar.show(GraceToken.NoPhotoTaken)
            }
        }
    }

    fun onMealFromGallery() {
        scope.launch {
            when (val result = photoPicker.pickPhoto()) {
                is PickResult.Success ->
                    navigator.replace(Screen.Loading(result.path, LoadingMessage.LetMeSee))

                PickResult.PermissionDenied -> snackbar.show(GraceToken.PermissionNotGranted)
                PickResult.Cancelled, is PickResult.Failed -> snackbar.show(GraceToken.NoPhotoSelected)
            }
        }
    }

    /** Top-right settings action: Settings stacks on top of Get Meal. */
    fun onSettingsClick() {
        navigator.push(Screen.Settings)
    }

    /** TnC `OK` — persist `disclaimer_tnc_key = true` and dismiss. */
    fun onTncAccepted() {
        settings.disclaimerTncAccepted = true
        _showTnc.value = false
    }

    /** TnC `Exit` — `getActivity().finish()`. */
    fun onTncExit() {
        systemUi.exitApp()
    }
}
