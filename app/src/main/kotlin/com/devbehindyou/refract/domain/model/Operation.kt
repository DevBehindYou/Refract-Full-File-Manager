package com.devbehindyou.refract.domain.model

import java.util.UUID

@JvmInline
value class OperationId(val raw: String) {
    companion object {
        fun random(): OperationId = OperationId(UUID.randomUUID().toString())
    }
}

enum class OperationType {
    COPY,
    MOVE,
    DELETE,
    RENAME,
    EXTRACT,
    COMPRESS,
    HIDE_GALLERY,
    UNHIDE_GALLERY,
    FAST_OBSCURE,
    RESTORE_OBSCURE,
    MOVE_TO_PRIVATE,
}

enum class CollisionPolicy { ASK, OVERWRITE, KEEP_BOTH, SKIP, RENAME_AUTO }

data class OperationOptions(
    val collisionPolicy: CollisionPolicy = CollisionPolicy.ASK,
    val deleteSourceAfterCopy: Boolean = false,
    val preserveTimestamps: Boolean = true,
    val useTrash: Boolean = true,
    /** `null` unless [OperationType.COMPRESS]; 0–9 when present. */
    val compressionLevel: Int? = null,
) {
    init {
        require(compressionLevel == null || compressionLevel in 0..9) {
            "compressionLevel must be null or in 0..9, was $compressionLevel"
        }
    }
}

/** The immutable definition of a requested operation — what was asked for. */
data class FileOperation(
    val id: OperationId,
    val type: OperationType,
    val sources: List<FileNodeId>,
    /** `null` for [OperationType.DELETE]. */
    val destination: FileNodeId?,
    val options: OperationOptions,
    val createdAt: Long,
) {
    init {
        require(sources.isNotEmpty()) { "FileOperation must have at least one source" }
    }
}

typealias FileOperationRequest = FileOperation

data class OperationProgress(
    val itemsDone: Int,
    val itemsTotal: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
    val currentName: String?,
    val bytesPerSecond: Long,
    val etaMillis: Long?,
)

@JvmInline
value class UndoToken(val raw: String)

data class FailedItem(val id: FileNodeId, val name: String, val error: FileError)

data class OperationSummary(
    val succeeded: List<FileNodeId>,
    val skipped: List<FileNodeId>,
    val failed: List<FailedItem>,
    val undoToken: UndoToken?,
)

/** Shown as a dialog with both files' name/size/date side by side (FILE_OPERATIONS.md §5). */
data class Conflict(val source: FileNode, val existingDestination: FileNode)

/** The mutable state of a [FileOperation] as it runs. */
sealed interface OperationStatus {
    data object Queued : OperationStatus

    data object Preparing : OperationStatus

    data class Running(val progress: OperationProgress) : OperationStatus

    data class Paused(val progress: OperationProgress) : OperationStatus

    data class AwaitingInput(val conflict: Conflict) : OperationStatus

    data class Completed(val summary: OperationSummary) : OperationStatus

    data class PartiallyCompleted(val summary: OperationSummary) : OperationStatus

    data class Failed(val error: FileError, val summary: OperationSummary) : OperationStatus

    data object Cancelled : OperationStatus

    data object Recovering : OperationStatus
}

/**
 * The roadmap names this "OperationSnapshot" as a Phase 2 deliverable. The one concrete usage
 * found in the docs (`screens/OPERATIONS.md`: "all three render the same `OperationSnapshot`
 * stream") matches this shape — the observable, streamable state of one operation, bundling
 * its immutable definition with its current status. See PHASE_2_NOTES.md ambiguity #1 for the
 * reconciliation this assumes between the roadmap's naming and `FILE_OPERATIONS.md`'s.
 */
data class OperationSnapshot(val operation: FileOperation, val status: OperationStatus)
