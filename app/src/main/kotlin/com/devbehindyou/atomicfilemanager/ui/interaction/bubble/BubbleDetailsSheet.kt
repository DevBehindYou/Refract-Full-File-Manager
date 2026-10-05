package com.devbehindyou.atomicfilemanager.ui.interaction.bubble

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicTitleRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

/** What a transfer bubble holds, with per-item remove, clear, and delete-bubble actions. */
@Composable
fun BubbleDetailsSheet(
    bubble: TransferBubble,
    onRemoveItem: (Long) -> Unit,
    onClearBubble: () -> Unit,
    onDeleteBubble: () -> Unit,
    onDismiss: () -> Unit,
) {
    AtomicSheet(label = "Transfer bubble", onDismiss = onDismiss) {
        AtomicTitleRow(
            title = bubble.displayName,
            counter = "${bubble.itemCount} items · ${FileUtils.formatBytes(bubble.totalKnownSize)}",
        )
        if (bubble.items.isEmpty()) {
            AtomicEmptyState(
                message = "This bubble is empty.",
                detail = "Drag files onto it, or select files and choose Add to bubble.",
            )
        } else {
            Column {
                bubble.items.forEach { item ->
                    AtomicFileRow(
                        name = item.displayNameSnapshot,
                        meta = FileUtils.formatBytes(item.sizeSnapshot),
                        icon = AtomicIcons.Document,
                        onClick = {},
                        trailing = {
                            AtomicIconButton(
                                AtomicIcons.Close,
                                "Remove ${item.displayNameSnapshot} from bubble",
                                onClick = { onRemoveItem(item.id) },
                            )
                        },
                    )
                }
            }
        }
        AtomicButton(
            "Empty bubble",
            onClick = onClearBubble,
            enabled = bubble.items.isNotEmpty(),
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton(
            "Delete bubble",
            onClick = onDeleteBubble,
            variant = AtomicButtonVariant.Destructive,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
