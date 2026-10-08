package com.grace.app.ui.components

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A user-facing message. ViewModels cannot resolve `Res.string.*` (those are
 * composable), so they emit a token and the UI layer resolves it. Raw text is
 * kept for the debug-only `onIsMealResponseFailure` path, which showed the
 * exception message verbatim in debug builds.
 */
sealed interface GraceMessage {
    data class Token(val token: GraceToken) : GraceMessage
    data class Raw(val text: String) : GraceMessage
}

enum class GraceToken {
    NoPhotoTaken,
    NoPhotoSelected,
    PermissionNotGranted,
    NoInternet,
    Interrupted,
    SomethingWentWrong,
    ShareSuccess,
    ShareError,

    /** Remove Ads purchase completed successfully. */
    RemoveAdsPurchased,

    /** Restore found an entitlement on the store account. */
    RestoreSuccess,

    /** Restore completed but the store account owns nothing. */
    RestoreNothing,

    /** Purchase failed for a real reason (cancellation never reaches this). */
    PurchaseFailed,

    /** Restore failed for a real reason. */
    RestoreFailed
}

/**
 * App-wide snackbar queue, replacing the direct `Snackbar.make(...)` calls in
 * `Main2Activity.showSnackBarMessage`. ViewModels and screen effects publish
 * here; `AppNavHost` renders whatever arrives in the `SnackbarHost`.
 */
class GraceSnackbarController {

    private val _messages = MutableSharedFlow<GraceMessage>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<GraceMessage> = _messages.asSharedFlow()

    fun show(message: GraceMessage) {
        _messages.tryEmit(message)
    }

    fun show(token: GraceToken) = show(GraceMessage.Token(token))

    fun showRaw(text: String) = show(GraceMessage.Raw(text))
}
