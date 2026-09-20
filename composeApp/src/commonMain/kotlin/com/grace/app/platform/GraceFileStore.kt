package com.grace.app.platform

import kotlinx.datetime.LocalDateTime

/**
 * App-private file storage, replacing `GracePhotoUtil.getOutputMediaFile` and
 * the gallery-copy path.
 *
 * Android: `getExternalFilesDir(DIRECTORY_PICTURES)/GraceApp` with a `cacheDir`
 * fallback. iOS: `Documents/GraceApp`.
 */
interface GraceFileStore {
    fun newCameraFile(now: LocalDateTime): String
    fun newBlessedFile(now: LocalDateTime): String
    fun newPickedFile(millis: Long, extension: String): String
}
