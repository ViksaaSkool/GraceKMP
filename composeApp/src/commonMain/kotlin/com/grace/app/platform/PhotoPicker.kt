package com.grace.app.platform

/**
 * Replaces `Main2Activity.startExternalCamera()/getPhotosFromGallery()` plus
 * `GracePhotoUtil.getOutputMediaFile/getSelectedPhotoPath`.
 *
 * Both operations are suspend: they resolve once the user has finished with the
 * platform picker (or immediately with a failure reason).
 */
interface PhotoPicker {
    suspend fun takePhoto(): PickResult
    suspend fun pickPhoto(): PickResult
}

sealed interface PickResult {
    /** Absolute path to an app-owned copy of the image. */
    data class Success(val path: String) : PickResult

    /** The user backed out of the camera / gallery. */
    data object Cancelled : PickResult

    /** A required runtime permission was denied. */
    data object PermissionDenied : PickResult

    /** The picker could not be launched or the copy failed. */
    data class Failed(val reason: String) : PickResult
}
