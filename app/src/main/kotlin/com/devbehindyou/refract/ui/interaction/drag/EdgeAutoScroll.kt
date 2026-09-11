package com.devbehindyou.refract.ui.interaction.drag

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay

/**
 * Automatically scrolls a LazyListState when a dragged item hovers near the top or bottom edges.
 */
@Composable
fun Modifier.edgeAutoScroll(
    controller: FileDragController,
    lazyListState: LazyListState,
    edgeThresholdPx: Float = 160f,
): Modifier {
    var listBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(controller.isDragging, controller.dragPosition) {
        if (!controller.isDragging) return@LaunchedEffect
        val bounds = listBounds ?: return@LaunchedEffect
        val pointerY = controller.dragPosition.y

        // Check top edge
        val distFromTop = pointerY - bounds.top
        val distFromBottom = bounds.bottom - pointerY

        if (distFromTop in 0f..edgeThresholdPx) {
            val speedFactor = ((edgeThresholdPx - distFromTop) / edgeThresholdPx).coerceIn(0.1f, 1.0f)
            val scrollDelta = -20f * speedFactor
            while (controller.isDragging && (controller.dragPosition.y - bounds.top) in 0f..edgeThresholdPx) {
                lazyListState.scrollBy(scrollDelta)
                delay(16)
            }
        } else if (distFromBottom in 0f..edgeThresholdPx) {
            val speedFactor = ((edgeThresholdPx - distFromBottom) / edgeThresholdPx).coerceIn(0.1f, 1.0f)
            val scrollDelta = 20f * speedFactor
            while (controller.isDragging && (bounds.bottom - controller.dragPosition.y) in 0f..edgeThresholdPx) {
                lazyListState.scrollBy(scrollDelta)
                delay(16)
            }
        }
    }

    return this.onGloballyPositioned { coordinates ->
        listBounds = coordinates.boundsInWindow()
    }
}
