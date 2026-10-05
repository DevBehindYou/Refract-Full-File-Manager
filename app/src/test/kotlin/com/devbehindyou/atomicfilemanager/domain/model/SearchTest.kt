package com.devbehindyou.atomicfilemanager.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchTest {
    private fun hit(
        name: String,
        directory: Boolean = false,
        mime: String? = null,
    ) = SearchHit(FileNodeId.file("/x/$name"), name, FileNodeId.file("/x"), directory, 1, 0, mime)

    @Test
    fun `like patterns are lower-case substrings with wildcards escaped`() {
        assertEquals("%report%", likePatternFor("  Report "))
        assertEquals("%50\\%\\_off\\\\x%", likePatternFor("50%_off\\x"))
        assertNull(likePatternFor("   "))
    }

    @Test
    fun `type chips classify like the categories do`() {
        assertTrue(SearchFilter.IMAGES.matches(hit("a.HEIC")))
        assertTrue(SearchFilter.VIDEOS.matches(hit("clip", mime = "video/mp4")))
        assertTrue(SearchFilter.DOCUMENTS.matches(hit("plan.pdf")))
        assertTrue(SearchFilter.DOCUMENTS.matches(hit("notes.md")))
        assertTrue(SearchFilter.APKS.matches(hit("app.apk")))
        assertFalse(SearchFilter.ARCHIVES.matches(hit("app.apk")))
        assertTrue(SearchFilter.FOLDERS.matches(hit("Photos", directory = true)))
        assertFalse(SearchFilter.IMAGES.matches(hit("Photos.jpg", directory = true)))
        assertTrue(SearchFilter.ALL.matches(hit("anything")))
    }
}
