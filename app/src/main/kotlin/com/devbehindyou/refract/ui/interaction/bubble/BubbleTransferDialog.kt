package com.devbehindyou.refract.ui.interaction.bubble

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.TransferBubble

@Composable
fun BubbleTransferDialog(
    bubble: TransferBubble,
    targetDirectoryName: String,
    onMove: (clearAfterTransfer: Boolean) -> Unit,
    onCopy: (clearAfterTransfer: Boolean) -> Unit,
    onReviewFiles: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var clearAfterCopy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(text = "Transfer from ${bubble.displayName}")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Transfer ${bubble.itemCount} item(s) to \"$targetDirectoryName\"?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = clearAfterCopy,
                        onCheckedChange = { clearAfterCopy = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear bubble after transfer",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                Button(
                    // Move clears the transferred items from the bubble.
                    onClick = { onMove(true) },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text("Move here")
                }
                Button(
                    onClick = { onCopy(clearAfterCopy) },
                ) {
                    Text("Copy here")
                }
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    onClick = onReviewFiles,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text("Review")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        modifier = modifier,
    )
}
