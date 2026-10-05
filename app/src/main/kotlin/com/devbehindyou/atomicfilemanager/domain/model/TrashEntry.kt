package com.devbehindyou.atomicfilemanager.domain.model

/**
 * One item in the Trash (ALL_IN_ONE_PLAN.md 1.1). The item lives at [trashedId], inside its own
 * [holderId] folder under the volume's trash folder, so items with the same name never clash and
 * the original name survives even if the database is lost.
 */
data class TrashEntry(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    /** Bytes; -1 when unknown (folders). */
    val size: Long,
    /** Where Restore puts it back. */
    val originalParent: FileNodeId,
    val trashedId: FileNodeId,
    val holderId: FileNodeId,
    /** The volume root the trash folder belongs to; used to recreate a missing original folder. */
    val volumeRoot: FileNodeId,
    val deletedAt: Long,
    /** The operation that trashed it, so Undo can restore exactly that batch. */
    val operationId: String,
)
