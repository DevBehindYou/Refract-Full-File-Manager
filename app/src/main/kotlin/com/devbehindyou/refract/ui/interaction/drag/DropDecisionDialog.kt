package com.devbehindyou.refract.ui.interaction.drag

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DropDecisionDialog(
    payload: DragPayload,
    target: ActiveDropTarget,
    onDecision: (DropDecision) -> Unit,
) {
    val itemCount = payload.selectionCount
    val targetName = target.displayName

    if (target.type == DropTargetType.TRANSFER_BUBBLE) {
        val bubbleId = target.destinationId.raw.removePrefix("refract://bubble/")
        AlertDialog(
            onDismissRequest = { onDecision(DropDecision.Cancel) },
            icon = {
                Icon(
                    imageVector = Icons.Default.AllInbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = {
                Text(
                    text = "Add to $targetName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text =
                        if (itemCount == 1) {
                            "Stage \"${payload.items.first().displayName}\" into $targetName for later transfer?"
                        } else {
                            "Stage $itemCount items into $targetName for later transfer?"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDecision(DropDecision.AddToBubble(payload, bubbleId)) },
                ) {
                    Text("Add to Bubble")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onDecision(DropDecision.Cancel) },
                ) {
                    Text("Cancel")
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { onDecision(DropDecision.Cancel) },
        icon = {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = if (itemCount == 1) "Transfer Item" else "Transfer $itemCount Items",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text =
                        if (itemCount == 1) {
                            "Choose action for \"${payload.items.first().name}\" to \"$targetName\":"
                        } else {
                            "Choose action for $itemCount items to \"$targetName\":"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onDecision(DropDecision.Copy(payload, target.destinationId)) },
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy here")
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { onDecision(DropDecision.Move(payload, target.destinationId)) },
                ) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Move here")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDecision(DropDecision.Cancel) },
            ) {
                Text("Cancel")
            }
        },
    )
}
