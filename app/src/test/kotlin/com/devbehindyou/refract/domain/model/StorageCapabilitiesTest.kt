package com.devbehindyou.refract.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageCapabilitiesTest {
    @Test
    fun `READ_ONLY preset matches expected flags`() {
        val caps = StorageCapabilities.READ_ONLY
        assertTrue(caps.canRead)
        assertFalse(caps.canWrite)
        assertFalse(caps.canCreate)
        assertFalse(caps.canDelete)
        assertFalse(caps.canRename)
        assertFalse(caps.supportsMove)
        assertFalse(caps.supportsCopy)
        assertFalse(caps.supportsAtomicMove)
        assertFalse(caps.supportsRandomAccess)
    }

    @Test
    fun `FULL_LOCAL preset matches expected flags`() {
        val caps = StorageCapabilities.FULL_LOCAL
        assertTrue(caps.canRead)
        assertTrue(caps.canWrite)
        assertTrue(caps.canCreate)
        assertTrue(caps.canDelete)
        assertTrue(caps.canRename)
        assertTrue(caps.supportsMove)
        assertTrue(caps.supportsCopy)
        assertTrue(caps.supportsAtomicMove)
        assertTrue(caps.supportsRandomAccess)
        assertTrue(caps.supportsSeek)
        assertTrue(caps.supportsTrash)
        assertTrue(caps.supportsHideMedia)
        assertTrue(caps.supportsObfuscate)
        assertTrue(caps.supportsServerSideCopy)
        assertTrue(caps.supportsResumableRead)
        assertTrue(caps.supportsResumableWrite)
    }

    @Test
    fun `SAF_STORAGE preset does not support atomic move or random access`() {
        val caps = StorageCapabilities.SAF_STORAGE
        assertTrue(caps.canRead)
        assertTrue(caps.canWrite)
        assertTrue(caps.canCreate)
        assertTrue(caps.canDelete)
        assertTrue(caps.canRename)
        assertFalse(caps.supportsAtomicMove)
        assertFalse(caps.supportsRandomAccess)
        assertFalse(caps.supportsTrash)
    }

    @Test
    fun `MEDIA_STORE preset is read-only except for delete and trash`() {
        val caps = StorageCapabilities.MEDIA_STORE
        assertTrue(caps.canRead)
        assertFalse(caps.canWrite)
        assertFalse(caps.canCreate)
        assertTrue(caps.canDelete)
        assertFalse(caps.canRename)
        assertTrue(caps.supportsTrash)
        assertFalse(caps.supportsAtomicMove)
    }

    @Test
    fun `REMOTE_NETWORK preset flags match network backend capabilities`() {
        val caps = StorageCapabilities.REMOTE_NETWORK
        assertTrue(caps.canRead)
        assertTrue(caps.canWrite)
        assertTrue(caps.canCreate)
        assertTrue(caps.canDelete)
        assertTrue(caps.canRename)
        assertFalse(caps.supportsAtomicMove)
        assertFalse(caps.supportsRandomAccess)
        assertFalse(caps.supportsTrash)
        assertTrue(caps.supportsResumableRead)
        assertFalse(caps.supportsResumableWrite)
    }

    @Test
    fun `APP_PRIVATE preset matches app-isolated vault capabilities`() {
        val caps = StorageCapabilities.APP_PRIVATE
        assertTrue(caps.canRead)
        assertTrue(caps.canWrite)
        assertTrue(caps.canCreate)
        assertTrue(caps.canDelete)
        assertTrue(caps.canRename)
        assertTrue(caps.supportsAtomicMove)
        assertTrue(caps.supportsHideMedia)
        assertTrue(caps.supportsObfuscate)
        assertFalse(caps.supportsTrash)
    }

    @Test
    fun `StorageLocation data class properties and defaults are correct`() {
        val rootId = FileNodeId.file("/storage/emulated/0")
        val location =
            StorageLocation(
                id = "internal_storage",
                displayName = "Internal Storage",
                type = StorageLocationType.LOCAL,
                rootNodeId = rootId,
                capabilities = StorageCapabilities.FULL_LOCAL,
                isRemovable = false,
                totalSpace = 128_000_000_000L,
                freeSpace = 64_000_000_000L,
            )

        assertEquals("internal_storage", location.id)
        assertEquals("Internal Storage", location.displayName)
        assertEquals(StorageLocationType.LOCAL, location.type)
        assertEquals(rootId, location.rootNodeId)
        assertEquals(StorageCapabilities.FULL_LOCAL, location.capabilities)
        assertFalse(location.isRemovable)
        assertEquals(128_000_000_000L, location.totalSpace)
        assertEquals(64_000_000_000L, location.freeSpace)
    }
}
