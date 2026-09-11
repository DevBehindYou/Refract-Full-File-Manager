package com.devbehindyou.refract.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.data.backend.FileSystemBackend
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.ui.components.BreadcrumbBar
import com.devbehindyou.refract.ui.components.BreadcrumbItem
import com.devbehindyou.refract.ui.components.FileDetailsDialog
import com.devbehindyou.refract.ui.components.FileListItem
import com.devbehindyou.refract.ui.components.NewFolderDialog
import com.devbehindyou.refract.ui.components.RenameDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SortOption(val label: String) {
    NAME("Name"),
    DATE_MODIFIED("Date modified"),
    SIZE("Size"),
}

private enum class ClipboardOp { COPY, MOVE }
private data class ClipboardState(val items: List<FileNode>, val operation: ClipboardOp)
private enum class CollisionChoice { OVERWRITE, KEEP_BOTH, SKIP }

private const val LISTING_UI_THROTTLE_MS = 120L

/**
 * Streams [backend]'s listing of [nodeId] into [onChunk], throttled to at most once every
 * [LISTING_UI_THROTTLE_MS] (plus always on the very first and very last chunk). This is the
 * fix for the reported "delay opening folders with thousands of files" — the original
 * version pushed a Compose state update on *every* 200-item chunk from the backend, which
 * retriggered a full re-sort of the whole accumulated list on every single chunk. For a
 * 10,000-item folder that's ~50 increasingly expensive synchronous re-sorts during
 * composition, and — since isLoading didn't flip to false until the flow fully completed —
 * all of that work was happening behind a frozen spinner, invisible to the user.
 */
private suspend fun collectListingThrottled(
    backend: FileSystemBackend,
    nodeId: FileNodeId,
    onChunk: suspend (accumulated: List<FileNode>, isFirst: Boolean) -> Unit,
    onFailure: suspend (FileError) -> Unit,
) {
    val accumulated = mutableListOf<FileNode>()
    var lastPush = 0L
    var pushedAny = false
    var sawFailure = false

    backend.listChildren(nodeId).collect { result ->
        when (result) {
            is FileResult.Success -> {
                accumulated.addAll(result.value)
                val now = System.currentTimeMillis()
                val isFirst = !pushedAny
                if (isFirst || now - lastPush >= LISTING_UI_THROTTLE_MS) {
                    onChunk(accumulated.toList(), isFirst)
                    lastPush = now
                    pushedAny = true
                }
            }
            is FileResult.Failure -> {
                sawFailure = true
                onFailure(result.error)
            }
        }
    }
    // Always push the true final state, even if the last chunk(s) were throttled out.
    if (!sawFailure) {
        onChunk(accumulated.toList(), !pushedAny)
    }
}

private fun comparatorFor(sortOption: SortOption): Comparator<FileNode> {
    val byField: Comparator<FileNode> = when (sortOption) {
        SortOption.NAME -> compareBy { it.name.lowercase() }
        SortOption.DATE_MODIFIED -> compareByDescending { it.modifiedAt }
        SortOption.SIZE -> compareByDescending { it.size }
    }
    return compareByDescending<FileNode> { it.isDirectory }.then(byField)
}

private suspend fun uniqueName(backend: FileSystemBackend, parent: FileNodeId, original: String): String {
    val dotIndex = original.lastIndexOf('.')
    val hasExtension = dotIndex > 0 && dotIndex < original.length - 1
    val base = if (hasExtension) original.substring(0, dotIndex) else original
    val ext = if (hasExtension) original.substring(dotIndex) else ""
    var counter = 1
    var candidate = "$base ($counter)$ext"
    while (backend.exists(parent, candidate)) {
        counter++
        candidate = "$base ($counter)$ext"
    }
    return candidate
}

/**
 * Recursively copies [source] into [destinationParent]. Folder name collisions are always
 * treated as "keep both" (a uniquified folder name), never a recursive merge — a true
 * merge-on-overwrite needs per-level conflict resolution this pass doesn't implement. File
 * collisions honor [collisionChoice] directly.
 */
