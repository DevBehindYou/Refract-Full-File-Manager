package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Stages and verifies a file before publishing it or removing its source. */
internal object VerifiedFileTransfer {
    suspend fun copy(
        source: FileNode,
        parent: FileNodeId,
        name: String,
        origin: StorageBackend,
        destination: StorageBackend,
        move: Boolean,
        onBytes: (Long) -> Unit,
        onProgress: suspend (String) -> Unit,
    ): FileOperationsEngine.ItemResult {
        if (!destination.capabilities.canRename) {
            return FileOperationsEngine.ItemResult.Failure(FileError.InvalidDestination(name))
        }
        val stagingName = ".refract-${UUID.randomUUID()}.partial"
        val output =
            when (val result = destination.openOutput(parent, stagingName, source.mimeType)) {
                is FileResult.Success -> result.value
                is FileResult.Failure -> return FileOperationsEngine.ItemResult.Failure(result.error)
            }
        var published = false
        var backup: FileNode? = null
        try {
            val input = origin.openInput(source.id).requireSuccess()
            val sourceHash = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            input.stream().use { from ->
                output.stream().use { to ->
                    val buffer = ByteArray(FileOperationsEngine.BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = from.read(buffer)
                        if (count < 0) break
                        if (count > 0) {
                            to.write(buffer, 0, count)
                            sourceHash.update(buffer, 0, count)
                            copied += count
                            onBytes(count.toLong())
                            onProgress(source.name)
                        }
                    }
                    to.flush()
                }
            }
            if (source.size >= 0 && copied != source.size) {
                throw TransferFailure(FileError.IncompleteWrite(source.name, copied, source.size))
            }
            output.setLastModified(source.modifiedAt)
            output.sync()
            val staged = output.toNode().requireSuccess()
            val targetHash = MessageDigest.getInstance("SHA-256")
            var verified = 0L
            destination.openInput(staged.id).requireSuccess().stream().use { inputStream ->
                val buffer = ByteArray(FileOperationsEngine.BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = inputStream.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        verified += count
                        targetHash.update(buffer, 0, count)
                    }
                }
            }
            if (verified != copied || !sourceHash.digest().contentEquals(targetHash.digest())) {
                throw TransferFailure(FileError.IncompleteWrite(source.name, verified, copied))
            }
            coroutineContext.ensureActive()
            // Existing contents remain intact throughout copy and verification. If publishing
            // fails, restore the backup; never discard an already published destination.
            destination.listChildren(parent).collect { chunk ->
                chunk.requireSuccess().firstOrNull { it.name == name }?.let { existing ->
                    check(backup == null) { "Ambiguous destination" }
                    backup = destination.rename(existing.id, ".refract-${UUID.randomUUID()}.backup").requireSuccess()
                }
            }
            destination.rename(staged.id, name).requireSuccess()
            published = true
            backup?.let { destination.delete(it.id).requireSuccess() }
            backup = null
            if (move) {
                coroutineContext.ensureActive()
                origin.delete(source.id).requireSuccess()
            }
            return FileOperationsEngine.ItemResult.Success
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return FileOperationsEngine.ItemResult.Failure(
                if (error is TransferFailure) error.fileError else FileError.IoFailure(source.name),
            )
        } finally {
            if (!published) {
                withContext(NonCancellable) {
                    try {
                        output.discard()
                    } finally {
                        // A failed restore leaves the backup in place for recovery, never deletes it.
                        backup?.let { destination.rename(it.id, name) }
                    }
                }
            }
        }
    }

    private class TransferFailure(val fileError: FileError) : Exception()

    private fun <T> FileResult<T>.requireSuccess(): T =
        when (this) {
            is FileResult.Success -> value
            is FileResult.Failure -> throw TransferFailure(error)
        }
}
