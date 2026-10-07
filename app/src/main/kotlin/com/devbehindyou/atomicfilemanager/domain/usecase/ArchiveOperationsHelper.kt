package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

internal object ArchiveOperationsHelper {
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_EXTRACT_BYTES = 10L * 1024 * 1024 * 1024 // 10 GB safe ceiling

    suspend fun compressItems(
        sources: List<FileNodeId>,
        destParentId: FileNodeId,
        zipName: String,
        compressionLevel: Int?,
        destBackend: StorageBackend,
        backendSelector: (FileNodeId) -> StorageBackend,
        onBytesCopied: (Long) -> Unit,
        emitProgress: suspend (String) -> Unit,
    ): FileOperationsEngine.ItemResult {
        val outRes = destBackend.openOutput(destParentId, zipName, "application/zip")
        val outputTarget =
            when (outRes) {
                is FileResult.Success -> outRes.value
                is FileResult.Failure -> return FileOperationsEngine.ItemResult.Failure(outRes.error)
            }

        try {
            outputTarget.stream().use { outStream ->
                ZipOutputStream(BufferedOutputStream(outStream)).use { zipOut ->
                    if (compressionLevel != null) {
                        zipOut.setLevel(compressionLevel)
                    }

                    for (sourceId in sources) {
                        if (!coroutineContext.isActive) throw CancellationException()
                        val sourceBackend = backendSelector(sourceId)
                        val nodeRes = sourceBackend.getNode(sourceId)
                        if (nodeRes !is FileResult.Success) continue
                        val node = nodeRes.value
                        emitProgress(node.name)

                        if (node.isDirectory) {
                            compressDirectoryRecursive(
                                dirNode = node,
                                relativePrefix = node.name,
                                sourceBackend = sourceBackend,
                                zipOut = zipOut,
                                onBytesCopied = onBytesCopied,
                                emitProgress = emitProgress,
                            )
                        } else {
                            compressSingleFile(
                                fileNode = node,
                                entryPath = node.name,
                                sourceBackend = sourceBackend,
                                zipOut = zipOut,
                                onBytesCopied = onBytesCopied,
                            )
                        }
                    }
                    zipOut.finish()
                    zipOut.flush()
                }
            }
            outputTarget.sync()
            return FileOperationsEngine.ItemResult.Success
        } catch (e: Exception) {
            outputTarget.discard()
            if (e is CancellationException) throw e
            return FileOperationsEngine.ItemResult.Failure(FileError.IoFailure(zipName))
        }
    }

