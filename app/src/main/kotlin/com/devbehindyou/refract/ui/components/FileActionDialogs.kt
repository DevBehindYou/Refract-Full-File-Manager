package com.devbehindyou.refract.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.ui.util.FileUtils

@Composable
fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit,
) {
    var folderName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        isError = it.isBlank() || it.contains("/")
                    },
                    label = { Text("Folder Name") },
                    isError = isError,
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("new_folder_input"),
                )
                if (isError) {
                    Text(
                        text = "Name cannot be empty or contain '/'",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank() && !folderName.contains("/")) {
                        onConfirm(folderName.trim())
                    }
                },
                enabled = folderName.isNotBlank() && !folderName.contains("/"),
                modifier = Modifier.testTag("confirm_create_folder_button"),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_create_folder_button"),
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    isError = it.isBlank() || it.contains("/")
                },
                label = { Text("New name") },
                isError = isError,
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("rename_input"),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && !name.contains("/")) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank() && !name.contains("/"),
                modifier = Modifier.testTag("confirm_rename_button"),
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_rename_button"),
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun FileDetailsDialog(
    node: FileNode,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (node.isDirectory) "Folder Details" else "File Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DetailRow(label = "Name", value = node.name)
                DetailRow(label = "Path", value = node.id.raw.removePrefix("file:"))
                if (!node.isDirectory) {
                    DetailRow(label = "Size", value = FileUtils.formatBytes(node.size))
                    DetailRow(label = "MIME Type", value = node.mimeType ?: "Unknown")
                } else if (node.childCount != null) {
                    DetailRow(label = "Items", value = "${node.childCount}")
                }
                DetailRow(label = "Modified", value = FileUtils.formatDate(node.modifiedAt))
                DetailRow(
                    label = "Permissions",
                    value =
                        listOfNotNull(
                            if (node.access.readable) "Read" else null,
                            if (node.access.writable) "Write" else null,
                            if (node.access.deletable) "Delete" else null,
                        ).joinToString(", "),
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}
