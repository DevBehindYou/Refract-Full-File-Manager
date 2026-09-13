package com.devbehindyou.refract.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.ui.util.FileUtils

private data class FileTypeVisual(val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color)

private fun visualFor(node: FileNode): FileTypeVisual =
    when {
        node.isDirectory -> FileTypeVisual(Icons.Filled.Folder, Color(0xFFFFC107))
        node.mimeType?.startsWith("image/") == true -> FileTypeVisual(Icons.Filled.Image, Color(0xFF4CAF50))
        node.mimeType?.startsWith("video/") == true -> FileTypeVisual(Icons.Filled.Movie, Color(0xFFE91E63))
        node.mimeType?.startsWith("audio/") == true -> FileTypeVisual(Icons.Filled.AudioFile, Color(0xFF9C27B0))
        node.mimeType == "application/pdf" ||
            node.mimeType?.startsWith("text/") == true ||
            node.mimeType?.contains("document") == true -> FileTypeVisual(Icons.Filled.Description, Color(0xFF2196F3))
        node.mimeType?.contains("zip") == true ||
            node.mimeType?.contains("compressed") == true ||
            node.mimeType?.contains("archive") == true -> FileTypeVisual(Icons.Filled.FolderZip, Color(0xFF795548))
        node.mimeType == "application/vnd.android.package-archive" ->
            FileTypeVisual(
                Icons.Filled.Android,
                Color(0xFF8BC34A),
            )
        else -> FileTypeVisual(Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF9E9E9E))
    }

/**
 * [isSelectionMode] is derived by the caller from whether any item is currently selected
 * (`selectedIds.isNotEmpty()`), not tracked as separate state — see BrowseScreen.
 */
@Composable
fun FileListItem(
    node: FileNode,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onShowDetails: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExtract: (() -> Unit)? = null,
    onCompress: (() -> Unit)? = null,
    onQuickPeek: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val visual = visualFor(node)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.4f,
                        )
                    } else {
                        Color.Transparent
                    },
                )
                .combinedClickable(
                    onClick = { if (isSelectionMode) onToggleSelect() else onClick() },
                    onLongClick = onToggleSelect,
                )
                .semantics {
                    contentDescription =
                        buildString {
                            append(if (node.isDirectory) "Folder " else "File ")
                            append(node.name)
                            if (!node.isDirectory) {
                                append(", ")
                                append(FileUtils.formatBytes(node.size))
                            }
                        }
                    if (isSelectionMode) {
                        selected = isSelected
                        role = Role.Checkbox
                    }
                    customActions =
                        listOfNotNull(
                            CustomAccessibilityAction("Rename") {
                                onRename()
                                true
                            },
                            CustomAccessibilityAction("Delete") {
                                onDelete()
                                true
                            },
                            CustomAccessibilityAction("Details") {
                                onShowDetails()
                                true
                            },
                            if (onQuickPeek != null) {
                                CustomAccessibilityAction("Quick preview") {
                                    onQuickPeek()
                                    true
                                }
                            } else {
                                null
                            },
                            if (onHide != null) {
                                CustomAccessibilityAction("Hide") {
                                    onHide()
                                    true
                                }
                            } else {
                                null
                            },
                        )
                }
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("file_item_${node.id.raw}"),
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(visual.tint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    tint = visual.tint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
                Text(
                    text =
                        if (node.isDirectory) {
                            node.childCount?.let { "$it item${if (it == 1) "" else "s"}" } ?: "Folder"
                        } else {
                            "${FileUtils.formatBytes(node.size)} • ${FileUtils.formatDate(node.modifiedAt)}"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics { contentDescription = "More options for ${node.name}" }
                                .testTag("file_item_menu_${node.id.raw}"),
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        val isZip =
                            node.name.endsWith(".zip", ignoreCase = true) ||
                                node.mimeType?.contains("zip") == true
                        if (isZip && onExtract != null) {
                            DropdownMenuItem(text = { Text("Extract") }, onClick = {
                                showMenu = false
                                onExtract()
                            })
                        }
                        val isMedia =
                            node.mimeType?.startsWith("image/") == true ||
                                node.mimeType?.startsWith("video/") == true
                        if (isMedia && onQuickPeek != null) {
                            DropdownMenuItem(text = { Text("Quick preview") }, onClick = {
                                showMenu = false
                                onQuickPeek()
                            })
                        }
                        if (onCompress != null) {
                            DropdownMenuItem(text = { Text("Compress to ZIP") }, onClick = {
                                showMenu = false
                                onCompress()
                            })
                        }
                        if (onHide != null) {
                            DropdownMenuItem(text = { Text("Hide") }, onClick = {
                                showMenu = false
                                onHide()
                            })
                        }
                        DropdownMenuItem(text = { Text("Details") }, onClick = {
                            showMenu = false
                            onShowDetails()
                        })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = {
                            showMenu = false
                            onRename()
                        })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            showMenu = false
                            onDelete()
                        })
                    }
                }
            }
        }
    }
}
