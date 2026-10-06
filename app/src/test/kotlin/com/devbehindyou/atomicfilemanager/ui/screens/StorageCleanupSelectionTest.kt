package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.DuplicateGroup
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StorageCleanupSelectionTest {
    private fun file(
        path: String,
        size: Long,
        directory: Boolean = false,
    ) = FileNode(
        id = FileNodeId.file(path),
        name = path.substringAfterLast('/'),
        displayName = path.substringAfterLast('/'),
        mimeType = null,
        size = size,
        modifiedAt = 0L,
        isDirectory = directory,
        isHidden = false,
        parentId = null,
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL,
        childCount = null,
        extras = null,
    )

    private val a1 = file("/s/a1.jpg", 100)
    private val a2 = file("/s/copy/a1.jpg", 100)
    private val a3 = file("/s/old/a1.jpg", 100)
    private val b1 = file("/s/b.mp4", 500)
    private val b2 = file("/s/b (1).mp4", 500)
    private val groups =
        listOf(
            DuplicateGroup(sizeBytes = 100, sha256 = "a", items = listOf(a1, a2, a3)),
            DuplicateGroup(sizeBytes = 500, sha256 = "b", items = listOf(b1, b2)),
        )
    private val result = StorageAnalysisResult(duplicateGroups = groups, emptyFolders = listOf(file("/s/e", -1, true)))

    @Test
    fun `keep one selects every copy except the first of each group`() {
        assertEquals(setOf(a2.id.raw, a3.id.raw, b2.id.raw), StorageCleanupSelection.allButFirstCopy(groups))
    }

    @Test
    fun `select all toggles between everything and nothing`() {
        val nodes = StorageCleanupSelection.nodesIn(result, StorageAnalysisCategory.DUPLICATE_FILES)
        val all = StorageCleanupSelection.toggleAll(emptySet(), nodes)
        assertEquals(5, all.size)
        assertEquals(emptySet<String>(), StorageCleanupSelection.toggleAll(all, nodes))
    }

    @Test
    fun `selected nodes resolve by raw id and bytes skip folders and unknown sizes`() {
        val picked =
            StorageCleanupSelection.selectedNodes(
                result,
                StorageAnalysisCategory.DUPLICATE_FILES,
                StorageCleanupSelection.allButFirstCopy(groups),
            )
        assertEquals(listOf(a2, a3, b2), picked)
        assertEquals(700L, StorageCleanupSelection.bytes(picked))
        assertEquals(0L, StorageCleanupSelection.bytes(result.emptyFolders))
    }

    @Test
    fun `duplicate count is the number of groups`() {
        assertEquals(2, StorageCleanupSelection.count(result, StorageAnalysisCategory.DUPLICATE_FILES))
        assertEquals(1, StorageCleanupSelection.count(result, StorageAnalysisCategory.EMPTY_FOLDERS))
    }

    @Test
    fun `only safe cards start selected`() {
        val withTemp = result.copy(tempCacheFiles = listOf(file("/s/x.tmp", 10)))
        assertEquals(
            setOf("file:/s/e"),
            StorageCleanupSelection.initialSelection(withTemp, StorageAnalysisCategory.EMPTY_FOLDERS),
        )
        assertEquals(
            setOf("file:/s/x.tmp"),
            StorageCleanupSelection.initialSelection(withTemp, StorageAnalysisCategory.TEMP_AND_CACHE),
        )
        assertEquals(
            emptySet<String>(),
            StorageCleanupSelection.initialSelection(withTemp, StorageAnalysisCategory.DUPLICATE_FILES),
        )
        assertEquals(
            "You get 1.0 KB back once they leave the Trash. You can restore them for 30 days; " +
                "after that they are deleted for good.",
            CleanupText.confirmBody(1024, 30),
        )
    }
}
