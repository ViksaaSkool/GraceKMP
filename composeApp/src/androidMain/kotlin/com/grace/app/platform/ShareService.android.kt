package com.grace.app.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import com.grace.app.core.GraceConstants.APP_TAG
import com.grace.app.core.debugLog
import java.io.File

/**
 * `util/ShareUtil.shareBlessedPhoto`: `ACTION_SEND` with an `image/jpg` type,
 * a FileProvider URI and the `share_meal_text` chooser title.
 *
 * Both `FileProvider.getUriForFile` (provider misconfiguration) and
 * `startActivity` (no app able to receive the share) can throw; neither should
 * take the app down, so they are logged and swallowed.
 */
class AndroidShareService(private val activity: Activity) : ShareService {

    override fun sharePhoto(path: String, chooserTitle: String) {
        val photoUri = try {
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                File(path)
            )
        } catch (e: IllegalArgumentException) {
            debugLog(APP_TAG) { "sharePhoto() | FileProvider error = ${e.message}" }
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpg"
            putExtra(Intent.EXTRA_STREAM, photoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            activity.startActivity(Intent.createChooser(shareIntent, chooserTitle))
        } catch (e: ActivityNotFoundException) {
            debugLog(APP_TAG) { "sharePhoto() | no activity to handle the share = ${e.message}" }
        }
    }

    /** Settings → Invite Friends: `ACTION_SEND` with `text/plain`. */
    override fun shareText(text: String, chooserTitle: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        try {
            activity.startActivity(Intent.createChooser(shareIntent, chooserTitle))
        } catch (e: ActivityNotFoundException) {
            debugLog(APP_TAG) { "shareText() | no activity to handle the share = ${e.message}" }
        }
    }
}
