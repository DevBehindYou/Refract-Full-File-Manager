package com.devbehindyou.refract.data.backend.network

import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.StorageCapabilities
import com.devbehindyou.refract.domain.repository.InputStreamProvider
import com.devbehindyou.refract.domain.repository.OutputTarget
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A [StorageBackend] for a protocol that is wired into the app's plumbing (id scheme,
 * [com.devbehindyou.refract.domain.repository.BackendType], credential storage, UI entry
 * points) but whose wire protocol is **not implemented**. Every operation fails with
 * [FileError.ProviderUnavailable] so the failure is visible instead of silent.
 *
 * ## Why this exists
 *
 * The previous `SftpBackend` and `SmbBackend` implementations fabricated success:
 * `openOutput` returned an [OutputTarget] whose stream discarded every byte and whose
 * `toNode()` reported success; `createDirectory`, `rename` and `moveWithin` returned
 * synthesised [FileNode]s for directories that were never created; `delete` returned
 * `Success` without deleting anything; and `listChildren` emitted an empty list, so a
 * populated remote share rendered as an empty folder.
 *
 * Nothing was ever sent over the network — both classes only opened a TCP socket to check
 * reachability, then invented a plausible answer. Implementing SSH or SMB requires a real
 * protocol library (neither is feasible over a bare socket), so the honest interim
 * behaviour is to fail loudly.
 *
 * Note that [com.devbehindyou.refract.domain.usecase.VerifiedFileTransfer] would have
 * caught the data-loss case anyway — it hashes the source, reads the destination back and
 * compares before deleting any source file, and the stub `openInput` always failed, so a
 * *move* off a local volume onto one of these backends aborted rather than destroying the
 * original. That safety net held, but it was the last line of defence rather than the
 * first, and it did not stop `delete` and `createDirectory` from reporting success for
 * work that never happened.
 *
 * Replace a subclass of this with a real implementation backed by a proper protocol
 * library when the time comes; the id parsing, credential store and UI wiring all already
 * exist. See BUILD_READINESS_NOTES.md §2.
 */
abstract class UnimplementedProtocolBackend : StorageBackend {
    /** Human-readable protocol name used in the failure surfaced to the UI. */
    protected abstract val protocolName: String

    /**
     * Nothing works, so advertise nothing. `VerifiedFileTransfer` consults
     * [StorageCapabilities.canRename] before staging a transfer, so a copy or move onto
     * this backend is rejected up front rather than after a pointless read of the source.
     */
    override val capabilities: StorageCapabilities =
        StorageCapabilities(
            canRead = false,
            canWrite = false,
            canCreate = false,
            canDelete = false,
            canRename = false,
            supportsMove = false,
            supportsCopy = false,
        )

    private fun unavailable(): FileError.ProviderUnavailable = FileError.ProviderUnavailable(protocolName)

    private fun <T> failure(): FileResult<T> = FileResult.Failure(unavailable())

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> = failure()

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> = flowOf(failure())

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> = failure()

    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> = failure()

    override suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode> = failure()

    override suspend fun delete(id: FileNodeId): FileResult<Unit> = failure()

    override suspend fun rename(
        id: FileNodeId,
        newName: String,
    ): FileResult<FileNode> = failure()

    override suspend fun moveWithin(
        id: FileNodeId,
        newParent: FileNodeId,
    ): FileResult<FileNode> = failure()

    override suspend fun exists(
        parent: FileNodeId,
        name: String,
    ): Boolean = false

    override suspend fun freeSpace(id: FileNodeId): Long = 0L
}
