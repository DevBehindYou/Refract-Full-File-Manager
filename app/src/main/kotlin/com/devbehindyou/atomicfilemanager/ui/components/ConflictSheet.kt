package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicCheckbox
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFact
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFactSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.data.operations.PendingConflict
import com.devbehindyou.atomicfilemanager.domain.model.Conflict
import com.devbehindyou.atomicfilemanager.domain.model.ConflictChoice
import com.devbehindyou.atomicfilemanager.domain.model.ConflictDecision
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

/**
 * Name-clash sheet (screens/OPERATIONS.md §6): both items side by side, Replace / Keep both /
 * Skip and "Apply to all remaining". Dismissing it decides nothing; the operation keeps waiting
 * and the Operations screen can reopen it. Replace is not offered for folders (no merge yet).
 */
@Composable
fun ConflictSheet(
    pending: PendingConflict,
    onDecide: (ConflictDecision) -> Unit,
    onCancelOperation: () -> Unit,
    onDismiss: () -> Unit,
) {
    val conflict = pending.conflict
    val isFolder = conflict.source.isDirectory
    var applyToAll by rememberSaveable(pending) { mutableStateOf(false) }
    val decide: (ConflictChoice) -> Unit = { onDecide(ConflictDecision(it, applyToAll)) }

    AtomicSheet(label = ConflictText.label(pending.operation), onDismiss = onDismiss) {
        Column(Modifier.testTag("conflict_sheet"), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
            AtomicText(ConflictText.headline(conflict), AtomicTextRole.DisplayPushed)
            AtomicText(ConflictText.body(pending.operation, conflict), AtomicTextRole.Body)
            AtomicSectionLabel(if (isFolder) "Folder you are adding" else "File you are adding")
            AtomicFactSheet(ConflictText.facts(conflict.source))
            AtomicSectionLabel("Already there")
            AtomicFactSheet(ConflictText.facts(conflict.existingDestination))
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = applyToAll, role = Role.Checkbox, onValueChange = { applyToAll = it })
                    .testTag("conflict_apply_all"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            ) {
                AtomicCheckbox(checked = applyToAll, onCheckedChange = null)
                AtomicText("Do this for every other name clash in this operation", AtomicTextRole.Body)
            }
        }
        if (!isFolder) {
            AtomicButton(
                "Replace",
                onClick = { decide(ConflictChoice.REPLACE) },
                variant = AtomicButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth().testTag("conflict_replace"),
            )
        }
        AtomicButton(
            "Keep both",
            onClick = { decide(ConflictChoice.KEEP_BOTH) },
            variant = AtomicButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth().testTag("conflict_keep_both"),
        )
        AtomicButton(
            "Skip",
            onClick = { decide(ConflictChoice.SKIP) },
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth().testTag("conflict_skip"),
        )
        AtomicButton(
            "Cancel ${ConflictText.verb(pending.operation.type).lowercase()}",
            onClick = onCancelOperation,
            variant = AtomicButtonVariant.Text,
            modifier = Modifier.fillMaxWidth().testTag("conflict_cancel_operation"),
        )
    }
}

/** Wording for [ConflictSheet]; pure so it is unit-tested. */
internal object ConflictText {
    fun verb(type: OperationType): String =
        when (type) {
            OperationType.MOVE -> "Move"
            else -> "Copy"
        }

    fun label(operation: FileOperation): String = "${verb(operation.type)} · name already used"

    fun headline(conflict: Conflict): String =
        if (conflict.source.isDirectory) "Folder already exists" else "File already exists"

    fun body(
        operation: FileOperation,
        conflict: Conflict,
    ): String {
        val folder = folderName(operation, conflict)
        val base = "“${conflict.source.name}” is already in $folder."
        return if (conflict.source.isDirectory) {
            "$base Folders can't be replaced yet: keep both adds a numbered copy, skip leaves it out."
        } else {
            "$base Replace swaps it only after the new copy is checked; keep both adds a numbered copy."
        }
    }

    fun facts(node: FileNode): List<AtomicFact> =
        listOfNotNull(
            AtomicFact("Name", node.name),
            if (node.isDirectory) null else AtomicFact("Size", FileUtils.formatBytes(node.size), monoValue = true),
            AtomicFact("Modified", FileUtils.formatDate(node.modifiedAt), monoValue = true),
        )

    private fun folderName(
        operation: FileOperation,
        conflict: Conflict,
    ): String {
        val raw = (conflict.existingDestination.parentId ?: operation.destination)?.raw.orEmpty()
        val name = raw.substringAfter(':').trimEnd('/').substringAfterLast('/')
        return if (name.isEmpty()) "this folder" else "“$name”"
    }
}
