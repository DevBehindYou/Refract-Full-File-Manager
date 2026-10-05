package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicChoiceCard
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.HideMode

/** Choose how to hide one file (ATOMIC_UI_PLAN.md §7.6); skipped when Settings has a default. */
@Composable
fun HideFileDialog(
    node: FileNode,
    onConfirm: (HideMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMode by rememberSaveable { mutableStateOf(HideMode.FAST_OBSCURE) }

    AtomicSheet(label = "Hide", onDismiss = onDismiss) {
        AtomicText(node.displayName, AtomicTextRole.NameLarge, maxLines = 2)
        AtomicChoiceCard(
            title = "Fast Obscure",
            description =
                "The file stays in its folder; its name and header are masked so apps don't recognise it. " +
                    "Reversible at once. This is not encryption.",
            selected = selectedMode == HideMode.FAST_OBSCURE,
            onSelect = { selectedMode = HideMode.FAST_OBSCURE },
        )
        AtomicChoiceCard(
            title = "Hide from Gallery",
            description =
                "Moves the file to your hidden folder (Atomic File Manager/Hidden unless changed in Settings), " +
                    "where photo apps ignore it.",
            selected = selectedMode == HideMode.GALLERY,
            onSelect = { selectedMode = HideMode.GALLERY },
        )
        AtomicChoiceCard(
            title = "Private Storage",
            description =
                "Moves the file into the app's private folder, out of reach of other apps. " +
                    "It is deleted if Atomic File Manager is uninstalled.",
            selected = selectedMode == HideMode.PRIVATE_STORAGE,
            onSelect = { selectedMode = HideMode.PRIVATE_STORAGE },
        )
        AtomicButton("Hide file", onClick = { onConfirm(selectedMode) }, modifier = Modifier.fillMaxWidth())
        AtomicButton(
            "Cancel",
            onClick = onDismiss,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
