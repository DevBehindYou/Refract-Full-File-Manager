package com.devbehindyou.refract.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devbehindyou.refract.domain.model.CollisionPolicy
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileOperation
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.OperationId
import com.devbehindyou.refract.domain.model.OperationOptions
import com.devbehindyou.refract.domain.model.OperationSnapshot
import com.devbehindyou.refract.domain.model.OperationStatus
import com.devbehindyou.refract.domain.model.OperationType
import com.devbehindyou.refract.domain.model.TransferBubble
import com.devbehindyou.refract.domain.repository.TransferBubbleRepository
import com.devbehindyou.refract.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.refract.domain.usecase.DeleteFileUseCase
import com.devbehindyou.refract.domain.usecase.FileOperationsEngine
import com.devbehindyou.refract.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.refract.domain.usecase.GetNodeUseCase
import com.devbehindyou.refract.domain.usecase.RenameFileUseCase
import com.devbehindyou.refract.ui.components.BreadcrumbItem
import com.devbehindyou.refract.ui.interaction.drag.DropDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOption(val label: String) {
    NAME("Name"),
    DATE_MODIFIED("Date modified"),
    SIZE("Size"),
}

enum class ClipboardOp { COPY, MOVE }

data class ClipboardState(val items: List<FileNode>, val operation: ClipboardOp)

