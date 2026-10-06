package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FailedItem
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.usecase.BatchRenameResult
import com.devbehindyou.atomicfilemanager.domain.usecase.RenamePreview
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameProblem
import com.devbehindyou.atomicfilemanager.domain.usecase.RenamedItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RenameTextTest {
    private fun node(name: String) =
        FileNode(
            id = FileNodeId.file("/storage/emulated/0/$name"),
            name = name,
            displayName = name,
            mimeType = null,
            size = 1,
            modifiedAt = 0,
            isDirectory = false,
            isHidden = false,
            parentId = null,
            storageType = StorageType.INTERNAL_SHARED,
            access = AccessFlags.FULL,
            childCount = null,
            extras = null,
        )

    @Test
    fun `summary counts problems before changes`() {
        val ok = RenamePreview(node("a.txt"), "b.txt", null)
        val same = RenamePreview(node("c.txt"), "c.txt", null)
        val bad = RenamePreview(node("d.txt"), "b.txt", RenameProblem.DUPLICATE_IN_BATCH)

        assertEquals("1 of 2 will be renamed", RenameText.summary(listOf(ok, same)))
        assertEquals("Nothing changes yet", RenameText.summary(listOf(same)))
        assertEquals("1 name needs fixing", RenameText.summary(listOf(ok, bad)))
        assertEquals("was d.txt · another item gets the same name", RenameText.rowMeta(bad))
        assertEquals("unchanged", RenameText.rowMeta(same))
    }

    @Test
    fun `outcome says how many were renamed and how many kept their name`() {
        val renamed = RenamedItem(node("b.txt"), "a.txt")
        val failed = FailedItem(FileNodeId.file("/x"), "x.txt", FileError.FileAlreadyExists("y.txt"))

        assertEquals("Renamed 1 item", RenameText.outcome(BatchRenameResult(listOf(renamed), emptyList())))
        assertEquals(
            "Renamed 2 items · 1 couldn't be renamed",
            RenameText.outcome(BatchRenameResult(listOf(renamed, renamed), listOf(failed))),
        )
        assertEquals("kept its name · the new name is already used", RenameText.reason(failed.error))
    }
}
