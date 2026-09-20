package com.grace.app.ui.components

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.grace.app.resources.Res
import com.grace.app.resources.interrupted_text
import com.grace.app.resources.no_internets
import com.grace.app.resources.no_photo_was_selected
import com.grace.app.resources.no_photo_was_taken
import com.grace.app.resources.permission_not_granted_text
import com.grace.app.resources.share_error_text
import com.grace.app.resources.share_success_text
import com.grace.app.resources.something_went_wrong_text
import org.jetbrains.compose.resources.getString

/**
 * Resolves a [GraceMessage] to display text. This is a plain `suspend` function
 * (not `@Composable`) so it can be called from the snackbar-collection
 * coroutine, keeping ViewModels free of `Res` access.
 */
suspend fun graceMessageText(message: GraceMessage): String = when (message) {
    is GraceMessage.Raw -> message.text
    is GraceMessage.Token -> getString(
        when (message.token) {
            GraceToken.NoPhotoTaken -> Res.string.no_photo_was_taken
            GraceToken.NoPhotoSelected -> Res.string.no_photo_was_selected
            GraceToken.PermissionNotGranted -> Res.string.permission_not_granted_text
            GraceToken.NoInternet -> Res.string.no_internets
            GraceToken.Interrupted -> Res.string.interrupted_text
            GraceToken.SomethingWentWrong -> Res.string.something_went_wrong_text
            GraceToken.ShareSuccess -> Res.string.share_success_text
            GraceToken.ShareError -> Res.string.share_error_text
        }
    )
}

/**
 * Replaces `Main2Activity.showSnackBarMessage` (default Snackbar, LENGTH_SHORT).
 */
@Composable
fun GraceSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(hostState = hostState, modifier = modifier)
}
