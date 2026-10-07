package com.devbehindyou.atomicfilemanager.data.volume

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LinkedFolderTextTest {
    @Test
    fun `fallback name is the last part of the document id`() {
        assertEquals("Work", LinkedFolderText.fallbackName("primary:Documents/Work"))
        assertEquals("Documents", LinkedFolderText.fallbackName("primary:Documents/"))
        assertEquals("primary:", LinkedFolderText.fallbackName("primary:"))
        assertEquals("a1b2c3", LinkedFolderText.fallbackName("a1b2c3"))
    }

    @Test
    fun `read-only folders say so`() {
        assertEquals("Nextcloud", LinkedFolderText.meta("Nextcloud", writable = true))
        assertEquals("Nextcloud · read only", LinkedFolderText.meta("Nextcloud", writable = false))
    }
}