private suspend fun copyNodeRecursively(
    backend: FileSystemBackend,
    source: FileNode,
    destinationParent: FileNodeId,
    collisionChoice: CollisionChoice,
): FileResult<Unit> {
    val alreadyExists = backend.exists(destinationParent, source.name)
    if (alreadyExists && collisionChoice == CollisionChoice.SKIP) return FileResult.Success(Unit)

    val targetName = if (alreadyExists && (collisionChoice == CollisionChoice.KEEP_BOTH || source.isDirectory)) {
        uniqueName(backend, destinationParent, source.name)
    } else {
        source.name
    }

    if (source.isDirectory) {
        val createResult = backend.createDirectory(destinationParent, targetName)
        val dirNode = when (createResult) {
            is FileResult.Success -> createResult.value
            is FileResult.Failure -> return createResult
        }
        val children = mutableListOf<FileNode>()
        var listFailure: FileError? = null
        backend.listChildren(source.id).collect { r ->
            when (r) {
                is FileResult.Success -> children.addAll(r.value)
                is FileResult.Failure -> listFailure = r.error
            }
        }
        listFailure?.let { return FileResult.Failure(it) }
        for (child in children) {
            val result = copyNodeRecursively(backend, child, dirNode.id, collisionChoice)
            if (result is FileResult.Failure) return result
        }
        return FileResult.Success(Unit)
    }

    val inputResult = backend.openInput(source.id)
    val input = when (inputResult) {
        is FileResult.Success -> inputResult.value
        is FileResult.Failure -> return inputResult
    }
    val outputResult = backend.openOutput(destinationParent, targetName, source.mimeType)
    val output = when (outputResult) {
        is FileResult.Success -> outputResult.value
        is FileResult.Failure -> return outputResult
    }
    return try {
        input.stream().use { inStream -> output.stream().use { outStream -> inStream.copyTo(outStream) } }
        when (val node = output.toNode()) {
            is FileResult.Success -> FileResult.Success(Unit)
            is FileResult.Failure -> node
        }
    } catch (e: java.io.IOException) {
        output.discard()
        FileResult.Failure(FileError.IoFailure(source.name))
    }
}

/**
 * `moveWithin` handles the common case directly. On a name collision, `moveWithin` can't
 * express "rename on conflict" by itself, so this falls back to copy-then-delete-source for
 * just that item — the same reasoning `FileSystemBackend.moveWithin`'s own KDoc documents
 * for why this backend doesn't attempt that fallback internally (it's an orchestration
 * concern, not the backend's).
 */
