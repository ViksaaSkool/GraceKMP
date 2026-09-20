package com.grace.app.platform

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.grace.app.core.GraceConstants.CAMERA_TAG
import com.grace.app.core.debugLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume

/**
 * `Main2Activity.startExternalCamera()` / `getPhotosFromGallery()` plus the
 * runtime-permission dance and `GracePhotoUtil.getSelectedPhotoPath`.
 *
 * Registered in `MainActivity.onCreate` because `registerForActivityResult`
 * must be called before the activity reaches STARTED.
 */
class AndroidPhotoPicker(
    private val activity: ComponentActivity,
    private val fileStore: GraceFileStore
) : PhotoPicker {

    private var captureContinuation: ((PickResult) -> Unit)? = null
    private var pickContinuation: ((PickResult) -> Unit)? = null
    private var permissionContinuation: ((Boolean) -> Unit)? = null
    private var pendingCameraPath: String? = null

    private val captureLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val continuation = captureContinuation
        captureContinuation = null
        val path = pendingCameraPath
        pendingCameraPath = null
        if (result.resultCode == Activity.RESULT_OK && path != null) {
            continuation?.invoke(PickResult.Success(path))
        } else {
            debugLog(CAMERA_TAG) { "onActivityResult() | no photo taken!" }
            continuation?.invoke(PickResult.Cancelled)
        }
    }

    private val pickLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val continuation = pickContinuation
        pickContinuation = null
        val uri: Uri? = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val copied = copyIntoAppStorage(uri)
            continuation?.invoke(
                if (copied != null) PickResult.Success(copied)
                else PickResult.Failed("Failed to copy the selected photo")
            )
        } else {
            debugLog(CAMERA_TAG) { "onActivityResult() | no photo selected!" }
            continuation?.invoke(PickResult.Cancelled)
        }
    }

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val continuation = permissionContinuation
        permissionContinuation = null
        continuation?.invoke(granted)
    }

    override suspend fun takePhoto(): PickResult {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            if (!requestPermission(Manifest.permission.CAMERA)) return PickResult.PermissionDenied
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val path = fileStore.newCameraFile(now)
        pendingCameraPath = path

        // FileProvider throws IllegalArgumentException when the provider is
        // misconfigured or the path is not covered by file_paths.xml. Surface it
        // as a failure instead of letting it crash the main thread.
        val photoUri = try {
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                File(path)
            )
        } catch (e: IllegalArgumentException) {
            pendingCameraPath = null
            debugLog(CAMERA_TAG) { "takePhoto() | FileProvider error = ${e.message}" }
            return PickResult.Failed(e.message ?: "Could not create the camera output file")
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (intent.resolveActivity(activity.packageManager) == null) {
            pendingCameraPath = null
            return PickResult.Failed("No camera application available")
        }
        return suspendCancellableCoroutine { continuation ->
            captureContinuation = { result -> continuation.resume(result) }
            captureLauncher.launch(intent)
        }
    }

    override suspend fun pickPhoto(): PickResult {
        val permission = readImagesPermission()
        if (!hasPermission(permission)) {
            if (!requestPermission(permission)) return PickResult.PermissionDenied
        }

        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        return suspendCancellableCoroutine { continuation ->
            pickContinuation = { result -> continuation.resume(result) }
            pickLauncher.launch(intent)
        }
    }

    private fun readImagesPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private suspend fun requestPermission(permission: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            permissionContinuation = { granted -> continuation.resume(granted) }
            permissionLauncher.launch(permission)
        }

    /** `GracePhotoUtil.getSelectedPhotoPath`: copy into app storage. */
    private fun copyIntoAppStorage(selectedPhoto: Uri): String? {
        var extension = "jpg"
        activity.contentResolver.getType(selectedPhoto)?.let { mimeType ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                ?.takeIf { it.isNotEmpty() }
                ?.let { extension = it }
        }

        val outputPath = fileStore.newPickedFile(System.currentTimeMillis(), extension)
        return try {
            activity.contentResolver.openInputStream(selectedPhoto).use { input ->
                if (input == null) return null
                FileOutputStream(outputPath).use { output ->
                    input.copyTo(output, bufferSize = 8 * 1024)
                    output.flush()
                }
            }
            outputPath
        } catch (e: IOException) {
            debugLog(CAMERA_TAG) { "getSelectedPhotoPath() | exception = ${e.message}" }
            null
        }
    }
}
