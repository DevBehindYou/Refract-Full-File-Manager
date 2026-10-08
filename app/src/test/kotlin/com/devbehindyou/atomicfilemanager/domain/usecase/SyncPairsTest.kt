package com.devbehindyou.atomicfilemanager.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncPairsTest {
    private val photos = SyncPair("file:/storage/emulated/0/DCIM", "sftp:nas:/backup/DCIM", SyncMode.COPY_NEW)
    private val docs = SyncPair("file:/storage/emulated/0/Documents", "smb:nas:/Docs", SyncMode.MIRROR)

    @Test
    fun `pairs survive saving, ids with colons included`() {
        val saved = SyncPairs.encode(listOf(photos, docs))
        assertEquals(listOf(photos, docs), SyncPairs.decode(saved))
        assertTrue(SyncPairs.decode(null).isEmpty())
        assertTrue(SyncPairs.decode("").isEmpty())
    }

    @Test
    fun `saving again moves the pair to the top with its new mode`() {
        val list = SyncPairs.add(SyncPairs.add(listOf(), photos), docs)
        assertEquals(listOf(docs, photos), list)
        val updated = SyncPairs.add(list, photos.copy(mode = SyncMode.MIRROR))
        assertEquals(listOf(photos.copy(mode = SyncMode.MIRROR), docs), updated)
    }

    @Test
    fun `find and remove go by both folders`() {
        val list = listOf(photos, docs)
        assertEquals(docs, SyncPairs.find(list, docs.leftRaw, docs.rightRaw))
        assertNull(SyncPairs.find(list, docs.rightRaw, docs.leftRaw))
        assertEquals(listOf(docs), SyncPairs.remove(list, photos.copy(mode = SyncMode.MIRROR)))
    }

    @Test
    fun `at most ten are kept and unknown rows are skipped`() {
        val many = (1..15).fold(listOf<SyncPair>()) { acc, i -> SyncPairs.add(acc, photos.copy(leftRaw = "file:/$i")) }
        assertEquals(SyncPairs.MAX, many.size)
        assertEquals("file:/15", many.first().leftRaw)
        val withBadRow = SyncPairs.encode(listOf(photos)) + "\u001Ex\u001Fy\u001FBACKWARDS"
        assertEquals(listOf(photos), SyncPairs.decode(withBadRow))
    }
}
