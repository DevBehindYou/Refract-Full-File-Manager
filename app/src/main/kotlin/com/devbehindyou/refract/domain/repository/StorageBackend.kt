package com.devbehindyou.refract.domain.repository

import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.StorageCapabilities
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/**
 * Phase 3 will provide three implementations of this — `FileSystemBackend`, `SafBackend`,
 * `MediaStoreBackend` — selected per volume by `StorageBackendSelector`
 * (`architecture/ARCHITECTURE.md` §5). `InMemoryBackend`, a fourth, test-only implementation
 * for domain unit tests, arrives this phase.
 *
 * `java.io.InputStream`/`OutputStream` here are plain JDK types, not `java.io.File` — they
 * don't trip `NoAndroidInDomain` and don't require an Android runtime to use or test against.
 */
enum class BackendType { FILE, SAF, MEDIASTORE, USB, SFTP, FTP, FTPS, SMB, WEBDAV }

/** Callers must close the returned stream (`use { }` — CODING_RULES.md #20). */
fun interface InputStreamProvider {
    fun stream(): InputStream
}

/**
 * A destination being written to. [discard] must leave no partial file behind on failure or
 * cancellation (`architecture/FILE_OPERATIONS.md` §4's invariant for the copy algorithm).
 */
interface OutputTarget {
    fun stream(): OutputStream

    fun setLastModified(epochMillis: Long)

    fun discard()

    fun sync()

    suspend fun toNode(): FileResult<FileNode>
}

interface StorageBackend {
    val type: BackendType
    val capabilities: StorageCapabilities
        get() =
            when (type) {
                BackendType.FILE -> StorageCapabilities.FULL_LOCAL
                BackendType.SAF -> StorageCapabilities.SAF_STORAGE
                BackendType.MEDIASTORE -> StorageCapabilities.MEDIA_STORE
                BackendType.USB -> StorageCapabilities.SAF_STORAGE
                BackendType.SFTP,
                BackendType.FTP,
                BackendType.FTPS,
                BackendType.SMB,
                BackendType.WEBDAV,
                -> StorageCapabilities.REMOTE_NETWORK
            }

    fun canHandle(id: FileNodeId): Boolean

    suspend fun getNode(id: FileNodeId): FileResult<FileNode>

    fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>>

    suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider>

    suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget>

    suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode>

    suspend fun delete(id: FileNodeId): FileResult<Unit>

    suspend fun rename(
        id: FileNodeId,
        newName: String,
    ): FileResult<FileNode>

    suspend fun moveWithin(
        id: FileNodeId,
        newParent: FileNodeId,
    ): FileResult<FileNode>

    suspend fun exists(
        parent: FileNodeId,
        name: String,
    ): Boolean

    suspend fun freeSpace(id: FileNodeId): Long
}
