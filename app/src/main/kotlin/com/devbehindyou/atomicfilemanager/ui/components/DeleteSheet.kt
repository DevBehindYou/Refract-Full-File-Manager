package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileNode

/**
 * Delete confirmation (ALL_IN_ONE_PLAN.md 1.1). Moves to the Trash when every item can go there
 * and the Trash is on; otherwise it says why and deletes for good. Says exactly what happens and
 * whether it can be undone (ATOMIC_UI_PLAN.md voice rules).
 */
@Composable
fun DeleteSheet(
    nodes: List<FileNode>,
    onConfirm: (toTrash: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (nodes.isEmpty()) return
    val container = (LocalContext.current.applicationContext as AtomicApp).container
    val settings by container.settingsRepository.settings.collectAsState()
    val trashable by produceState<Boolean?>(null, nodes) {
        value = nodes.all { container.trashManager.canTrash(it) }
    }
    val canTrash = trashable ?: return
    val toTrash = settings.useTrash && canTrash
    val reason =
        when {
            toTrash -> null
            !settings.useTrash -> DeleteText.Reason.TRASH_OFF
            else -> DeleteText.Reason.NO_TRASH_HERE
        }
    AtomicSheet(label = if (toTrash) "Move to Trash" else "Delete forever", onDismiss = onDismiss) {
        AtomicText(DeleteText.headline(nodes, toTrash), AtomicTextRole.DisplayPushed)
        AtomicText(
            DeleteText.body(nodes, toTrash, settings.trashRetentionDays, reason),
            AtomicTextRole.Body,
            modifier = Modifier.testTag("delete_sheet_body"),
        )
        AtomicButton(
            if (toTrash) "Move to Trash" else "Delete forever",
            onClick = { onConfirm(toTrash) },
            variant = if (toTrash) AtomicButtonVariant.Primary else AtomicButtonVariant.Destructive,
            modifier = Modifier.fillMaxWidth().testTag("delete_confirm"),
        )
        if (toTrash) {
            AtomicButton(
                "Delete forever instead",
                onClick = { onConfirm(false) },
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.fillMaxWidth().testTag("delete_forever_instead"),
            )
        }
        AtomicButton(
            "Cancel",
            onClick = onDismiss,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Wording for [DeleteSheet]; pure so it is unit-tested. */
internal object DeleteText {
    enum class Reason { TRASH_OFF, NO_TRASH_HERE }

    fun headline(
        nodes: List<FileNode>,
        toTrash: Boolean,
    ): String {
        val what =
            when {
                nodes.size > 1 -> "${nodes.size} items"
                nodes.first().isDirectory -> "folder"
                else -> "file"
            }
        return if (toTrash) "Move $what to Trash?" else "Delete $what forever?"
    }

    fun body(
        nodes: List<FileNode>,
        toTrash: Boolean,
        retentionDays: Int,
        reason: Reason?,
    ): String {
        val single = nodes.size == 1
        val subject = if (single) "“${nodes.first().name}”" else "${nodes.size} items"
        val inside = if (nodes.any { it.isDirectory }) ", with everything inside," else ""
        if (toTrash) {
            val them = if (single) "it" else "them"
            return "Moves $subject$inside to Trash. You can restore $them for $retentionDays days."
        }
        val why =
            when (reason) {
                Reason.TRASH_OFF -> "Trash is off in Settings. "
                Reason.NO_TRASH_HERE -> "This storage has no Trash. "
                null -> ""
            }
        return "${why}Deletes $subject$inside permanently. This can't be undone."
    }
}
