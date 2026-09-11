package com.devbehindyou.refract.data.backend.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.NetworkProtocol
import com.devbehindyou.refract.domain.model.NetworkServerConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkStorageTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `NetworkServerConfig creates correct root FileNodeId for each protocol`() {
        val ftpConfig = NetworkServerConfig(
            id = "ftp-1",
            name = "FTP Server",
            protocol = NetworkProtocol.FTP,
            host = "ftp.example.com",
            port = 21,
            remotePath = "/files",
        )
        assertEquals(FileNodeId.ftp("ftp-1", "/files"), ftpConfig.toRootNodeId())

        val ftpsConfig = NetworkServerConfig(
            id = "ftps-1",
            name = "FTPS Server",
            protocol = NetworkProtocol.FTPS,
            host = "ftps.example.com",
            port = 21,
            remotePath = "/",
        )
        assertEquals(FileNodeId.ftps("ftps-1", "/"), ftpsConfig.toRootNodeId())

        val sftpConfig = NetworkServerConfig(
            id = "sftp-1",
            name = "SSH Server",
            protocol = NetworkProtocol.SFTP,
            host = "ssh.example.com",
            port = 22,
            remotePath = "/home/user",
        )
        assertEquals(FileNodeId.sftp("sftp-1", "/home/user"), sftpConfig.toRootNodeId())

        val webdavConfig = NetworkServerConfig(
            id = "dav-1",
            name = "Nextcloud",
            protocol = NetworkProtocol.WEBDAV,
            host = "cloud.example.com",
            port = 443,
            remotePath = "/remote.php/dav/files/user",
        )
        assertEquals(FileNodeId.webdav("dav-1", "/remote.php/dav/files/user"), webdavConfig.toRootNodeId())

        val smbConfig = NetworkServerConfig(
            id = "smb-1",
            name = "Office Share",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.100",
            port = 445,
            remotePath = "/shared/docs",
        )
        assertEquals(FileNodeId.smb("smb-1", "/shared/docs"), smbConfig.toRootNodeId())
    }

    @Test
    fun `NetworkCredentialsStore saves, retrieves, updates and deletes servers and passwords`() {
        val store = NetworkCredentialsStore(context)

        // Clear existing
        store.getAllServers().forEach { store.deleteServer(it.id) }
        assertTrue(store.getAllServers().isEmpty())

        val server1 = NetworkServerConfig(
            id = "test-server-1",
            name = "My NAS",
            protocol = NetworkProtocol.FTP,
            host = "192.168.1.50",
            port = 21,
            username = "admin",
            remotePath = "/data",
            anonymous = false,
        )

        store.saveServer(server1, "secret123")

        val retrieved = store.getAllServers()
        assertEquals(1, retrieved.size)
        assertEquals("My NAS", retrieved[0].name)
        assertEquals("192.168.1.50", retrieved[0].host)
        assertEquals(NetworkProtocol.FTP, retrieved[0].protocol)
        assertEquals("secret123", store.getPassword("test-server-1"))

        // Update server
        val updatedServer1 = server1.copy(name = "Home NAS Updated")
        store.saveServer(updatedServer1, "newSecret456")

        val updatedList = store.getAllServers()
        assertEquals(1, updatedList.size)
        assertEquals("Home NAS Updated", updatedList[0].name)
        assertEquals("newSecret456", store.getPassword("test-server-1"))

        // Add a second server
        val server2 = NetworkServerConfig(
            id = "test-server-2",
            name = "WebDAV Cloud",
            protocol = NetworkProtocol.WEBDAV,
            host = "cloud.local",
            port = 80,
            anonymous = true,
        )
        store.saveServer(server2, null)
        assertEquals(2, store.getAllServers().size)

        // Delete server 1
        store.deleteServer("test-server-1")
        val remaining = store.getAllServers()
        assertEquals(1, remaining.size)
        assertEquals("test-server-2", remaining[0].id)
        assertNull(store.getPassword("test-server-1"))
    }

    @Test
    fun `FtpBackend validates handled schemes and resolves root node`() = runTest {
        val store = NetworkCredentialsStore(context)
        val ftpBackend = FtpBackend(store)

        assertTrue(ftpBackend.canHandle(FileNodeId.ftp("srv1", "/")))
        assertTrue(ftpBackend.canHandle(FileNodeId.ftps("srv1", "/")))
        assertFalse(ftpBackend.canHandle(FileNodeId.file("/sdcard")))
        assertFalse(ftpBackend.canHandle(FileNodeId.sftp("srv1", "/")))

        val rootResult = ftpBackend.getNode(FileNodeId.ftp("srv1", "/"))
        assertTrue(rootResult is FileResult.Success)
        val node = (rootResult as FileResult.Success).value
        assertTrue(node.isDirectory)
        assertNull(node.parentId)
    }

    @Test
    fun `WebDavBackend validates handled schemes and resolves root node`() = runTest {
        val store = NetworkCredentialsStore(context)
        val webdavBackend = WebDavBackend(store)

        assertTrue(webdavBackend.canHandle(FileNodeId.webdav("srv1", "/")))
        assertFalse(webdavBackend.canHandle(FileNodeId.ftp("srv1", "/")))
        assertFalse(webdavBackend.canHandle(FileNodeId.file("/storage/emulated/0")))

        val rootResult = webdavBackend.getNode(FileNodeId.webdav("srv1", "/"))
        assertTrue(rootResult is FileResult.Success)
        val node = (rootResult as FileResult.Success).value
        assertTrue(node.isDirectory)
        assertNull(node.parentId)
    }

    @Test
    fun `SftpBackend and SmbBackend handle their respective schemes`() = runTest {
        val store = NetworkCredentialsStore(context)
        val sftp = SftpBackend(store)
        val smb = SmbBackend(store)

        assertTrue(sftp.canHandle(FileNodeId.sftp("srv1", "/")))
        assertFalse(sftp.canHandle(FileNodeId.smb("srv1", "/")))

        assertTrue(smb.canHandle(FileNodeId.smb("srv1", "/")))
        assertFalse(smb.canHandle(FileNodeId.sftp("srv1", "/")))
    }
}
