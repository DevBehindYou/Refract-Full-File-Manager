package com.devbehindyou.refract.ui.interaction.drag

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileNode
import kotlin.math.roundToInt

@Composable
fun DragFloatingPreview(
    controller: FileDragController,
    modifier: Modifier = Modifier,
) {
    val session = controller.activeSession ?: return
    val pos = controller.dragPosition

    Box(
        modifier =
            modifier
                .offset { IntOffset(pos.x.roundToInt() + 16, pos.y.roundToInt() - 40) },
    ) {
        if (session.selectionCount > 1) {
            MultiItemDragBadge(
                items = session.items,
                totalCount = session.selectionCount,
            )
        } else {
            SingleItemDragBadge(item = session.items.first())
        }
    }
}

@Composable
private fun SingleItemDragBadge(
    item: FileNode,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .shadow(8.dp, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = getIconForNode(item),
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MultiItemDragBadge(
    items: List<FileNode>,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val stackCount = minOf(3, items.size)

    Box(modifier = modifier) {
        // Render stacked background card layers
        for (i in (stackCount - 1) downTo 1) {
            Surface(
                modifier =
                    Modifier
                        .offset(x = (i * 6).dp, y = (i * 6).dp)
                        .size(width = 160.dp, height = 48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = (4 - i).dp,
            ) {}
        }

        // Top card
        Surface(
            modifier =
                Modifier
                    .shadow(10.dp, shape = RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = getIconForNode(items.first()),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = items.first().name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(100.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        text = "$totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun getIconForNode(node: FileNode): ImageVector {
    return when {
        node.isDirectory -> Icons.Default.Folder
        node.mimeType?.startsWith("image/") == true -> Icons.Default.Image
        else -> Icons.Default.Description
    }
}
