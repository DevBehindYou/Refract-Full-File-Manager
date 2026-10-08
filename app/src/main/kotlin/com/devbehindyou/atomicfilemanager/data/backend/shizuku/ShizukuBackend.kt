package com.devbehindyou.atomicfilemanager.data.backend.shizuku

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.OutputTarget
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * `Android/data` and `Android/obb` through Shizuku (ALL_IN_ONE_PLAN.md 4.3, Labs). Read-only: list
 * and copy out. Every write is refused here, and the service refuses anything outside the two
 * folders, so other apps' data can be read but never changed.
 */
class ShizukuBackend(private val access: ShizukuAccess) : StorageBackend {
    override val type: BackendType = BackendType.SHIZUKU

    override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.SHIZUKU

    private fun pathOf(id: FileNodeId): String = id.raw.removePrefix(FileNodeId.Prefix.SHIZUKU.scheme)

    private suspend fun <T> withService(block: (IShizukuFileService) -> T): FileResult<T> =
        withContext(Dispatchers.IO) {
            val service =
                access.service() ?: return@withContext FileResult.Failure(FileError.ProviderUnavailable("Shizuku"))
            try {
                FileResult.Success(block(service))
            } catch (e: SecurityException) {
                FileResult.Failure(FileError.AccessDenied(e.message))
            } catch (e: RemoteException) {
                FileResult.Failure(FileError.ProviderUnavailable("Shizuku"))
            } catch (e: IllegalArgumentException) {
                FileResult.Failure(FileError.InvalidDestination(e.message))
            } catch (e: IllegalStateException) {
                FileResult.Failure(FileError.IoFailure(e.message))
            }
        }

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> {
        val path = pathOf(id)
        return when (val stat = withService { ShizukuEntries.decode(it.stat(path)).firstOrNull() }) {
            is FileResult.Failure -> stat
            is FileResult.Success ->
                stat.value?.let { FileResult.Success(node(path, it)) }
                    ?: FileResult.Failure(FileError.FileNotFound(path.substringAfterLast('/')))
        }
    }

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
        flow {
            val path = pathOf(id).trimEnd('/')
            emit(withService { svc -> ShizukuEntries.decode(svc.list(path)).map { node("$path/${it.name}", it) } })
        }.flowOn(Dispatchers.IO)

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
        val path = pathOf(id)
        return withService { svc ->
            svc.stat(path).ifEmpty { error("${path.substringAfterLast('/')} is gone") }
            InputStreamProvider { ParcelFileDescriptor.AutoCloseInputStream(svc.openRead(path)) }
        }
    }

    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> = FileResult.Failure(FileError.ReadOnlyStorage)

    override suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode> = FileResult.Failure(FileError.ReadOnlyStorage)

    override suspend fun delete(id: FileNodeId): FileResult<Unit> = FileResult.Failure(FileError.ReadOnlyStorage)

    override suspend fun rename(
        id: FileNodeId,
        newName: String,
    ): FileResult<FileNode> = FileResult.Failure(FileError.ReadOnlyStorage)

    override suspend fun moveWithin(
        id: FileNodeId,
        newParent: FileNodeId,
    ): FileResult<FileNode> = FileResult.Failure(FileError.ReadOnlyStorage)

    override suspend fun exists(
        parent: FileNodeId,
        name: String,
    ): Boolean {
        val path = pathOf(parent).trimEnd('/') + "/" + name
        return (withService { it.stat(path).isNotEmpty() } as? FileResult.Success)?.value ?: false
    }

    override suspend fun freeSpace(id: FileNodeId): Long = 0L

    private fun node(
        path: String,
        entry: ShizukuEntry,
    ): FileNode {
        val parent = path.substringBeforeLast('/').takeIf { ShizukuEntries.allowed(it) }
        return FileNode(
            id = FileNodeId.shizuku(path),
            name = entry.name,
            displayName = entry.name,
            mimeType = null,
            size = entry.size,
            modifiedAt = entry.modifiedAt.coerceAtLeast(0),
            isDirectory = entry.isDirectory,
            isHidden = entry.name.startsWith("."),
            parentId = parent?.let(FileNodeId::shizuku),
            storageType = StorageType.INTERNAL_SHARED,
            access = AccessFlags.READ_ONLY,
            childCount = null,
            extras = null,
        )
    }
}
