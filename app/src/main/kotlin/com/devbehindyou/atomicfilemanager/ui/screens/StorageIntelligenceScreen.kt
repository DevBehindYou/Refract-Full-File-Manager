package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicStatTile
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicWarningBox
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicConfirmSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisResult
import com.devbehindyou.atomicfilemanager.ui.components.iconFor
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.launch

private val StorageAnalysisCategory.label: String
    get() =
        when (this) {
            StorageAnalysisCategory.LARGE_FILES -> "Large files"
            StorageAnalysisCategory.DUPLICATE_FILES -> "Duplicates"
            StorageAnalysisCategory.EMPTY_FOLDERS -> "Empty folders"
            StorageAnalysisCategory.TEMP_AND_CACHE -> "Temp & cache"
        }

private fun StorageAnalysisCategory.emptyMessage(): String =
    when (this) {
        StorageAnalysisCategory.LARGE_FILES -> "No files over 50 MB."
        StorageAnalysisCategory.DUPLICATE_FILES -> "No duplicate files found."
        StorageAnalysisCategory.EMPTY_FOLDERS -> "No empty folders found."
        StorageAnalysisCategory.TEMP_AND_CACHE -> "No temporary or cache files found."
    }

/**
 * Storage analysis (ATOMIC_UI_PLAN.md §7.4, canvas "Analysis"): what could be freed, four
 * categories as chips, selectable rows, and a confirm sheet that names the count and size before
 * anything is deleted. Nothing is selected by default.
 */
