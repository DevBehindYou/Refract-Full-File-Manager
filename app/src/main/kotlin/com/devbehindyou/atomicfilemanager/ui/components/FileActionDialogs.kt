package com.devbehindyou.atomicfilemanager.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFact
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFactSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.RemovableNames
import com.devbehindyou.atomicfilemanager.ui.shortcuts.AppShortcuts
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import java.util.Locale

private const val MAX_NAME_BYTES = 255

/**
 * Checks a file or folder name before it reaches the backend. Returns null when the name is
 * usable, otherwise one sentence that says how to fix it (spec §11: errors say what to do).
 */
fun fileNameError(
    name: String,
    removable: Boolean = false,
): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Enter a name."
        trimmed.contains('/') -> "A name can't contain \"/\". Try \"${trimmed.replace('/', ' ')}\"."
        trimmed.contains('\u0000') -> "A name can't contain hidden control characters."
        trimmed == "." || trimmed == ".." -> "\"$trimmed\" is reserved. Choose another name."
        trimmed.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES -> "That name is too long. Shorten it."
        removable -> RemovableNames.problem(trimmed)
        else -> null
    }
}

/** New folder sheet (canvas "Sheet · new folder"). [parentName] is shown as "In /Download". */
@Composable
fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit,
    parentName: String? = null,
    /** The folder is on a USB drive or SD card, so FAT/exFAT name rules apply. */
    removable: Boolean = false,
) {
    var folderName by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    val error = fileNameError(folderName, removable)

    AtomicSheet(label = if (parentName != null) "New folder in /$parentName" else "New folder", onDismiss = onDismiss) {
        AtomicTextField(
            value = folderName,
            onValueChange = {
                folderName = it
                touched = true
            },
            label = "Name",
            placeholder = "Tickets 2026",
            errorText = if (touched) error else null,
            modifier = Modifier.fillMaxWidth().testTag("new_folder_input"),
        )
        AtomicButton(
            "Create folder",
            onClick = { if (error == null) onConfirm(folderName.trim()) },
            enabled = error == null,
            modifier = Modifier.fillMaxWidth().testTag("confirm_create_folder_button"),
        )
        AtomicButton(
            "Cancel",
            onClick = onDismiss,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth().testTag("cancel_create_folder_button"),
        )
    }
}

@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit,
    /** The file is on a USB drive or SD card, so FAT/exFAT name rules apply. */
    removable: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val error = fileNameError(name, removable)
    val unchanged = name.trim() == initialName

    AtomicSheet(label = "Rename", onDismiss = onDismiss) {
        AtomicTextField(
            value = name,
            onValueChange = { name = it },
            label = "New name",
            errorText = error,
            modifier = Modifier.fillMaxWidth().testTag("rename_input"),
        )
        AtomicButton(
            "Rename",
            onClick = { if (error == null && !unchanged) onConfirm(name.trim()) },
            enabled = error == null && !unchanged,
            modifier = Modifier.fillMaxWidth().testTag("confirm_rename_button"),
        )
        AtomicButton(
            "Cancel",
            onClick = onDismiss,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth().testTag("cancel_rename_button"),
        )
    }
}

/** The full path for local files; other storage shows its node id without the scheme prefix. */
fun displayPath(node: FileNode): String = node.id.raw.removePrefix("file:")

/** Facts for [FileDetailsDialog]; pure so the content is unit-testable. */
fun fileFacts(node: FileNode): List<AtomicFact> =
    buildList {
        add(AtomicFact("Name", node.name))
        add(AtomicFact("Path", displayPath(node), monoValue = true))
        if (!node.isDirectory) {
            add(
                AtomicFact(
                    "Size",
                    if (node.size < 0) {
                        "Unknown"
                    } else {
                        "${FileUtils.formatBytes(node.size)} (${String.format(Locale.US, "%,d", node.size)} bytes)"
                    },
                    monoValue = true,
                ),
            )
            add(AtomicFact("Type", node.mimeType ?: "Unknown", monoValue = true))
        } else if (node.childCount != null) {
            add(AtomicFact("Items", "${node.childCount}", monoValue = true))
        }
        add(AtomicFact("Modified", FileUtils.formatDate(node.modifiedAt), monoValue = true))
        add(
            AtomicFact(
                "Access",
                listOfNotNull(
                    if (node.access.readable) "Read" else null,
                    if (node.access.writable) "Write" else null,
                    if (node.access.deletable) "Delete" else null,
                ).joinToString(" · ").ifEmpty { "None" },
                monoValue = true,
            ),
        )
    }

/** File info (spec §7.5) as a fact sheet. Checksums arrive with the operation queue. */
@Composable
fun FileDetailsDialog(
    node: FileNode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(node.id) { mutableStateOf(false) }
    AtomicSheet(label = if (node.isDirectory) "Folder info" else "File info", onDismiss = onDismiss) {
        AtomicFactSheet(fileFacts(node))
        localImageFile(node)?.let { ImageMetadataSection(node, it) }
        AtomicButton(
            if (copied) "Path copied" else "Copy path",
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Path", displayPath(node)))
                copied = clipboard != null
            },
            variant = AtomicButtonVariant.Ghost,
            leadingIcon = AtomicIcons.Copy,
            modifier = Modifier.fillMaxWidth().testTag("copy_path_button"),
        )
        if (node.isDirectory && AppShortcuts.canPinFolder(context)) {
            AtomicButton(
                "Add to home screen",
                onClick = { AppShortcuts.pinFolder(context, node.id.raw, node.name) },
                variant = AtomicButtonVariant.Ghost,
                leadingIcon = AtomicIcons.Add,
                modifier = Modifier.fillMaxWidth().testTag("pin_folder_button"),
            )
        }
        AtomicButton(
            "Close",
            onClick = onDismiss,
            variant = AtomicButtonVariant.Solid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
