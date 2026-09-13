package com.devbehindyou.refract.domain.testing

import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.BackendType
import com.devbehindyou.refract.domain.repository.InputStreamProvider
import com.devbehindyou.refract.domain.repository.OutputTarget
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.io.OutputStream

/*
 * Four decorators over a real [StorageBackend] (usually [InMemoryBackend]), named exactly per
 * `testing/TEST_STRATEGY.md` §3: "These make failure paths testable deterministically, which
 * is the only way to prove [FILE_OPERATIONS.md]'s invariants without relying on real device I/O
 * misbehaving on demand." Test-only — not shipped.
 *
 * Only [FailAfterNBytes]'s exact use is pinned down elsewhere in the docs (roadmap Phase 3 AC4:
 * proving a cross-volume move never deletes its source before the destination write is
 * verified). The other three's precise triggering semantics below are this file's own
 * reasonable design to match their names, not a literal spec.
 */

/**
 * Fails the write partway through: the [OutputTarget] returned by `openOutput` throws
 * [IOException] once more than [thresholdBytes] have been written to its stream, simulating an
 * interrupted copy (disk full, provider crash, ...).
 */
class FailAfterNBytes(
    private val thresholdBytes: Long,
    private val delegate: StorageBackend,
) : StorageBackend by delegate {
    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> =
        delegate.openOutput(parent, name, mime).let { result ->
            when (result) {
                is FileResult.Success -> FileResult.Success(FailingOutputTarget(result.value, thresholdBytes))
                is FileResult.Failure -> result
            }
        }

    private class FailingOutputTarget(
        private val real: OutputTarget,
        private val thresholdBytes: Long,
    ) : OutputTarget by real {
        override fun stream(): OutputStream {
            val realStream = real.stream()
            var written = 0L
            return object : OutputStream() {
                override fun write(b: Int) {
                    if (written >= thresholdBytes) {
                        throw IOException("FailAfterNBytes: simulated failure after $thresholdBytes bytes")
                    }
                    realStream.write(b)
                    written++
                }

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) {
                    if (written + len > thresholdBytes) {
                        val allowed = (thresholdBytes - written).coerceAtLeast(0).toInt()
                        if (allowed > 0) realStream.write(b, off, allowed)
                        written += allowed
                        throw IOException("FailAfterNBytes: simulated failure after $thresholdBytes bytes")
                    }
                    realStream.write(b, off, len)
                    written += len
                }

                override fun close() {
                    realStream.close()
                }
            }
        }
    }
}

/**
 * Every write operation (`openOutput`, `createDirectory`, `delete`, `rename`, `moveWithin`)
 * fails with [FileError.AccessDenied] without touching the delegate. Reads pass through
 * unchanged — simulates a backend that lost write access (e.g. a revoked SAF grant).
 */
class DenyWrite(private val delegate: StorageBackend) : StorageBackend by delegate {
    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> = FileResult.Failure(FileError.AccessDenied(name))

    override suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode> = FileResult.Failure(FileError.AccessDenied(name))

    override suspend fun delete(id: FileNodeId): FileResult<Unit> = FileResult.Failure(FileError.AccessDenied(id.raw))

    override suspend fun rename(
        id: FileNodeId,
        newName: String,
    ): FileResult<FileNode> = FileResult.Failure(FileError.AccessDenied(newName))

    override suspend fun moveWithin(
        id: FileNodeId,
        newParent: FileNodeId,
    ): FileResult<FileNode> = FileResult.Failure(FileError.AccessDenied(id.raw))
}

/**
 * Adds [delayMillis] before every suspend operation delegates — for testing
 * cancellation, timeout, and progress-reporting behaviour deterministically instead of
 * relying on real device I/O being slow when a test needs it to be.
 */
class SlowBackend(
    private val delayMillis: Long,
    private val delegate: StorageBackend,
) : StorageBackend {
    override val type: BackendType get() = delegate.type

    override fun canHandle(id: FileNodeId): Boolean = delegate.canHandle(id)

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> = delegate.listChildren(id)

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> {
        delay(delayMillis)
        return delegate.getNode(id)
    }

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
        delay(delayMillis)
        return delegate.openInput(id)
    }

    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> {
        delay(delayMillis)
        return delegate.openOutput(parent, name, mime)
    }

    override suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode> {
        delay(delayMillis)
        return delegate.createDirectory(parent, name)
    }

    override suspend fun delete(id: FileNodeId): FileResult<Unit> {
        delay(delayMillis)
        return delegate.delete(id)
    }

    override suspend fun rename(
        id: FileNodeId,
        newName: String,
    ): FileResult<FileNode> {
        delay(delayMillis)
        return delegate.rename(id, newName)
    }

    override suspend fun moveWithin(
        id: FileNodeId,
        newParent: FileNodeId,
    ): FileResult<FileNode> {
        delay(delayMillis)
        return delegate.moveWithin(id, newParent)
    }

    override suspend fun exists(
        parent: FileNodeId,
        name: String,
    ): Boolean {
        delay(delayMillis)
        return delegate.exists(parent, name)
    }

    override suspend fun freeSpace(id: FileNodeId): Long {
        delay(delayMillis)
        return delegate.freeSpace(id)
    }
}

/**
 * [target] can be read successfully [survivesReads] times (default 1 — "it was there when the
 * listing happened"); every read attempt after that returns [FileError.FileNotFound], as if
 * another process deleted it in between. Only [target] is affected — everything else passes
 * through unchanged.
 */
class VanishingSource(
    private val target: FileNodeId,
    private val survivesReads: Int = 1,
    private val delegate: StorageBackend,
) : StorageBackend by delegate {
    private var reads = 0

    private fun hasVanished(id: FileNodeId): Boolean {
        if (id != target) return false
        reads++
        return reads > survivesReads
    }

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> =
        if (hasVanished(id)) FileResult.Failure(FileError.FileNotFound(id.raw)) else delegate.getNode(id)

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
        if (hasVanished(id)) FileResult.Failure(FileError.FileNotFound(id.raw)) else delegate.openInput(id)
}
