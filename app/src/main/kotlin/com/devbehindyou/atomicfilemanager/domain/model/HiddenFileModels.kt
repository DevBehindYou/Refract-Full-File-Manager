package com.devbehindyou.atomicfilemanager.domain.model

enum class HideMode {
    GALLERY,
    FAST_OBSCURE,
    PRIVATE_STORAGE,
}

enum class JournalState {
    PREPARING,
    HEADER_TRANSFORMED,
    RENAMED,
    COMMITTED,
    ROLLBACK_REQUIRED,
    RESTORED,
}

data class HiddenItem(
    val id: String,
    val originalLocation: String,
    val currentLocation: String,
    val originalName: String,
    val size: Long,
    val mode: HideMode,
    val hiddenAt: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = true,
)

/** A Private Storage item kept encrypted in the vault (ALL_IN_ONE_PLAN.md 2.5), named "<id>.vault". */
val HiddenItem.isVaultEncrypted: Boolean
    get() = mode == HideMode.PRIVATE_STORAGE && currentLocation.endsWith("/$id.vault")

/** The folder the item was hidden from. Restoring a private file puts it back here. */
fun HiddenItem.originalParent(): FileNodeId = FileNodeId.file(originalLocation.substringBeforeLast('/').ifEmpty { "/" })

data class HideJournalEntry(
    val operationId: String,
    val originalPath: String,
    val currentPath: String,
    val originalName: String,
    val state: JournalState,
    val mode: HideMode,
    val timestamp: Long = System.currentTimeMillis(),
)
