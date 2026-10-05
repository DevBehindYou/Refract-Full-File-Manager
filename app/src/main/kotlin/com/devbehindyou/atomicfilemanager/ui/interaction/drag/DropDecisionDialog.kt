package com.devbehindyou.atomicfilemanager.ui.interaction.drag

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet

/**
 * Asks what a drop should do: copy or move into a folder, or stage into a transfer bubble.
 * Dismissing the sheet cancels the drop; nothing happens until a button is pressed.
 */
@Composable
fun DropDecisionDialog(
    payload: DragPayload,
    target: ActiveDropTarget,
    onDecision: (DropDecision) -> Unit,
) {
    val itemCount = payload.selectionCount
    val targetName = target.displayName
    val cancel = { onDecision(DropDecision.Cancel) }

    if (target.type == DropTargetType.TRANSFER_BUBBLE) {
        val bubbleId = target.destinationId.raw.removePrefix("atomic://bubble/")
        AtomicSheet(label = "Add to $targetName", onDismiss = cancel) {
            AtomicText(
                if (itemCount == 1) {
                    "Stage \"${payload.items.first().displayName}\" in $targetName to move or copy later."
                } else {
                    "Stage $itemCount items in $targetName to move or copy later."
                },
                AtomicTextRole.Body,
            )
            AtomicButton(
                "Add to bubble",
                onClick = { onDecision(DropDecision.AddToBubble(payload, bubbleId)) },
                leadingIcon = AtomicIcons.Bubble,
                modifier = Modifier.fillMaxWidth(),
            )
            AtomicButton(
                "Cancel",
                onClick = cancel,
                variant = AtomicButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    AtomicSheet(label = if (itemCount == 1) "Drop 1 item" else "Drop $itemCount items", onDismiss = cancel) {
        AtomicText(
            if (itemCount == 1) {
                "Copy or move \"${payload.items.first().name}\" into \"$targetName\"?"
            } else {
                "Copy or move $itemCount items into \"$targetName\"?"
            },
            AtomicTextRole.Body,
        )
        AtomicButton(
            "Move here",
            onClick = { onDecision(DropDecision.Move(payload, target.destinationId)) },
            leadingIcon = AtomicIcons.Move,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton(
            "Copy here",
            onClick = { onDecision(DropDecision.Copy(payload, target.destinationId)) },
            variant = AtomicButtonVariant.Solid,
            leadingIcon = AtomicIcons.Copy,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton(
            "Cancel",
            onClick = cancel,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
