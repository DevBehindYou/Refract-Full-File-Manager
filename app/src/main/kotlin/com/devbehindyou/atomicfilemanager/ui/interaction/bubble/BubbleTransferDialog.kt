package com.devbehindyou.atomicfilemanager.ui.interaction.bubble

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicToggleCard
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble

/** Move or copy everything staged in [bubble] into the current folder. Move always empties the bubble. */
@Composable
fun BubbleTransferDialog(
    bubble: TransferBubble,
    targetDirectoryName: String,
    onMove: (clearAfterTransfer: Boolean) -> Unit,
    onCopy: (clearAfterTransfer: Boolean) -> Unit,
    onReviewFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    var clearAfterCopy by rememberSaveable { mutableStateOf(false) }
    val items = if (bubble.itemCount == 1) "1 item" else "${bubble.itemCount} items"

    AtomicSheet(label = "Transfer from ${bubble.displayName}", onDismiss = onDismiss) {
        AtomicText("Put $items into \"$targetDirectoryName\".", AtomicTextRole.Body)
        AtomicToggleCard(
            title = "Empty bubble after copy",
            description = "Moving always empties it.",
            checked = clearAfterCopy,
            onCheckedChange = { clearAfterCopy = it },
        )
        // Move clears the transferred items from the bubble.
        AtomicButton(
            "Move here",
            onClick = { onMove(true) },
            leadingIcon = AtomicIcons.Move,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton(
            "Copy here",
            onClick = { onCopy(clearAfterCopy) },
            variant = AtomicButtonVariant.Solid,
            leadingIcon = AtomicIcons.Copy,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton(
            "Review files",
            onClick = onReviewFiles,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton("Cancel", onClick = onDismiss, variant = AtomicButtonVariant.Text)
    }
}
