package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import java.util.UUID

/** What start-up recovery did in one folder. */
data class CleanupReport(
    /** Unfinished copies (staging files) deleted. */
    val removedPartials: Int = 0,
    /** Originals put back under their own name after a crash in the middle of a Replace. */
    val restoredOriginals: Int = 0,
    /** Set-aside originals left alone because a file with their name exists again; never deleted. */
    val keptBackups: Int = 0,
) {
    operator fun plus(other: CleanupReport) =
        CleanupReport(
            removedPartials + other.removedPartials,
            restoredOriginals + other.restoredOriginals,
            keptBackups + other.keptBackups,
        )

    val isEmpty: Boolean get() = removedPartials == 0 && restoredOriginals == 0 && keptBackups == 0
}

/**
 * Tidies a destination folder after the app died mid-transfer (ALL_IN_ONE_PLAN.md §16.5).
 * [VerifiedFileTransfer] writes to a staging name and, for Replace, moves the existing file to a
 * backup name that keeps the original name. A crash can leave either behind:
 *
 * - staging files are unverified partial copies and are deleted;
 * - a backup whose original name is free again is renamed back, so Replace never loses the old file;
 * - a backup whose name is taken (the new copy was published) is kept and reported, never deleted.
 *
 * Only this folder is checked, not subfolders: a folder copy that was cut short can leave staging
 * files deeper down, which Retry overwrites or the user can delete.
 */
object InterruptedTransferCleanup {
    private const val PREFIX = ".atomic-"
    private const val BACKUP_PREFIX = ".atomic-orig-"
    private const val ID_LENGTH = 8
    private const val MAX_NAME_BYTES = 255
    private val partial = Regex("""^\.atomic-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\.partial$""")
    private val backup = Regex("""^\.atomic-orig-[0-9a-f]{8}-(.+)$""")

    fun stagingName(): String = "$PREFIX${UUID.randomUUID()}.partial"

    /** Keeps [original] in the name when it fits; otherwise an anonymous backup recovery can't restore. */
    fun backupName(original: String): String {
        val id = UUID.randomUUID().toString().take(ID_LENGTH)
        val named = "$BACKUP_PREFIX$id-$original"
        return if (named.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES) named else "$PREFIX$id.backup"
    }

    /** The original name stored in a backup name, or null. */
    fun originalNameOf(backupName: String): String? = backup.matchEntire(backupName)?.groupValues?.get(1)

    fun isStaging(name: String): Boolean = partial.matches(name)

    suspend fun clean(
        folder: FileNodeId,
        backend: StorageBackend,
    ): CleanupReport {
        val children = mutableListOf<FileNode>()
        backend.listChildren(folder).collect { chunk -> if (chunk is FileResult.Success) children += chunk.value }
        val names = children.mapTo(mutableSetOf()) { it.name }
        var report = CleanupReport()
        for (child in children.filterNot { it.isDirectory }) {
            val original = originalNameOf(child.name)
            report =
                when {
                    isStaging(child.name) ->
                        if (backend.delete(child.id) is FileResult.Success) {
                            report.copy(removedPartials = report.removedPartials + 1)
                        } else {
                            report
                        }
                    original == null -> report
                    original !in names && backend.rename(child.id, original) is FileResult.Success -> {
                        names += original
                        report.copy(restoredOriginals = report.restoredOriginals + 1)
                    }
                    else -> report.copy(keptBackups = report.keptBackups + 1)
                }
        }
        return report
    }
}
