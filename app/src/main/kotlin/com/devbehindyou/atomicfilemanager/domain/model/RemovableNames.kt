package com.devbehindyou.atomicfilemanager.domain.model

/**
 * SD cards and USB drives are almost always FAT32 or exFAT, which refuse some names that internal
 * storage accepts (ALL_IN_ONE_PLAN.md 3.5). Checking up front gives a clear message instead of a
 * failed rename or copy halfway through.
 */
object RemovableNames {
    /** Characters FAT and exFAT reject in any name. */
    const val FORBIDDEN = "\"*:<>?\\|"

    /** FAT/exFAT long names are limited to 255 UTF-16 units. */
    private const val MAX_UNITS = 255

    /**
     * True for folders on removable storage: USB ids, and local paths under `/storage/<volume>`
     * other than the emulated internal storage, or under `/mnt/media_rw`.
     */
    fun appliesTo(id: FileNodeId): Boolean {
        if (id.prefix == FileNodeId.Prefix.USB) return true
        if (id.prefix != FileNodeId.Prefix.FILE) return false
        val path = id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme)
        if (path.startsWith("/mnt/media_rw/")) return true
        val volume = path.removePrefix("/storage/").takeIf { it != path }?.substringBefore('/') ?: return false
        return volume.isNotEmpty() && volume != "emulated" && volume != "self"
    }

    /** Why [name] can't be used on a removable drive, or null when it can. */
    fun problem(name: String): String? {
        val bad = name.firstOrNull { it in FORBIDDEN || it.code < SPACE }
        return when {
            bad != null && bad.code < SPACE -> "USB drives and SD cards can't store control characters in a name."
            bad != null -> "USB drives and SD cards can't store \"$bad\" in a name."
            name.endsWith('.') || name.endsWith(' ') ->
                "USB drives and SD cards drop a dot or space at the end of a name."
            name.length > MAX_UNITS -> "That name is too long for a USB drive or SD card."
            else -> null
        }
    }

    private const val SPACE = 0x20
}
