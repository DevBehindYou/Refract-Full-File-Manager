package com.devbehindyou.refract.ui.interaction.drag

import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId

/**
 * Payload carried during an active file drag-and-drop session.
 */
data class DragPayload(
    val sessionId: String,
    val itemIds: List<FileNodeId>,
    val items: List<FileNode>,
    val originLocation: FileNodeId,
    val selectionCount: Int,
    val estimatedBytes: Long,
)

enum class DropTargetType {
    FOLDER,
    BREADCRUMB,
    PANE,
    TRANSFER_BUBBLE,
}

data class ActiveDropTarget(
    val id: String,
    val destinationId: FileNodeId,
    val type: DropTargetType,
    val displayName: String,
    val isWritable: Boolean = true,
)

sealed interface DropDecision {
    data class Move(val payload: DragPayload, val destination: FileNodeId) : DropDecision

    data class Copy(val payload: DragPayload, val destination: FileNodeId) : DropDecision

    data class AddToBubble(val payload: DragPayload, val bubbleId: String) : DropDecision

    data object Cancel : DropDecision
}