data class BrowseUiState(
    val currentFolderId: FileNodeId,
    val currentFolderName: String = "",
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val rawItems: List<FileNode> = emptyList(),
    val filteredItems: List<FileNode> = emptyList(),
    val selectedIds: Set<FileNodeId> = emptySet(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val sortOption: SortOption = SortOption.NAME,
    val showHiddenFiles: Boolean = true,
    val clipboard: ClipboardState? = null,
    val activeOperation: OperationSnapshot? = null,
)

class BrowseViewModel(
    initialFolderId: FileNodeId,
    private val getDirectoryListingUseCase: GetDirectoryListingUseCase,
    private val getNodeUseCase: GetNodeUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val fileOperationsEngine: FileOperationsEngine,
    private val transferBubbleRepository: TransferBubbleRepository,
) : ViewModel() {
    private val folderHistory = mutableListOf<FileNodeId>()
    private val _uiState = MutableStateFlow(BrowseUiState(currentFolderId = initialFolderId))
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()
    val bubbles: StateFlow<List<TransferBubble>> = transferBubbleRepository.bubbles

    private var listingJob: Job? = null
    private var operationJob: Job? = null
    private var filterJob: Job? = null

    init {
        loadDirectory(initialFolderId)
    }

    fun loadDirectory(
        folderId: FileNodeId,
        isRefresh: Boolean = false,
    ) {
        listingJob?.cancel()
        listingJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        currentFolderId = folderId,
                        isLoading = !isRefresh,
                        isRefreshing = isRefresh,
                        errorMessage = null,
                        selectedIds = emptySet(),
                    )
                }

                // Resolve folder node for name and breadcrumbs
                val nodeResult = getNodeUseCase(folderId)
                val folderName =
                    when (nodeResult) {
                        is FileResult.Success -> nodeResult.value.displayName
                        is FileResult.Failure -> folderId.raw.substringAfterLast('/').ifEmpty { "Folder" }
                    }
                updateBreadcrumbs(folderId, folderName)

                val accumulated = mutableListOf<FileNode>()
                var lastPush = 0L
                var pushedAny = false
                var sawFailure = false

                getDirectoryListingUseCase(folderId).collect { result ->
                    when (result) {
                        is FileResult.Success -> {
                            accumulated.addAll(result.value)
                            val now = System.currentTimeMillis()
                            val isFirst = !pushedAny
                            if (isFirst || now - lastPush >= 120L) {
                                applyNewItems(accumulated.toList())
                                lastPush = now
                                pushedAny = true
                            }
                        }
                        is FileResult.Failure -> {
                            sawFailure = true
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    errorMessage = result.error.message(),
                                )
                            }
                        }
                    }
                }

                if (!sawFailure) {
                    applyNewItems(accumulated.toList())
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                }
            }
    }

    private suspend fun applyNewItems(items: List<FileNode>) {
        val query = _uiState.value.searchQuery
        val sort = _uiState.value.sortOption
        val showHidden = _uiState.value.showHiddenFiles
        val filtered =
            withContext(Dispatchers.Default) {
                filterAndSort(items, query, sort, showHidden)
            }
        _uiState.update {
            it.copy(
                rawItems = items,
                filteredItems = filtered,
                isLoading = false,
            )
        }
        // The query or sort may have changed while the list was being sorted.
        val current = _uiState.value
        if (current.searchQuery != query || current.sortOption != sort || current.showHiddenFiles != showHidden) {
            refilter()
        }
    }

    /**
     * Recomputes [BrowseUiState.filteredItems] off the main thread. Only the newest request may
     * publish: an older, slower run would otherwise overwrite the result for the latest query.
     */
    private fun refilter() {
        filterJob?.cancel()
        filterJob =
            viewModelScope.launch {
                val requested = _uiState.value
                val filtered =
                    withContext(Dispatchers.Default) {
                        filterAndSort(
                            requested.rawItems,
                            requested.searchQuery,
                            requested.sortOption,
                            requested.showHiddenFiles,
                        )
                    }
                _uiState.update { current ->
                    if (current.rawItems === requested.rawItems &&
                        current.searchQuery == requested.searchQuery &&
                        current.sortOption == requested.sortOption &&
                        current.showHiddenFiles == requested.showHiddenFiles
                    ) {
                        current.copy(filteredItems = filtered)
                    } else {
                        current
                    }
                }
            }
    }

    private fun filterAndSort(
        items: List<FileNode>,
        query: String,
        sort: SortOption,
        showHidden: Boolean,
    ): List<FileNode> {
        val visible = if (showHidden) items else items.filterNot { it.isHidden }
        val filtered =
            if (query.isBlank()) {
                visible
            } else {
                visible.filter { it.name.contains(query, ignoreCase = true) }
            }
        val comparator = comparatorFor(sort)
        return filtered.sortedWith(comparator)
    }

    private fun comparatorFor(sortOption: SortOption): Comparator<FileNode> {
        val byField: Comparator<FileNode> =
            when (sortOption) {
                SortOption.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortOption.DATE_MODIFIED -> compareByDescending { it.modifiedAt }
                SortOption.SIZE -> compareByDescending { it.size }
            }
        return compareByDescending<FileNode> { it.isDirectory }.then(byField)
    }

    private fun updateBreadcrumbs(
        folderId: FileNodeId,
        folderName: String,
    ) {
        val raw = folderId.raw
        val prefix = folderId.prefix?.scheme ?: ""
        val pathPart = raw.removePrefix(prefix)

        val segments = pathPart.split('/').filter { it.isNotEmpty() }
        val crumbs = mutableListOf<BreadcrumbItem>()

        var runningPath = ""
        for (segment in segments) {
            runningPath += "/$segment"
            crumbs.add(
                BreadcrumbItem(
                    name = segment,
                    path = "$prefix$runningPath",
                ),
            )
        }

        if (crumbs.isEmpty()) {
            crumbs.add(BreadcrumbItem(name = folderName.ifEmpty { "Root" }, path = raw))
        }

        _uiState.update { it.copy(currentFolderName = folderName, breadcrumbs = crumbs) }
    }

    fun navigateTo(folder: FileNode) {
        if (!folder.isDirectory) return
        folderHistory.add(_uiState.value.currentFolderId)
        loadDirectory(folder.id)
    }

    fun navigateBack(): Boolean {
        if (folderHistory.isNotEmpty()) {
            val previous = folderHistory.removeAt(folderHistory.lastIndex)
            loadDirectory(previous)
            return true
        }
        return false
    }

    fun navigateUp(): Boolean {
        val crumbs = _uiState.value.breadcrumbs
        if (crumbs.size > 1) {
            val parentCrumb = crumbs[crumbs.size - 2]
            navigateToBreadcrumb(parentCrumb)
            return true
        }
        return navigateBack()
    }

    fun navigateToBreadcrumb(crumb: BreadcrumbItem) {
        val targetId = FileNodeId.parse(crumb.path) ?: return
        if (targetId != _uiState.value.currentFolderId) {
            folderHistory.add(_uiState.value.currentFolderId)
            loadDirectory(targetId)
        }
    }

    fun refresh() {
        loadDirectory(_uiState.value.currentFolderId, isRefresh = true)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refilter()
    }

    fun toggleSearch(active: Boolean) {
        _uiState.update { it.copy(isSearching = active, searchQuery = if (!active) "" else it.searchQuery) }
        if (!active) {
            onSearchQueryChanged("")
        }
    }

    fun setShowHiddenFiles(show: Boolean) {
        if (_uiState.value.showHiddenFiles == show) return
        _uiState.update { it.copy(showHiddenFiles = show) }
        refilter()
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
        refilter()
    }

    fun toggleSelection(nodeId: FileNodeId) {
        _uiState.update { state ->
            val updated = state.selectedIds.toMutableSet()
            if (updated.contains(nodeId)) updated.remove(nodeId) else updated.add(nodeId)
            state.copy(selectedIds = updated)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.filteredItems.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            when (val result = createDirectoryUseCase(_uiState.value.currentFolderId, name)) {
                is FileResult.Success -> refresh()
                is FileResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.message()) }
            }
        }
    }

    fun rename(
        nodeId: FileNodeId,
        newName: String,
    ) {
        viewModelScope.launch {
            when (val result = renameFileUseCase(nodeId, newName)) {
                is FileResult.Success -> refresh()
                is FileResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.message()) }
            }
        }
    }

    fun deleteNodes(nodeIds: List<FileNodeId>) {
        viewModelScope.launch {
            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.DELETE,
                    sources = nodeIds,
                    destination = null,
                    options = OperationOptions(),
                    createdAt = System.currentTimeMillis(),
                )
            runOperation(op)
        }
    }

    fun copySelected() {
        val selectedNodes = _uiState.value.rawItems.filter { it.id in _uiState.value.selectedIds }
        if (selectedNodes.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clipboard = ClipboardState(selectedNodes, ClipboardOp.COPY),
                    selectedIds = emptySet(),
                )
            }
        }
    }

    fun cutSelected() {
        val selectedNodes = _uiState.value.rawItems.filter { it.id in _uiState.value.selectedIds }
        if (selectedNodes.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clipboard = ClipboardState(selectedNodes, ClipboardOp.MOVE),
                    selectedIds = emptySet(),
                )
            }
        }
    }

    fun clearClipboard() {
        _uiState.update { it.copy(clipboard = null) }
    }

    fun paste(collisionPolicy: CollisionPolicy = CollisionPolicy.ASK) {
        val clip = _uiState.value.clipboard ?: return
        val currentFolder = _uiState.value.currentFolderId

        val opType =
            when (clip.operation) {
                ClipboardOp.COPY -> OperationType.COPY
                ClipboardOp.MOVE -> OperationType.MOVE
            }

        val op =
            FileOperation(
                id = OperationId.random(),
                type = opType,
                sources = clip.items.map { it.id },
                destination = currentFolder,
                options = OperationOptions(collisionPolicy = collisionPolicy),
                createdAt = System.currentTimeMillis(),
            )

        runOperation(op)
        _uiState.update { it.copy(clipboard = null) }
    }

    fun compressSelected() {
        val selectedNodes = _uiState.value.rawItems.filter { it.id in _uiState.value.selectedIds }
        if (selectedNodes.isEmpty()) return
        val operation =
            FileOperation(
                id = OperationId.random(),
                type = OperationType.COMPRESS,
                sources = selectedNodes.map { it.id },
                destination = _uiState.value.currentFolderId,
                options = OperationOptions(),
                createdAt = System.currentTimeMillis(),
            )
        clearSelection()
        runOperation(operation)
    }

    fun compressSingle(node: FileNode) {
        val operation =
            FileOperation(
                id = OperationId.random(),
                type = OperationType.COMPRESS,
                sources = listOf(node.id),
                destination = _uiState.value.currentFolderId,
                options = OperationOptions(),
                createdAt = System.currentTimeMillis(),
            )
        runOperation(operation)
    }

    fun extractArchive(node: FileNode) {
        val operation =
            FileOperation(
                id = OperationId.random(),
                type = OperationType.EXTRACT,
                sources = listOf(node.id),
                destination = _uiState.value.currentFolderId,
                options = OperationOptions(),
                createdAt = System.currentTimeMillis(),
            )
        runOperation(operation)
    }

    private fun runOperation(operation: FileOperation) {
        operationJob?.cancel()
        operationJob =
            viewModelScope.launch {
                fileOperationsEngine.execute(operation).collect { snapshot ->
                    _uiState.update { it.copy(activeOperation = snapshot) }
                    if (snapshot.status is OperationStatus.Completed ||
                        snapshot.status is OperationStatus.PartiallyCompleted
                    ) {
                        refresh()
                    }
                }
            }
    }

    fun executeDrop(decision: DropDecision) {
        when (decision) {
            is DropDecision.Move -> {
                val operation =
                    FileOperation(
                        id = OperationId.random(),
                        type = OperationType.MOVE,
                        sources = decision.payload.itemIds,
                        destination = decision.destination,
                        options = OperationOptions(collisionPolicy = CollisionPolicy.ASK),
                        createdAt = System.currentTimeMillis(),
                    )
                clearSelection()
                runOperation(operation)
            }
            is DropDecision.Copy -> {
                val operation =
                    FileOperation(
                        id = OperationId.random(),
                        type = OperationType.COPY,
                        sources = decision.payload.itemIds,
                        destination = decision.destination,
                        options = OperationOptions(collisionPolicy = CollisionPolicy.ASK),
                        createdAt = System.currentTimeMillis(),
                    )
                clearSelection()
                runOperation(operation)
            }
            is DropDecision.AddToBubble -> {
                viewModelScope.launch {
                    transferBubbleRepository.addItemsToBubble(decision.bubbleId, decision.payload.items)
                }
            }
            is DropDecision.Cancel -> {}
        }
    }

    fun createBubble(name: String? = null) {
        viewModelScope.launch {
            transferBubbleRepository.createBubble(name)
        }
    }

    fun addFilesToBubble(
        bubbleId: String,
        files: List<FileNode>,
    ) {
        viewModelScope.launch {
            transferBubbleRepository.addItemsToBubble(bubbleId, files)
        }
    }

    fun addSelectionToBubble(bubbleId: String) {
        val selectedNodes = _uiState.value.rawItems.filter { it.id in _uiState.value.selectedIds }
        if (selectedNodes.isNotEmpty()) {
            addFilesToBubble(bubbleId, selectedNodes)
            clearSelection()
        }
    }

    fun removeBubbleItem(
        bubbleId: String,
        itemId: Long,
    ) {
        viewModelScope.launch {
            transferBubbleRepository.removeItemFromBubble(bubbleId, itemId)
        }
    }

    fun clearBubble(bubbleId: String) {
        viewModelScope.launch {
            transferBubbleRepository.clearBubble(bubbleId)
        }
    }

    fun deleteBubble(bubbleId: String) {
        viewModelScope.launch {
            transferBubbleRepository.deleteBubble(bubbleId)
        }
    }

    fun transferBubble(
        bubble: TransferBubble,
        isMove: Boolean,
        clearAfter: Boolean,
    ) {
        if (bubble.items.isEmpty()) return
        val currentFolder = _uiState.value.currentFolderId
        val operation =
            FileOperation(
                id = OperationId.random(),
                type = if (isMove) OperationType.MOVE else OperationType.COPY,
                sources = bubble.items.map { it.fileNodeId },
                destination = currentFolder,
                options = OperationOptions(collisionPolicy = CollisionPolicy.ASK),
                createdAt = System.currentTimeMillis(),
            )
        runOperation(operation)
        if (isMove || clearAfter) {
            viewModelScope.launch {
                transferBubbleRepository.clearBubble(bubble.id)
            }
        }
    }

    fun cancelActiveOperation() {
        operationJob?.cancel()
        _uiState.update { it.copy(activeOperation = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun FileError.message(): String =
        when (this) {
            is FileError.FileNotFound -> "File not found: ${name.orEmpty()}"
            is FileError.FileAlreadyExists -> "File already exists: $name"
            is FileError.AccessDenied -> "Access denied: ${name.orEmpty()}"
            is FileError.PermissionDenied -> "Permission denied"
            is FileError.PlatformRestricted -> "Access restricted by Android OS"
            is FileError.ProviderUnavailable -> "Storage provider unavailable"
            is FileError.StorageUnavailable -> "Storage volume unavailable: ${volumeLabel.orEmpty()}"
            is FileError.ReadOnlyStorage -> "Storage is read-only"
            is FileError.DiskFull -> "Insufficient storage space"
            is FileError.OutOfMemory -> "Out of memory"
            is FileError.PathTooLong -> "Path is too long: ${name.orEmpty()}"
            is FileError.InvalidDestination -> "Invalid destination: ${name.orEmpty()}"
            is FileError.InvalidName -> "Invalid name: $name"
            is FileError.OperationCancelled -> "Operation cancelled"
            is FileError.IncompleteWrite -> "Write incomplete for $name"
            is FileError.PartialFailure -> "Partial failure: $failedCount of $totalCount failed"
            is FileError.IoFailure -> "I/O failure: ${name.orEmpty()}"
            is FileError.UnsupportedFormat -> "Unsupported format: ${mimeType.orEmpty()}"
            is FileError.CorruptedArchive -> "Corrupted archive: ${name.orEmpty()}"
            is FileError.SuspiciousArchive -> "Suspicious archive: $reason"
            is FileError.FileTooLarge -> "File too large: $name"
            is FileError.Unknown -> "Unknown error ($marker)"
        }

    companion object {
        fun provideFactory(
            initialFolderId: FileNodeId,
            getDirectoryListingUseCase: GetDirectoryListingUseCase,
            getNodeUseCase: GetNodeUseCase,
            createDirectoryUseCase: CreateDirectoryUseCase,
            renameFileUseCase: RenameFileUseCase,
            deleteFileUseCase: DeleteFileUseCase,
            fileOperationsEngine: FileOperationsEngine,
            transferBubbleRepository: TransferBubbleRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BrowseViewModel(
                        initialFolderId = initialFolderId,
                        getDirectoryListingUseCase = getDirectoryListingUseCase,
                        getNodeUseCase = getNodeUseCase,
                        createDirectoryUseCase = createDirectoryUseCase,
                        renameFileUseCase = renameFileUseCase,
                        deleteFileUseCase = deleteFileUseCase,
                        fileOperationsEngine = fileOperationsEngine,
                        transferBubbleRepository = transferBubbleRepository,
                    ) as T
                }
            }
    }
}
