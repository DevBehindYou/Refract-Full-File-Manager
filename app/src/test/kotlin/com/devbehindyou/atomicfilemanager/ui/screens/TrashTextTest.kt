package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.TrashEntry
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId

class TrashTextTest {
    private val day = 24L * 60 * 60 * 1000

    private fun entry(
        size: Long = 2_048,
        deletedAt: Long = 0L,
    ) = TrashEntry(
        id = "t",
        name = "notes.txt",
        isDirectory = false,
        size = size,
        originalParent = FileNodeId.file("/storage/emulated/0/Download"),
        trashedId = FileNodeId.file("/storage/emulated/0/.Atomic File Manager/Trash/t/notes.txt"),
        holderId = FileNodeId.file("/storage/emulated/0/.Atomic File Manager/Trash/t"),
        volumeRoot = FileNodeId.file("/storage/emulated/0"),
        deletedAt = deletedAt,
        operationId = "op",
    )

    @Test
    fun `meta shows where it goes back, its size and the days left`() {
        val deleted = 1_000 * day
        val meta = TrashText.meta(entry(deletedAt = deleted), retentionDays = 30, now = deleted + 7 * day)
        assertTrue(meta.endsWith(" · restores to /Download · 2.0 KB · 23 days left"), meta)
        assertTrue(meta.startsWith("Deleted ${FileUtils.formatDate(deleted, ZoneId.systemDefault())}"))
    }

    @Test
    fun `expired items never show negative days`() {
        assertTrue(TrashText.meta(entry(), retentionDays = 7, now = 100 * day).endsWith("0 days left"))
    }

    @Test
    fun `summaries and snackbars count items`() {
        assertEquals("Nothing here", TrashText.summary(emptyList()))
        assertEquals("2 items · 4.0 KB", TrashText.summary(listOf(entry(), entry())))
        assertEquals("Moved 1 item to Trash.", TrashText.moved(1, 0))
        assertEquals("Moved 3 items to Trash; 1 failed.", TrashText.moved(3, 1))
        assertEquals("Restored 2 items.", TrashText.restored(2, 0))
        assertEquals("Deletes “notes.txt” permanently. This can't be undone.", TrashText.foreverBody(listOf(entry())))
    }
}
