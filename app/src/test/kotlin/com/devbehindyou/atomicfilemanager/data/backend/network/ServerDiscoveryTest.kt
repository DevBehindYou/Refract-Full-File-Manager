package com.devbehindyou.atomicfilemanager.data.backend.network

import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerDiscoveryTest {
    private val nas = DiscoveredServer("NAS", NetworkProtocol.SMB, "192.168.1.5", 445)

    @Test
    fun `each file protocol has a service type`() {
        val protocols = ServerDiscovery.SERVICE_TYPES.values.toSet()
        assertEquals(
            setOf(NetworkProtocol.SMB, NetworkProtocol.SFTP, NetworkProtocol.WEBDAV, NetworkProtocol.FTP),
            protocols,
        )
        // NsdManager takes types without the trailing dot; they all end in one here.
        ServerDiscovery.SERVICE_TYPES.keys.forEach { assertEquals('.', it.last()) }
    }

    @Test
    fun `one entry per protocol, host and port, newest name wins`() {
        val renamed = nas.copy(name = "Home NAS")
        val ssh = nas.copy(protocol = NetworkProtocol.SFTP, port = 22)
        val list = ServerDiscovery.merge(ServerDiscovery.merge(listOf(nas), ssh), renamed)
        assertEquals(2, list.size)
        assertEquals("Home NAS", list.single { it.protocol == NetworkProtocol.SMB }.name)
    }

    @Test
    fun `list is sorted by name`() {
        val b = nas.copy(name = "beta", host = "10.0.0.2")
        val a = nas.copy(name = "Alpha", host = "10.0.0.1")
        assertEquals(listOf("Alpha", "beta"), ServerDiscovery.merge(listOf(b), a).map { it.name })
    }

    @Test
    fun `paths and hosts come out in the form's shape`() {
        assertEquals("/", ServerDiscovery.pathFrom(null))
        assertEquals("/", ServerDiscovery.pathFrom("  "))
        assertEquals("/dav", ServerDiscovery.pathFrom("dav"))
        assertEquals("/dav/", ServerDiscovery.pathFrom("/dav/"))
        assertEquals("192.168.1.5", ServerDiscovery.hostFrom("/192.168.1.5"))
    }
}
