package com.devbehindyou.refract.domain.model

enum class StorageLocationType {
    LOCAL,
    SAF,
    MEDIASTORE,
    APP_PRIVATE,
    USB,
    SFTP,
    FTP,
    FTPS,
    SMB,
    WEBDAV,
}

/**
 * Declares the functional capabilities of a storage backend or storage location.
 * The UI and operations engine consult capabilities directly rather than making
 * assumptions based on backend type or Java file abstractions.
 */
data class StorageCapabilities(
    val canRead: Boolean = true,
    val canWrite: Boolean = false,
    val canCreate: Boolean = false,
    val canDelete: Boolean = false,
    val canRename: Boolean = false,
    val supportsMove: Boolean = false,
    val supportsCopy: Boolean = false,
    val supportsAtomicMove: Boolean = false,
    val supportsRandomAccess: Boolean = false,
    val supportsSeek: Boolean = false,
    val supportsTrash: Boolean = false,
    val supportsHideMedia: Boolean = false,
    val supportsObfuscate: Boolean = false,
    val supportsServerSideCopy: Boolean = false,
    val supportsResumableRead: Boolean = false,
    val supportsResumableWrite: Boolean = false,
) {
    companion object {
        val READ_ONLY =
            StorageCapabilities(
                canRead = true,
                canWrite = false,
                canCreate = false,
                canDelete = false,
                canRename = false,
                supportsMove = false,
                supportsCopy = false,
                supportsAtomicMove = false,
            )

        val FULL_LOCAL =
            StorageCapabilities(
                canRead = true,
                canWrite = true,
                canCreate = true,
                canDelete = true,
                canRename = true,
                supportsMove = true,
                supportsCopy = true,
                supportsAtomicMove = true,
                supportsRandomAccess = true,
                supportsSeek = true,
                supportsTrash = true,
                supportsHideMedia = true,
                supportsObfuscate = true,
                supportsServerSideCopy = true,
                supportsResumableRead = true,
                supportsResumableWrite = true,
            )

        val SAF_STORAGE =
            StorageCapabilities(
                canRead = true,
                canWrite = true,
                canCreate = true,
                canDelete = true,
                canRename = true,
                supportsMove = true,
                supportsCopy = true,
                supportsAtomicMove = false,
                supportsRandomAccess = false,
                supportsSeek = false,
                supportsTrash = false,
                supportsHideMedia = false,
                supportsObfuscate = false,
                supportsServerSideCopy = false,
            )

        val MEDIA_STORE =
            StorageCapabilities(
                canRead = true,
                canWrite = false,
                canCreate = false,
                canDelete = true,
                canRename = false,
                supportsMove = false,
                supportsCopy = false,
                supportsAtomicMove = false,
                supportsRandomAccess = false,
                supportsSeek = false,
                supportsTrash = true,
                supportsHideMedia = false,
                supportsObfuscate = false,
                supportsServerSideCopy = false,
            )

        val REMOTE_NETWORK =
            StorageCapabilities(
                canRead = true,
                canWrite = true,
                canCreate = true,
                canDelete = true,
                canRename = true,
                supportsMove = true,
                supportsCopy = true,
                supportsAtomicMove = false,
                supportsRandomAccess = false,
                supportsSeek = false,
                supportsTrash = false,
                supportsHideMedia = false,
                supportsObfuscate = false,
                supportsServerSideCopy = false,
                supportsResumableRead = true,
                supportsResumableWrite = false,
            )

        val APP_PRIVATE =
            StorageCapabilities(
                canRead = true,
                canWrite = true,
                canCreate = true,
                canDelete = true,
                canRename = true,
                supportsMove = true,
                supportsCopy = true,
                supportsAtomicMove = true,
                supportsRandomAccess = true,
                supportsSeek = true,
                supportsTrash = false,
                supportsHideMedia = true,
                supportsObfuscate = true,
                supportsServerSideCopy = true,
                supportsResumableRead = true,
                supportsResumableWrite = true,
            )
    }
}

/**
 * Represents a high-level storage location (internal storage, SD card, SAF tree,
 * app-private vault, or remote server) exposed to the UI.
 */
data class StorageLocation(
    val id: String,
    val displayName: String,
    val type: StorageLocationType,
    val rootNodeId: FileNodeId,
    val capabilities: StorageCapabilities,
    val isRemovable: Boolean = false,
    val totalSpace: Long = 0L,
    val freeSpace: Long = 0L,
)
