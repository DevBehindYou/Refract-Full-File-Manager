package com.devbehindyou.atomicfilemanager.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemovableNamesTest {
    @Test
    fun `removable folders are recognised by where they live`() {
        assertTrue(RemovableNames.appliesTo(FileNodeId.file("/storage/1A2B-3C4D/DCIM")))
        assertTrue(RemovableNames.appliesTo(FileNodeId.file("/storage/1A2B-3C4D")))
        assertTrue(RemovableNames.appliesTo(FileNodeId.file("/mnt/media_rw/1A2B-3C4D/x")))
        assertFalse(RemovableNames.appliesTo(FileNodeId.file("/storage/emulated/0/Download")))
        assertFalse(RemovableNames.appliesTo(FileNodeId.file("/storage/self/primary")))
        assertFalse(RemovableNames.appliesTo(FileNodeId.file("/data/user/0/app")))
        assertFalse(RemovableNames.appliesTo(FileNodeId.sftp("nas", "/storage/x")))
    }

    @Test
    fun `names FAT and exFAT refuse`() {
        assertNull(RemovableNames.problem("Holiday 2026.jpg"))
        assertNull(RemovableNames.problem(".nomedia"))
        assertEquals("USB drives and SD cards can't store \":\" in a name.", RemovableNames.problem("10:30 meeting"))
        RemovableNames.FORBIDDEN.forEach { assertTrue(RemovableNames.problem("a${it}b") != null, "$it") }
        assertTrue(RemovableNames.problem("tab\there")!!.contains("control"))
        assertTrue(RemovableNames.problem("notes.")!!.contains("dot or space"))
        assertTrue(RemovableNames.problem("notes ")!!.contains("dot or space"))
        assertTrue(RemovableNames.problem("é".repeat(256))!!.contains("too long"))
        assertNull(RemovableNames.problem("é".repeat(255)))
    }
}
