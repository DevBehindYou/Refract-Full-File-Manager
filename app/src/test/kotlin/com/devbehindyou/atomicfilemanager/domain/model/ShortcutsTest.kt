package com.devbehindyou.atomicfilemanager.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShortcutsTest {
    private fun item(
        path: String,
        at: Long,
        source: RecentSource,
    ) = RecentItem(FileNodeId.file(path), path.substringAfterLast('/'), null, 1, at, source)

    @Test
    fun `recents are newest first, each file once, capped`() {
        val opened = listOf(item("/a.pdf", 50, RecentSource.OPENED), item("/b.jpg", 10, RecentSource.OPENED))
        val media =
            listOf(
                item("/b.jpg", 40, RecentSource.MEDIA),
                item("/c.mp4", 30, RecentSource.MEDIA),
                item("/d.mp3", 20, RecentSource.MEDIA),
            )

        val merged = mergeRecents(opened, media, limit = 3)

        assertEquals(listOf("a.pdf", "b.jpg", "c.mp4"), merged.map { it.name })
        // The newer of the two b.jpg entries wins.
        assertEquals(RecentSource.MEDIA, merged[1].source)
    }

    @Test
    fun `nothing in gives nothing out`() {
        assertEquals(emptyList<RecentItem>(), mergeRecents(emptyList(), emptyList(), limit = 6))
    }
}
