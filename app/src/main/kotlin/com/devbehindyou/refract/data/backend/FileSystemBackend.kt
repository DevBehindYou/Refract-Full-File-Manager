package com.devbehindyou.refract.data.backend

import android.content.Context
import android.os.StatFs
import com.devbehindyou.refract.data.mapping.ErrorContext
import com.devbehindyou.refract.data.mapping.toFileError
import com.devbehindyou.refract.domain.model.AccessFlags
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.StorageType
import com.devbehindyou.refract.domain.repository.BackendType
import com.devbehindyou.refract.domain.repository.InputStreamProvider
import com.devbehindyou.refract.domain.repository.OutputTarget
import com.devbehindyou.refract.domain.repository.StorageBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLConnection
import java.nio.file.Files
import javax.inject.Inject

/**
 * Direct `java.io.File` I/O — the primary backend for internal storage always, and for
 * shared storage where All Files Access is granted (API 30+) or under the legacy storage
 * permission (API 27/28) (`ANDROID_STORAGE_RESEARCH.md` §2). *Which* backend a shared
 * volume actually gets routed through is a live-permission decision — Phase 4's job, not
 * this class's (see `StorageBackendSelector`'s KDoc).
 *
 * Highest-confidence backend in this phase: `java.io.File` isn't shadowed or simulated by
 * Robolectric — it's real file I/O against a real (temporary) directory, identical to how
 * it behaves on a device. The other two backends don't have that property.
 *
 * `listFiles()` returning `null` (permission denial, or the path isn't actually a readable
 * directory) always maps to [FileError.AccessDenied], never an empty list — this is Phase 3
 * AC3, and it's the one invariant this backend exists specifically to prove.
 */
