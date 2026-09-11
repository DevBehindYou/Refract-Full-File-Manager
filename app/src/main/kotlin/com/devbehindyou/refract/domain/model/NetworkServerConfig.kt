package com.devbehindyou.refract.domain.model

enum class NetworkProtocol(
    val displayName: String,
    val defaultPort: Int,
    val scheme: String,
) {
    FTP("FTP", 21, "ftp"),
    FTPS("FTPS (Explicit TLS)", 21, "ftps"),
    SFTP("SFTP (SSH)", 22, "sftp"),
    WEBDAV("WebDAV", 80, "webdav"),
    SMB("SMB / Windows Share", 445, "smb"),
}

data class NetworkServerConfig(
    val id: String,
    val name: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int = protocol.defaultPort,
    val username: String = "",
    val remotePath: String = "/",
    val anonymous: Boolean = false,
) {
    fun toRootNodeId(): FileNodeId {
        val cleanPath = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
        return when (protocol) {
            NetworkProtocol.FTP -> FileNodeId.ftp(id, cleanPath)
            NetworkProtocol.FTPS -> FileNodeId.ftps(id, cleanPath)
            NetworkProtocol.SFTP -> FileNodeId.sftp(id, cleanPath)
            NetworkProtocol.WEBDAV -> FileNodeId.webdav(id, cleanPath)
            NetworkProtocol.SMB -> FileNodeId.smb(id, cleanPath)
        }
    }
}
