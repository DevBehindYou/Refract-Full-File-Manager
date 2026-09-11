package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.CollisionPolicy
import com.devbehindyou.refract.domain.model.FailedItem
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileOperation
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.OperationProgress
import com.devbehindyou.refract.domain.model.OperationSnapshot
import com.devbehindyou.refract.domain.model.OperationStatus
import com.devbehindyou.refract.domain.model.OperationSummary
import com.devbehindyou.refract.domain.model.OperationType
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class FileOperationsEngine @Inject constructor(
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    companion object {
        const val BUFFER_SIZE = 64 * 1024 // 64 KB bounded streaming buffer
        const val PROGRESS_THROTTLE_MS = 250L // 4 Hz progress reporting
    }

    /**
     * Executes [operation] and emits [OperationSnapshot] progress updates.
     */
    fun execute(operation: FileOperation): Flow<OperationSnapshot> = flow {
        emit(OperationSnapshot(operation, OperationStatus.Queued))

        val succeeded = mutableListOf<FileNodeId>()
        val skipped = mutableListOf<FileNodeId>()
        val failed = mutableListOf<FailedItem>()

        var itemsDone = 0
        var bytesDone = 0L
        val itemsTotal = operation.sources.size
        var totalBytesEstimate = 0L

        // Initial scan for size estimation
        for (sourceId in operation.sources) {
            val backend = backendSelector(sourceId)
            when (val nodeResult = backend.getNode(sourceId)) {
                is FileResult.Success -> totalBytesEstimate += nodeResult.value.size
                is FileResult.Failure -> { /* size unknown, best effort */ }
            }
        }

        var lastProgressTime = System.currentTimeMillis()
        var bytesSinceLastSpeedCheck = 0L
        var currentSpeed = 0L

        suspend fun emitProgress(currentName: String?) {
            val now = System.currentTimeMillis()
            val timeDelta = now - lastProgressTime
            if (timeDelta >= PROGRESS_THROTTLE_MS) {
                currentSpeed = if (timeDelta > 0) (bytesSinceLastSpeedCheck * 1000L) / timeDelta else 0L
                lastProgressTime = now
                bytesSinceLastSpeedCheck = 0L
            }

            val eta = if (currentSpeed > 0L && totalBytesEstimate > bytesDone) {
                ((totalBytesEstimate - bytesDone) * 1000L) / currentSpeed
            } else null

            emit(
                OperationSnapshot(
                    operation,
                    OperationStatus.Running(
                        OperationProgress(
                            itemsDone = itemsDone,
                            itemsTotal = itemsTotal,
                            bytesDone = bytesDone,
                            bytesTotal = totalBytesEstimate,
                            currentName = currentName,
                            bytesPerSecond = currentSpeed,
                            etaMillis = eta,
                        )
                    )
                )
            )
        }

        try {
            when (operation.type) {
                OperationType.DELETE -> {
                    for (sourceId in operation.sources) {
                        if (!coroutineContext.isActive) throw CancellationException()
                        val backend = backendSelector(sourceId)
                        val node = (backend.getNode(sourceId) as? FileResult.Success)?.value
                        val name = node?.name ?: sourceId.raw
                        emitProgress(name)

                        when (val result = backend.delete(sourceId)) {
                            is FileResult.Success -> {
                                succeeded.add(sourceId)
                                itemsDone++
                            }
                            is FileResult.Failure -> {
                                failed.add(FailedItem(sourceId, name, result.error))
                            }
                        }
                    }
                }
                OperationType.COPY, OperationType.MOVE -> {
                    val destParent = requireNotNull(operation.destination) {
                        "Destination must be provided for ${operation.type}"
                    }
                    val isMove = operation.type == OperationType.MOVE

                    for (sourceId in operation.sources) {
                        if (!coroutineContext.isActive) throw CancellationException()
                        val sourceBackend = backendSelector(sourceId)
                        val destBackend = backendSelector(destParent)

                        val nodeResult = sourceBackend.getNode(sourceId)
                        if (nodeResult is FileResult.Failure) {
                            failed.add(FailedItem(sourceId, sourceId.raw, nodeResult.error))
                            continue
                        }
                        val sourceNode = (nodeResult as FileResult.Success).value
                        emitProgress(sourceNode.name)

                        val copyResult = copyOrMoveItem(
                            sourceNode = sourceNode,
                            destParentId = destParent,
                            sourceBackend = sourceBackend,
                            destBackend = destBackend,
                            isMove = isMove,
                            collisionPolicy = operation.options.collisionPolicy,
                            onBytesCopied = { copied ->
                                bytesDone += copied
                                bytesSinceLastSpeedCheck += copied
                            },
                            emitProgress = { name -> emitProgress(name) }
                        )

                        when (copyResult) {
                            is ItemResult.Success -> {
                                succeeded.add(sourceId)
                                itemsDone++
                            }
                            is ItemResult.Skipped -> {
                                skipped.add(sourceId)
                                itemsDone++
                            }
                            is ItemResult.Failure -> {
                                failed.add(FailedItem(sourceId, sourceNode.name, copyResult.error))
                            }
                        }
                    }
                }
                OperationType.RENAME -> {
                    for (sourceId in operation.sources) {
                        val backend = backendSelector(sourceId)
                        val newName = operation.destination?.raw ?: continue
                        when (val result = backend.rename(sourceId, newName)) {
                            is FileResult.Success -> {
                                succeeded.add(sourceId)
                                itemsDone++
                            }
                            is FileResult.Failure -> {
                                failed.add(FailedItem(sourceId, newName, result.error))
                            }
                        }
                    }
                }
                OperationType.COMPRESS -> {
                    val destParent = requireNotNull(operation.destination) {
                        "Destination must be provided for COMPRESS"
                    }
                    val destBackend = backendSelector(destParent)
                    val baseName = if (operation.sources.size == 1) {
                        val firstSourceNode = (backendSelector(operation.sources[0]).getNode(operation.sources[0]) as? FileResult.Success)?.value
                        val rawName = firstSourceNode?.name ?: "archive"
                        if (rawName.endsWith(".zip", ignoreCase = true)) rawName else "$rawName.zip"
                    } else {
                        "Archive.zip"
                    }
                    val zipName = resolveUniqueName(destBackend, destParent, baseName)
                    emitProgress(zipName)

                    val compressResult = ArchiveOperationsHelper.compressItems(
                        sources = operation.sources,
                        destParentId = destParent,
                        zipName = zipName,
                        compressionLevel = operation.options.compressionLevel,
                        destBackend = destBackend,
                        backendSelector = backendSelector,
                        onBytesCopied = { copied ->
                            bytesDone += copied
                            bytesSinceLastSpeedCheck += copied
                        },
                        emitProgress = { name -> emitProgress(name) }
                    )

                    when (compressResult) {
                        is ItemResult.Success -> {
                            succeeded.addAll(operation.sources)
                            itemsDone += operation.sources.size
                        }
                        is ItemResult.Skipped -> {
                            skipped.addAll(operation.sources)
                            itemsDone += operation.sources.size
                        }
                        is ItemResult.Failure -> {
                            failed.add(FailedItem(operation.sources.first(), zipName, compressResult.error))
                        }
                    }
                }
                OperationType.EXTRACT -> {
                    val destParent = requireNotNull(operation.destination) {
                        "Destination must be provided for EXTRACT"
                    }
                    val destBackend = backendSelector(destParent)

                    for (sourceId in operation.sources) {
                        if (!coroutineContext.isActive) throw CancellationException()
                        val sourceBackend = backendSelector(sourceId)
                        val sourceNode = (sourceBackend.getNode(sourceId) as? FileResult.Success)?.value
                        val name = sourceNode?.name ?: sourceId.raw
                        emitProgress(name)

                        val extractResult = ArchiveOperationsHelper.extractArchive(
                            sourceId = sourceId,
                            destParentId = destParent,
                            sourceBackend = sourceBackend,
                            destBackend = destBackend,
                            onBytesCopied = { copied ->
                                bytesDone += copied
                                bytesSinceLastSpeedCheck += copied
                            },
                            emitProgress = { entryName -> emitProgress(entryName) }
                        )

                        when (extractResult) {
                            is ItemResult.Success -> {
                                succeeded.add(sourceId)
                                itemsDone++
                            }
                            is ItemResult.Skipped -> {
                                skipped.add(sourceId)
                                itemsDone++
                            }
                            is ItemResult.Failure -> {
                                failed.add(FailedItem(sourceId, name, extractResult.error))
                            }
                        }
                    }
                }
                OperationType.HIDE_GALLERY,
                OperationType.UNHIDE_GALLERY,
                OperationType.FAST_OBSCURE,
                OperationType.RESTORE_OBSCURE,
                OperationType.MOVE_TO_PRIVATE,
                -> {
                    val destParent = operation.destination
                    if (destParent != null) {
                        for (sourceId in operation.sources) {
                            if (!coroutineContext.isActive) throw CancellationException()
                            val sourceBackend = backendSelector(sourceId)
                            val destBackend = backendSelector(destParent)
                            val nodeResult = sourceBackend.getNode(sourceId)
                            if (nodeResult is FileResult.Failure) {
                                failed.add(FailedItem(sourceId, sourceId.raw, nodeResult.error))
                                continue
                            }
                            val sourceNode = (nodeResult as FileResult.Success).value
                            emitProgress(sourceNode.name)
                            val copyResult = copyOrMoveItem(
                                sourceNode = sourceNode,
                                destParentId = destParent,
                                sourceBackend = sourceBackend,
                                destBackend = destBackend,
                                isMove = true,
                                collisionPolicy = operation.options.collisionPolicy,
                                onBytesCopied = { copied ->
                                    bytesDone += copied
                                    bytesSinceLastSpeedCheck += copied
                                },
                                emitProgress = { name -> emitProgress(name) },
                            )
                            when (copyResult) {
                                is ItemResult.Success -> {
                                    succeeded.add(sourceId)
                                    itemsDone++
                                }
                                is ItemResult.Skipped -> {
                                    skipped.add(sourceId)
                                    itemsDone++
                                }
                                is ItemResult.Failure -> {
                                    failed.add(FailedItem(sourceId, sourceNode.name, copyResult.error))
                                }
                            }
                        }
                    }
                }
            }

            val summary = OperationSummary(
                succeeded = succeeded,
                skipped = skipped,
                failed = failed,
                undoToken = null,
            )

            if (failed.isEmpty()) {
                emit(OperationSnapshot(operation, OperationStatus.Completed(summary)))
            } else if (succeeded.isNotEmpty() || skipped.isNotEmpty()) {
                emit(OperationSnapshot(operation, OperationStatus.PartiallyCompleted(summary)))
            } else {
                emit(
                    OperationSnapshot(
                        operation,
                        OperationStatus.Failed(
                            failed.firstOrNull()?.error ?: FileError.IoFailure("Operation failed"),
                            summary
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            emit(OperationSnapshot(operation, OperationStatus.Cancelled))
            throw e
        } catch (e: Exception) {
            val summary = OperationSummary(succeeded, skipped, failed, null)
            emit(OperationSnapshot(operation, OperationStatus.Failed(FileError.IoFailure(e.message ?: "Unknown error"), summary)))
        }
    }.flowOn(Dispatchers.IO)

    internal sealed interface ItemResult {
        data object Success : ItemResult
        data object Skipped : ItemResult
        data class Failure(val error: FileError) : ItemResult
    }

    private suspend fun copyOrMoveItem(
        sourceNode: FileNode,
        destParentId: FileNodeId,
        sourceBackend: StorageBackend,
        destBackend: StorageBackend,
        isMove: Boolean,
        collisionPolicy: CollisionPolicy,
        onBytesCopied: (Long) -> Unit,
        emitProgress: suspend (String) -> Unit,
    ): ItemResult {
        // Check read capability
        if (!sourceBackend.capabilities.canRead) {
            return ItemResult.Failure(FileError.AccessDenied("Source is unreadable"))
        }

        // Fast path for same-backend move without conflict when atomic move is supported
        if (isMove && sourceBackend == destBackend && destBackend.capabilities.supportsAtomicMove) {
            val exists = destBackend.exists(destParentId, sourceNode.name)
            if (!exists) {
                return when (val moveRes = destBackend.moveWithin(sourceNode.id, destParentId)) {
                    is FileResult.Success -> ItemResult.Success
                    is FileResult.Failure -> ItemResult.Failure(moveRes.error)
                }
            }
        }

        // Check write capability on destination
        if (!destBackend.capabilities.canWrite && !destBackend.capabilities.canCreate) {
            return ItemResult.Failure(FileError.AccessDenied("Destination is read-only"))
        }

        // Resolving destination name based on collision policy
        val exists = destBackend.exists(destParentId, sourceNode.name)
        val targetName = when {
            !exists -> sourceNode.name
            collisionPolicy == CollisionPolicy.SKIP -> return ItemResult.Skipped
            collisionPolicy == CollisionPolicy.OVERWRITE -> sourceNode.name
            else -> resolveUniqueName(destBackend, destParentId, sourceNode.name)
        }

        if (sourceNode.isDirectory) {
            // Create destination directory
            val createDirResult = destBackend.createDirectory(destParentId, targetName)
            val newDirNode = when (createDirResult) {
                is FileResult.Success -> createDirResult.value
                is FileResult.Failure -> return ItemResult.Failure(createDirResult.error)
            }

            // Copy/move children recursively
            var allChildrenSucceeded = true
            sourceBackend.listChildren(sourceNode.id).collect { chunkResult ->
                when (chunkResult) {
                    is FileResult.Success -> {
                        for (child in chunkResult.value) {
                            if (!coroutineContext.isActive) throw CancellationException()
                            emitProgress(child.name)
                            val childRes = copyOrMoveItem(
                                sourceNode = child,
                                destParentId = newDirNode.id,
                                sourceBackend = sourceBackend,
                                destBackend = destBackend,
                                isMove = isMove,
                                collisionPolicy = collisionPolicy,
                                onBytesCopied = onBytesCopied,
                                emitProgress = emitProgress,
                            )
                            if (childRes !is ItemResult.Success && childRes !is ItemResult.Skipped) {
                                allChildrenSucceeded = false
                            }
                        }
                    }
                    is FileResult.Failure -> {
                        allChildrenSucceeded = false
                    }
                }
            }

            if (isMove && allChildrenSucceeded) {
                sourceBackend.delete(sourceNode.id)
            }
            return if (allChildrenSucceeded) ItemResult.Success else ItemResult.Failure(FileError.IoFailure("Failed copying all children"))
        } else {
            // Stream file data with bounded buffer
            val inputProvider = when (val inRes = sourceBackend.openInput(sourceNode.id)) {
                is FileResult.Success -> inRes.value
                is FileResult.Failure -> return ItemResult.Failure(inRes.error)
            }

            val outputTarget = when (val outRes = destBackend.openOutput(destParentId, targetName, sourceNode.mimeType)) {
                is FileResult.Success -> outRes.value
                is FileResult.Failure -> return ItemResult.Failure(outRes.error)
            }

            try {
                inputProvider.stream().use { input ->
                    outputTarget.stream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!coroutineContext.isActive) {
                                outputTarget.discard()
                                throw CancellationException("Operation cancelled")
                            }
                            output.write(buffer, 0, bytesRead)
                            onBytesCopied(bytesRead.toLong())
                        }
                        output.flush()
                    }
                }
                outputTarget.setLastModified(sourceNode.modifiedAt)
                outputTarget.sync()

                if (isMove) {
                    sourceBackend.delete(sourceNode.id)
                }
                return ItemResult.Success
            } catch (e: Exception) {
                outputTarget.discard()
                if (e is CancellationException) throw e
                return ItemResult.Failure(FileError.IoFailure(sourceNode.name))
            }
        }
    }

    private suspend fun resolveUniqueName(backend: StorageBackend, parent: FileNodeId, name: String): String {
        val dotIndex = name.lastIndexOf('.')
        val hasExtension = dotIndex > 0 && dotIndex < name.length - 1
        val base = if (hasExtension) name.substring(0, dotIndex) else name
        val ext = if (hasExtension) name.substring(dotIndex) else ""
        var counter = 1
        var candidate = "$base ($counter)$ext"
        while (backend.exists(parent, candidate)) {
            counter++
            candidate = "$base ($counter)$ext"
        }
        return candidate
    }
}
