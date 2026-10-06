package com.devbehindyou.atomicfilemanager.data.search

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TouchedFoldersTest {
    private fun op(
        type: OperationType,
        sources: List<FileNodeId>,
        destination: FileNodeId?,
    ) = FileOperation(OperationId("o"), type, sources, destination, OperationOptions(), 0)

    private val a = FileNodeId.file("/storage/emulated/0/DCIM/a.jpg")
    private val b = FileNodeId.file("/storage/emulated/0/Download/b.pdf")
    private val backup = FileNodeId.file("/storage/emulated/0/Backup")

    @Test
    fun `a move touches every source folder and the destination`() {
        assertEquals(
            setOf(FileNodeId.file("/storage/emulated/0/DCIM"), FileNodeId.file("/storage/emulated/0/Download"), backup),
            foldersTouchedBy(op(OperationType.MOVE, listOf(a, b), backup)),
        )
    }

    @Test
    fun `trash and delete touch only the source folders, rename never treats the new name as a folder`() {
        assertEquals(
            setOf(FileNodeId.file("/storage/emulated/0/DCIM")),
            foldersTouchedBy(op(OperationType.TRASH, listOf(a), null)),
        )
        assertEquals(
            setOf(FileNodeId.file("/storage/emulated/0/DCIM")),
            foldersTouchedBy(op(OperationType.RENAME, listOf(a), FileNodeId("b.jpg"))),
        )
    }

    @Test
    fun `network items and trash restores are left to the next full build`() {
        assertEquals(
            emptySet<FileNodeId>(),
            foldersTouchedBy(op(OperationType.COPY, listOf(FileNodeId.ftp("s", "/x/a")), null)),
        )
        assertEquals(emptySet<FileNodeId>(), foldersTouchedBy(op(OperationType.RESTORE_FROM_TRASH, listOf(a), null)))
    }
}
