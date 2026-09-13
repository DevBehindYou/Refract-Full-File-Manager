package com.devbehindyou.refract.data.backend.network

import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.NetworkProtocol
import com.devbehindyou.refract.domain.repository.BackendType
import com.devbehindyou.refract.domain.repository.InputStreamProvider
import com.devbehindyou.refract.domain.repository.OutputTarget
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.net.ssl.SSLSocketFactory

/**
 * Robust FTP and FTPS storage backend conforming to [StorageBackend].
 * Supports RFC 959 (FTP) and RFC 4217 (FTPS) over passive TCP connections.
 */
class FtpBackend(
    private val credentialsStore: NetworkCredentialsStore,
    override val type: BackendType = BackendType.FTP,
) : StorageBackend {
    override fun canHandle(id: FileNodeId): Boolean {
        return id.prefix == FileNodeId.Prefix.FTP || id.prefix == FileNodeId.Prefix.FTPS
    }

    private data class ParsedFtpId(val isFtps: Boolean, val serverId: String, val path: String)

    private fun parseId(id: FileNodeId): ParsedFtpId {
        val isFtps = id.prefix == FileNodeId.Prefix.FTPS
        val prefix = if (isFtps) FileNodeId.Prefix.FTPS.scheme else FileNodeId.Prefix.FTP.scheme
        val body = id.raw.removePrefix(prefix)
        val firstColon = body.indexOf(':')
        val serverId = if (firstColon >= 0) body.substring(0, firstColon) else body
        val rawPath = if (firstColon >= 0) body.substring(firstColon + 1) else "/"
        val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        return ParsedFtpId(isFtps, serverId, path)
    }

    private fun makeNodeId(
        parsed: ParsedFtpId,
        newPath: String,
    ): FileNodeId {
        val norm = if (newPath.startsWith("/")) newPath else "/$newPath"
        return if (parsed.isFtps) {
            FileNodeId.ftps(parsed.serverId, norm)
        } else {
            FileNodeId.ftp(parsed.serverId, norm)
        }
    }

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            if (parsed.path == "/" || parsed.path.isEmpty()) {
                val server = credentialsStore.getServerById(parsed.serverId)
                val name = server?.name ?: "FTP Server"
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

            val parentPath = getParentPath(parsed.path)
            val parentId = makeNodeId(parsed, parentPath)
            val name = getFileName(parsed.path)

            // Query parent directory listing to locate this entry
            val listResult = runCatching { listRemote(parsed, parentPath) }
            val items = listResult.getOrNull()
            if (items != null) {
                val match = items.firstOrNull { it.name == name }
                if (match != null) {
                    return@withContext FileResult.Success(match)
                }
            }

            // Fallback: create placeholder directory or file node
            FileResult.Success(
                NetworkNodeHelper.createNode(
                    id = id,
                    parentId = parentId,
                    name = name,
                    isDirectory = false,
                    isVirtual = false,
                ),
            )
        }

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
        flow {
            val parsed = parseId(id)
            val server = credentialsStore.getServerById(parsed.serverId)
            if (server == null) {
                emit(FileResult.Failure(FileError.StorageUnavailable("FTP")))
                return@flow
            }

            try {
                val entries = listRemote(parsed, parsed.path)
                emit(FileResult.Success(entries))
            } catch (e: Exception) {
                val error = mapException(e)
                emit(FileResult.Failure(error))
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            try {
                val client = connectAndLogin(parsed)
                val dataSocket = client.openPassiveDataSocket()
                client.sendCommand("TYPE I")
                val resp = client.sendCommand("RETR ${parsed.path}")
                if (!resp.startsWith("150") && !resp.startsWith("125")) {
                    client.close()
                    dataSocket.close()
                    return@withContext FileResult.Failure(FileError.FileNotFound(parsed.path))
                }
                FileResult.Success(
                    InputStreamProvider {
                        val rawIn = dataSocket.getInputStream()
                        object : InputStream() {
                            override fun read(): Int = rawIn.read()

                            override fun read(
                                b: ByteArray,
                                off: Int,
                                len: Int,
                            ): Int = rawIn.read(b, off, len)

                            override fun close() {
                                try {
                                    rawIn.close()
                                    dataSocket.close()
                                    client.readResponse()
                                    client.close()
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
            val targetNodeId = makeNodeId(parsed, targetPath)

            try {
                val client = connectAndLogin(parsed)
                val dataSocket = client.openPassiveDataSocket()
                client.sendCommand("TYPE I")
                val resp = client.sendCommand("STOR $targetPath")
                if (!resp.startsWith("150") && !resp.startsWith("125")) {
                    client.close()
                    dataSocket.close()
                    return@withContext FileResult.Failure(FileError.AccessDenied("STOR rejected"))
                }

                val dataOut = dataSocket.getOutputStream()
                var discarded = false

                val target =
                    object : OutputTarget {
                        override fun stream(): OutputStream = dataOut

                        override fun setLastModified(epochMillis: Long) {
                            // FTP does not support direct timestamp setting in standard RFC 959 without MFMT extension
                        }

                        override fun discard() {
                            discarded = true
                            try {
                                dataOut.close()
                                dataSocket.close()
                                client.close()
                                val cleanClient = connectAndLogin(parsed)
                                cleanClient.sendCommand("DELE $targetPath")
                                cleanClient.close()
                            } catch (_: Exception) {
                            }
                        }

                        override fun sync() {
                            dataOut.flush()
                        }

                        override suspend fun toNode(): FileResult<FileNode> {
                            if (discarded) {
                                return FileResult.Failure(FileError.OperationCancelled)
                            }
                            try {
                                dataOut.flush()
                                dataOut.close()
                                dataSocket.close()
                                client.readResponse()
                                client.close()
                            } catch (_: Exception) {
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
            val newPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
            val newNodeId = makeNodeId(parsed, newPath)

            try {
                val client = connectAndLogin(parsed)
                val resp = client.sendCommand("MKD $newPath")
                client.close()
                if (resp.startsWith("257")) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = parent,
                            name = name,
                            isDirectory = true,
                        ),
                    )
                } else if (resp.startsWith("550")) {
                    FileResult.Failure(FileError.FileAlreadyExists(name))
                } else {
                    FileResult.Failure(FileError.AccessDenied("MKD rejected"))
                }
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun delete(id: FileNodeId): FileResult<Unit> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            try {
                val client = connectAndLogin(parsed)
                var resp = client.sendCommand("DELE ${parsed.path}")
                if (!resp.startsWith("250")) {
                    resp = client.sendCommand("RMD ${parsed.path}")
                }
                client.close()
                if (resp.startsWith("250")) {
                    FileResult.Success(Unit)
                } else {
                    FileResult.Failure(FileError.AccessDenied("Delete failed"))
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
            val newNodeId = makeNodeId(parsed, newPath)

            try {
                val client = connectAndLogin(parsed)
                client.sendCommand("RNFR ${parsed.path}")
                val resp = client.sendCommand("RNTO $newPath")
                client.close()
                if (resp.startsWith("250")) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = makeNodeId(parsed, parentPath),
                            name = newName,
                            isDirectory = false,
                        ),
                    )
                } else {
                    FileResult.Failure(FileError.AccessDenied("Rename failed"))
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
            val newNodeId = makeNodeId(parsed, newPath)

            try {
                val client = connectAndLogin(parsed)
                client.sendCommand("RNFR ${parsed.path}")
                val resp = client.sendCommand("RNTO $newPath")
                client.close()
                if (resp.startsWith("250")) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = newParent,
                            name = fileName,
                            isDirectory = false,
                        ),
                    )
                } else {
                    FileResult.Failure(FileError.AccessDenied("Move failed"))
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
            try {
                val items = listRemote(parsed, parsed.path)
                items.any { it.name == name }
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun freeSpace(id: FileNodeId): Long = 0L

    private fun connectAndLogin(parsed: ParsedFtpId): FtpProtocolClient {
        val server =
            credentialsStore.getServerById(parsed.serverId)
                ?: error("Server configuration not found for id: ${parsed.serverId}")

        val client =
            FtpProtocolClient(
                host = server.host,
                port = server.port,
                isFtps = parsed.isFtps || server.protocol == NetworkProtocol.FTPS,
            )
        client.connect()

        val pass = credentialsStore.getPassword(server.id) ?: ""
        val username = if (server.anonymous || server.username.isBlank()) "anonymous" else server.username
        val userPass = if (server.anonymous) "refract@filemanager.local" else pass

        client.login(username, userPass)
        return client
    }

    private fun listRemote(
        parsed: ParsedFtpId,
        remotePath: String,
    ): List<FileNode> {
        val client = connectAndLogin(parsed)
        try {
            val dataSocket = client.openPassiveDataSocket()
            client.sendCommand("TYPE A")
            val resp = client.sendCommand("LIST $remotePath")
            if (!resp.startsWith("150") && !resp.startsWith("125")) {
                dataSocket.close()
                return emptyList()
            }

            val reader = BufferedReader(InputStreamReader(dataSocket.getInputStream(), StandardCharsets.UTF_8))
            val lines = reader.readLines()
            dataSocket.close()
            client.readResponse()

            val parentNodeId = makeNodeId(parsed, remotePath)
            return lines.mapNotNull { parseFtpListLine(it, parentNodeId, parsed) }
        } finally {
            client.close()
        }
    }

    private fun parseFtpListLine(
        line: String,
        parentId: FileNodeId,
        parsed: ParsedFtpId,
    ): FileNode? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // Unix-style format: drwxr-xr-x 1 owner group 4096 Jan 01 12:00 filename
        if (trimmed[0] in listOf('-', 'd', 'l')) {
            val parts = trimmed.split(Pattern.compile("\\s+"), 9)
            if (parts.size >= 9) {
                val isDir = parts[0].startsWith("d")
                val isLink = parts[0].startsWith("l")
                val size = parts[4].toLongOrNull() ?: 0L
                var name = parts[8]
                if (isLink && name.contains(" -> ")) {
                    name = name.substringBefore(" -> ")
                }
                if (name == "." || name == "..") return null

                val itemPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
                return NetworkNodeHelper.createNode(
                    id = makeNodeId(parsed, itemPath),
                    parentId = parentId,
                    name = name,
                    size = size,
                    isDirectory = isDir,
                )
            }
        }

        // MS-DOS style format: 05-12-20 03:45PM <DIR> foldername
        val dosParts = trimmed.split(Pattern.compile("\\s+"), 4)
        if (dosParts.size >= 4) {
            val isDir = dosParts[2].equals("<DIR>", ignoreCase = true)
            val size = if (isDir) 0L else dosParts[2].toLongOrNull() ?: 0L
            val name = dosParts[3]
            if (name == "." || name == "..") return null

            val itemPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
            return NetworkNodeHelper.createNode(
                id = makeNodeId(parsed, itemPath),
                parentId = parentId,
                name = name,
                size = size,
                isDirectory = isDir,
            )
        }

        return null
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
            msg.contains("login", ignoreCase = true) || msg.contains("530", ignoreCase = true) ->
                FileError.AccessDenied("Authentication failed")
            msg.contains("timeout", ignoreCase = true) || msg.contains("refused", ignoreCase = true) ||
                msg.contains("unreachable", ignoreCase = true) ->
                FileError.StorageUnavailable("FTP Server Unreachable")
            msg.contains("550", ignoreCase = true) ->
                FileError.FileNotFound(msg)
            else -> FileError.StorageUnavailable("FTP Error: $msg")
        }
    }
}

/**
 * Lightweight RFC 959 / 4217 compliant FTP control client.
 */
internal class FtpProtocolClient(
    private val host: String,
    private val port: Int,
    private val isFtps: Boolean,
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    fun connect() {
        val sock = Socket()
        sock.connect(InetSocketAddress(host, port), 10_000)
        sock.soTimeout = 15_000
        socket = sock

        reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8))

        readResponse()

        if (isFtps) {
            val authResp = sendCommand("AUTH TLS")
            if (!authResp.startsWith("234")) {
                sock.close()
                error("FTPS server rejected TLS: $authResp")
            }
            if (authResp.startsWith("234")) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSock = sslFactory.createSocket(sock, host, port, true)
                socket = sslSock
                reader = BufferedReader(InputStreamReader(sslSock.getInputStream(), StandardCharsets.UTF_8))
                writer = BufferedWriter(OutputStreamWriter(sslSock.getOutputStream(), StandardCharsets.UTF_8))
                sendCommand("PBSZ 0")
                sendCommand("PROT P")
            }
        }
    }

    fun login(
        user: String,
        pass: String,
    ) {
        val userResp = sendCommand("USER $user")
        if (userResp.startsWith("331")) {
            val passResp = sendCommand("PASS $pass")
            if (!passResp.startsWith("230")) {
                error("FTP login rejected: $passResp")
            }
        } else if (!userResp.startsWith("230")) {
            error("FTP user rejected: $userResp")
        }
    }

    fun sendCommand(cmd: String): String {
        val w = writer ?: error("Not connected")
        w.write(cmd + "\r\n")
        w.flush()
        return readResponse()
    }

    fun readResponse(): String {
        val r = reader ?: error("Not connected")
        val initialLine = r.readLine() ?: error("Connection closed by FTP server")
        val response = StringBuilder(initialLine)

        if (initialLine.length >= 4 && initialLine[3] == '-') {
            val code = initialLine.substring(0, 3)
            var nextLine = r.readLine()
            while (nextLine != null) {
                response.append("\n").append(nextLine)
                if (nextLine.startsWith("$code ")) break
                nextLine = r.readLine()
            }
        }
        return response.toString()
    }

    fun openPassiveDataSocket(): Socket {
        val pasvResp = sendCommand("PASV")
        val pattern = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)")
        val matcher = pattern.matcher(pasvResp)
        if (!matcher.find()) {
            error("Invalid PASV response: $pasvResp")
        }
        val dataHost = "${matcher.group(1)}.${matcher.group(2)}.${matcher.group(3)}.${matcher.group(4)}"
        val dataPort = (matcher.group(5)!!.toInt() shl 8) + matcher.group(6)!!.toInt()

        val dataSocket = Socket()
        dataSocket.connect(InetSocketAddress(dataHost, dataPort), 10_000)
        dataSocket.soTimeout = 15_000
        return dataSocket
    }

    fun close() {
        try {
            sendCommand("QUIT")
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }
}