@Composable
fun StorageIntelligenceScreen(
    rootId: FileNodeId,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFile: ((FileNode) -> Unit)? = null,
) {
    BackHandler(onBack = onNavigateBack)
    val app = LocalContext.current.applicationContext as AtomicApp
    val coroutineScope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scannedFilesCount by remember { mutableIntStateOf(0) }
    var currentCategory by rememberSaveable { mutableStateOf(StorageAnalysisCategory.LARGE_FILES) }
    var analysisResult by remember { mutableStateOf(StorageAnalysisResult()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var showConfirmDelete by remember { mutableStateOf(false) }

    fun startScan() {
        isScanning = true
        scannedFilesCount = 0
        coroutineScope.launch {
            app.container.storageAnalyzerUseCase.analyze(rootId).collect { progress ->
                scannedFilesCount = progress.scannedFilesCount
                if (progress.isComplete) {
                    analysisResult = progress.result ?: StorageAnalysisResult(isPartial = true)
                    isScanning = false
                }
            }
        }
    }

    LaunchedEffect(rootId) { startScan() }

    val toggle: (FileNode) -> Unit = { node ->
        selected = if (node.id.raw in selected) selected - node.id.raw else selected + node.id.raw
    }
    val selectedNodes = StorageCleanupSelection.selectedNodes(analysisResult, currentCategory, selected)

    Column(
        modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AtomicPushedHeader(
            onBack = onNavigateBack,
            title = "Analysis",
            actions = {
                AtomicIconButton(
                    AtomicIcons.Refresh,
                    "Rescan storage",
                    onClick = { if (!isScanning) startScan() },
                    enabled = !isScanning,
                )
            },
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(AtomicSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            item {
                if (isScanning) {
                    AtomicLoading("Scanning · $scannedFilesCount files")
                } else {
                    AtomicStatTile(
                        value = FileUtils.formatBytes(analysisResult.totalPotentialSavingsBytes),
                        caption = "You could free · ${analysisResult.scannedFilesCount} files scanned",
                        featured = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (analysisResult.isPartial && !isScanning) {
                item {
                    AtomicWarningBox(
                        title = "Partial scan",
                        body = "Some folders couldn't be read or were too deep. Results may be incomplete.",
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                ) {
                    StorageAnalysisCategory.entries.forEach { category ->
                        AtomicChip(
                            label = "${category.label} ${StorageCleanupSelection.count(analysisResult, category)}",
                            selected = currentCategory == category,
                            onSelectedChange = {
                                currentCategory = category
                                selected = emptySet()
                            },
                        )
                    }
                }
            }
            if (isScanning) {
                item { AtomicEmptyState(message = "Results appear when the scan finishes.") }
            } else {
                categoryItems(
                    result = analysisResult,
                    category = currentCategory,
                    selected = selected,
                    onToggle = toggle,
                    onSelectionChange = { selected = it },
                    onOpenFile = onOpenFile,
                )
            }
        }

        if (selectedNodes.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Atomic.colors.background)) {
                AtomicDivider(strong = true)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s10),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                ) {
                    AtomicText(
                        "${selectedNodes.size} selected · ${FileUtils.formatBytes(
                            StorageCleanupSelection.bytes(selectedNodes),
                        )}",
                        AtomicTextRole.MonoLabel,
                        modifier = Modifier.weight(1f),
                    )
                    AtomicButton("Clear", onClick = { selected = emptySet() }, variant = AtomicButtonVariant.Text)
                    AtomicButton(
                        "Delete",
                        onClick = { showConfirmDelete = true },
                        variant = AtomicButtonVariant.Destructive,
                    )
                }
            }
        }
    }

    if (showConfirmDelete) {
        val targets = selectedNodes
        AtomicConfirmSheet(
            label = "Cleanup",
            headline = "Delete ${targets.size} items?",
            body =
                "${FileUtils.formatBytes(StorageCleanupSelection.bytes(targets))} will be freed. " +
                    "The files are deleted, not moved to a trash. This can't be undone.",
            confirmLabel = "Delete ${targets.size}",
            destructive = true,
            onConfirm = {
                coroutineScope.launch {
                    val op =
                        FileOperation(
                            id = OperationId.random(),
                            type = OperationType.DELETE,
                            // Ids come straight from the scanned nodes; they are already full FileNodeIds.
                            sources = targets.map { it.id },
                            destination = null,
                            options = OperationOptions(),
                            createdAt = System.currentTimeMillis(),
                        )
                    // Runs through the queue and waits, so the rescan sees the result. (Calling the
                    // engine directly only built a cold Flow that nobody collected: nothing was deleted.)
                    app.container.operationQueue.runAndAwait(op)
                    showConfirmDelete = false
                    selected = emptySet()
                    startScan()
                }
            },
            onDismiss = { showConfirmDelete = false },
        )
    }
}

private fun LazyListScope.categoryItems(
    result: StorageAnalysisResult,
    category: StorageAnalysisCategory,
    selected: Set<String>,
    onToggle: (FileNode) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onOpenFile: ((FileNode) -> Unit)?,
) {
    val nodes = StorageCleanupSelection.nodesIn(result, category)
    if (nodes.isEmpty()) {
        item { AtomicEmptyState(message = category.emptyMessage()) }
        return
    }
    if (category == StorageAnalysisCategory.DUPLICATE_FILES) {
        item {
            AtomicSectionLabel(
                "${result.duplicateGroups.size} groups",
                actionLabel = "Keep one of each",
                onAction = { onSelectionChange(StorageCleanupSelection.allButFirstCopy(result.duplicateGroups)) },
            )
        }
        result.duplicateGroups.forEach { group ->
            item(key = "group:${group.sha256}") {
                AtomicText(
                    "${FileUtils.formatBytes(group.sizeBytes)} each · ${group.items.size} copies",
                    AtomicTextRole.MonoLabel,
                    color = Atomic.colors.accentText,
                    modifier = Modifier.padding(top = AtomicSpacing.s8),
                )
            }
            items(group.items, key = { "dup:${group.sha256}:${it.id.raw}" }) { node ->
                val first = node == group.items.first()
                CleanupRow(
                    node,
                    selected,
                    onToggle,
                    onOpenFile,
                    note = if (first) "Original · kept by Keep one" else null,
                )
            }
        }
        return
    }
    item {
        AtomicSectionLabel(
            "${nodes.size} ${category.label.lowercase()}",
            actionLabel = if (nodes.all { it.id.raw in selected }) "Select none" else "Select all",
            onAction = { onSelectionChange(StorageCleanupSelection.toggleAll(selected, nodes)) },
        )
    }
    items(nodes, key = { it.id.raw }) { node -> CleanupRow(node, selected, onToggle, onOpenFile, note = null) }
}

@Composable
private fun CleanupRow(
    node: FileNode,
    selected: Set<String>,
    onToggle: (FileNode) -> Unit,
    onOpenFile: ((FileNode) -> Unit)?,
    note: String?,
) {
    val path = node.id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme)
    AtomicFileRow(
        name = node.name,
        meta =
            listOfNotNull(
                if (node.isDirectory) null else FileUtils.formatBytes(node.size),
                note,
                path,
            ).joinToString(" · "),
        icon = iconFor(node),
        onClick = { onToggle(node) },
        selectionMode = true,
        selected = node.id.raw in selected,
        trailing =
            if (onOpenFile != null) {
                {
                    AtomicIconButton(
                        AtomicIcons.FolderOpen,
                        "Show ${node.name} in Files",
                        onClick = { onOpenFile(node) },
                    )
                }
            } else {
                null
            },
    )
}
