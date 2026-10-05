package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.Conflict
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConflictTextTest {
    private val download = FileNodeId.file("/storage/emulated/0/Download")

    private fun node(
        path: String,
        directory: Boolean = false,
        size: Long = 2_048,
    ) = FileNode(
        id = FileNodeId.file(path),
        name = path.substringAfterLast('/'),
        displayName = path.substringAfterLast('/'),
        mimeType = null,
        size = size,
        modifiedAt = 0L,
        isDirectory = directory,
        isHidden = false,
        parentId = FileNodeId.file(path.substringBeforeLast('/')),
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL,
        childCount = null,
        extras = null,
    )

    private fun operation(type: OperationType) =
        FileOperation(
            id = OperationId("op"),
            type = type,
            sources = listOf(FileNodeId.file("/storage/1A2B-3C4D/report.pdf")),
            destination = download,
            options = OperationOptions(),
            createdAt = 0L,
        )

    private val fileClash =
        Conflict(node("/storage/1A2B-3C4D/report.pdf"), node("/storage/emulated/0/Download/report.pdf"))

    @Test
    fun `names the file and the folder it clashes in`() {
        val body = ConflictText.body(operation(OperationType.COPY), fileClash)
        assertTrue(body.startsWith("“report.pdf” is already in “Download”."), body)
        assertEquals("File already exists", ConflictText.headline(fileClash))
        assertEquals("Move · name already used", ConflictText.label(operation(OperationType.MOVE)))
    }

    @Test
    fun `folders explain that they can't be replaced`() {
        val folders =
            Conflict(
                node("/storage/1A2B-3C4D/Photos", directory = true, size = -1),
                node("/storage/emulated/0/Download/Photos", directory = true, size = -1),
            )
        assertEquals("Folder already exists", ConflictText.headline(folders))
        assertTrue(ConflictText.body(operation(OperationType.COPY), folders).contains("can't be replaced"))
        assertEquals(listOf("Name", "Modified"), ConflictText.facts(folders.source).map { it.label })
    }

    @Test
    fun `file facts show size and ISO date`() {
        val facts = ConflictText.facts(fileClash.source).associate { it.label to it.value }
        assertEquals("report.pdf", facts["Name"])
        assertEquals("2.0 KB", facts["Size"])
        assertEquals("—", facts["Modified"])
    }
}
