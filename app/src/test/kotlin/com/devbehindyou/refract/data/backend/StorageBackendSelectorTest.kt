package com.devbehindyou.refract.data.backend

import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.repository.BackendType
import com.devbehindyou.refract.domain.repository.StorageBackend
import com.devbehindyou.refract.domain.testing.InMemoryBackend
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StorageBackendSelectorTest {
    private lateinit var fileBackend: StorageBackend
    private lateinit var safBackend: StorageBackend
    private lateinit var mediaStoreBackend: StorageBackend
    private lateinit var usbBackend: StorageBackend
    private lateinit var sftpBackend: StorageBackend
    private lateinit var ftpBackend: StorageBackend
    private lateinit var ftpsBackend: StorageBackend
    private lateinit var smbBackend: StorageBackend
    private lateinit var webdavBackend: StorageBackend

    private lateinit var selector: StorageBackendSelector

    @BeforeEach
    fun setUp() {
        fileBackend = InMemoryBackend()
        safBackend = InMemoryBackend()
        mediaStoreBackend = InMemoryBackend()
        usbBackend = InMemoryBackend()
        sftpBackend = InMemoryBackend()
        ftpBackend = InMemoryBackend()
        ftpsBackend = InMemoryBackend()
        smbBackend = InMemoryBackend()
        webdavBackend = InMemoryBackend()

        val map =
            mapOf(
                BackendType.FILE to fileBackend,
                BackendType.SAF to safBackend,
                BackendType.MEDIASTORE to mediaStoreBackend,
                BackendType.USB to usbBackend,
                BackendType.SFTP to sftpBackend,
                BackendType.FTP to ftpBackend,
                BackendType.FTPS to ftpsBackend,
                BackendType.SMB to smbBackend,
                BackendType.WEBDAV to webdavBackend,
            )
        selector = StorageBackendSelector(map)
    }

    @Test
    fun `routes FILE prefix to FileSystemBackend`() {
        val id = FileNodeId.file("/storage/emulated/0/Download/test.pdf")
        assertSame(fileBackend, selector.forNode(id))
    }

    @Test
    fun `routes SAF prefix to SAF backend`() {
        val id = FileNodeId("saf:content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        assertSame(safBackend, selector.forNode(id))
    }

    @Test
    fun `routes MEDIA prefix to MediaStore backend`() {
        val id = FileNodeId("media:content://media/external/images/media/42")
        assertSame(mediaStoreBackend, selector.forNode(id))
    }

    @Test
    fun `routes USB prefix to USB backend`() {
        val id = FileNodeId("usb:otg://device1/DCIM")
        assertSame(usbBackend, selector.forNode(id))
    }

    @Test
    fun `routes SFTP prefix to SFTP backend`() {
        val id = FileNodeId("sftp:user@host:22/home/user/docs")
        assertSame(sftpBackend, selector.forNode(id))
    }

    @Test
    fun `routes FTP prefix to FTP backend`() {
        val id = FileNodeId("ftp:anonymous@host:21/pub")
        assertSame(ftpBackend, selector.forNode(id))
    }

    @Test
    fun `routes FTPS prefix to FTPS backend`() {
        val id = FileNodeId("ftps:user@host:990/secure")
        assertSame(ftpsBackend, selector.forNode(id))
    }

    @Test
    fun `routes SMB prefix to SMB backend`() {
        val id = FileNodeId("smb:guest@server/share/folder")
        assertSame(smbBackend, selector.forNode(id))
    }

    @Test
    fun `routes WEBDAV prefix to WebDAV backend`() {
        val id = FileNodeId("webdav:https://cloud.example.com/remote.php/webdav/files")
        assertSame(webdavBackend, selector.forNode(id))
    }

    @Test
    fun `throws IllegalStateException when backend is not bound`() {
        val partialSelector = StorageBackendSelector(mapOf(BackendType.FILE to fileBackend))
        val safId = FileNodeId("saf:content://tree")
        val exception =
            assertThrows(IllegalStateException::class.java) {
                partialSelector.forNode(safId)
            }
        assertEquals("No StorageBackend bound for SAF", exception.message)
    }

    @Test
    fun `throws IllegalStateException for malformed or unknown prefix`() {
        val malformedId = FileNodeId("unknown_scheme:/path/to/file")
        val exception =
            assertThrows(IllegalStateException::class.java) {
                selector.forNode(malformedId)
            }
        assert(exception.message?.contains("Malformed FileNodeId") == true)
    }
}
