@file:OptIn(ExperimentalForeignApi::class)

package com.grace.app.platform

import com.grace.app.core.GraceConstants.CAMERA_TAG
import com.grace.app.core.debugLog
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSData
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * `UIImagePickerController` for the camera and `PHPickerViewController` for the
 * gallery, mirroring `Main2Activity.startExternalCamera()` /
 * `getPhotosFromGallery()`. Both copy the result into app storage before
 * returning, exactly like `GracePhotoUtil.getSelectedPhotoPath`.
 *
 * `PHPicker` runs out of process, so no photo-library permission is required;
 * only the camera prompts.
 */
class IosPhotoPicker(private val fileStore: GraceFileStore) : PhotoPicker {

    private var imagePickerDelegate: ImagePickerDelegate? = null
    private var phPickerDelegate: PhPickerDelegate? = null

    override suspend fun takePhoto(): PickResult {
        if (!UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            )
        ) {
            return PickResult.Failed("Camera is not available on this device")
        }
        if (!requestCameraPermission()) return PickResult.PermissionDenied

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val outputPath = fileStore.newCameraFile(now)

        return suspendCancellableCoroutine { continuation ->
            val controller = UIImagePickerController().apply {
                sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                allowsEditing = false
            }
            val delegate = ImagePickerDelegate(
                onImage = { image ->
                    val data = UIImageJPEGRepresentation(image, 1.0)
                    val saved = data?.writeToFile(outputPath, true) ?: false
                    continuation.resume(
                        if (saved) PickResult.Success(outputPath)
                        else PickResult.Failed("Failed to save the captured photo")
                    )
                },
                onCancel = { continuation.resume(PickResult.Cancelled) }
            )
            controller.delegate = delegate
            imagePickerDelegate = delegate

            val root = rootViewController()
            if (root == null) {
                continuation.resume(PickResult.Failed("No root view controller"))
            } else {
                root.presentViewController(controller, animated = true, completion = null)
            }
        }
    }

    override suspend fun pickPhoto(): PickResult {
        return suspendCancellableCoroutine { continuation ->
            val configuration = PHPickerConfiguration().apply {
                setSelectionLimit(1)
                setFilter(PHPickerFilter.imagesFilter)
            }
            val controller = PHPickerViewController(configuration = configuration)
            val pickerDelegate = PhPickerDelegate { result ->
                val provider = result?.itemProvider
                if (provider == null) {
                    debugLog(CAMERA_TAG) { "onActivityResult() | no photo selected!" }
                    continuation.resume(PickResult.Cancelled)
                    return@PhPickerDelegate
                }
                val extension = extensionFor(provider.registeredTypeIdentifiers)
                val outputPath = fileStore.newPickedFile(
                    Clock.System.now().toEpochMilliseconds(),
                    extension
                )
                provider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                    if (data == null) {
                        debugLog(CAMERA_TAG) { "getSelectedPhotoPath() | error = ${error?.localizedDescription}" }
                        continuation.resume(PickResult.Failed("Failed to read the selected photo"))
                    } else {
                        val saved = data.writeToFile(outputPath, true)
                        continuation.resume(
                            if (saved) PickResult.Success(outputPath)
                            else PickResult.Failed("Failed to copy the selected photo")
                        )
                    }
                }
            }
            controller.delegate = pickerDelegate
            phPickerDelegate = pickerDelegate

            val root = rootViewController()
            if (root == null) {
                continuation.resume(PickResult.Failed("No root view controller"))
            } else {
                root.presentViewController(controller, animated = true, completion = null)
            }
        }
    }

    private suspend fun requestCameraPermission(): Boolean {
        if (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
            AVAuthorizationStatusAuthorized
        ) {
            return true
        }
        return suspendCancellableCoroutine { continuation ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                continuation.resume(granted)
            }
        }
    }

    private fun extensionFor(identifiers: List<*>): String {
        val identifier = identifiers.firstOrNull() as? String ?: return "jpg"
        return when {
            identifier.contains("png", ignoreCase = true) -> "png"
            identifier.contains("gif", ignoreCase = true) -> "gif"
            identifier.contains("heic", ignoreCase = true) -> "heic"
            identifier.contains("tiff", ignoreCase = true) -> "tiff"
            else -> "jpg"
        }
    }

    private fun rootViewController(): UIViewController? {
        val application = UIApplication.sharedApplication
        val window = application.keyWindow
            ?: application.windows.firstOrNull() as? UIWindow
        return window?.rootViewController
    }

    private class ImagePickerDelegate(
        private val onImage: (UIImage) -> Unit,
        private val onCancel: () -> Unit
    ) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>
        ) {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            picker.dismissViewControllerAnimated(true, null)
            if (image != null) onImage(image) else onCancel()
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, null)
            onCancel()
        }
    }

    private class PhPickerDelegate(
        private val onResult: (PHPickerResult?) -> Unit
    ) : NSObject(), PHPickerViewControllerDelegateProtocol {

        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, null)
            onResult(didFinishPicking.firstOrNull() as? PHPickerResult)
        }
    }
}
