package com.devbehindyou.refract.ui.interaction.drag

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import kotlinx.coroutines.delay

/**
 * Modifier enabling an item to initiate a drag-and-drop session.
 */
@Composable
fun Modifier.fileDragSource(
    controller: FileDragController,
    node: FileNode,
    selectedNodes: List<FileNode>,
    originLocation: FileNodeId,
    onDragStarted: () -> Unit = {},
): Modifier {
    val haptic = LocalHapticFeedback.current
    var isCurrentDragSource by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isCurrentDragSource) 1.03f else 1.0f,
        label = "dragSourceScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (isCurrentDragSource) 8.dp else 0.dp,
        label = "dragSourceElevation",
    )

    return this
        .scale(scale)
        .shadow(elevation, shape = RoundedCornerShape(12.dp))
        .pointerInput(node.id, selectedNodes) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    isCurrentDragSource = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val itemsToDrag =
                        if (selectedNodes.any { it.id == node.id }) {
                            selectedNodes
                        } else {
                            listOf(node)
                        }
                    controller.startDrag(
                        items = itemsToDrag,
                        originLocation = originLocation,
                        startOffset = offset,
                    )
                    onDragStarted()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val currentPos = controller.dragPosition
                    controller.updateDragPosition(currentPos + dragAmount)
                },
                onDragEnd = {
                    isCurrentDragSource = false
                    controller.currentDropTarget?.let { target ->
                        controller.onDrop(target)
                    } ?: controller.cancelDrag()
                },
                onDragCancel = {
                    isCurrentDragSource = false
                    controller.cancelDrag()
                },
            )
        }
}

/**
 * Modifier marking a composable as a drop target (e.g. folder row or breadcrumb).
 */
@Composable
fun Modifier.fileDropTarget(
    controller: FileDragController,
    target: ActiveDropTarget,
    onHoverSpringOpen: (() -> Unit)? = null,
): Modifier {
    val haptic = LocalHapticFeedback.current
    var targetBounds by remember { mutableStateOf<Rect?>(null) }
    val isTargetActive = controller.currentDropTarget?.id == target.id

    // Hover delay detection for spring-loaded folder navigation (600ms)
    LaunchedEffect(isTargetActive) {
        if (isTargetActive && onHoverSpringOpen != null && target.type == DropTargetType.FOLDER) {
            val startTime = System.currentTimeMillis()
            val duration = 600L
            while (controller.currentDropTarget?.id == target.id) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                controller.setFolderHover(target.destinationId, progress)
                if (progress >= 1f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHoverSpringOpen()
                    break
                }
                delay(30)
            }
        } else {
            if (controller.hoveredFolderId == target.destinationId) {
                controller.setFolderHover(null, 0f)
            }
        }
    }

    // Monitor pointer position during drag
    LaunchedEffect(controller.dragPosition, controller.isDragging) {
        if (!controller.isDragging) {
            if (controller.currentDropTarget?.id == target.id) {
                controller.onDragExit(target)
            }
            return@LaunchedEffect
        }

        val bounds = targetBounds ?: return@LaunchedEffect
        val pointer = controller.dragPosition
        val contains = bounds.contains(pointer)

        if (contains && controller.currentDropTarget?.id != target.id) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            controller.onDragEnter(target)
        } else if (!contains && controller.currentDropTarget?.id == target.id) {
            controller.onDragExit(target)
        }
    }

    val highlightColor by animateColorAsState(
        targetValue = if (isTargetActive) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "dropHighlightColor",
    )

    return this
        .onGloballyPositioned { coordinates ->
            targetBounds = coordinates.boundsInWindow()
        }
        .border(
            width = if (isTargetActive) 2.dp else 0.dp,
            color = highlightColor,
            shape = RoundedCornerShape(12.dp),
        )
}
