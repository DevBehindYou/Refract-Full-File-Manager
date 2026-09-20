package com.devbehindyou.refract.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HiddenItemAndSettingsTest {
    private fun item(location: String) =
        HiddenItem(
            id = "1",
            originalLocation = location,
            currentLocation = "/data/private/file",
            originalName = "file",
            size = 1,
            mode = HideMode.PRIVATE_STORAGE,
        )

    @Test
    fun `the original parent is a valid file node id without a second file prefix`() {
        // The restore screen used to build "file:/dir" and pass it to FileNodeId.file, which threw.
        assertEquals(
            FileNodeId.file("/storage/emulated/0/Download"),
            item("/storage/emulated/0/Download/a.txt").originalParent(),
        )
        assertEquals(FileNodeId.file("/"), item("/a.txt").originalParent())
    }

    @Test
    fun `hidden folder input is cleaned up`() {
        assertEquals("Refract/Hidden", normalizeHiddenFolder("Refract/Hidden"))
        assertEquals("Refract/Hidden", normalizeHiddenFolder("  /Refract//Hidden/ "))
        assertEquals("Private/Stuff", normalizeHiddenFolder("Private\\Stuff"))
    }

    @Test
    fun `hidden folder input that could leave the storage root is rejected`() {
        assertNull(normalizeHiddenFolder(""))
        assertNull(normalizeHiddenFolder("   "))
        assertNull(normalizeHiddenFolder("../Elsewhere"))
        assertNull(normalizeHiddenFolder("Refract/./Hidden"))
        assertNull(normalizeHiddenFolder("C:/Refract"))
    }

    @Test
    fun `settings default to the Refract Hidden folder`() {
        assertEquals("Refract/Hidden", AppSettings().hiddenFolder)
    }
}
