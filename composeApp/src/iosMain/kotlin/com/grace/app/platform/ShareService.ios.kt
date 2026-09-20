@file:OptIn(ExperimentalForeignApi::class)

package com.grace.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIPopoverArrowDirectionUnknown
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController

/**
 * `util/ShareUtil.shareBlessedPhoto` on iOS: `UIActivityViewController`.
 * The chooser title has no direct iOS equivalent (the sheet shows the item
 * itself), so `chooserTitle` is accepted for API parity but unused.
 */
class IosShareService : ShareService {

    override fun sharePhoto(path: String, chooserTitle: String) {
        val image = UIImage.imageWithContentsOfFile(path) ?: return
        present(
            UIActivityViewController(
                activityItems = listOf(image),
                applicationActivities = null
            )
        )
    }

    /** Settings → Invite Friends: the invite copy is the sheet's only item. */
    override fun shareText(text: String, chooserTitle: String) {
        present(
            UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null
            )
        )
    }

    private fun present(activityController: UIActivityViewController) {
        val root = rootViewController() ?: return

        // On iPad the controller is presented as a popover and UIKit raises if it
        // has no source view/anchor, so always give it one.
        activityController.popoverPresentationController?.let { popover ->
            popover.sourceView = root.view
            popover.sourceRect = root.view.bounds
            popover.permittedArrowDirections = UIPopoverArrowDirectionUnknown
        }

        root.presentViewController(activityController, animated = true, completion = null)
    }

    private fun rootViewController(): UIViewController? {
        val application = UIApplication.sharedApplication
        val window = application.keyWindow
            ?: application.windows.firstOrNull() as? UIWindow
        return window?.rootViewController
    }
}
