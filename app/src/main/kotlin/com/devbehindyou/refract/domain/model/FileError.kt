package com.devbehindyou.refract.domain.model

/**
 * One sealed hierarchy. Every failure in the app is one of these (`ERROR_MODEL.md`). The
 * UI never sees a `Throwable`, an errno, or a platform message.
 *
 * `severity`/`recoverable`/`action` per variant are this file's own addition: `ERROR_MODEL.md`
 * defines these three properties on the interface and gives a message-mapping *table*
 * (title/body/action per error), but doesn't assign `severity`/`recoverable` per variant
 * explicitly. The mapping below is inferred from that table's implied UI behaviour — flagged
 * here rather than silently presented as spec. A few variants route through UI that doesn't
 * exist yet (the conflict dialog for [FileAlreadyExists], an archive-specific dialog for
 * [SuspiciousArchive]) and so intentionally have `action = null` here; the generic
 * error-state renderer isn't how those get resolved.
 */
sealed interface FileError {
    val severity: Severity
    val recoverable: Boolean
    val action: RecoveryAction?

    // Access
    data class PermissionDenied(val what: AccessTarget) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.RequestAccess(what)
    }

    data class AccessDenied(val name: String?) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.OpenTroubleshooting
    }

    /** `/Android/data` on API 30+. Deliberately no retry — see the class KDoc. */
    data object PlatformRestricted : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = false
        override val action: RecoveryAction = RecoveryAction.Dismiss
    }

    data class ProviderUnavailable(val authority: String?) : FileError {
        override val severity = Severity.TRANSIENT
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.Retry
    }

    // Presence
    data class FileNotFound(val name: String?) : FileError {
        override val severity = Severity.TRANSIENT
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.Refresh
    }

    data class StorageUnavailable(val volumeLabel: String?) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.Refresh
    }

    data object ReadOnlyStorage : FileError {
        override val severity = Severity.TRANSIENT
        override val recoverable = false
        override val action: RecoveryAction? = null
    }

    // Capacity
    data class DiskFull(val required: Long, val available: Long) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.OpenStorage
    }

    data object OutOfMemory : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.Retry
    }

    data class PathTooLong(val name: String?) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.Rename
    }

    // Conflict
    /** Normally intercepted before reaching the generic error UI — see the class KDoc. */
    data class FileAlreadyExists(val name: String) : FileError {
        override val severity = Severity.SILENT
        override val recoverable = true
        override val action: RecoveryAction? = null
    }

    data class InvalidDestination(val name: String?) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction? = null
    }

    data class InvalidName(val name: String, val reason: NameProblem) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.Rename
    }

    // Operation
    data object OperationCancelled : FileError {
        override val severity = Severity.TRANSIENT
        override val recoverable = false
        override val action: RecoveryAction? = null
    }

    data class IncompleteWrite(val name: String, val written: Long, val expected: Long) : FileError {
        override val severity = Severity.TRANSIENT
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.Retry
    }

    data class PartialFailure(val failedCount: Int, val totalCount: Int) : FileError {
        override val severity = Severity.TRANSIENT
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.ViewFailures
    }

    data class IoFailure(val name: String?) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.Retry
    }

    // Content
    data class UnsupportedFormat(val mimeType: String?) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = false
        override val action: RecoveryAction = RecoveryAction.OpenWith
    }

    data class CorruptedArchive(val name: String?) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = false
        override val action: RecoveryAction? = null
    }

    /** The "Extract safely" action is archive-screen-specific — see the class KDoc. */
    data class SuspiciousArchive(val name: String?, val reason: String) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction? = null
    }

    data class FileTooLarge(val name: String, val limit: Long) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = false
        override val action: RecoveryAction = RecoveryAction.OpenWith
    }

    // Fallback
    /** [marker] is a short opaque tag (e.g. a class name) — never a message or stack trace. */
    data class Unknown(val marker: String) : FileError {
        override val severity = Severity.BLOCKING
        override val recoverable = true
        override val action: RecoveryAction = RecoveryAction.Retry
    }
}

enum class Severity { BLOCKING, TRANSIENT, SILENT }

sealed interface RecoveryAction {
    data object Retry : RecoveryAction
    data object Refresh : RecoveryAction
    data class RequestAccess(val target: AccessTarget) : RecoveryAction
    data object OpenTroubleshooting : RecoveryAction
    data object OpenStorage : RecoveryAction
    data object OpenWith : RecoveryAction
    data object ViewFailures : RecoveryAction
    data object Rename : RecoveryAction
    data object Dismiss : RecoveryAction
}

/**
 * What [FileError.PermissionDenied] / [RecoveryAction.RequestAccess] refers to. Provisional
 * and minimal — `architecture/PERMISSIONS.md`'s `requiredStepsFor(target: AccessTarget)` is
 * Phase 4 scope and will likely need to extend this.
 */
sealed interface AccessTarget {
    data class Volume(val volumeId: String, val volumeLabel: String) : AccessTarget
    data object AllFiles : AccessTarget
    data object MediaImages : AccessTarget
    data object MediaVideo : AccessTarget
    data object MediaAudio : AccessTarget
}

/**
 * Why [FileError.InvalidName] was raised. Provisional and minimal — real path-safety /
 * naming validation is Phase 7 scope (`architecture/FILE_OPERATIONS.md` §8).
 */
sealed interface NameProblem {
    data class IllegalCharacters(val characters: Set<Char>) : NameProblem
    data object Empty : NameProblem
    data object ReservedName : NameProblem
    data object TrailingDotOrSpace : NameProblem
}
