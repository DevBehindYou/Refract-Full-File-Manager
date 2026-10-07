package com.devbehindyou.atomicfilemanager.data.preview

/** What a photo says about itself (ALL_IN_ONE_PLAN.md 4.2). Every field is null when absent. */
data class ImageMetadata(
    val make: String? = null,
    val model: String? = null,
    val lens: String? = null,
    val takenAt: String? = null,
    val iso: Int? = null,
    val exposureSeconds: Double? = null,
    val fNumber: Double? = null,
    val focalLengthMm: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val software: String? = null,
    val owner: String? = null,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val isEmpty: Boolean get() = this == ImageMetadata()
}

/** How much a clean copy removes. */
enum class MetadataStrip {
    /** Only GPS tags; camera and date stay. */
    LOCATION,

    /** GPS plus everything that identifies the camera, owner, software or time. Orientation stays. */
    ALL,
}

/** EXIF tag names removed by each [MetadataStrip]; pure so the lists are unit-tested. */
object ExifTags {
    val LOCATION: List<String> =
        listOf(
            "GPSVersionID", "GPSLatitudeRef", "GPSLatitude", "GPSLongitudeRef", "GPSLongitude",
            "GPSAltitudeRef", "GPSAltitude", "GPSTimeStamp", "GPSSatellites", "GPSStatus",
            "GPSMeasureMode", "GPSDOP", "GPSSpeedRef", "GPSSpeed", "GPSTrackRef", "GPSTrack",
            "GPSImgDirectionRef", "GPSImgDirection", "GPSMapDatum", "GPSDestLatitudeRef",
            "GPSDestLatitude", "GPSDestLongitudeRef", "GPSDestLongitude", "GPSDestBearingRef",
            "GPSDestBearing", "GPSDestDistanceRef", "GPSDestDistance", "GPSProcessingMethod",
            "GPSAreaInformation", "GPSDateStamp", "GPSDifferential", "GPSHPositioningError",
        )

    val IDENTIFYING: List<String> =
        listOf(
            "Make", "Model", "Software", "Artist", "Copyright", "ImageDescription", "UserComment",
            "MakerNote", "DateTime", "DateTimeOriginal", "DateTimeDigitized", "SubSecTime",
            "SubSecTimeOriginal", "SubSecTimeDigitized", "OffsetTime", "OffsetTimeOriginal",
            "OffsetTimeDigitized", "CameraOwnerName", "BodySerialNumber", "LensMake", "LensModel",
            "LensSerialNumber", "LensSpecification", "ImageUniqueID", "Xmp",
        )

    fun removedBy(strip: MetadataStrip): List<String> =
        when (strip) {
            MetadataStrip.LOCATION -> LOCATION
            MetadataStrip.ALL -> LOCATION + IDENTIFYING
        }

    /** True when [after] no longer carries what [strip] promised to remove. */
    fun isClean(
        after: ImageMetadata,
        strip: MetadataStrip,
    ): Boolean =
        !after.hasLocation &&
            (
                strip == MetadataStrip.LOCATION ||
                    listOf(after.make, after.model, after.lens, after.takenAt, after.software, after.owner)
                        .all { it == null }
            )

    /** Formats ExifInterface can rewrite. */
    private val WRITABLE = setOf("image/jpeg", "image/png", "image/webp")

    fun canStrip(mime: String?): Boolean = mime?.lowercase() in WRITABLE

    /** "IMG_1.jpg" becomes "IMG_1 (clean).jpg". */
    fun cleanName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "$name (clean)" else "${name.substring(0, dot)} (clean)${name.substring(dot)}"
    }
}
