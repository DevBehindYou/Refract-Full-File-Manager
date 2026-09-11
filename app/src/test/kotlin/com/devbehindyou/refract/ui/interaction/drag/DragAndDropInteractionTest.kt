package com.devbehindyou.refract.ui.interaction.drag

import androidx.compose.ui.geometry.Offset
import com.devbehindyou.refract.domain.model.AccessFlags
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DragAndDropInteractionTest {

    private lateinit var controller: FileDragController
    private val parentId = FileNodeId.file("/storage/emulated/0/Documents")
    private val targetFolderId = FileNodeId.file("/storage/emulated/0/Documents/Projects")

    private fun createTestNode(
        id: FileNodeId,
        name: String,
        isDirectory: Boolean,
        size: Long = 0L,
        mimeType: String? = null,
    ): FileNode = FileNode(
        id = id,
        name = name,
        displayName = name,
        mimeType = mimeType,
        size = size,
        modifiedAt = 1000L,
        isDirectory = isDirectory,
        isHidden = false,
        parentId = parentId,
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL,
        childCount = if (isDirectory) 0 else null,
        extras = null,
    )

    private val file1 by lazy {
        createTestNode(
            id = FileNodeId.file("/storage/emulated/0/Documents/report.pdf"),
            name = "report.pdf",
            isDirectory = false,
            size = 1024L,
            mimeType = "application/pdf",
        )
    }

    private val file2 by lazy {
        createTestNode(
            id = FileNodeId.file("/storage/emulated/0/Documents/budget.xlsx"),
            name = "budget.xlsx",
            isDirectory = false,
            size = 2048L,
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )
    }

    @BeforeEach
    fun setUp() {
        controller = FileDragController()
    }

    @Test
    fun `single item drag initialises session correctly`() {
        val payload = controller.startDrag(listOf(file1), parentId, Offset(100f, 200f))

        assertTrue(controller.isDragging)
        assertEquals(1, payload.selectionCount)
        assertEquals(1024L, payload.estimatedBytes)
        assertEquals(parentId, payload.originLocation)
        assertEquals(Offset(100f, 200f), controller.dragPosition)
    }

    @Test
    fun `multi selection drag calculates total count and estimated bytes`() {
        val payload = controller.startDrag(listOf(file1, file2), parentId)

        assertTrue(controller.isDragging)
        assertEquals(2, payload.selectionCount)
        assertEquals(3072L, payload.estimatedBytes)
        assertEquals(listOf(file1.id, file2.id), payload.itemIds)
    }

    @Test
    fun `hover enter and exit updates active target`() {
        controller.startDrag(listOf(file1), parentId)
        val target = ActiveDropTarget(
            id = "projects",
            destinationId = targetFolderId,
            type = DropTargetType.FOLDER,
            displayName = "Projects",
            isWritable = true,
        )

        controller.onDragEnter(target)
        assertEquals(target, controller.currentDropTarget)

        controller.onDragExit(target)
        assertNull(controller.currentDropTarget)
    }

    @Test
    fun `dropping on writable destination sets pending decision`() {
        controller.startDrag(listOf(file1), parentId)
        val target = ActiveDropTarget(
            id = "projects",
            destinationId = targetFolderId,
            type = DropTargetType.FOLDER,
            displayName = "Projects",
            isWritable = true,
        )

        controller.onDrop(target)

        assertFalse(controller.isDragging)
        assertNotNull(controller.pendingDecision)
        val (payload, decidedTarget) = controller.pendingDecision!!
        assertEquals(target, decidedTarget)
        assertEquals(1, payload.selectionCount)
    }

    @Test
    fun `dropping on read only destination does not set pending decision`() {
        controller.startDrag(listOf(file1), parentId)
        val readOnlyTarget = ActiveDropTarget(
            id = "system",
            destinationId = FileNodeId.file("/system"),
            type = DropTargetType.FOLDER,
            displayName = "System",
            isWritable = false,
        )

        controller.onDrop(readOnlyTarget)

        assertFalse(controller.isDragging)
        assertNull(controller.pendingDecision)
    }

    @Test
    fun `dropping in same folder does not trigger transfer decision`() {
        controller.startDrag(listOf(file1), parentId)
        val sameFolderTarget = ActiveDropTarget(
            id = "documents",
            destinationId = parentId,
            type = DropTargetType.FOLDER,
            displayName = "Documents",
            isWritable = true,
        )

        controller.onDrop(sameFolderTarget)

        assertFalse(controller.isDragging)
        assertNull(controller.pendingDecision)
    }

    @Test
    fun `spring loaded folder hover updates progress and folder id`() {
        controller.startDrag(listOf(file1), parentId)

        controller.setFolderHover(targetFolderId, 0.5f)
        assertEquals(targetFolderId, controller.hoveredFolderId)
        assertEquals(0.5f, controller.hoverProgress)

        controller.setFolderHover(targetFolderId, 1.5f) // Clamped to 1f
        assertEquals(1.0f, controller.hoverProgress)

        controller.setFolderHover(null, 0f)
        assertNull(controller.hoveredFolderId)
        assertEquals(0f, controller.hoverProgress)
    }

    @Test
    fun `cancelling drag resets all controller state`() {
        controller.startDrag(listOf(file1), parentId)
        controller.setFolderHover(targetFolderId, 0.4f)

        controller.cancelDrag()

        assertFalse(controller.isDragging)
        assertNull(controller.activeSession)
        assertNull(controller.currentDropTarget)
        assertNull(controller.hoveredFolderId)
        assertEquals(0f, controller.hoverProgress)
    }

    @Test
    fun `dropping onto secondary browse panel triggers transfer decision`() {
        val secondaryPanelId = FileNodeId.file("/storage/emulated/0/Download")
        controller.startDrag(listOf(file1, file2), parentId)

        val panelTarget = ActiveDropTarget(
            id = secondaryPanelId.raw,
            destinationId = secondaryPanelId,
            type = DropTargetType.PANE,
            displayName = "Downloads (Pane B)",
            isWritable = true,
        )

        controller.onDrop(panelTarget)

        assertFalse(controller.isDragging)
        val decision = controller.pendingDecision
        assertNotNull(decision)
        assertEquals(2, decision!!.first.selectionCount)
        assertEquals(panelTarget, decision.second)
    }

    @Test
    fun `dropping onto read-only panel is rejected without decision`() {
        val readOnlyPanelId = FileNodeId.file("/system/etc")
        controller.startDrag(listOf(file1), parentId)

        val readOnlyTarget = ActiveDropTarget(
            id = readOnlyPanelId.raw,
            destinationId = readOnlyPanelId,
            type = DropTargetType.PANE,
            displayName = "System (Read Only)",
            isWritable = false,
        )

        controller.onDrop(readOnlyTarget)

        assertFalse(controller.isDragging)
        assertNull(controller.pendingDecision)
    }
}
