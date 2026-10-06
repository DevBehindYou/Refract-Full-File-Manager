package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.usecase.LineEnding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextEncoding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFileEditor
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EditorTextTest {
    @Test
    fun `meta names the stored format and unsaved state`() {
        assertEquals("Opening…", EditorText.meta(null, dirty = false))
        assertEquals("UTF-8 · CRLF", EditorText.meta(TextFormat(TextEncoding.UTF8, LineEnding.CRLF), dirty = false))
        assertEquals(
            "UTF-8 BOM · LF · unsaved",
            EditorText.meta(TextFormat(TextEncoding.UTF8_BOM, LineEnding.LF), dirty = true),
        )
    }

    @Test
    fun `match counter and failure wording`() {
        assertEquals("Type to find", EditorText.matches(-1, 0, ""))
        assertEquals("No matches", EditorText.matches(-1, 0, "x"))
        assertEquals("2 of 5", EditorText.matches(1, 5, "x"))
        assertEquals("5 matches", EditorText.matches(-1, 5, "x"))
        assertEquals(
            "Files over 2 MB open read-only in the preview.",
            EditorText.openFailed(FileError.FileTooLarge("a.log", TextFileEditor.MAX_EDIT_BYTES)),
        )
        assertEquals("Not saved · the original is unchanged", EditorText.saveFailed(FileError.IoFailure("a")))
    }
}
