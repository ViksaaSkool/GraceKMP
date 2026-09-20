package com.grace.app.platform

import android.content.Context
import android.os.Environment
import com.grace.app.core.GraceConstants.CAMERA_TAG
import com.grace.app.core.GraceConstants
import com.grace.app.core.debugLog
import com.grace.app.domain.photo.GraceFileNames
import kotlinx.datetime.LocalDateTime
import java.io.File

/**
 * `GracePhotoUtil.getOutputMediaFile` + the gallery-copy destination.
 *
 * Camera and blessed files live in `<externalFilesDir>/Pictures/GraceApp`
 * (falling back to `cacheDir`), while picked gallery images are written
 * directly into the base directory — matching the original, which only added
 * the `GraceApp` sub-folder in `getOutputMediaFile`.
 */
class AndroidGraceFileStore(private val context: Context) : GraceFileStore {

    private fun baseDir(): File {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return external ?: context.cacheDir
    }

    private fun appDir(): File {
        val dir = File(baseDir(), GraceConstants.APP_FOLDER)
        if (!dir.exists() && !dir.mkdirs()) {
            debugLog(CAMERA_TAG) { "getOutputMediaFile() | failed to create directory" }
        }
        return dir
    }

    override fun newCameraFile(now: LocalDateTime): String =
        File(appDir(), GraceFileNames.cameraFileName(now)).absolutePath

    override fun newBlessedFile(now: LocalDateTime): String =
        File(appDir(), GraceFileNames.blessedFileName(now)).absolutePath

    override fun newPickedFile(millis: Long, extension: String): String =
        File(baseDir(), GraceFileNames.pickedFileName(millis, extension)).absolutePath
}
