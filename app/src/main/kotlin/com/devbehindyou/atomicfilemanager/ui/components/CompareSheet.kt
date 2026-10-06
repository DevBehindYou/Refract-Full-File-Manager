package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicProgressBar
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFact
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFactSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.FileChecksums
import com.devbehindyou.atomicfilemanager.domain.usecase.sameContent
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

/**
 * Compares two files by content (ALL_IN_ONE_PLAN.md 2.8, Total Commander's "compare by content").
 * Different sizes answer at once; otherwise both files are hashed with SHA-256.
 */
@Composable
fun CompareSheet(
    first: FileNode,
    second: FileNode,
    onDismiss: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as AtomicApp).container
    val result by produceState<CompareResult>(CompareResult.Working, first.id, second.id) {
        value =
            if (first.size >= 0 && second.size >= 0 && first.size != second.size) {
                CompareResult.Done(same = false, first = null, second = null)
            } else {
                val a = container.readFileContentUseCase.calculateChecksums(first.id)
                val b = container.readFileContentUseCase.calculateChecksums(second.id)
                if (a is FileResult.Success && b is FileResult.Success) {
                    CompareResult.Done(sameContent(first.size, a.value, second.size, b.value), a.value, b.value)
                } else {
                    CompareResult.Failed
                }
            }
    }
    AtomicSheet(label = "Compare", onDismiss = onDismiss) {
        when (val r = result) {
            CompareResult.Working -> {
                AtomicText("Reading both files…", AtomicTextRole.Body)
                AtomicProgressBar(fraction = 0f, contentDescription = "Comparing")
            }
            CompareResult.Failed ->
                AtomicText(
                    "One of the files couldn't be read, so they can't be compared.",
                    AtomicTextRole.Body,
                    modifier = Modifier.testTag("compare_result"),
                )
            is CompareResult.Done -> {
                AtomicText(
                    CompareText.headline(r.same),
                    AtomicTextRole.DisplayPushed,
                    modifier = Modifier.testTag("compare_result"),
                )
                AtomicText(CompareText.body(r.same, first, second, hashed = r.first != null), AtomicTextRole.Body)
            }
        }
        AtomicFactSheet(CompareText.facts(first, (result as? CompareResult.Done)?.first))
        AtomicFactSheet(CompareText.facts(second, (result as? CompareResult.Done)?.second))
        AtomicButton(
            "Done",
            onClick = onDismiss,
            variant = AtomicButtonVariant.Solid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private sealed interface CompareResult {
    data object Working : CompareResult

    data object Failed : CompareResult

    data class Done(
        val same: Boolean,
        val first: FileChecksums?,
        val second: FileChecksums?,
    ) : CompareResult
}

/** Wording for [CompareSheet]; pure so it is unit-tested. */
internal object CompareText {
    fun headline(same: Boolean): String = if (same) "Same content" else "Different content"

    fun body(
        same: Boolean,
        first: FileNode,
        second: FileNode,
        hashed: Boolean,
    ): String =
        when {
            same -> "“${first.name}” and “${second.name}” are identical byte for byte (same size and SHA-256)."
            !hashed -> "They differ in size, so their content can't be the same."
            else -> "They are the same size but their contents differ."
        }

    fun facts(
        node: FileNode,
        hashes: FileChecksums?,
    ): List<AtomicFact> =
        listOfNotNull(
            AtomicFact("Name", node.name),
            AtomicFact("Size", FileUtils.formatBytes(node.size), monoValue = true),
            hashes?.let { AtomicFact("SHA-256", it.sha256, monoValue = true) },
        )
}
