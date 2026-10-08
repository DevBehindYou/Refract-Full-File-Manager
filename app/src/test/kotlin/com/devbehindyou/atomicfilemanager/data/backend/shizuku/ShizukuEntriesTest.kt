package com.devbehindyou.atomicfilemanager.data.backend.shizuku

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShizukuEntriesTest {
    @Test
    fun `entries survive the binder round trip`() {
        val entries =
            listOf(
                ShizukuEntry("com.example.game", true, -1, 1_700_000_000_000),
                ShizukuEntry("save 1.dat", false, 4096, 1_700_000_001_000),
            )
        assertEquals(entries, ShizukuEntries.decode(ShizukuEntries.encode(entries)))
        assertTrue(ShizukuEntries.decode("").isEmpty())
        assertTrue(ShizukuEntries.decode(null).isEmpty())
        assertEquals(2, ShizukuEntries.decode(ShizukuEntries.encode(entries) + "\u001Ebroken").size)
    }

    @Test
    fun `only Android data and obb are reachable`() {
        assertTrue(ShizukuEntries.allowed("/storage/emulated/0/Android/data"))
        assertTrue(ShizukuEntries.allowed("/storage/emulated/0/Android/obb/com.example/main.obb"))
        assertTrue(ShizukuEntries.allowed("/storage/emulated/10/Android/data/x"))
        assertFalse(ShizukuEntries.allowed("/storage/emulated/0/Android"))
        assertFalse(ShizukuEntries.allowed("/storage/emulated/0/Android/media"))
        assertFalse(ShizukuEntries.allowed("/storage/emulated/0/Download"))
        assertFalse(ShizukuEntries.allowed("/data/data/com.example"))
        assertFalse(ShizukuEntries.allowed("/storage/emulated/0/Android/data/../../../../data/system"))
        assertFalse(ShizukuEntries.allowed("/storage/emulated/self/Android/data"))
        assertFalse(ShizukuEntries.allowed("storage/emulated/0/Android/data"))
        assertTrue(ShizukuEntries.allowed("/storage/emulated/0/Android/data/./a/../b"))
    }

    @Test
    fun `roots are valid ids`() {
        ShizukuEntries.roots().forEach {
            assertTrue(ShizukuEntries.allowed(it))
            assertEquals(FileNodeId.Prefix.SHIZUKU, FileNodeId.parse(FileNodeId.shizuku(it).raw)?.prefix)
        }
    }
}
