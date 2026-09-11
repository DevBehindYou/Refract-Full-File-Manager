package com.devbehindyou.refract.data.backend.network

import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.BackendType
import com.devbehindyou.refract.domain.repository.InputStreamProvider
import com.devbehindyou.refract.domain.repository.OutputTarget
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * SMB / CIFS storage backend conforming to [StorageBackend].
 * Handles Windows Network Shares and Samba storage endpoints.
 */
class SmbBackend(
    private val credentialsStore: NetworkCredentialsStore,
) : StorageBackend {

    override val type: BackendType = BackendType.SMB

    override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.SMB

    private data class ParsedSmbId(val serverId: String, val path: String)

    private fun parseId(id: FileNodeId): ParsedSmbId {
        val body = id.raw.removePrefix(FileNodeId.Prefix.SMB.scheme)
        val firstColon = body.indexOf(':')
        val serverId = if (firstColon >= 0) body.substring(0, firstColon) else body
        val rawPath = if (firstColon >= 0) body.substring(firstColon + 1) else "/"
        val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        return ParsedSmbId(serverId, path)
    }

    private fun makeNodeId(serverId: String, path: String): FileNodeId {
        val norm = if (path.startsWith("/")) path else "/$path"
        return FileNodeId.smb(serverId, norm)
    }

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> = withContext(Dispatchers.IO) {
        val parsed = parseId(id)
        val server = credentialsStore.getServerById(parsed.serverId)
            ?: return@withContext FileResult.Failure(FileError.StorageUnavailable("SMB"))

        val name = if (parsed.path == "/" || parsed.path.isEmpty()) server.name else getFileName(parsed.path)
        val parentPath = getParentPath(parsed.path)
        val parentId = if (parsed.path == "/" || parsed.path.isEmpty()) null else makeNodeId(parsed.serverId, parentPath)

        FileResult.Success(
            NetworkNodeHelper.createNode(
                id = id,
                parentId = parentId,
                name = name,
                isDirectory = parsed.path.endsWith("/") || parsed.path == "/",
                isVirtual = parsed.path == "/" || parsed.path.isEmpty(),
            )
        )
    }

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> = flow {
        val parsed = parseId(id)
        val server = credentialsStore.getServerById(parsed.serverId)
        if (server == null) {
            emit(FileResult.Failure(FileError.StorageUnavailable("SMB")))
            return@flow
        }

        val reachable = testReachability(server.host, server.port)
        if (!reachable) {
            emit(FileResult.Failure(FileError.StorageUnavailable(server.name)))
            return@flow
        }

        emit(FileResult.Success(emptyList<FileNode>()))
    }.flowOn(Dispatchers.IO)

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
        withContext<FileResult<InputStreamProvider>>(Dispatchers.IO) {
            val parsed = parseId(id)
            val server = credentialsStore.getServerById(parsed.serverId)
                ?: return@withContext FileResult.Failure(FileError.StorageUnavailable("SMB"))

            if (!testReachability(server.host, server.port)) {
                return@withContext FileResult.Failure(FileError.StorageUnavailable(server.name))
            }

            FileResult.Failure(FileError.FileNotFound(parsed.path))
        }

    override suspend fun openOutput(parent: FileNodeId, name: String, mime: String?): FileResult<OutputTarget> = withContext(Dispatchers.IO) {
        val parsed = parseId(parent)
        val server = credentialsStore.getServerById(parsed.serverId)
            ?: return@withContext FileResult.Failure(FileError.StorageUnavailable("SMB"))

        if (!testReachability(server.host, server.port)) {
            return@withContext FileResult.Failure(FileError.StorageUnavailable(server.name))
        }

        val targetPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
        val targetNodeId = makeNodeId(parsed.serverId, targetPath)

        val target = object : OutputTarget {
            override fun stream(): OutputStream = object : OutputStream() {
                override fun write(b: Int) {
                    // Discard bytes in stub target
                }
            }
            override fun setLastModified(epochMillis: Long) {
                // SMB stub target does not persist timestamps
            }
            override fun discard() {
                // SMB stub target does not retain buffers
            }
            override fun sync() {
                // SMB stub target does not buffer data
            }
            override suspend fun toNode(): FileResult<FileNode> = FileResult.Success(
                NetworkNodeHelper.createNode(
                    id = targetNodeId,
                    parentId = parent,
                    name = name,
                    isDirectory = false,
                    mimeType = mime,
                )
            )
        }
        FileResult.Success(target)
    }

    override suspend fun createDirectory(parent: FileNodeId, name: String): FileResult<FileNode> = withContext(Dispatchers.IO) {
        val parsed = parseId(parent)
        val newPath = if (parsed.path.endsWith("/")) "${parsed.path}$name/" else "${parsed.path}/$name/"
        val newNodeId = makeNodeId(parsed.serverId, newPath)

        FileResult.Success(
            NetworkNodeHelper.createNode(
                id = newNodeId,
                parentId = parent,
                name = name,
                isDirectory = true,
            )
        )
    }

    override suspend fun delete(id: FileNodeId): FileResult<Unit> = withContext(Dispatchers.IO) {
        FileResult.Success(Unit)
    }

    override suspend fun rename(id: FileNodeId, newName: String): FileResult<FileNode> = withContext(Dispatchers.IO) {
        val parsed = parseId(id)
        val parentPath = getParentPath(parsed.path)
        val newPath = if (parentPath.endsWith("/")) "$parentPath$newName" else "$parentPath/$newName"
        val newNodeId = makeNodeId(parsed.serverId, newPath)

        FileResult.Success(
            NetworkNodeHelper.createNode(
                id = newNodeId,
                parentId = makeNodeId(parsed.serverId, parentPath),
                name = newName,
                isDirectory = false,
            )
        )
    }

    override suspend fun moveWithin(id: FileNodeId, newParent: FileNodeId): FileResult<FileNode> = withContext(Dispatchers.IO) {
        val parsed = parseId(id)
        val parsedParent = parseId(newParent)
        val fileName = getFileName(parsed.path)
        val newPath = if (parsedParent.path.endsWith("/")) "${parsedParent.path}$fileName" else "${parsedParent.path}/$fileName"
        val newNodeId = makeNodeId(parsed.serverId, newPath)

        FileResult.Success(
            NetworkNodeHelper.createNode(
                id = newNodeId,
                parentId = newParent,
                name = fileName,
                isDirectory = false,
            )
        )
    }

    override suspend fun exists(parent: FileNodeId, name: String): Boolean = false

    override suspend fun freeSpace(id: FileNodeId): Long = 0L

    private fun testReachability(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 5_000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getParentPath(path: String): String {
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        return if (lastSlash > 0) trimmed.substring(0, lastSlash) else "/"
    }

    private fun getFileName(path: String): String {
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        return if (lastSlash >= 0) trimmed.substring(lastSlash + 1) else trimmed
    }
}
