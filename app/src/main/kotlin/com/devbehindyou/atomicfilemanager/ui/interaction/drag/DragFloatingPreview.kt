package com.devbehindyou.atomicfilemanager.ui.interaction.drag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicBadge
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicElevation
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.hardShadow
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
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

/** Card look shared by the drag badges: white card, ink border, hard offset shadow. */
@Composable
private fun Modifier.dragCard(): Modifier {
    val colors = Atomic.colors
    return this
        .hardShadow(AtomicElevation.level3, colors.shadow, AtomicShape.sm)
        .background(colors.surfaceCard, AtomicShape.sm)
        .border(AtomicBorder.structure, colors.borderStrong, AtomicShape.sm)
}

@Composable
private fun SingleItemDragBadge(
    item: FileNode,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.dragCard().padding(horizontal = AtomicSpacing.s12, vertical = AtomicSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        Icon(
            getIconForNode(item),
            contentDescription = null,
            tint = Atomic.colors.content,
            modifier = Modifier.size(AtomicSize.iconSmall),
        )
        AtomicText(item.name, AtomicTextRole.Name, maxLines = 1, modifier = Modifier.widthIn(max = BADGE_NAME_WIDTH))
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
        // Stacked cards behind the top one, offset like the hard shadow.
        for (i in (stackCount - 1) downTo 1) {
            Box(
                Modifier
                    .offset(x = AtomicSpacing.s6 * i, y = AtomicSpacing.s6 * i)
                    .size(width = STACK_WIDTH, height = STACK_HEIGHT)
                    .dragCard(),
            )
        }
        Row(
            modifier = Modifier.dragCard().padding(horizontal = AtomicSpacing.s12, vertical = AtomicSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            Icon(
                getIconForNode(items.first()),
                contentDescription = null,
                tint = Atomic.colors.content,
                modifier = Modifier.size(AtomicSize.iconSmall),
            )
            AtomicText(
                items.first().name,
                AtomicTextRole.Name,
                maxLines = 1,
                modifier = Modifier.widthIn(max = MULTI_NAME_WIDTH),
            )
            AtomicBadge(totalCount)
        }
    }
}

private fun getIconForNode(node: FileNode): ImageVector =
    when {
        node.isDirectory -> AtomicIcons.Files
        node.mimeType?.startsWith("image/") == true -> AtomicIcons.Image
        else -> AtomicIcons.Document
    }

private val BADGE_NAME_WIDTH = 200.dp
private val MULTI_NAME_WIDTH = 100.dp
private val STACK_WIDTH = 160.dp
private val STACK_HEIGHT = 44.dp
