package com.grace.app.domain.photo

import com.grace.app.core.GraceConstants
import kotlinx.datetime.LocalDateTime

/**
 * File naming, ported from `util/GracePhotoUtil.java:39-67` and `:101`.
 *
 * The original used `SimpleDateFormat("yyyyMMdd_HHmmss")` with the default
 * locale. We format explicitly so the output is locale-independent (PLAN.md §4).
 */
object GraceFileNames {

    fun timestamp(dateTime: LocalDateTime): String {
        val date = dateTime.date
        val time = dateTime.time
        return buildString {
            append(date.year.toString().padStart(4, '0'))
            append(date.monthNumber.toString().padStart(2, '0'))
            append(date.dayOfMonth.toString().padStart(2, '0'))
            append('_')
            append(time.hour.toString().padStart(2, '0'))
            append(time.minute.toString().padStart(2, '0'))
            append(time.second.toString().padStart(2, '0'))
        }
    }

    fun cameraFileName(dateTime: LocalDateTime): String =
        GraceConstants.GRACE_PHOTO + timestamp(dateTime) + GraceConstants.GRACE_PHOTO_JPG

    fun blessedFileName(dateTime: LocalDateTime): String =
        GraceConstants.GRACE_BLESSED_PHOTO + timestamp(dateTime) + GraceConstants.GRACE_PHOTO_JPG

    fun pickedFileName(millis: Long, extension: String): String {
        val ext = extension.ifEmpty { "jpg" }
        return "${GraceConstants.PICKED_PHOTO_PREFIX}$millis.$ext"
    }
}
