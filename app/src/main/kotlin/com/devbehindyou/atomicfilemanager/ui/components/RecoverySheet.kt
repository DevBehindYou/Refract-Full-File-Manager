package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicActivityRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalEntity
import com.devbehindyou.atomicfilemanager.data.database.room.toFileOperation
import com.devbehindyou.atomicfilemanager.data.operations.RecoveredOperations
import com.devbehindyou.atomicfilemanager.domain.usecase.CleanupReport
import com.devbehindyou.atomicfilemanager.ui.screens.OperationText

/**
 * Shown once after start-up when the previous run was closed mid-operation (ALL_IN_ONE_PLAN.md
 * 0.1, "Resume or discard"). Dismissing it is "decide later": the operations stay in Operations.
 */
@Composable
fun RecoverySheet(
    recovered: RecoveredOperations,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onLater: () -> Unit,
) {
    AtomicSheet(label = "Unfinished operations", onDismiss = onLater) {
        Column(Modifier.testTag("recovery_sheet"), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
            AtomicText(RecoveryText.headline(recovered.entries.size), AtomicTextRole.DisplayPushed)
            AtomicText(RecoveryText.body(recovered.entries.size), AtomicTextRole.Body)
            recovered.entries.forEach { entry ->
                AtomicActivityRow(title = RecoveryText.entryTitle(entry), timestamp = RecoveryText.entryMeta(entry))
            }
            RecoveryText.cleanup(recovered.cleanup)?.let { AtomicText(it, AtomicTextRole.BodySecondary) }
        }
        AtomicButton(
            "Resume",
            onClick = onResume,
            variant = AtomicButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth().testTag("recovery_resume"),
        )
        AtomicButton(
            "Discard",
            onClick = onDiscard,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth().testTag("recovery_discard"),
        )
        AtomicButton(
            "Decide later",
            onClick = onLater,
            variant = AtomicButtonVariant.Text,
            modifier = Modifier.fillMaxWidth().testTag("recovery_later"),
        )
    }
}

/** Wording for [RecoverySheet]; pure so it is unit-tested. */
internal object RecoveryText {
    fun headline(count: Int): String =
        if (count == 1) "An operation was cut short" else "$count operations were cut short"

    fun body(count: Int): String {
        val what = if (count == 1) "this operation" else "these operations"
        return "The app closed before $what finished. Resume runs $what again from the start: " +
            "anything already copied is found again and you choose what to do with it. " +
            "Discard leaves the files as they are now."
    }

    fun entryTitle(entry: OperationJournalEntity): String =
        "${OperationText.verb(entry.type)} ${OperationText.items(entry.sources.size)}"

    fun entryMeta(entry: OperationJournalEntity): String {
        val progress = if (entry.itemsTotal > 0) " · ${entry.itemsDone} of ${entry.itemsTotal} done" else ""
        return OperationText.route(entry.toFileOperation()) + progress
    }

    /** One sentence on what start-up tidied, or null when nothing was found. */
    fun cleanup(report: CleanupReport): String? {
        if (report.isEmpty) return null
        val removed = report.removedPartials
        val restored = report.restoredOriginals
        val kept = report.keptBackups
        val parts =
            listOfNotNull(
                if (removed > 0) "removed ${count(removed, "unfinished copy", "unfinished copies")}" else null,
                when {
                    restored == 1 -> "put back 1 file that was being replaced"
                    restored > 1 -> "put back $restored files that were being replaced"
                    else -> null
                },
                if (kept > 0) {
                    "kept ${count(kept, "replaced original", "replaced originals")} as a hidden “.atomic-orig-” " +
                        "file next to the new copy; delete it when you no longer need it"
                } else {
                    null
                },
            )
        return parts.joinToString("; ").replaceFirstChar { it.uppercase() } + "."
    }

    private fun count(
        n: Int,
        one: String,
        many: String,
    ): String = if (n == 1) "1 $one" else "$n $many"
}
