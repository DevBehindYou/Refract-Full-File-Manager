package com.devbehindyou.refract.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.RefractApp
import com.devbehindyou.refract.domain.model.DuplicateGroup
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileOperation
import com.devbehindyou.refract.domain.model.OperationId
import com.devbehindyou.refract.domain.model.OperationOptions
import com.devbehindyou.refract.domain.model.OperationType
import com.devbehindyou.refract.domain.model.StorageAnalysisCategory
import com.devbehindyou.refract.domain.model.StorageAnalysisResult
import com.devbehindyou.refract.ui.util.FileUtils
import kotlinx.coroutines.launch

/**
 * Storage Intelligence Hub providing deep analysis of large files, duplicates,
 * empty directories, and temporary cache files with one-touch transactional cleanup.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StorageIntelligenceScreen(
    rootId: FileNodeId,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFile: ((FileNode) -> Unit)? = null,
) {
    BackHandler(onBack = onNavigateBack)
    val app = LocalContext.current.applicationContext as RefractApp
    val coroutineScope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scannedFilesCount by remember { mutableStateOf(0) }
    var currentCategory by remember { mutableStateOf(StorageAnalysisCategory.LARGE_FILES) }
    var analysisResult by remember { mutableStateOf(StorageAnalysisResult()) }

    // Selected file IDs for deletion
    val selectedIds = remember { mutableStateMapOf<String, Boolean>() }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

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

    LaunchedEffect(rootId) {
        startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Intelligence", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!isScanning) startScan() },
                        enabled = !isScanning,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rescan Storage")
                    }
                },
            )
        },
        bottomBar = {
            val selectedCount = selectedIds.count { it.value }
            if (selectedCount > 0) {
                Surface(tonalElevation = 3.dp) {
                    FlowRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "$selectedCount item(s) selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { selectedIds.clear() }) {
                                Text("Clear")
                            }
                            Button(
                                onClick = { showConfirmDeleteDialog = true },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                    ),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
        ) {
            if (analysisResult.isPartial) {
                Text("Partial scan: some locations could not be read or exceeded the depth limit.")
            }
            // Live scanning summary card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isScanning) "Analyzing storage..." else "Analysis Complete",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (isScanning) {
                            Text(
                                text = "$scannedFilesCount files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (isScanning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        val potentialReclaim = FileUtils.formatBytes(analysisResult.totalPotentialSavingsBytes)
                        Text(
                            text = "Potential reclaimable space: $potentialReclaim",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Category filter chips stay readable and scroll to all four categories.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StorageAnalysisCategory.entries.forEach { category ->
                    val badgeCount =
                        when (category) {
                            StorageAnalysisCategory.LARGE_FILES -> analysisResult.largeFiles.size
                            StorageAnalysisCategory.DUPLICATE_FILES -> analysisResult.duplicateGroups.size
                            StorageAnalysisCategory.EMPTY_FOLDERS -> analysisResult.emptyFolders.size
                            StorageAnalysisCategory.TEMP_AND_CACHE -> analysisResult.tempCacheFiles.size
                        }
                    FilterChip(
                        selected = currentCategory == category,
                        onClick = {
                            currentCategory = category
                            selectedIds.clear()
                        },
                        label = { Text("${category.displayName} ($badgeCount)", maxLines = 1) },
                    )
                }
            }

            // Do not report empty results before the scan finishes.
            if (isScanning) {
                EmptyStateMessage("Scanning files… Results will appear when the scan finishes.")
            } else {
                when (currentCategory) {
                    StorageAnalysisCategory.LARGE_FILES -> {
                        LargeFilesList(
                            files = analysisResult.largeFiles,
                            selectedIds = selectedIds,
                            onToggleSelect = { id -> selectedIds[id.raw] = !(selectedIds[id.raw] ?: false) },
                            onSelectAll = {
                                val allSelected = analysisResult.largeFiles.all { selectedIds[it.id.raw] == true }
                                if (allSelected) {
                                    selectedIds.clear()
                                } else {
                                    analysisResult.largeFiles.forEach { selectedIds[it.id.raw] = true }
                                }
                            },
                            onOpenFile = onOpenFile,
                        )
                    }
                    StorageAnalysisCategory.DUPLICATE_FILES -> {
                        DuplicateGroupsList(
                            groups = analysisResult.duplicateGroups,
                            selectedIds = selectedIds,
                            onToggleSelect = { id -> selectedIds[id.raw] = !(selectedIds[id.raw] ?: false) },
                            onKeepOnlyOnePerGroup = {
                                selectedIds.clear()
                                for (group in analysisResult.duplicateGroups) {
                                    // Keep first, select the rest for deletion
                                    group.items.drop(1).forEach { selectedIds[it.id.raw] = true }
                                }
                            },
                            onOpenFile = onOpenFile,
                        )
                    }
                    StorageAnalysisCategory.EMPTY_FOLDERS -> {
                        EmptyFoldersList(
                            folders = analysisResult.emptyFolders,
                            selectedIds = selectedIds,
                            onToggleSelect = { id -> selectedIds[id.raw] = !(selectedIds[id.raw] ?: false) },
                            onSelectAll = {
                                val allSelected = analysisResult.emptyFolders.all { selectedIds[it.id.raw] == true }
                                if (allSelected) {
                                    selectedIds.clear()
                                } else {
                                    analysisResult.emptyFolders.forEach { selectedIds[it.id.raw] = true }
                                }
                            },
                        )
                    }
                    StorageAnalysisCategory.TEMP_AND_CACHE -> {
                        TempFilesList(
                            files = analysisResult.tempCacheFiles,
                            selectedIds = selectedIds,
                            onToggleSelect = { id -> selectedIds[id.raw] = !(selectedIds[id.raw] ?: false) },
                            onSelectAll = {
                                val allSelected = analysisResult.tempCacheFiles.all { selectedIds[it.id.raw] == true }
                                if (allSelected) {
                                    selectedIds.clear()
                                } else {
                                    analysisResult.tempCacheFiles.forEach { selectedIds[it.id.raw] = true }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showConfirmDeleteDialog) {
        val targetIds = selectedIds.filter { it.value }.keys.map { FileNodeId.file(it) }
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = { Text("Delete ${targetIds.size} item(s)?") },
            text = { Text("Are you sure you want to delete these files permanently? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val op =
                                FileOperation(
                                    id = OperationId.random(),
                                    type = OperationType.DELETE,
                                    sources = targetIds,
                                    destination = null,
                                    options = OperationOptions(),
                                    createdAt = System.currentTimeMillis(),
                                )
                            app.container.fileOperationsEngine.execute(op)
                            showConfirmDeleteDialog = false
                            selectedIds.clear()
                            startScan()
                        }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun LargeFilesList(
    files: List<FileNode>,
    selectedIds: Map<String, Boolean>,
    onToggleSelect: (FileNodeId) -> Unit,
    onSelectAll: () -> Unit,
    onOpenFile: ((FileNode) -> Unit)?,
) {
    if (files.isEmpty()) {
        EmptyStateMessage("No large files found matching threshold (>50 MB).")
        return
    }

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onSelectAll) {
                Text("Select All")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files, key = { it.id.raw }) { file ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { if (onOpenFile != null) onOpenFile(file) else onToggleSelect(file.id) }
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selectedIds[file.id.raw] == true,
                            onCheckedChange = { onToggleSelect(file.id) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${FileUtils.formatBytes(file.size)} â€¢ ${file.id.raw}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuplicateGroupsList(
    groups: List<DuplicateGroup>,
    selectedIds: Map<String, Boolean>,
    onToggleSelect: (FileNodeId) -> Unit,
    onKeepOnlyOnePerGroup: () -> Unit,
    onOpenFile: ((FileNode) -> Unit)?,
) {
    if (groups.isEmpty()) {
        EmptyStateMessage("No duplicate files found across storage.")
        return
    }

    Column {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${groups.size} duplicate group(s)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onKeepOnlyOnePerGroup) {
                Text("Select All Duplicates (Keep 1)")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups, key = { it.sha256 }) { group ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Size: ${FileUtils.formatBytes(group.sizeBytes)} each",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${group.items.size} copies",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        group.items.forEachIndexed { index, item ->
                            val isSelected = selectedIds[item.id.raw] == true
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (onOpenFile != null) {
                                                onOpenFile(
                                                    item,
                                                )
                                            } else {
                                                onToggleSelect(item.id)
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSelect(item.id) },
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (index == 0) "${item.name} (Original)" else item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = item.id.raw,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFoldersList(
    folders: List<FileNode>,
    selectedIds: Map<String, Boolean>,
    onToggleSelect: (FileNodeId) -> Unit,
    onSelectAll: () -> Unit,
) {
    if (folders.isEmpty()) {
        EmptyStateMessage("No empty directories found.")
        return
    }

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onSelectAll) {
                Text("Select All")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folders, key = { it.id.raw }) { folder ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSelect(folder.id) }
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selectedIds[folder.id.raw] == true,
                            onCheckedChange = { onToggleSelect(folder.id) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = folder.id.raw,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TempFilesList(
    files: List<FileNode>,
    selectedIds: Map<String, Boolean>,
    onToggleSelect: (FileNodeId) -> Unit,
    onSelectAll: () -> Unit,
) {
    if (files.isEmpty()) {
        EmptyStateMessage("No temporary or cache files found.")
        return
    }

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onSelectAll) {
                Text("Select All")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files, key = { it.id.raw }) { file ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSelect(file.id) }
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selectedIds[file.id.raw] == true,
                            onCheckedChange = { onToggleSelect(file.id) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.CleaningServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${FileUtils.formatBytes(file.size)} â€¢ ${file.id.raw}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
