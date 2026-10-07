package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.usecase.CompareEntry
import com.devbehindyou.atomicfilemanager.domain.usecase.CompareState
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FolderCompareTextTest {
    private val parent = FileNodeId.file("/l")

    private fun node(name: String) =
        FileNode(
            id = FileNodeId.file("/l/$name"),
            name = name,
            displayName = name,
            mimeType = null,
            size = 10,
            modifiedAt = 0,
            isDirectory = false,
            isHidden = false,
            parentId = parent,
            storageType = StorageType.INTERNAL_SHARED,
            access = AccessFlags.FULL,
            childCount = null,
            extras = null,
        )

    private fun entry(
        path: String,
        state: CompareState,
    ) = CompareEntry(path, node(path), null, parent, parent, state)

    @Test
    fun `differences hide same files unless filtered`() {
        val entries = listOf(entry("a", CompareState.SAME), entry("b", CompareState.ONLY_LEFT))
        assertEquals(listOf("b"), CompareText.visible(entries, null).map { it.path })
        assertEquals(listOf("a"), CompareText.visible(entries, CompareState.SAME).map { it.path })
    }

    @Test
    fun `summary counts each kind of change`() {
        val plan =
            SyncPlan(
                copies = listOf(entry("a", CompareState.ONLY_LEFT), entry("b", CompareState.ONLY_LEFT)),
                replacements = listOf(entry("c", CompareState.NEWER_LEFT)),
                trash = listOf(node("d")),
            )
        assertEquals("Copy 2 new items, replace 1 file, move 1 extra item to Trash.", CompareText.summary(plan))
        assertEquals(listOf("+ a", "+ b", "↻ c", "− d"), CompareText.preview(plan))
    }

    @Test
    fun `long previews are cut short and empty plans say so`() {
        val many = SyncPlan((1..12).map { entry("f$it", CompareState.ONLY_LEFT) }, emptyList(), emptyList())
        val preview = CompareText.preview(many)
        assertEquals(9, preview.size)
        assertEquals("and 4 more", preview.last())
        assertEquals(
            "The right folder already has everything this sync would copy.",
            CompareText.summary(SyncPlan(emptyList(), emptyList(), emptyList())),
        )
    }

    @Test
    fun `folder names come from the last part of the id`() {
        assertEquals("DCIM", CompareText.folderName("file:/storage/emulated/0/DCIM/"))
        assertEquals("Storage", CompareText.folderName("file:/"))
    }
}
