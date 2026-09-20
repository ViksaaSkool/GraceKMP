package com.grace.app.domain.photo

/**
 * The 8 EXIF orientation values, named after the `ExifInterface` constants the
 * original code switched on (`util/GracePhotoUtil.java:170-203`).
 */
enum class ExifOrientation(val exifValue: Int) {
    Undefined(0),
    Normal(1),
    FlipHorizontal(2),
    Rotate180(3),
    FlipVertical(4),
    Transpose(5),
    Rotate90(6),
    Transverse(7),
    Rotate270(8);

    companion object {
        fun fromExifValue(value: Int): ExifOrientation =
            entries.firstOrNull { it.exifValue == value } ?: Undefined
    }
}

/**
 * The rotation/mirroring the original applied for each EXIF orientation.
 *
 * Note the deliberate no-ops for [ExifOrientation.Transpose] and
 * [ExifOrientation.Transverse]: the original left those `//TODO` and returned
 * the decoded bitmap untouched. Parity means keeping that behaviour.
 */
data class OrientationTransform(
    val rotationDegrees: Int,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean
) {
    companion object {
        val None = OrientationTransform(0, false, false)

        fun forOrientation(orientation: ExifOrientation): OrientationTransform =
            when (orientation) {
                ExifOrientation.Undefined -> None
                ExifOrientation.Normal -> None
                ExifOrientation.FlipHorizontal -> OrientationTransform(0, flipHorizontal = true, flipVertical = false)
                ExifOrientation.Rotate180 -> OrientationTransform(180, false, false)
                ExifOrientation.FlipVertical -> OrientationTransform(0, false, flipVertical = true)
                ExifOrientation.Transpose -> None
                ExifOrientation.Rotate90 -> OrientationTransform(90, false, false)
                ExifOrientation.Transverse -> None
                ExifOrientation.Rotate270 -> OrientationTransform(270, false, false)
            }
    }
}

object ImageTransforms {
    fun transformFor(orientation: ExifOrientation): OrientationTransform =
        OrientationTransform.forOrientation(orientation)
}
