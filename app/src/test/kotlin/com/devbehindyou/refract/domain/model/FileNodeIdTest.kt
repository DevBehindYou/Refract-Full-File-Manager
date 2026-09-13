package com.devbehindyou.refract.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FileNodeIdTest {
    // --- file: -------------------------------------------------------------------------

    @Test
    fun `file id round-trips through parse`() {
        val id = FileNodeId.file("/storage/emulated/0/Download/a.pdf")

        val parsed = FileNodeId.parse(id.raw)

        assertEquals(id, parsed)
    }

    @Test
    fun `file factory rejects a relative path`() {
        val exception = runCatching { FileNodeId.file("Download/a.pdf") }.exceptionOrNull()

        assertNotNull(exception)
    }

    @Test
    fun `parse rejects a file id with a relative path`() {
        assertNull(FileNodeId.parse("file:Download/a.pdf"))
    }

    // --- saf: ----------------------------------------------------------------------------

    @Test
    fun `saf id round-trips through parse`() {
        val contentUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload"
        val id = FileNodeId.saf(contentUri)

        val parsed = FileNodeId.parse(id.raw)

        assertEquals(id, parsed)
    }

    @Test
    fun `parse rejects a saf id whose decoded content is not a content uri`() {
        // Percent-encodes cleanly but decodes to something that isn't a content:// URI.
        assertNull(FileNodeId.parse("saf:not-a-content-uri"))
    }

    @Test
    fun `parse rejects an empty saf id`() {
        assertNull(FileNodeId.parse("saf:"))
    }

    // --- media: ----------------------------------------------------------------------------

    @Test
    fun `media id round-trips through parse`() {
        val id = FileNodeId.media(volume = "external", collection = "images", id = 10432L)

        val parsed = FileNodeId.parse(id.raw)

        assertEquals(id, parsed)
        assertEquals("media:external:images:10432", id.raw)
    }

    @Test
    fun `parse rejects a media id with too few segments`() {
        assertNull(FileNodeId.parse("media:external:10432"))
    }

    @Test
    fun `parse rejects a media id with a non-numeric row id`() {
        assertNull(FileNodeId.parse("media:external:images:not-a-number"))
    }

    // --- cross-cutting malformed input ------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "not-a-known-prefix:whatever",
            "file:",
            "http://example.com",
        ],
    )
    fun `parse rejects malformed or unrecognised input`(raw: String) {
        assertNull(FileNodeId.parse(raw))
    }

    @Test
    fun `prefix reports the correct scheme for each kind`() {
        assertEquals(FileNodeId.Prefix.FILE, FileNodeId.file("/a").prefix)
        assertEquals(FileNodeId.Prefix.SAF, FileNodeId.saf("content://a/b").prefix)
        assertEquals(FileNodeId.Prefix.MEDIA, FileNodeId.media("external", "images", 1L).prefix)
    }
}
