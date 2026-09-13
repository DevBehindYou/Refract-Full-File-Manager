package com.devbehindyou.refract.domain.testing

import com.devbehindyou.refract.domain.model.AccessFlags
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.StorageType
import com.devbehindyou.refract.domain.model.getOrElse
import com.devbehindyou.refract.domain.repository.BackendType
import com.devbehindyou.refract.domain.repository.InputStreamProvider
import com.devbehindyou.refract.domain.repository.OutputTarget
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * A fake [StorageBackend] over an in-memory map — `testing/TEST_STRATEGY.md` §3: "Fakes, not
 * mocks, for StorageBackend." Test-only: lives under `src/test/`, never shipped, never wired
 * into `StorageBackendSelector`'s real backend map.
 *
 * Ids are opaque and identity-stable (`file:/mem/<counter>`), *not* path-derived. A real
 * `FileSystemBackend`'s ids do encode a path (per the `file:` example in
 * `architecture/STORAGE_ARCHITECTURE.md`), but doing that here would mean a directory rename
 * has to cascade a path change to every descendant's id — real complexity this fake doesn't
 * need to take on to be behaviourally correct for [StorageBackendContractTest]. The `file:`
 * prefix is reused rather than inventing a fourth prefix, since [FileNodeId] documents exactly
 * three (Phase 2 AC2).
 */
class InMemoryBackend(rootLabel: String = "root") : StorageBackend {
    // Arbitrary — this fake is constructed and used directly by tests, never selected through
    // StorageBackendSelector's BackendType-keyed map, so the value carries no real meaning.
    override val type: BackendType = BackendType.FILE

    private val nodes = mutableMapOf<FileNodeId, FileNode>()
    private val childIds = mutableMapOf<FileNodeId, MutableList<FileNodeId>>()
    private val fileContents = mutableMapOf<FileNodeId, ByteArray>()
    private var nextId = 0
    private var clockMillis = 1_700_000_000_000L

    val rootId: FileNodeId = newId()

    init {
        val root =
            FileNode(
                id = rootId,
                name = rootLabel,
                displayName = rootLabel,
                mimeType = null,
                size = -1,
                modifiedAt = 0L,
                isDirectory = true,
                isHidden = false,
                parentId = null,
                storageType = StorageType.VIRTUAL,
                access = AccessFlags.FULL,
                childCount = 0,
                extras = null,
            )
        nodes[rootId] = root
        childIds[rootId] = mutableListOf()
    }

    private fun newId(): FileNodeId = FileNodeId.file("/mem/${nextId++}")

    private fun tick(): Long = ++clockMillis

    private fun setChildCount(id: FileNodeId) {
        val node = nodes[id] ?: return
        nodes[id] = node.copy(childCount = childIds[id]?.size ?: node.childCount)
    }

