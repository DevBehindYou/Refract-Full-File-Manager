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
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * High-performance WebDAV storage backend conforming to [StorageBackend].
 * Communicates with Nextcloud, ownCloud, Apache WebDAV, and standard HTTP/HTTPS DAV shares.
 */
class WebDavBackend(
    private val credentialsStore: NetworkCredentialsStore,
) : StorageBackend {
    override val type: BackendType = BackendType.WEBDAV

    override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.WEBDAV

    private data class ParsedWebDavId(val serverId: String, val path: String)

    private fun parseId(id: FileNodeId): ParsedWebDavId {
        val body = id.raw.removePrefix(FileNodeId.Prefix.WEBDAV.scheme)
        val firstColon = body.indexOf(':')
        val serverId = if (firstColon >= 0) body.substring(0, firstColon) else body
        val rawPath = if (firstColon >= 0) body.substring(firstColon + 1) else "/"
        val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        return ParsedWebDavId(serverId, path)
    }

    private fun makeNodeId(
        serverId: String,
        path: String,
    ): FileNodeId {
        val norm = if (path.startsWith("/")) path else "/$path"
        return FileNodeId.webdav(serverId, norm)
    }

    private fun buildUrl(
        serverId: String,
        path: String,
    ): URL {
        val server =
            credentialsStore.getServerById(serverId)
                ?: error("Server configuration not found for id: $serverId")
        val protocol = if (server.port == 443 || server.host.startsWith("https://")) "https" else "http"
        val cleanHost = server.host.removePrefix("http://").removePrefix("https://").trimEnd('/')
        val portPart =
            if ((protocol == "http" && server.port == 80) || (protocol == "https" && server.port == 443)) {
                ""
            } else {
                ":${server.port}"
            }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return URL("$protocol://$cleanHost$portPart$cleanPath")
    }

    private fun openConnection(
        url: URL,
        serverId: String,
        method: String,
    ): HttpURLConnection {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000

        val server = credentialsStore.getServerById(serverId)
        if (server != null && !server.anonymous && server.username.isNotBlank()) {
            val pass = credentialsStore.getPassword(serverId) ?: ""
            val auth = "${server.username}:$pass"
            val encodedAuth = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $encodedAuth")
        }
        return conn
    }

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            if (parsed.path == "/" || parsed.path.isEmpty()) {
                val server = credentialsStore.getServerById(parsed.serverId)
                val name = server?.name ?: "WebDAV Server"
                return@withContext FileResult.Success(
                    NetworkNodeHelper.createNode(
                        id = id,
                        parentId = null,
                        name = name,
                        isDirectory = true,
                        isVirtual = true,
                    ),
                )
            }

            try {
                val url = buildUrl(parsed.serverId, parsed.path)
                val conn = openConnection(url, parsed.serverId, "PROPFIND")
                conn.setRequestProperty("Depth", "0")
                conn.connect()

                val responseCode = conn.responseCode
                if (responseCode == 404) {
                    return@withContext FileResult.Failure(FileError.FileNotFound(parsed.path))
                } else if (responseCode == 401 || responseCode == 403) {
                    return@withContext FileResult.Failure(FileError.AccessDenied("WebDAV unauthorized"))
                }

                val xml = conn.inputStream.bufferedReader().readText()
                val parsedNodes = parseMultiStatusXml(xml, parsed.serverId)
                val node =
                    parsedNodes.firstOrNull()
                        ?: NetworkNodeHelper.createNode(
                            id = id,
                            parentId = makeNodeId(parsed.serverId, getParentPath(parsed.path)),
                            name = getFileName(parsed.path),
                            isDirectory = false,
                        )
                FileResult.Success(node)
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
        flow {
            val parsed = parseId(id)
            val server = credentialsStore.getServerById(parsed.serverId)
            if (server == null) {
                emit(FileResult.Failure(FileError.StorageUnavailable("WebDAV Server")))
                return@flow
            }

            try {
                val url = buildUrl(parsed.serverId, parsed.path)
                val conn = openConnection(url, parsed.serverId, "PROPFIND")
                conn.setRequestProperty("Depth", "1")
                conn.connect()

                val responseCode = conn.responseCode
                if (responseCode == 401 || responseCode == 403) {
                    emit(FileResult.Failure(FileError.AccessDenied("WebDAV unauthorized")))
                    return@flow
                }
                if (responseCode !in 200..299) {
                    emit(FileResult.Failure(FileError.StorageUnavailable("WebDAV response: $responseCode")))
                    return@flow
                }

                val xml = conn.inputStream.bufferedReader().readText()
                val nodes = parseMultiStatusXml(xml, parsed.serverId)

                val normCurrent = parsed.path.trimEnd('/')
                val children =
                    nodes.filter { node ->
                        val nodePath = parseId(node.id).path.trimEnd('/')
                        nodePath != normCurrent && nodePath.isNotEmpty()
                    }
                emit(FileResult.Success(children))
            } catch (e: Exception) {
                emit(FileResult.Failure(mapException(e)))
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            try {
                val url = buildUrl(parsed.serverId, parsed.path)
                val conn = openConnection(url, parsed.serverId, "GET")
                conn.connect()

                val responseCode = conn.responseCode
                if (responseCode == 404) {
                    return@withContext FileResult.Failure(FileError.FileNotFound(parsed.path))
                } else if (responseCode == 401 || responseCode == 403) {
                    return@withContext FileResult.Failure(FileError.AccessDenied("WebDAV unauthorized"))
                } else if (responseCode !in 200..299) {
                    return@withContext FileResult.Failure(
                        FileError.StorageUnavailable("WebDAV response: $responseCode"),
                    )
                }

                val rawStream = conn.inputStream
                FileResult.Success(
                    InputStreamProvider {
                        object : InputStream() {
                            override fun read(): Int = rawStream.read()

                            override fun read(
                                b: ByteArray,
                                off: Int,
                                len: Int,
                            ): Int = rawStream.read(b, off, len)

                            override fun close() {
                                try {
                                    rawStream.close()
                                    conn.disconnect()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(parent)
            val targetPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
            val targetNodeId = makeNodeId(parsed.serverId, targetPath)

            try {
                val url = buildUrl(parsed.serverId, targetPath)
                val conn = openConnection(url, parsed.serverId, "PUT")
                conn.doOutput = true
                conn.setChunkedStreamingMode(64 * 1024)
                if (mime != null) conn.setRequestProperty("Content-Type", mime)
                conn.connect()

                val outStream = conn.outputStream
                var discarded = false

                val target =
                    object : OutputTarget {
                        override fun stream(): OutputStream = outStream

                        override fun setLastModified(epochMillis: Long) {
                            // WebDAV PUT sets server-side mtime; client override is not universally supported
                        }

                        override fun discard() {
                            discarded = true
                            try {
                                outStream.close()
                                conn.disconnect()
                                val delUrl = buildUrl(parsed.serverId, targetPath)
                                val delConn = openConnection(delUrl, parsed.serverId, "DELETE")
                                delConn.connect()
                                delConn.responseCode
                                delConn.disconnect()
                            } catch (_: Exception) {
                            }
                        }

                        override fun sync() {
                            outStream.flush()
                        }

                        override suspend fun toNode(): FileResult<FileNode> {
                            if (discarded) return FileResult.Failure(FileError.OperationCancelled)
                            try {
                                outStream.flush()
                                outStream.close()
                                val code = conn.responseCode
                                conn.disconnect()
                                if (code !in 200..299 && code != 201 && code != 204) {
                                    return FileResult.Failure(FileError.AccessDenied("PUT rejected ($code)"))
                                }
                            } catch (e: Exception) {
                                return FileResult.Failure(mapException(e))
                            }

                            return FileResult.Success(
                                NetworkNodeHelper.createNode(
                                    id = targetNodeId,
                                    parentId = parent,
                                    name = name,
                                    size = 0L,
                                    modifiedAt = System.currentTimeMillis(),
                                    isDirectory = false,
                                    mimeType = mime,
                                ),
                            )
                        }
                    }
                FileResult.Success(target)
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(parent)
            val newPath = if (parsed.path.endsWith("/")) "${parsed.path}$name/" else "${parsed.path}/$name/"
            val newNodeId = makeNodeId(parsed.serverId, newPath)

            try {
                val url = buildUrl(parsed.serverId, newPath)
                val conn = openConnection(url, parsed.serverId, "MKCOL")
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()

                if (code == 201) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = parent,
                            name = name,
                            isDirectory = true,
                        ),
                    )
                } else if (code == 405) {
                    FileResult.Failure(FileError.FileAlreadyExists(name))
                } else {
                    FileResult.Failure(FileError.AccessDenied("MKCOL failed ($code)"))
                }
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun delete(id: FileNodeId): FileResult<Unit> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            try {
                val url = buildUrl(parsed.serverId, parsed.path)
                val conn = openConnection(url, parsed.serverId, "DELETE")
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()

                if (code in 200..299 || code == 204) {
                    FileResult.Success(Unit)
                } else {
                    FileResult.Failure(FileError.AccessDenied("DELETE failed ($code)"))
                }
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun rename(
        id: FileNodeId,
        newName: String,
    ): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            val parentPath = getParentPath(parsed.path)
            val newPath = if (parentPath.endsWith("/")) "$parentPath$newName" else "$parentPath/$newName"
            val newNodeId = makeNodeId(parsed.serverId, newPath)

            try {
                val url = buildUrl(parsed.serverId, parsed.path)
                val destUrl = buildUrl(parsed.serverId, newPath)
                val conn = openConnection(url, parsed.serverId, "MOVE")
                conn.setRequestProperty("Destination", destUrl.toString())
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()

                if (code in 200..299 || code == 201 || code == 204) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = makeNodeId(parsed.serverId, parentPath),
                            name = newName,
                            isDirectory = false,
                        ),
                    )
                } else {
                    FileResult.Failure(FileError.AccessDenied("MOVE failed ($code)"))
                }
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun moveWithin(
        id: FileNodeId,
        newParent: FileNodeId,
    ): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            val parsedParent = parseId(newParent)
            val fileName = getFileName(parsed.path)
            val newPath =
                if (parsedParent.path.endsWith(
                        "/",
                    )
                ) {
                    "${parsedParent.path}$fileName"
                } else {
                    "${parsedParent.path}/$fileName"
                }
            val newNodeId = makeNodeId(parsed.serverId, newPath)

            try {
                val url = buildUrl(parsed.serverId, parsed.path)
                val destUrl = buildUrl(parsed.serverId, newPath)
                val conn = openConnection(url, parsed.serverId, "MOVE")
                conn.setRequestProperty("Destination", destUrl.toString())
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()

                if (code in 200..299 || code == 201 || code == 204) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = newParent,
                            name = fileName,
                            isDirectory = false,
                        ),
                    )
                } else {
                    FileResult.Failure(FileError.AccessDenied("MOVE failed ($code)"))
                }
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun exists(
        parent: FileNodeId,
        name: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val parsed = parseId(parent)
            val targetPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
            try {
                val url = buildUrl(parsed.serverId, targetPath)
                val conn = openConnection(url, parsed.serverId, "PROPFIND")
                conn.setRequestProperty("Depth", "0")
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun freeSpace(id: FileNodeId): Long = 0L

    private fun parseMultiStatusXml(
        xml: String,
        serverId: String,
    ): List<FileNode> {
        val nodes = mutableListOf<FileNode>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))
            val responses = doc.getElementsByTagNameNS("*", "response")

            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

            for (i in 0 until responses.length) {
                val resp = responses.item(i) as? Element
                val hrefList = resp?.getElementsByTagNameNS("*", "href")
                if (resp == null || hrefList == null || hrefList.length == 0) continue
                val rawHref = hrefList.item(0).textContent.trim()
                val hrefPath = URL(if (rawHref.startsWith("http")) rawHref else "http://dummy$rawHref").path

                val isDir = resp.getElementsByTagNameNS("*", "collection").length > 0
                val lengthElements = resp.getElementsByTagNameNS("*", "getcontentlength")
                val size =
                    if (lengthElements.length > 0) {
                        lengthElements.item(0).textContent.toLongOrNull() ?: 0L
                    } else {
                        0L
                    }

                val dateElements = resp.getElementsByTagNameNS("*", "getlastmodified")
                val modified =
                    if (dateElements.length > 0) {
                        runCatching { dateFormat.parse(dateElements.item(0).textContent)?.time }.getOrNull()
                            ?: System.currentTimeMillis()
                    } else {
                        System.currentTimeMillis()
                    }

                val typeElements = resp.getElementsByTagNameNS("*", "getcontenttype")
                val mime = if (typeElements.length > 0) typeElements.item(0).textContent.trim() else null

                val cleanPath = hrefPath.trimEnd('/')
                val name = getFileName(cleanPath)
                if (name.isNotEmpty()) {
                    nodes.add(
                        NetworkNodeHelper.createNode(
                            id = makeNodeId(serverId, hrefPath),
                            parentId = makeNodeId(serverId, getParentPath(cleanPath)),
                            name = name,
                            size = size,
                            modifiedAt = modified,
                            isDirectory = isDir,
                            mimeType = mime,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return nodes
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

    private fun mapException(e: Throwable): FileError {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("401", ignoreCase = true) || msg.contains("403", ignoreCase = true) ->
                FileError.AccessDenied("WebDAV authentication failed")
            msg.contains("404", ignoreCase = true) ->
                FileError.FileNotFound(msg)
            else -> FileError.StorageUnavailable("WebDAV error: $msg")
        }
    }
}
