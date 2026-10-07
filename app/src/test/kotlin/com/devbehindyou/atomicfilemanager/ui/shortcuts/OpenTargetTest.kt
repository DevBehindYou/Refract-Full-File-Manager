package com.devbehindyou.atomicfilemanager.ui.shortcuts

import com.devbehindyou.atomicfilemanager.ui.screens.PaletteCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OpenTargetTest {
    @Test
    fun `every target survives the intent round trip`() {
        val targets =
            listOf(
                OpenTarget.Operations,
                OpenTarget.Palette,
                OpenTarget.Folder("file:/storage/emulated/0/DCIM"),
                OpenTarget.Folder("sftp:nas/home/me"),
            ) + PaletteCommand.entries.map(OpenTarget::Command)
        targets.forEach { assertEquals(it, OpenTarget.parse(it.encode())) }
    }

    @Test
    fun `notification extra keeps its old value`() {
        assertEquals("operations", OpenTarget.Operations.encode())
        assertEquals(OpenTarget.Operations, OpenTarget.parse("operations"))
    }

    @Test
    fun `unknown or stale values are ignored`() {
        assertNull(OpenTarget.parse(null))
        assertNull(OpenTarget.parse(""))
        assertNull(OpenTarget.parse("command:REMOVED_IN_AN_UPDATE"))
        assertNull(OpenTarget.parse("folder:"))
        assertNull(OpenTarget.parse("something"))
    }
}
