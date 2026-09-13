package com.devbehindyou.refract.ui.interaction.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.TransferBubble
import com.devbehindyou.refract.ui.interaction.drag.ActiveDropTarget
import com.devbehindyou.refract.ui.interaction.drag.DropTargetType
import com.devbehindyou.refract.ui.interaction.drag.FileDragController
import com.devbehindyou.refract.ui.interaction.drag.fileDropTarget

@Composable
fun TransferBubbleRail(
    bubbles: List<TransferBubble>,
    dragController: FileDragController,
    onBubbleClick: (TransferBubble) -> Unit,
    onBubbleLongClick: (TransferBubble) -> Unit,
    onCreateBubble: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(12.dp),
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
                modifier = Modifier.size(44.dp),
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
