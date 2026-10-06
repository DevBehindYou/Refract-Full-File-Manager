package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeleteTextTest {
    private fun node(
        name: String,
        directory: Boolean = false,
    ) = FileNode(
        id = FileNodeId.file("/storage/emulated/0/$name"),
        name = name,
        displayName = name,
        mimeType = null,
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

    @Test
    fun `trash wording says how long it can be restored`() {
        val one = listOf(node("notes.txt"))
        assertEquals("Move file to Trash?", DeleteText.headline(one, toTrash = true))
        assertEquals(
            "Moves “notes.txt” to Trash. You can restore it for 30 days.",
            DeleteText.body(one, toTrash = true, retentionDays = 30, reason = null),
        )
        val many = listOf(node("a.txt"), node("Photos", directory = true))
        assertEquals("Move 2 items to Trash?", DeleteText.headline(many, toTrash = true))
        assertEquals(
            "Moves 2 items, with everything inside, to Trash. You can restore them for 7 days.",
            DeleteText.body(many, toTrash = true, retentionDays = 7, reason = null),
        )
    }

    @Test
    fun `permanent wording says why and that it can't be undone`() {
        val folder = listOf(node("Photos", directory = true))
        assertEquals("Delete folder forever?", DeleteText.headline(folder, toTrash = false))
        assertEquals(
            "This storage has no Trash. Deletes “Photos”, with everything inside, permanently. This can't be undone.",
            DeleteText.body(folder, toTrash = false, retentionDays = 30, reason = DeleteText.Reason.NO_TRASH_HERE),
        )
        assertEquals(
            "Trash is off in Settings. Deletes “Photos”, with everything inside, permanently. This can't be undone.",
            DeleteText.body(folder, toTrash = false, retentionDays = 30, reason = DeleteText.Reason.TRASH_OFF),
        )
    }
}
