package com.devbehindyou.atomicfilemanager.data.backend.network

import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol

/** A file server announced on the local network (ALL_IN_ONE_PLAN.md 3.2). */
data class DiscoveredServer(
    val name: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int,
    /** From the announcement's `path` attribute when it has one (WebDAV, FTP). */
    val path: String = "/",
)

/** Which mDNS service types mean which protocol, and how found servers merge; unit-tested. */
object ServerDiscovery {
    /** Service types browsed, most specific first; `_sftp-ssh` beats plain `_ssh` for one host. */
    val SERVICE_TYPES: Map<String, NetworkProtocol> =
        linkedMapOf(
            "_smb._tcp." to NetworkProtocol.SMB,
            "_sftp-ssh._tcp." to NetworkProtocol.SFTP,
            "_ssh._tcp." to NetworkProtocol.SFTP,
            "_webdav._tcp." to NetworkProtocol.WEBDAV,
            "_ftp._tcp." to NetworkProtocol.FTP,
        )

    /**
     * Adds [found] to [known]: one entry per protocol, host and port, the newest name winning.
     * Sorted by name so the list doesn't jump as answers arrive.
     */
    fun merge(
        known: List<DiscoveredServer>,
        found: DiscoveredServer,
    ): List<DiscoveredServer> =
        (known.filterNot { it.protocol == found.protocol && it.host == found.host && it.port == found.port } + found)
            .sortedWith(compareBy({ it.name.lowercase() }, { it.protocol.ordinal }))

    /** The TXT `path` attribute as a path the add-server form accepts. */
    fun pathFrom(attribute: String?): String {
        val trimmed = attribute?.trim().orEmpty()
        return when {
            trimmed.isEmpty() -> "/"
            trimmed.startsWith("/") -> trimmed
            else -> "/$trimmed"
        }
    }

    /** InetAddress prints "/192.168.1.5"; the form wants the bare address. */
    fun hostFrom(address: String): String = address.trim().removePrefix("/")
}
