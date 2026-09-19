package com.devbehindyou.refract.ui.interaction.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.TransferBubble
import com.devbehindyou.refract.ui.interaction.drag.ActiveDropTarget
import com.devbehindyou.refract.ui.interaction.drag.DropTargetType
import com.devbehindyou.refract.ui.interaction.drag.FileDragController
import com.devbehindyou.refract.ui.interaction.drag.fileDropTarget
import kotlin.math.roundToInt

@Composable
fun TransferBubbleRail(
    bubbles: List<TransferBubble>,
    dragController: FileDragController,
    onBubbleClick: (TransferBubble) -> Unit,
    onBubbleLongClick: (TransferBubble) -> Unit,
    onCreateBubble: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("bubble_position", android.content.Context.MODE_PRIVATE) }
    var fractionX by remember {
        mutableFloatStateOf(if (preferences.getFloat("x", 1f) < 0.5f) 0f else 1f)
    }
    var fractionY by remember { mutableFloatStateOf(preferences.getFloat("y", 0.5f).coerceIn(0f, 1f)) }
    var railSize by remember { mutableStateOf(IntSize.Zero) }
    var isRepositioning by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    var dragDistance by remember { mutableStateOf(Offset.Zero) }
    val displayedX by animateFloatAsState(
        targetValue = fractionX,
        animationSpec = if (isRepositioning) snap() else spring(),
        label = "bubbleEdgeDock",
    )

    fun savePosition() {
        preferences.edit().putFloat("x", fractionX).putFloat("y", fractionY).apply()
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(top = 64.dp, bottom = 64.dp)) {
        val density = LocalDensity.current
        val maxX = (with(density) { maxWidth.toPx() } - railSize.width).coerceAtLeast(0f)
        val maxY = (with(density) { maxHeight.toPx() } - railSize.height).coerceAtLeast(0f)
        Column(
            modifier =
                Modifier
                    .absoluteOffset { IntOffset((displayedX * maxX).roundToInt(), (fractionY * maxY).roundToInt()) }
                    .onSizeChanged { railSize = it }
                    .pointerInput(maxX, maxY) {
                        detectDragGestures(
                            onDragStart = {
                                isRepositioning = true
                                velocityTracker.resetTracking()
                                dragDistance = Offset.Zero
                            },
                            onDragEnd = {
                                isRepositioning = false
                                fractionX = bubbleDockEdge(fractionX, velocityTracker.calculateVelocity().x, maxX)
                                savePosition()
                            },
                            onDragCancel = {
                                isRepositioning = false
                                fractionX = bubbleDockEdge(fractionX, 0f, maxX)
                                savePosition()
                            },
                        ) { change, amount ->
                            change.consume()
                            dragDistance += amount
                            velocityTracker.addPosition(change.uptimeMillis, dragDistance)
                            if (maxX > 0f) fractionX = (fractionX + amount.x / maxX).coerceIn(0f, 1f)
                            if (maxY > 0f) fractionY = (fractionY + amount.y / maxY).coerceIn(0f, 1f)
                        }
                    }
                    .semantics {
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Move bubbles left") {
                                    fractionX = 0f
                                    savePosition()
                                    true
                                },
                                CustomAccessibilityAction("Move bubbles right") {
                                    fractionX = 1f
                                    savePosition()
                                    true
                                },
                                CustomAccessibilityAction("Move bubbles up") {
                                    fractionY = (fractionY - 0.25f).coerceAtLeast(0f)
                                    savePosition()
                                    true
                                },
                                CustomAccessibilityAction("Move bubbles down") {
                                    fractionY = (fractionY + 0.25f).coerceAtMost(1f)
                                    savePosition()
                                    true
                                },
                            )
                    }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            for (bubble in bubbles) {
                BubbleItem(
                    bubble = bubble,
                    dragController = dragController,
                    onClick = { onBubbleClick(bubble) },
                    onLongClick = { onBubbleLongClick(bubble) },
                )
            }

            if (bubbles.size < TransferBubble.MAX_BUBBLES) {
                FloatingActionButton(
                    onClick = onCreateBubble,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Transfer Bubble",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

internal fun bubbleDockEdge(
    fraction: Float,
    velocityX: Float,
    travelWidth: Float,
): Float {
    val projected = fraction + if (travelWidth > 0f) velocityX * 0.2f / travelWidth else 0f
    return if (projected < 0.5f) 0f else 1f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleItem(
    bubble: TransferBubble,
    dragController: FileDragController,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val bubbleNodeId = FileNodeId("refract://bubble/${bubble.id}")

    val isTarget = dragController.currentDropTarget?.destinationId == bubbleNodeId
    val scale by animateFloatAsState(
        targetValue = if (isTarget) 1.15f else 1.0f,
        label = "bubbleScale",
    )
    val containerColor by animateColorAsState(
        targetValue =
            if (isTarget) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        label = "bubbleColor",
    )

    val elevation = if (isTarget) 8.dp else 4.dp

    Surface(
        shape = CircleShape,
        color = containerColor,
        tonalElevation = elevation,
        shadowElevation = elevation,
        modifier =
            modifier
                .scale(scale)
                .size(56.dp)
                .fileDropTarget(
                    controller = dragController,
                    target =
                        ActiveDropTarget(
                            id = bubbleNodeId.raw,
                            destinationId = bubbleNodeId,
                            type = DropTargetType.TRANSFER_BUBBLE,
                            displayName = bubble.displayName,
                        ),
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp),
        ) {
            BadgedBox(
                badge = {
                    AnimatedVisibility(
                        visible = bubble.itemCount > 0,
                        enter = scaleIn(),
                        exit = scaleOut(),
                    ) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text(text = "${bubble.itemCount}")
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Default.AllInbox,
                    contentDescription = bubble.displayName,
                    tint =
                        if (isTarget) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
