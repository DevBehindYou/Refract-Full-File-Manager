package com.devbehindyou.refract.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FileUtilsTest {
    @Test
    fun `formatBytes formats various sizes correctly`() {
        assertEquals("0 B", FileUtils.formatBytes(0))
        assertEquals("0 B", FileUtils.formatBytes(-100))
        assertEquals("500.0 B", FileUtils.formatBytes(500))
        assertEquals("1.0 KB", FileUtils.formatBytes(1024))
        assertEquals("1.5 MB", FileUtils.formatBytes(1572864))
        assertEquals("1.0 GB", FileUtils.formatBytes(1073741824))
    }

    @Test
    fun `formatDuration formats timestamps into standard time strings`() {
        assertEquals("0:00", FileUtils.formatDuration(0))
        assertEquals("0:00", FileUtils.formatDuration(-5000))
        assertEquals("0:45", FileUtils.formatDuration(45000))
        assertEquals("3:25", FileUtils.formatDuration(205000))
        assertEquals("1:05:08", FileUtils.formatDuration(3908000))
    }

    @Test
    fun `formatDate returns placeholder for non-positive timestamps`() {
        assertEquals("—", FileUtils.formatDate(0))
        assertEquals("—", FileUtils.formatDate(-1))
    }
}