    override fun canHandle(id: FileNodeId): Boolean = id.raw.startsWith("file:/mem/")

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> =
        nodes[id]?.let { FileResult.Success(it) } ?: FileResult.Failure(FileError.FileNotFound(id.raw))

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
        flow {
            val kids = childIds[id]
            if (kids == null) {
                emit(FileResult.Failure(FileError.FileNotFound(id.raw)))
                return@flow
            }
            emit(FileResult.Success(kids.mapNotNull { nodes[it] }))
        }

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
        val node = nodes[id] ?: return FileResult.Failure(FileError.FileNotFound(id.raw))
        if (!node.access.readable) return FileResult.Failure(FileError.AccessDenied(node.name))
        val bytes = fileContents[id] ?: ByteArray(0)
        return FileResult.Success(InputStreamProvider { ByteArrayInputStream(bytes) })
    }

    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> {
        val parentNode = nodes[parent] ?: return FileResult.Failure(FileError.FileNotFound(parent.raw))
        if (!parentNode.access.writable) return FileResult.Failure(FileError.AccessDenied(name))
        val existingId = childIds.getValue(parent).firstOrNull { nodes[it]?.name == name }
        val targetId = existingId ?: newId()
        return FileResult.Success(
            InMemoryOutputTarget(
                backend = this,
                parent = parent,
                id = targetId,
                name = name,
                mime = mime,
                isNew = existingId == null,
            ),
        )
    }

    override suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode> {
        val parentNode = nodes[parent] ?: return FileResult.Failure(FileError.FileNotFound(parent.raw))
        if (!parentNode.access.writable) return FileResult.Failure(FileError.AccessDenied(name))
        if (childIds.getValue(parent).any { nodes[it]?.name == name }) {
            return FileResult.Failure(FileError.FileAlreadyExists(name))
        }
        val id = newId()
        val node =
            FileNode(
                id = id,
                name = name,
                displayName = name,
                mimeType = null,
                size = -1,
                modifiedAt = tick(),
                isDirectory = true,
                isHidden = name.startsWith("."),
                parentId = parent,
                storageType = parentNode.storageType,
                access = AccessFlags.FULL,
                childCount = 0,
                extras = null,
            )
        nodes[id] = node
        childIds[id] = mutableListOf()
        childIds.getValue(parent).add(id)
        setChildCount(parent)
        return FileResult.Success(node)
    }

    override suspend fun delete(id: FileNodeId): FileResult<Unit> {
        val node = nodes[id] ?: return FileResult.Failure(FileError.FileNotFound(id.raw))
        if (!node.access.deletable) return FileResult.Failure(FileError.AccessDenied(node.name))
        childIds[id]?.toList()?.forEach { child -> delete(child) }
        node.parentId?.let { parent ->
            childIds[parent]?.remove(id)
            setChildCount(parent)
        }
        nodes.remove(id)
        childIds.remove(id)
        fileContents.remove(id)
        return FileResult.Success(Unit)
    }

    override suspend fun rename(
        id: FileNodeId,
        newName: String,
    ): FileResult<FileNode> {
        val node = nodes[id] ?: return FileResult.Failure(FileError.FileNotFound(id.raw))
        if (!node.access.renamable) return FileResult.Failure(FileError.AccessDenied(node.name))
        val siblings = node.parentId?.let { childIds[it] }.orEmpty()
        if (siblings.any { it != id && nodes[it]?.name == newName }) {
            return FileResult.Failure(FileError.FileAlreadyExists(newName))
        }
        val updated = node.copy(name = newName, displayName = newName, modifiedAt = tick())
        nodes[id] = updated
        return FileResult.Success(updated)
    }

    override suspend fun moveWithin(
        id: FileNodeId,
        newParent: FileNodeId,
    ): FileResult<FileNode> {
        val node = nodes[id] ?: return FileResult.Failure(FileError.FileNotFound(id.raw))
        val newParentNode =
            nodes[newParent]
                ?: return FileResult.Failure(FileError.FileNotFound(newParent.raw))
        if (!newParentNode.access.writable) return FileResult.Failure(FileError.AccessDenied(node.name))
        if (childIds.getValue(newParent).any { nodes[it]?.name == node.name }) {
            return FileResult.Failure(FileError.FileAlreadyExists(node.name))
        }
        node.parentId?.let { oldParent ->
            childIds[oldParent]?.remove(id)
            setChildCount(oldParent)
        }
        childIds.getValue(newParent).add(id)
        val updated = node.copy(parentId = newParent, modifiedAt = tick())
        nodes[id] = updated
        setChildCount(newParent)
        return FileResult.Success(updated)
    }

    override suspend fun exists(
        parent: FileNodeId,
        name: String,
    ): Boolean = childIds[parent]?.any { nodes[it]?.name == name } == true

    override suspend fun freeSpace(id: FileNodeId): Long = Long.MAX_VALUE / 2

    /** Test-only helper, not part of [StorageBackend] — lets tests seed a small file directly. */
    suspend fun putFile(
        parent: FileNodeId,
        name: String,
        bytes: ByteArray,
        mime: String? = null,
    ): FileNodeId {
        val target =
            openOutput(parent, name, mime).getOrElse {
                error("putFile setup failed: $it")
            } as InMemoryOutputTarget
        target.stream().use { it.write(bytes) }
        target.commitForTest()
        return target.id
    }

    internal fun commitOutput(
        parent: FileNodeId,
        id: FileNodeId,
        name: String,
        mime: String?,
        bytes: ByteArray,
        lastModified: Long,
        isNew: Boolean,
    ) {
        fileContents[id] = bytes
        val existing = nodes[id]
        nodes[id] =
            FileNode(
                id = id,
                name = name,
                displayName = name,
                mimeType = mime,
                size = bytes.size.toLong(),
                modifiedAt = if (lastModified != 0L) lastModified else tick(),
                isDirectory = false,
                isHidden = name.startsWith("."),
                parentId = parent,
                storageType = existing?.storageType ?: nodes[parent]?.storageType ?: StorageType.VIRTUAL,
                access = existing?.access ?: AccessFlags.FULL,
                childCount = null,
                extras = null,
            )
        if (isNew) {
            childIds.getValue(parent).add(id)
            setChildCount(parent)
        }
    }

    internal fun removeCommitted(
        parent: FileNodeId,
        id: FileNodeId,
        isNew: Boolean,
    ) {
        if (isNew) {
            nodes.remove(id)
            childIds[parent]?.remove(id)
            setChildCount(parent)
            fileContents.remove(id)
        }
    }
}

private class InMemoryOutputTarget(
    private val backend: InMemoryBackend,
    private val parent: FileNodeId,
    val id: FileNodeId,
    private val name: String,
    private val mime: String?,
    private val isNew: Boolean,
) : OutputTarget {
    private val buffer = ByteArrayOutputStream()
    private var lastModified: Long = 0L
    private var discarded = false
    private var committed = false

    override fun stream(): OutputStream =
        object : OutputStream() {
            override fun write(b: Int) = buffer.write(b)

            override fun write(
                b: ByteArray,
                off: Int,
                len: Int,
            ) = buffer.write(b, off, len)

            override fun close() {
                if (!discarded) commit()
            }
        }

    override fun setLastModified(epochMillis: Long) {
        lastModified = epochMillis
    }

    override fun discard() {
        discarded = true
        if (committed) {
            backend.removeCommitted(parent, id, isNew)
        }
    }

    override fun sync() {
        // No-op: there is no fsync equivalent for an in-memory byte array.
    }

    override suspend fun toNode(): FileResult<FileNode> {
        if (discarded) return FileResult.Failure(FileError.OperationCancelled)
        commit()
        return backend.getNode(id)
    }

    fun commitForTest() = commit()

    private fun commit() {
        if (committed || discarded) return
        committed = true
        backend.commitOutput(
            parent = parent,
            id = id,
            name = name,
            mime = mime,
            bytes = buffer.toByteArray(),
            lastModified = lastModified,
            isNew = isNew,
        )
    }
}