    private suspend fun compressSingleFile(
        fileNode: FileNode,
        entryPath: String,
        sourceBackend: StorageBackend,
        zipOut: ZipOutputStream,
        onBytesCopied: (Long) -> Unit,
    ) {
        val inRes = sourceBackend.openInput(fileNode.id)
        if (inRes !is FileResult.Success) return

        val entry = ZipEntry(entryPath)
        entry.time = fileNode.modifiedAt
        zipOut.putNextEntry(entry)

        inRes.value.stream().use { inStream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                if (!coroutineContext.isActive) throw CancellationException()
                zipOut.write(buffer, 0, bytesRead)
                onBytesCopied(bytesRead.toLong())
            }
        }
        zipOut.closeEntry()
    }

    private suspend fun compressDirectoryRecursive(
        dirNode: FileNode,
        relativePrefix: String,
        sourceBackend: StorageBackend,
        zipOut: ZipOutputStream,
        onBytesCopied: (Long) -> Unit,
        emitProgress: suspend (String) -> Unit,
    ) {
        val dirEntry = ZipEntry("$relativePrefix/")
        dirEntry.time = dirNode.modifiedAt
        zipOut.putNextEntry(dirEntry)
        zipOut.closeEntry()

        sourceBackend.listChildren(dirNode.id).collect { chunkResult ->
            if (chunkResult is FileResult.Success) {
                for (child in chunkResult.value) {
                    if (!coroutineContext.isActive) throw CancellationException()
                    emitProgress(child.name)
                    val childPath = "$relativePrefix/${child.name}"
                    if (child.isDirectory) {
                        compressDirectoryRecursive(
                            dirNode = child,
                            relativePrefix = childPath,
                            sourceBackend = sourceBackend,
                            zipOut = zipOut,
                            onBytesCopied = onBytesCopied,
                            emitProgress = emitProgress,
                        )
                    } else {
                        compressSingleFile(
                            fileNode = child,
                            entryPath = childPath,
                            sourceBackend = sourceBackend,
                            zipOut = zipOut,
                            onBytesCopied = onBytesCopied,
                        )
                    }
                }
            }
        }
    }

    suspend fun extractArchive(
        sourceId: FileNodeId,
        destParentId: FileNodeId,
        sourceBackend: StorageBackend,
        destBackend: StorageBackend,
        onBytesCopied: (Long) -> Unit,
        emitProgress: suspend (String) -> Unit,
        spool: SeekableSpool? = null,
    ): FileOperationsEngine.ItemResult {
        val inRes = sourceBackend.openInput(sourceId)
        val inProvider =
            when (inRes) {
                is FileResult.Success -> inRes.value
                is FileResult.Failure -> return FileOperationsEngine.ItemResult.Failure(inRes.error)
            }

        val node = (sourceBackend.getNode(sourceId) as? FileResult.Success)?.value
        val archiveName = node?.name ?: sourceId.raw.substringAfterLast('/')
        // Anything not recognised by name is read as ZIP, as before.
        val kind = ArchiveFormats.kindOf(archiveName) ?: ArchiveFormats.Kind.ZIP
        // Given once from the preview, never stored; gone after this extract whatever happens.
        val password = ArchivePasswords.take(sourceId)
        var totalExtractedBytes = 0L
        var entryCount = 0

        try {
            inProvider.stream().use { rawIn ->
                ArchiveFormats.open(
                    kind,
                    archiveName,
                    rawIn,
                    node?.size ?: -1,
                    password = password,
                    spool = spool,
                ).use {
                        archive ->
                    var entry = archive.next()
                    while (entry != null) {
                        if (!coroutineContext.isActive) throw CancellationException()
                        val normalized = entry.path.replace('\\', '/').trimStart('/')

                        if (escapesTarget(entry.path)) {
                            return FileOperationsEngine.ItemResult.Failure(
                                FileError.SuspiciousArchive(entry.path, "Path traversal sequence detected"),
                            )
                        }
                        if (++entryCount > ArchiveFormats.MAX_ENTRIES) {
                            return FileOperationsEngine.ItemResult.Failure(
                                FileError.SuspiciousArchive(
                                    archiveName,
                                    "More than ${ArchiveFormats.MAX_ENTRIES} entries",
                                ),
                            )
                        }

                        emitProgress(normalized)

                        if (entry.isDirectory) {
                            ensureDirectoryPath(
                                destBackend,
                                destParentId,
                                normalized.split('/').filter { it.isNotEmpty() },
                            )
                        } else {
                            val segments = normalized.split('/').filter { it.isNotEmpty() }
                            val parentDirId =
                                if (segments.size > 1) {
                                    ensureDirectoryPath(destBackend, destParentId, segments.dropLast(1))
                                } else {
                                    destParentId
                                }
                            val fileName = segments.last()

                            val outRes = destBackend.openOutput(parentDirId, fileName, null)
                            if (outRes is FileResult.Success) {
                                val outTarget = outRes.value
                                try {
                                    outTarget.stream().use { outStream ->
                                        val buffer = ByteArray(BUFFER_SIZE)
                                        var read: Int
                                        while (archive.read(buffer, 0, buffer.size).also { read = it } != -1) {
                                            if (!coroutineContext.isActive) {
                                                outTarget.discard()
                                                throw CancellationException()
                                            }
                                            outStream.write(buffer, 0, read)
                                            totalExtractedBytes += read
                                            if (totalExtractedBytes > MAX_EXTRACT_BYTES) {
                                                outTarget.discard()
                                                return FileOperationsEngine.ItemResult.Failure(
                                                    FileError.SuspiciousArchive(
                                                        entry.path,
                                                        "Archive bomb detected: exceeded max size limit",
                                                    ),
                                                )
                                            }
                                            onBytesCopied(read.toLong())
                                        }
                                        outStream.flush()
                                    }
                                    outTarget.sync()
                                } catch (e: Exception) {
                                    outTarget.discard()
                                    throw e
                                }
                            }
                        }

                        entry = archive.next()
                    }
                }
            }
            return FileOperationsEngine.ItemResult.Success
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is ArchiveWrongPassword) {
                return FileOperationsEngine.ItemResult.Failure(FileError.UnsupportedFormat(WRONG_PASSWORD))
            }
            if (e is ArchivePasswordRequired) {
                return FileOperationsEngine.ItemResult.Failure(FileError.UnsupportedFormat(PASSWORD_PROTECTED))
            }
            return FileOperationsEngine.ItemResult.Failure(FileError.CorruptedArchive(sourceId.raw))
        }
    }

    private suspend fun ensureDirectoryPath(
        backend: StorageBackend,
        rootParentId: FileNodeId,
        segments: List<String>,
    ): FileNodeId {
        var currentParent = rootParentId
        for (seg in segments) {
            if (seg.isEmpty()) continue
            if (!backend.exists(currentParent, seg)) {
                when (val created = backend.createDirectory(currentParent, seg)) {
                    is FileResult.Success -> currentParent = created.value.id
                    is FileResult.Failure -> {
                        val prefix = currentParent.prefix?.scheme ?: ""
                        val pathPart = currentParent.raw.removePrefix(prefix).trimEnd('/')
                        currentParent = FileNodeId("$prefix$pathPart/$seg")
                    }
                }
            } else {
                val prefix = currentParent.prefix?.scheme ?: ""
                val pathPart = currentParent.raw.removePrefix(prefix).trimEnd('/')
                currentParent = FileNodeId("$prefix$pathPart/$seg")
            }
        }
        return currentParent
    }
}
