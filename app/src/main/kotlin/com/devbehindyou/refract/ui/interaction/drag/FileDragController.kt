package com.devbehindyou.refract.ui.interaction.drag

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import java.util.UUID

/**
 * Central state controller for file drag-and-drop operations across Refract.
 */
class FileDragController {
    var activeSession by mutableStateOf<DragPayload?>(null)
        private set

    var dragPosition by mutableStateOf(Offset.Zero)
        private set

    var currentDropTarget by mutableStateOf<ActiveDropTarget?>(null)
        private set

    var pendingDecision by mutableStateOf<Pair<DragPayload, ActiveDropTarget>?>(null)
        private set

    var hoveredFolderId by mutableStateOf<FileNodeId?>(null)
        private set

    var hoverProgress by mutableFloatStateOf(0f)
        private set

    val isDragging: Boolean
        get() = activeSession != null

    fun startDrag(
        items: List<FileNode>,
        originLocation: FileNodeId,
        startOffset: Offset = Offset.Zero,
    ): DragPayload {
        val payload =
            DragPayload(
                sessionId = UUID.randomUUID().toString(),
                itemIds = items.map { it.id },
                items = items,
                originLocation = originLocation,
                selectionCount = items.size,
                estimatedBytes = items.sumOf { if (it.size > 0) it.size else 0L },
            )
        activeSession = payload
        dragPosition = startOffset
        currentDropTarget = null
        hoveredFolderId = null
        hoverProgress = 0f
        return payload
    }

    fun updateDragPosition(offset: Offset) {
        dragPosition = offset
    }

    fun onDragEnter(target: ActiveDropTarget) {
        if (activeSession != null) {
            currentDropTarget = target
        }
    }

    fun onDragExit(target: ActiveDropTarget) {
        if (currentDropTarget?.id == target.id) {
            currentDropTarget = null
            hoveredFolderId = null
            hoverProgress = 0f
        }
    }

    fun onDrop(target: ActiveDropTarget) {
        val session = activeSession
        if (session != null && target.isWritable) {
            // Prevent dropping onto the exact folder items are already in
            val isSameFolder = session.items.all { it.parentId == target.destinationId }
            if (!isSameFolder) {
                pendingDecision = Pair(session, target)
            }
        }
        endDrag()
    }

    fun cancelDrag() {
        endDrag()
    }

    fun clearPendingDecision() {
        pendingDecision = null
    }

    fun setFolderHover(
        folderId: FileNodeId?,
        progress: Float,
    ) {
        hoveredFolderId = folderId
        hoverProgress = progress.coerceIn(0f, 1f)
    }

    private fun endDrag() {
        activeSession = null
        currentDropTarget = null
        hoveredFolderId = null
        hoverProgress = 0f
    }
}