private suspend fun moveNode(
    backend: FileSystemBackend,
    source: FileNode,
    destinationParent: FileNodeId,
    collisionChoice: CollisionChoice,
): FileResult<Unit> {
    val alreadyExists = backend.exists(destinationParent, source.name)
    if (alreadyExists && collisionChoice == CollisionChoice.SKIP) return FileResult.Success(Unit)

    if (alreadyExists) {
        val copyResult = copyNodeRecursively(backend, source, destinationParent, collisionChoice)
        if (copyResult is FileResult.Failure) return copyResult
        return when (val deleteResult = backend.delete(source.id)) {
            is FileResult.Success -> FileResult.Success(Unit)
            is FileResult.Failure -> deleteResult
        }
    }

    return when (val result = backend.moveWithin(source.id, destinationParent)) {
        is FileResult.Success -> FileResult.Success(Unit)
        is FileResult.Failure -> result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    initialDirectory: File,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val backend = remember { FileSystemBackend(context) }
    val scope = rememberCoroutineScope()

    var currentDir by remember { mutableStateOf(initialDirectory) }
    var rawItems by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var filteredItems by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.NAME) }
    var showSortMenu by remember { mutableStateOf(false) }

    var nodeToDelete by remember { mutableStateOf<FileNode?>(null) }
    var nodeForRename by remember { mutableStateOf<FileNode?>(null) }
    var nodeForDetails by remember { mutableStateOf<FileNode?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    var selectedIds by remember { mutableStateOf<Set<FileNodeId>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var confirmMultiDelete by remember { mutableStateOf(false) }

    var clipboard by remember { mutableStateOf<ClipboardState?>(null) }
    var isPerformingOperation by remember { mutableStateOf(false) }
    var operationProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pendingCollisionCount by remember { mutableStateOf(0) }
    var pendingPaste by remember { mutableStateOf<ClipboardState?>(null) }

    fun loadCurrentDirectory(showSpinnerOnStart: Boolean) {
        val nodeId = FileNodeId.file(currentDir.absolutePath)
        if (showSpinnerOnStart) {
            isLoading = true
        }
        errorMessage = null
        rawItems = emptyList()
        scope.launch {
            collectListingThrottled(
                backend = backend,
                nodeId = nodeId,
                onChunk = { accumulated, isFirst ->
                    rawItems = accumulated
                    if (isFirst) isLoading = false
                },
                onFailure = { error ->
                    isLoading = false
                    errorMessage = when (error) {
                        is FileError.AccessDenied -> "Access denied to this folder"
                        is FileError.FileNotFound -> "This folder no longer exists"
                        else -> "Couldn't read this folder"
                    }
                },
            )
            isLoading = false
            isRefreshing = false
        }
    }

    // The actual sort/filter pass runs off the main thread and only when rawItems, the
    // search query, or the sort option genuinely change — not synchronously during
    // composition, so it never blocks rendering even while a large folder is still
    // streaming in.
    LaunchedEffect(rawItems, searchQuery, sortOption) {
        val computed = withContext(Dispatchers.Default) {
            val base = if (searchQuery.isBlank()) {
                rawItems
            } else {
                rawItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
            base.sortedWith(comparatorFor(sortOption))
        }
        filteredItems = computed
    }

    LaunchedEffect(currentDir) {
        selectedIds = emptySet()
        loadCurrentDirectory(showSpinnerOnStart = true)
    }

    fun buildBreadcrumbs(dir: File): List<BreadcrumbItem> {
        val segments = mutableListOf<BreadcrumbItem>()
        var current: File? = dir
        while (current != null) {
            segments.add(0, BreadcrumbItem(name = current.name.ifEmpty { "Storage" }, path = current.absolutePath))
            current = current.parentFile
        }
        return segments
    }

    fun toggleSelected(id: FileNodeId) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    suspend fun performPaste(state: ClipboardState, collisionChoice: CollisionChoice) {
        isPerformingOperation = true
        operationProgress = 0 to state.items.size
        val destination = FileNodeId.file(currentDir.absolutePath)
        var index = 0
        for (item in state.items) {
            val result = if (state.operation == ClipboardOp.COPY) {
                copyNodeRecursively(backend, item, destination, collisionChoice)
            } else {
                moveNode(backend, item, destination, collisionChoice)
            }
            index++
            operationProgress = index to state.items.size
            if (result is FileResult.Failure) {
                errorMessage = "Couldn't ${if (state.operation == ClipboardOp.COPY) "copy" else "move"} \"${item.name}\""
            }
        }
        isPerformingOperation = false
        operationProgress = null
        clipboard = null
        loadCurrentDirectory(showSpinnerOnStart = false)
    }

    fun startPaste(state: ClipboardState) {
        scope.launch {
            val collisions = state.items.count { backend.exists(FileNodeId.file(currentDir.absolutePath), it.name) }
            if (collisions > 0) {
                pendingCollisionCount = collisions
                pendingPaste = state
            } else {
                performPaste(state, CollisionChoice.SKIP) // no collisions, choice is irrelevant
            }
        }
    }

    Scaffold(
        topBar = {
            when {
                isSelectionMode -> TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { selectedIds = filteredItems.map { it.id }.toSet() },
                            modifier = Modifier.testTag("select_all_button"),
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                    },
                )
                isSearching -> TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search this folder") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_field"),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                )
                else -> TopAppBar(
                    title = { Text(currentDir.name.ifEmpty { "Storage" }) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isSearching = true },
                            modifier = Modifier.testTag("search_icon_button"),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Search this folder")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }, modifier = Modifier.testTag("sort_button")) {
                                Icon(Icons.Filled.Sort, contentDescription = "Sort by")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        leadingIcon = {
                                            if (sortOption == option) {
                                                Icon(Icons.Filled.Check, contentDescription = "Currently sorted by ${option.label}")
                                            }
                                        },
                                        onClick = { sortOption = option; showSortMenu = false },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showNewFolderDialog = true }, modifier = Modifier.testTag("new_folder_button")) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                    ) {
                        SelectionAction(Icons.Filled.ContentCopy, "Copy") {
                            clipboard = ClipboardState(filteredItems.filter { it.id in selectedIds }, ClipboardOp.COPY)
                            selectedIds = emptySet()
                        }
                        SelectionAction(Icons.Filled.ContentCut, "Move") {
                            clipboard = ClipboardState(filteredItems.filter { it.id in selectedIds }, ClipboardOp.MOVE)
                            selectedIds = emptySet()
                        }
                        SelectionAction(Icons.Filled.Delete, "Delete") { confirmMultiDelete = true }
                        if (selectedIds.size == 1) {
                            SelectionAction(Icons.Filled.Info, "Info") {
                                nodeForDetails = filteredItems.firstOrNull { it.id in selectedIds }
                                selectedIds = emptySet()
                            }
                        }
                    }
                }
            } else if (clipboard != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${clipboard!!.items.size} item(s) ready to " +
                                if (clipboard!!.operation == ClipboardOp.COPY) "copy" else "move",
                        )
                        Row {
                            TextButton(onClick = { clipboard = null }) { Text("Cancel") }
                            Button(
                                onClick = { clipboard?.let { startPaste(it) } },
                                modifier = Modifier.testTag("paste_button"),
                            ) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste here")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                loadCurrentDirectory(showSpinnerOnStart = false)
            },
            state = pullState,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isSelectionMode && !isSearching) {
                    BreadcrumbBar(
                        breadcrumbs = buildBreadcrumbs(currentDir),
                        onBreadcrumbClick = { crumb -> currentDir = File(crumb.path) },
                    )
                }

                if (isPerformingOperation) {
                    val progress = operationProgress
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = if (progress != null) "Processing ${progress.first} of ${progress.second}…" else "Working…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                when {
                    isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag("loading_indicator"))
                    }
                    errorMessage != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                    filteredItems.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isNotBlank()) "No matches for \"$searchQuery\"" else "This folder is empty",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize().testTag("file_list")) {
                        items(filteredItems, key = { it.id.raw }) { node ->
                            FileListItem(
                                node = node,
                                isSelectionMode = isSelectionMode,
                                isSelected = node.id in selectedIds,
                                onToggleSelect = { toggleSelected(node.id) },
                                onClick = { if (node.isDirectory) currentDir = File(node.id.raw.removePrefix("file:")) },
                                onShowDetails = { nodeForDetails = node },
                                onRename = { nodeForRename = node },
                                onDelete = { nodeToDelete = node },
                            )
                        }
                    }
                }
            }
        }
    }

    nodeToDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { nodeToDelete = null },
            title = { Text("Delete \"${node.name}\"?") },
            text = { Text(if (node.isDirectory) "This folder and everything inside it will be deleted." else "This file will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        backend.delete(node.id)
                        nodeToDelete = null
                        loadCurrentDirectory(showSpinnerOnStart = false)
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { nodeToDelete = null }) { Text("Cancel") } },
        )
    }

    if (confirmMultiDelete) {
        val toDelete = filteredItems.filter { it.id in selectedIds }
        AlertDialog(
            onDismissRequest = { confirmMultiDelete = false },
            title = { Text("Delete ${toDelete.size} item(s)?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        confirmMultiDelete = false
                        isPerformingOperation = true
                        operationProgress = 0 to toDelete.size
                        toDelete.forEachIndexed { index, node ->
                            backend.delete(node.id)
                            operationProgress = (index + 1) to toDelete.size
                        }
                        isPerformingOperation = false
                        operationProgress = null
                        selectedIds = emptySet()
                        loadCurrentDirectory(showSpinnerOnStart = false)
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmMultiDelete = false }) { Text("Cancel") } },
        )
    }

    pendingPaste?.let { state ->
        AlertDialog(
            onDismissRequest = { pendingPaste = null },
            title = { Text("$pendingCollisionCount item(s) already exist here") },
            text = { Text("Choose how to handle the conflicting item(s). This choice applies to all of them.") },
            confirmButton = {
                Column {
                    listOf(
                        CollisionChoice.KEEP_BOTH to "Keep both",
                        CollisionChoice.OVERWRITE to "Overwrite",
                        CollisionChoice.SKIP to "Skip",
                    ).forEach { (choice, label) ->
                        TextButton(
                            onClick = {
                                pendingPaste = null
                                scope.launch { performPaste(state, choice) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { pendingPaste = null; clipboard = null }) { Text("Cancel") } },
        )
    }

    nodeForDetails?.let { node ->
        FileDetailsDialog(node = node, onDismiss = { nodeForDetails = null })
    }

    nodeForRename?.let { node ->
        RenameDialog(
            initialName = node.name,
            onConfirm = { newName ->
                scope.launch {
                    backend.rename(node.id, newName)
                    nodeForRename = null
                    loadCurrentDirectory(showSpinnerOnStart = false)
                }
            },
            onDismiss = { nodeForRename = null },
        )
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onConfirm = { name ->
                scope.launch {
                    backend.createDirectory(FileNodeId.file(currentDir.absolutePath), name)
                    showNewFolderDialog = false
                    loadCurrentDirectory(showSpinnerOnStart = false)
                }
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }
}

@Composable
private fun SelectionAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
