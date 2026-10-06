package com.devbehindyou.atomicfilemanager.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class BrowseTabsTest {
    private val home = "file:/storage/emulated/0"

    @Test
    fun `opening adds after the active tab, switches to it and stops at four`() {
        var tabs = BrowseTabsState.single(home)
        tabs = tabs.open("file:/storage/emulated/0/Download", "Download")
        assertEquals(listOf(0, 1), tabs.tabs.map { it.id })
        assertEquals(1, tabs.activeId)

        tabs = tabs.select(0).open("file:/storage/emulated/0/DCIM")
        assertEquals(listOf(0, 2, 1), tabs.tabs.map { it.id })
        tabs = tabs.open("file:/x")
        assertFalse(tabs.canOpenMore)
        assertSame(tabs, tabs.open("file:/y"))
    }

    @Test
    fun `closing moves to the left neighbour and never closes the last tab`() {
        val tabs = BrowseTabsState.single(home).open("file:/a").open("file:/b")
        val closed = tabs.close(2)
        assertEquals(1, closed.activeId)
        assertEquals(0, closed.close(1).activeId)
        val one = BrowseTabsState.single(home)
        assertSame(one, one.close(0))
        assertEquals(2, tabs.close(0).activeId)
    }

    @Test
    fun `an open from elsewhere reuses the active tab and bumps its request`() {
        val tabs = BrowseTabsState.single(home).openInActive("file:/storage/emulated/0/Music")
        assertEquals("file:/storage/emulated/0/Music", tabs.active.folderRaw)
        assertEquals(1, tabs.active.openRequest)
    }

    @Test
    fun `tabs survive encoding and bad saves fall back to one tab`() {
        val tabs = BrowseTabsState.single(home).open("file:/a b/c", "c").retitle(0, "Internal").openInActive("file:/d")
        assertEquals(tabs, BrowseTabsState.decode(tabs.encode(), home))
        assertEquals(BrowseTabsState.single(home), BrowseTabsState.decode(null, home))
        assertEquals(BrowseTabsState.single(home), BrowseTabsState.decode("garbage", home))
        assertEquals("Download", BrowseTabText.label(BrowseTab(1, "file:/storage/emulated/0/Download/")))
        assertEquals("Photos", BrowseTabText.label(BrowseTab(1, "file:/x", title = "Photos")))
    }
}
