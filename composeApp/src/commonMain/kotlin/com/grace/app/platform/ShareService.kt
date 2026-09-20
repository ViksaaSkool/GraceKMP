package com.grace.app.platform

/**
 * Replaces `util/ShareUtil.shareBlessedPhoto`: an `ACTION_SEND` chooser on
 * Android and a `UIActivityViewController` on iOS, sharing the blessed JPEG
 * with the chooser title `share_meal_text`.
 */
interface ShareService {
    fun sharePhoto(path: String, chooserTitle: String)

    /**
     * Shares a plain-text message (used by Settings → Invite Friends). On
     * Android this is an `ACTION_SEND` with `text/plain`; on iOS the text is the
     * single item of a `UIActivityViewController`, where `chooserTitle` has no
     * equivalent.
     */
    fun shareText(text: String, chooserTitle: String)
}
