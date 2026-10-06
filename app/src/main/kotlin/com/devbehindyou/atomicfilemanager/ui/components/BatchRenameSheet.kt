package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicSwitch
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicActivityRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.usecase.BatchRenameResult
import com.devbehindyou.atomicfilemanager.domain.usecase.BatchRenameRules
import com.devbehindyou.atomicfilemanager.domain.usecase.NameCase
import com.devbehindyou.atomicfilemanager.domain.usecase.Numbering
import com.devbehindyou.atomicfilemanager.domain.usecase.RenamePattern
import com.devbehindyou.atomicfilemanager.domain.usecase.RenamePreview
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameProblem
import kotlinx.coroutines.launch

private const val PREVIEW_ROWS = 8

/**
 * Batch rename (ALL_IN_ONE_PLAN.md 2.1): build a pattern, see every new name before anything
 * changes, then apply. Rename stays off while any name has a problem. Afterwards the sheet says
 * what happened and offers Undo until it is closed. [onChanged] runs after each apply or undo.
 */
@Composable
fun BatchRenameSheet(
    nodes: List<FileNode>,
    folderNames: Collection<String>,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as AtomicApp).container
    val scope = rememberCoroutineScope()
    var pattern by remember { mutableStateOf(RenamePattern()) }
    var numbered by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BatchRenameResult?>(null) }
    val effective = pattern.copy(numbering = if (numbered) Numbering() else null)
    val preview = remember(nodes, effective, folderNames) { BatchRenameRules.preview(nodes, effective, folderNames) }

    AtomicSheet(label = "Rename ${nodes.size} items", onDismiss = onDismiss) {
        val done = result
        if (done != null) {
            AtomicText(RenameText.outcome(done), AtomicTextRole.DisplayPushed)
            done.failed.forEach {
                AtomicActivityRow(
                    title = it.name,
                    timestamp = RenameText.reason(it.error),
                    failed = true,
                )
            }
            if (done.renamed.isNotEmpty()) {
                AtomicButton(
                    if (busy) "Undoing…" else "Undo",
                    onClick = {
                        busy = true
                        scope.launch {
                            container.batchRenameUseCase.undo(done)
                            onChanged()
                            onDismiss()
                        }
                    },
                    enabled = !busy,
                    variant = AtomicButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth().testTag("batch_rename_undo"),
                )
            }
            AtomicButton(
                "Done",
                onClick = onDismiss,
                variant = AtomicButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            return@AtomicSheet
        }

        AtomicTextField(
            value = pattern.newBase,
            onValueChange = { pattern = pattern.copy(newBase = it) },
            label = "New name",
            placeholder = "Leave empty to keep each name",
            modifier = Modifier.fillMaxWidth().testTag("batch_rename_base"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
            AtomicTextField(
                value = pattern.find,
                onValueChange = { pattern = pattern.copy(find = it) },
                label = "Find",
                modifier = Modifier.weight(1f),
            )
            AtomicTextField(
                value = pattern.replace,
                onValueChange = { pattern = pattern.copy(replace = it) },
                label = "Replace with",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
            AtomicTextField(
                value = pattern.prefix,
                onValueChange = { pattern = pattern.copy(prefix = it) },
                label = "Add before",
                modifier = Modifier.weight(1f),
            )
            AtomicTextField(
                value = pattern.suffix,
                onValueChange = { pattern = pattern.copy(suffix = it) },
                label = "Add after",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            NameCase.entries.forEach { case ->
                AtomicChip(RenameText.caseLabel(case), selected = pattern.case == case, onSelectedChange = {
                    pattern = pattern.copy(case = case)
                })
            }
        }
        SwitchRow("Number in selection order (01, 02, …)", numbered) { numbered = it }
        SwitchRow("Keep extensions", pattern.keepExtension) { pattern = pattern.copy(keepExtension = it) }

        AtomicText(RenameText.summary(preview), AtomicTextRole.MonoLabel)
        preview.take(PREVIEW_ROWS).forEach { row ->
            AtomicActivityRow(
                title = row.newName.ifBlank { "—" },
                timestamp = RenameText.rowMeta(row),
                failed = row.problem != null,
            )
        }
        if (preview.size > PREVIEW_ROWS) {
            AtomicText("and ${preview.size - PREVIEW_ROWS} more", AtomicTextRole.BodySecondary)
        }
        val canApply = !busy && preview.any { it.changes } && preview.none { it.problem != null }
        AtomicButton(
            if (busy) "Renaming…" else "Rename",
            onClick = {
                busy = true
                scope.launch {
                    result = container.batchRenameUseCase(preview.map { it.node to it.newName })
                    busy = false
                    onChanged()
                }
            },
            enabled = canApply,
            variant = AtomicButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth().testTag("batch_rename_apply"),
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AtomicText(label, AtomicTextRole.Body, modifier = Modifier.weight(1f))
        AtomicSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** Wording for [BatchRenameSheet]; pure so it is unit-tested. */
internal object RenameText {
    fun caseLabel(case: NameCase): String =
        when (case) {
            NameCase.KEEP -> "Keep case"
            NameCase.LOWER -> "lowercase"
            NameCase.UPPER -> "UPPERCASE"
            NameCase.TITLE -> "Title Case"
        }

    fun rowMeta(row: RenamePreview): String =
        when (row.problem) {
            RenameProblem.EMPTY -> "was ${row.node.name} · the name would be empty"
            RenameProblem.INVALID_CHARACTER -> "was ${row.node.name} · names can't contain / \\ : * ? \" < > |"
            RenameProblem.DUPLICATE_IN_BATCH -> "was ${row.node.name} · another item gets the same name"
            RenameProblem.EXISTS -> "was ${row.node.name} · already used in this folder"
            null -> if (row.changes) "was ${row.node.name}" else "unchanged"
        }

    fun summary(preview: List<RenamePreview>): String {
        val problems = preview.count { it.problem != null }
        val changing = preview.count { it.changes }
        return when {
            problems == 1 -> "1 name needs fixing"
            problems > 1 -> "$problems names need fixing"
            changing == 0 -> "Nothing changes yet"
            else -> "$changing of ${preview.size} will be renamed"
        }
    }

    fun reason(error: FileError): String =
        when (error) {
            is FileError.FileAlreadyExists -> "kept its name · the new name is already used"
            is FileError.AccessDenied, is FileError.PermissionDenied, is FileError.PlatformRestricted ->
                "kept its name · this storage doesn't allow renaming it"
            is FileError.ReadOnlyStorage -> "kept its name · the storage is read-only"
            is FileError.FileNotFound -> "it's no longer there"
            is FileError.InvalidName -> "kept its name · the new name isn't allowed here"
            else -> "kept its name · it couldn't be renamed"
        }

    fun outcome(result: BatchRenameResult): String {
        val done = result.renamed.size
        val failed = result.failed.size
        val renamed = if (done == 1) "Renamed 1 item" else "Renamed $done items"
        return if (failed == 0) renamed else "$renamed · $failed couldn't be renamed"
    }
}
