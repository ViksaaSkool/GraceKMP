@file:OptIn(ExperimentalForeignApi::class)

package com.grace.app.platform

import com.grace.app.core.GraceConstants
import com.grace.app.domain.photo.GraceFileNames
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS file storage. The original had no iOS implementation; PLAN.md §Phase 3
 * specifies `Documents/GraceApp`. Camera and blessed files live in that
 * sub-folder, picked gallery images go directly into `Documents` — mirroring
 * the Android split in `GracePhotoUtil`.
 */
class IosGraceFileStore : GraceFileStore {

    private fun documentsDir(): String =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String) ?: NSURL.URLWithString("")?.path.orEmpty()

    private fun appDir(): String {
        val dir = "${documentsDir()}/${GraceConstants.APP_FOLDER}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return dir
    }

    override fun newCameraFile(now: LocalDateTime): String =
        "${appDir()}/${GraceFileNames.cameraFileName(now)}"

    override fun newBlessedFile(now: LocalDateTime): String =
        "${appDir()}/${GraceFileNames.blessedFileName(now)}"

    override fun newPickedFile(millis: Long, extension: String): String =
        "${documentsDir()}/${GraceFileNames.pickedFileName(millis, extension)}"
}