class FileSystemBackend
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : StorageBackend {
        override val type: BackendType = BackendType.FILE

        override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.FILE

        override suspend fun getNode(id: FileNodeId): FileResult<FileNode> {
            val file = fileFor(id)
            if (!file.exists()) return FileResult.Failure(FileError.FileNotFound(file.name))
            return FileResult.Success(file.toFileNode(id))
        }

        override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
            flow {
                val dir = fileFor(id)
                if (!dir.exists()) {
                    emit(FileResult.Failure(FileError.FileNotFound(dir.name)))
                    return@flow
                }
                if (!dir.isDirectory) {
                    emit(FileResult.Failure(FileError.InvalidDestination(dir.name)))
                    return@flow
                }

                // Files.newDirectoryStream (NIO.2, available since API 26) yields entries as the OS
                // returns them, rather than File.listFiles()'s single blocking call that must finish
                // reading the *entire* directory before returning anything. That distinction is the
                // whole point of "streaming" here (Phase 3 AC4) — chunking an already-fully-buffered
                // array afterwards wouldn't reduce time-to-first-chunk at all.
                var emittedAny = false
                val chunk = ArrayList<FileNode>(LISTING_CHUNK_SIZE)
                try {
                    Files.newDirectoryStream(dir.toPath()).use { stream ->
                        for (path in stream) {
                            val childFile = path.toFile()
                            chunk.add(childFile.toFileNode(FileNodeId.file(childFile.absolutePath)))
                            if (chunk.size >= LISTING_CHUNK_SIZE) {
                                emit(FileResult.Success(chunk.toList()))
                                emittedAny = true
                                chunk.clear()
                            }
                        }
                    }
                    if (chunk.isNotEmpty() || !emittedAny) {
                        emit(FileResult.Success(chunk.toList()))
                    }
                } catch (e: IOException) {
                    emit(FileResult.Failure(e.toFileError(ErrorContext(dir.name))))
                }
            }

        override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
            val file = fileFor(id)
            if (!file.exists()) return FileResult.Failure(FileError.FileNotFound(file.name))
            if (!file.canRead()) return FileResult.Failure(FileError.AccessDenied(file.name))
            return FileResult.Success(InputStreamProvider { BufferedInputStream(FileInputStream(file)) })
        }

        override suspend fun openOutput(
            parent: FileNodeId,
            name: String,
            mime: String?,
        ): FileResult<OutputTarget> {
            val parentFile = fileFor(parent)
            if (!parentFile.isDirectory) return FileResult.Failure(FileError.FileNotFound(parentFile.name))
            if (!parentFile.canWrite()) return FileResult.Failure(FileError.AccessDenied(name))
            return FileResult.Success(FileSystemOutputTarget(File(parentFile, name)))
        }

        override suspend fun createDirectory(
            parent: FileNodeId,
            name: String,
        ): FileResult<FileNode> {
            val parentFile = fileFor(parent)
            if (!parentFile.isDirectory) return FileResult.Failure(FileError.FileNotFound(parentFile.name))
            val target = File(parentFile, name)
            if (target.exists()) return FileResult.Failure(FileError.FileAlreadyExists(name))
            return if (target.mkdir()) {
                FileResult.Success(target.toFileNode(FileNodeId.file(target.absolutePath)))
            } else {
                FileResult.Failure(FileError.AccessDenied(name))
            }
        }

        override suspend fun delete(id: FileNodeId): FileResult<Unit> {
            val file = fileFor(id)
            if (!file.exists()) return FileResult.Failure(FileError.FileNotFound(file.name))
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            return if (deleted) FileResult.Success(Unit) else FileResult.Failure(FileError.AccessDenied(file.name))
        }

        override suspend fun rename(
            id: FileNodeId,
            newName: String,
        ): FileResult<FileNode> {
            val file = fileFor(id)
            if (!file.exists()) return FileResult.Failure(FileError.FileNotFound(file.name))
            val parent = file.parentFile ?: return FileResult.Failure(FileError.InvalidDestination(newName))
            val target = File(parent, newName)
            if (target.exists()) return FileResult.Failure(FileError.FileAlreadyExists(newName))
            return if (file.renameTo(target)) {
                FileResult.Success(target.toFileNode(FileNodeId.file(target.absolutePath)))
            } else {
                FileResult.Failure(FileError.AccessDenied(file.name))
            }
        }

        /**
         * `File.renameTo()` can silently fail across filesystem boundaries (e.g. internal
         * storage to an SD card at the OS level) — that's not handled here with a copy+delete
         * fallback. "Move within" is read literally: moving within what this one backend can
         * reach directly. A genuine cross-volume move (with the copy-verify-then-delete-source
         * safety invariant Phase 7 AC4 asks for) is orchestration above this interface, calling
         * openInput/openOutput across two different backends — not this method.
         */
        override suspend fun moveWithin(
            id: FileNodeId,
            newParent: FileNodeId,
        ): FileResult<FileNode> {
            val file = fileFor(id)
            if (!file.exists()) return FileResult.Failure(FileError.FileNotFound(file.name))
            val parentFile = fileFor(newParent)
            if (!parentFile.isDirectory) return FileResult.Failure(FileError.FileNotFound(parentFile.name))
            val target = File(parentFile, file.name)
            if (target.exists()) return FileResult.Failure(FileError.FileAlreadyExists(file.name))
            return if (file.renameTo(target)) {
                FileResult.Success(target.toFileNode(FileNodeId.file(target.absolutePath)))
            } else {
                FileResult.Failure(FileError.AccessDenied(file.name))
            }
        }

        override suspend fun exists(
            parent: FileNodeId,
            name: String,
        ): Boolean = File(fileFor(parent), name).exists()

        override suspend fun freeSpace(id: FileNodeId): Long =
            runCatching {
                StatFs(fileFor(id).absolutePath).availableBytes
            }.getOrElse { 0L }

        private fun fileFor(id: FileNodeId): File {
            require(id.prefix == FileNodeId.Prefix.FILE) { "FileSystemBackend cannot handle $id" }
            return File(id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme))
        }

        /**
         * `INTERNAL_PRIVATE` if under this app's own private storage, `INTERNAL_SHARED`
         * otherwise. Finer classification (which shared volume — primary vs. SD card vs. USB)
         * needs [com.devbehindyou.refract.data.volume.StorageVolumes] cross-referenced by path
         * prefix, deliberately not done per-node here to avoid an enumeration call on every
         * single node conversion during a listing.
         */
        private fun File.classifyStorageType(): StorageType {
            val privateRoots = listOfNotNull(context.filesDir, context.noBackupFilesDir, context.cacheDir)
            return if (privateRoots.any { absolutePath.startsWith(it.absolutePath) }) {
                StorageType.INTERNAL_PRIVATE
            } else {
                StorageType.INTERNAL_SHARED
            }
        }

        private fun File.toFileNode(id: FileNodeId): FileNode =
            FileNode(
                id = id,
                name = name,
                displayName = name,
                mimeType = if (isDirectory) null else guessMimeType(this),
                size = if (isDirectory) -1L else length(),
                modifiedAt = lastModified(),
                isDirectory = isDirectory,
                isHidden = isHidden || name.startsWith("."),
                parentId = parentFile?.let { FileNodeId.file(it.absolutePath) },
                storageType = classifyStorageType(),
                access =
                    AccessFlags(
                        readable = canRead(),
                        writable = canWrite(),
                        deletable = canWrite(),
                        renamable = canWrite(),
                    ),
                // Deliberately not computed eagerly — see class KDoc on listChildren performance
                // (Phase 3 AC4). A directory's count is a caller-driven, separate query, not a
                // side effect of every node conversion.
                childCount = null,
                extras = null,
            )
    }

private fun guessMimeType(file: File): String? = URLConnection.guessContentTypeFromName(file.name)

private class FileSystemOutputTarget(private val target: File) : OutputTarget {
    private var discarded = false

    override fun stream() = BufferedOutputStream(FileOutputStream(target))

    override fun setLastModified(epochMillis: Long) {
        target.setLastModified(epochMillis)
    }

    override fun discard() {
        discarded = true
        target.delete()
    }

    override fun sync() {
        check(!discarded && target.exists()) { "Output is unavailable" }
        RandomAccessFile(target, "rw").use { it.fd.sync() }
    }

    override suspend fun toNode(): FileResult<FileNode> {
        if (discarded) return FileResult.Failure(FileError.OperationCancelled)
        if (!target.exists()) return FileResult.Failure(FileError.IoFailure(target.name))
        return FileResult.Success(
            FileNode(
                id = FileNodeId.file(target.absolutePath),
                name = target.name,
                displayName = target.name,
                mimeType = guessMimeType(target),
                size = target.length(),
                modifiedAt = target.lastModified(),
                isDirectory = false,
                isHidden = target.isHidden || target.name.startsWith("."),
                parentId = target.parentFile?.let { FileNodeId.file(it.absolutePath) },
                storageType = StorageType.INTERNAL_SHARED,
                access =
                    AccessFlags(
                        readable = target.canRead(),
                        writable = target.canWrite(),
                        deletable = target.canWrite(),
                        renamable = target.canWrite(),
                    ),
                childCount = null,
                extras = null,
            ),
        )
    }
}
