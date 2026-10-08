package com.grace.app.ui.photo

import com.grace.app.domain.model.MealContent
import com.grace.app.platform.PurchaseResult
import com.grace.app.platform.PurchasesRepository
import com.grace.app.platform.RemoveAdsProduct
import com.grace.app.platform.RestoreResult
import com.grace.app.platform.ShareService
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
 * `PhotoFragment`: the result screen for all three [MealContent] states, plus the
 * one-shot Remove Ads prompt that overlays the Approves result after an ad was shown.
 */
class PhotoViewModel(
    val content: MealContent,
    val photoUri: String?,
    showRemoveAdsPrompt: Boolean,
    private val navigator: Navigator,
    private val shareService: ShareService,
    private val snackbar: GraceSnackbarController,
    val purchases: PurchasesRepository,
    private val shareChooserTitle: String
) : GraceViewModel() {

    private val _showRemoveAdsPrompt =
        MutableStateFlow(showRemoveAdsPrompt && content == MealContent.Approves)
    val showRemoveAdsPrompt: StateFlow<Boolean> = _showRemoveAdsPrompt.asStateFlow()

    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()

    /**
     * Dismiss the prompt. Deliberately a plain function (not a coroutine) so it is safe to
     * call from composition and from tests without a Main dispatcher.
     */
    fun onDismissRemoveAdsPrompt() {
        _showRemoveAdsPrompt.value = false
    }

    /** Remove Ads, from either the result prompt or Settings. */
    fun onRemoveAds() {
        if (_purchaseInProgress.value) return
        _purchaseInProgress.value = true
        scope.launch {
            try {
                when (val result = purchases.purchaseRemoveAds()) {
                    PurchaseResult.Success -> {
                        _showRemoveAdsPrompt.value = false
                        snackbar.show(GraceToken.RemoveAdsPurchased)
                    }
                    // Dismissal is normal — the blessed image stays visible, no message.
                    PurchaseResult.Cancelled,
                    PurchaseResult.Unavailable -> Unit
                    is PurchaseResult.Failure -> snackbar.show(GraceToken.PurchaseFailed)
                }
            } finally {
                _purchaseInProgress.value = false
            }
        }
    }

    /** Restore Purchases, from either the result prompt or Settings. */
    fun onRestorePurchases() {
        if (_purchaseInProgress.value) return
        _purchaseInProgress.value = true
        scope.launch {
            try {
                when (val result = purchases.restorePurchases()) {
                    RestoreResult.Success -> {
                        _showRemoveAdsPrompt.value = false
                        snackbar.show(GraceToken.RestoreSuccess)
                    }
                    RestoreResult.NothingToRestore -> snackbar.show(GraceToken.RestoreNothing)
                    RestoreResult.Unavailable -> Unit
                    is RestoreResult.Failure -> snackbar.show(GraceToken.RestoreFailed)
                }
            } finally {
                _purchaseInProgress.value = false
            }
        }
    }

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

/** Exposed so the prompt sheet can render the store-localised price. */
val PhotoViewModel.removeAdsProduct: StateFlow<RemoveAdsProduct?>
    get() = purchases.product
