package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicWarningBox
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicConfirmSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSnackbarHost
import com.devbehindyou.atomicfilemanager.domain.model.HiddenItem
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.originalParent
import com.devbehindyou.atomicfilemanager.domain.repository.HiddenFilesRepository
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val HideMode.tabLabel: String
    get() =
        when (this) {
            HideMode.FAST_OBSCURE -> "Obscured"
            HideMode.GALLERY -> "Gallery"
            HideMode.PRIVATE_STORAGE -> "Private"
        }

private val HideMode.explanation: String
    get() =
        when (this) {
            HideMode.FAST_OBSCURE ->
                "Files stay in their folder with a masked name and header, so other apps don't recognise them."
            HideMode.GALLERY -> "Files sit in your hidden folder, where photo apps don't look."
            HideMode.PRIVATE_STORAGE ->
                "Files live inside the app's own folder. Other apps can't see them. They are deleted if " +
                    "Atomic File Manager is uninstalled, so restore them first."
        }

/**
 * Private & hidden (ATOMIC_UI_PLAN.md §7.6, canvas "Private & hidden"): one chip per hiding
 * method, what that method means, the files with Restore, and a reminder that none of this is
 * encryption. Deleting a hidden file asks first.
 */
@Composable
fun HiddenFilesScreen(
    repository: HiddenFilesRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialMode: HideMode = HideMode.FAST_OBSCURE,
    privateOnly: Boolean = false,
) {
    val hiddenItems by repository.hiddenItems.collectAsState()
    var currentMode by rememberSaveable { mutableStateOf(initialMode) }
    var itemToDelete by remember { mutableStateOf<HiddenItem?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // One place for every restore path so a failure is reported instead of ignored or thrown.
    suspend fun restore(item: HiddenItem): Result<Unit> =
        try {
            when (item.mode) {
                HideMode.FAST_OBSCURE -> repository.restoreFastObscured(item)
                HideMode.GALLERY -> repository.unhideFromGallery(item)
                HideMode.PRIVATE_STORAGE -> repository.restoreFromPrivateStorage(item, item.originalParent())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun restoreAndReport(items: List<HiddenItem>) {
        val failures = items.mapNotNull { item -> restore(item).exceptionOrNull()?.let { item to it } }
        val first = failures.firstOrNull()
        val message =
            when {
                first == null && items.size == 1 -> "Restored ${items.first().originalName}."
                first == null -> "Restored ${items.size} files."
                failures.size == 1 ->
                    "Couldn't restore ${first.first.originalName}: ${first.second.message ?: "unknown error"}"
                else ->
                    "Couldn't restore ${failures.size} of ${items.size} files. First error: " +
                        (first.second.message ?: "unknown error")
            }
        snackbarHostState.showSnackbar(message)
    }

    val mode = if (privateOnly) HideMode.PRIVATE_STORAGE else currentMode
    val currentItems = hiddenItems.filter { it.mode == mode }

    Box(modifier.fillMaxSize().background(Atomic.colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            AtomicPushedHeader(
                onBack = onNavigateBack,
                title = if (privateOnly) "Private files" else "Private & hidden",
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(AtomicSpacing.s16),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
            ) {
                if (!privateOnly) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                        ) {
                            HideMode.entries.forEach { option ->
                                AtomicChip(
                                    label = option.tabLabel,
                                    selected = mode == option,
                                    onSelectedChange = { currentMode = option },
                                )
                            }
                        }
                    }
                }
                item { AtomicText(mode.explanation, AtomicTextRole.BodySecondary) }
                if (currentItems.isEmpty()) {
                    item {
                        AtomicEmptyState(
                            message =
                                if (mode == HideMode.PRIVATE_STORAGE) {
                                    "No private files yet."
                                } else {
                                    "Nothing ${mode.tabLabel.lowercase()} yet."
                                },
                            detail = "Open a file's actions in Files and choose Hide.",
                        )
                    }
                } else {
                    item {
                        AtomicSectionLabel(
                            "${currentItems.size} files · ${FileUtils.formatBytes(currentItems.sumOf { it.size })}",
                            actionLabel = "Restore all",
                            onAction = { scope.launch { restoreAndReport(currentItems) } },
                        )
                    }
                    items(currentItems, key = { it.id }) { item ->
                        HiddenItemRow(
                            item = item,
                            onRestore = { scope.launch { restoreAndReport(listOf(item)) } },
                            onDelete = { itemToDelete = item },
                        )
                    }
                }
                item {
                    AtomicWarningBox(
                        title = "Not encryption",
                        body =
                            "Hiding keeps files out of other apps' sight. " +
                                "The encrypted Vault comes in a later update.",
                    )
                }
            }
        }
        AtomicSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    itemToDelete?.let { item ->
        AtomicConfirmSheet(
            label = "Delete",
            headline = "Delete for good?",
            body = "\"${item.originalName}\" (${FileUtils.formatBytes(
                item.size,
            )}) is deleted, not restored. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                itemToDelete = null
                scope.launch {
                    val error = repository.deleteHiddenItem(item).exceptionOrNull()
                    snackbarHostState.showSnackbar(
                        if (error == null) {
                            "Deleted ${item.originalName}."
                        } else {
                            "Couldn't delete ${item.originalName}: ${error.message ?: "unknown error"}"
                        },
                    )
                }
            },
            onDismiss = { itemToDelete = null },
        )
    }
}

@Composable
private fun HiddenItemRow(
    item: HiddenItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val from = item.originalLocation.substringBeforeLast('/').ifEmpty { "/" }
    AtomicFileRow(
        name = item.originalName,
        meta = "From $from · ${FileUtils.formatBytes(item.size)} · ${FileUtils.formatDate(item.hiddenAt)}",
        icon = if (item.mode == HideMode.PRIVATE_STORAGE) AtomicIcons.Lock else AtomicIcons.Hidden,
        // Restoring moves files, so it only happens from the explicit Restore button.
        onClick = {},
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtomicButton("Restore", onClick = onRestore, variant = AtomicButtonVariant.Text)
                AtomicIconButton(
                    AtomicIcons.Trash,
                    "Delete ${item.originalName} for good",
                    onClick = onDelete,
                    variant = AtomicIconButtonVariant.Destructive,
                )
            }
        },
    )
}
