package com.devbehindyou.refract.domain.model

/** One entry in the volume picker / drawer (`architecture/STORAGE_ARCHITECTURE.md` §6). */
data class StorageVolumeInfo(
    val id: String,
    val label: String,
    val type: StorageType,
    val totalBytes: Long,
    val freeBytes: Long,
    val isRemovable: Boolean,
    val isMounted: Boolean,
    /** `null` when the volume is known to exist but hasn't been granted access yet. */
    val rootNodeId: FileNodeId?,
    val requiresGrant: Boolean,
)
