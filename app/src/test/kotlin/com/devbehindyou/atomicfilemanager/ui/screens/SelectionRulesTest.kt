package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectionRulesTest {
    private fun node(
        name: String,
        directory: Boolean = false,
        mime: String? = null,
    ) = FileNode(
        id = FileNodeId.file("/d/$name"),
        name = name,
        displayName = name,
        mimeType = mime,
        size = 1,
        modifiedAt = 0,
        isDirectory = directory,
        isHidden = false,
        parentId = null,
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL,
        childCount = null,
        extras = null,
    )

    private val shown =
        listOf(
            node("Photos", directory = true),
            node("a.jpg"),
            node("b.JPG"),
            node("c.pdf"),
            node("README"),
            node("d.jpg"),
        )

    private fun ids(vararg names: String) = names.map { FileNodeId.file("/d/$it") }.toSet()

    @Test
    fun `long-press after a selection selects the range from the anchor, either direction`() {
        assertEquals(
            ids("a.jpg", "b.JPG", "c.pdf", "README"),
            SelectionRules.longPress(shown, ids("README"), FileNodeId.file("/d/README"), FileNodeId.file("/d/a.jpg")),
        )
        assertEquals(
            ids("Photos", "c.pdf", "README", "d.jpg"),
            SelectionRules.longPress(
                shown,
                ids("Photos", "c.pdf"),
                FileNodeId.file("/d/c.pdf"),
                FileNodeId.file("/d/d.jpg"),
            ),
        )
    }

    @Test
    fun `the first long-press, or one on the anchor itself, just toggles`() {
        assertEquals(ids("a.jpg"), SelectionRules.longPress(shown, emptySet(), null, FileNodeId.file("/d/a.jpg")))
        val anchor = FileNodeId.file("/d/a.jpg")
        assertEquals(emptySet<FileNodeId>(), SelectionRules.longPress(shown, ids("a.jpg"), anchor, anchor))
        // An anchor that is no longer shown (filtered away) falls back to a toggle as well.
        assertEquals(
            ids("a.jpg", "c.pdf"),
            SelectionRules.longPress(shown, ids("a.jpg"), FileNodeId.file("/x"), FileNodeId.file("/d/c.pdf")),
        )
    }

    @Test
    fun `invert swaps selected and unselected`() {
        assertEquals(ids("Photos", "b.JPG", "README", "d.jpg"), SelectionRules.invert(shown, ids("a.jpg", "c.pdf")))
    }

    @Test
    fun `same type matches folders or extensions, ignoring case`() {
        assertEquals(ids("a.jpg", "b.JPG", "d.jpg"), SelectionRules.sameType(shown, ids("a.jpg")))
        assertEquals(ids("Photos", "c.pdf"), SelectionRules.sameType(shown, ids("Photos", "c.pdf")))
        assertEquals("mime:", SelectionRules.kindOf(node("README")))
        assertEquals("pdf", SelectionRules.kindOf(node(".hidden.pdf")))
    }
}
