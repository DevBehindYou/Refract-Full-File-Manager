package com.devbehindyou.refract.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.devbehindyou.refract.RefractApp
import com.devbehindyou.refract.domain.model.CollisionPolicy
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.HideMode
import com.devbehindyou.refract.domain.model.OperationStatus
import com.devbehindyou.refract.domain.model.TransferBubble
import com.devbehindyou.refract.ui.components.BreadcrumbBar
import com.devbehindyou.refract.ui.components.FileDetailsDialog
import com.devbehindyou.refract.ui.components.FileListItem
import com.devbehindyou.refract.ui.components.FilePreviewDialog
import com.devbehindyou.refract.ui.components.FilePreviewPane
import com.devbehindyou.refract.ui.components.HideFileDialog
import com.devbehindyou.refract.ui.components.NewFolderDialog
import com.devbehindyou.refract.ui.components.RenameDialog
import com.devbehindyou.refract.ui.interaction.bubble.BubbleDetailsSheet
import com.devbehindyou.refract.ui.interaction.bubble.BubbleTransferDialog
import com.devbehindyou.refract.ui.interaction.bubble.TransferBubbleRail
import com.devbehindyou.refract.ui.interaction.drag.ActiveDropTarget
import com.devbehindyou.refract.ui.interaction.drag.DragFloatingPreview
import com.devbehindyou.refract.ui.interaction.drag.DropDecisionDialog
import com.devbehindyou.refract.ui.interaction.drag.DropTargetType
import com.devbehindyou.refract.ui.interaction.drag.FileDragController
import com.devbehindyou.refract.ui.interaction.drag.edgeAutoScroll
import com.devbehindyou.refract.ui.interaction.drag.fileDragSource
import com.devbehindyou.refract.ui.interaction.drag.fileDropTarget
import com.devbehindyou.refract.ui.interaction.peek.QuickPeekController
import com.devbehindyou.refract.ui.interaction.peek.QuickPeekOverlay
import com.devbehindyou.refract.ui.interaction.peek.mediaGestureArbiter
import com.devbehindyou.refract.ui.security.AuthGate
import kotlinx.coroutines.launch

enum class DualPaneMode {
    DUAL_BROWSE,
    PREVIEW,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    initialFolderId: FileNodeId,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFile: ((FileNode) -> Unit)? = null,
    viewModel: BrowseViewModel =
        run {
            val app = LocalContext.current.applicationContext as RefractApp
            val owner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner())
            remember(owner, initialFolderId.raw) {
                ViewModelProvider(
                    owner,
                    BrowseViewModel.provideFactory(
                        initialFolderId = initialFolderId,
                        getDirectoryListingUseCase = app.container.getDirectoryListingUseCase,
                        getNodeUseCase = app.container.getNodeUseCase,
                        createDirectoryUseCase = app.container.createDirectoryUseCase,
                        renameFileUseCase = app.container.renameFileUseCase,
                        deleteFileUseCase = app.container.deleteFileUseCase,
                        fileOperationsEngine = app.container.fileOperationsEngine,
                        transferBubbleRepository = app.container.transferBubbleRepository,
                    ),
                )["browse:${initialFolderId.raw}", BrowseViewModel::class.java]
            }
        },
) {
    val app = LocalContext.current.applicationContext as RefractApp
    val uiState by viewModel.uiState.collectAsState()
    val settings by app.container.settingsRepository.settings.collectAsState()

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var nodeToRename by remember { mutableStateOf<FileNode?>(null) }
    var nodeForDetails by remember { mutableStateOf<FileNode?>(null) }
    var nodeForPreview by remember { mutableStateOf<FileNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<FileNode?>(null) }
    var confirmMultiDelete by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val isSelectionMode = uiState.selectedIds.isNotEmpty()
    val configuration = LocalConfiguration.current
    val isDualPane = configuration.screenWidthDp >= 720
    var dualPaneMode by remember { mutableStateOf(DualPaneMode.DUAL_BROWSE) }
    val dragController = remember { FileDragController() }
    val quickPeekController = remember { QuickPeekController() }

    val secondaryViewModel =
        remember(initialFolderId.raw) {
            BrowseViewModel(
                initialFolderId = initialFolderId,
                getDirectoryListingUseCase = app.container.getDirectoryListingUseCase,
                getNodeUseCase = app.container.getNodeUseCase,
                createDirectoryUseCase = app.container.createDirectoryUseCase,
                renameFileUseCase = app.container.renameFileUseCase,
                deleteFileUseCase = app.container.deleteFileUseCase,
                fileOperationsEngine = app.container.fileOperationsEngine,
                transferBubbleRepository = app.container.transferBubbleRepository,
            )
        }
    val secondaryUiState by secondaryViewModel.uiState.collectAsState()

    val bubbles by viewModel.bubbles.collectAsState()
    var bubbleForDetails by remember { mutableStateOf<TransferBubble?>(null) }
    var bubbleForTransfer by remember { mutableStateOf<TransferBubble?>(null) }
    var showAddToBubbleMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var nodeToHide by remember { mutableStateOf<FileNode?>(null) }
    var showHiddenScreen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    fun hideNode(
        node: FileNode,
        mode: HideMode,
    ) {
        coroutineScope.launch {
            val repository = app.container.hiddenFilesRepository
            val result =
                when (mode) {
                    HideMode.GALLERY -> repository.hideFromGallery(node)
                    HideMode.FAST_OBSCURE -> repository.fastObscure(node)
                    HideMode.PRIVATE_STORAGE -> repository.moveToPrivateStorage(node)
                }
            val error = result.exceptionOrNull()
            val message =
                if (error == null) {
                    "Hidden ${node.name}"
                } else {
                    "Could not hide ${node.name}: ${error.message ?: "unknown error"}"
                }
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            viewModel.refresh()
            nodeToHide = null
        }
    }

    // With a default hiding method set in Settings the choice dialog is skipped.
    LaunchedEffect(nodeToHide, settings.defaultHideMode) {
        val node = nodeToHide
        val mode = settings.defaultHideMode
        if (node != null && mode != null) hideNode(node, mode)
    }

    LaunchedEffect(settings.showHiddenFiles) {
        viewModel.setShowHiddenFiles(settings.showHiddenFiles)
        secondaryViewModel.setShowHiddenFiles(settings.showHiddenFiles)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler {
        when {
            showHiddenScreen -> {
                showHiddenScreen = false
                viewModel.refresh()
            }
            quickPeekController.isPeeking -> quickPeekController.dismiss()
            isSelectionMode -> viewModel.clearSelection()
            uiState.isSearching -> viewModel.toggleSearch(false)
            !viewModel.navigateBack() -> onNavigateBack()
        }
    }

    if (showHiddenScreen) {
        AuthGate(
            required = settings.requireAuthForHidden,
            title = "Unlock hidden files",
            onDenied = {
                showHiddenScreen = false
                viewModel.refresh()
            },
        ) {
            HiddenFilesScreen(
                repository = app.container.hiddenFilesRepository,
                onNavigateBack = {
                    showHiddenScreen = false
                    viewModel.refresh()
                },
            )
        }
        return
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    handleBrowseKeyEvent(
                        event = event,
                        viewModel = viewModel,
                        isSelectionMode = isSelectionMode,
                        uiState = uiState,
                        onOpenFile = onOpenFile,
                        onRequestRename = { nodeToRename = it },
                        onRequestPreview = { nodeForPreview = it },
                        onRequestMultiDelete = { confirmMultiDelete = true },
                    )
                },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                BrowseTopBar(
                    uiState = uiState,
                    isSelectionMode = isSelectionMode,
                    bubbles = bubbles,
                    isDualPane = isDualPane,
                    dualPaneMode = dualPaneMode,
                    showSortMenu = showSortMenu,
                    onShowSortMenu = { showSortMenu = it },
                    showAddToBubbleMenu = showAddToBubbleMenu,
                    onShowAddToBubbleMenu = { showAddToBubbleMenu = it },
                    actions =
                        BrowseTopBarActions(
                            onClearSelection = { viewModel.clearSelection() },
                            onSelectAll = { viewModel.selectAll() },
                            onAddToBubble = { bubbleId -> viewModel.addSelectionToBubble(bubbleId) },
                            onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                            onToggleSearch = { viewModel.toggleSearch(it) },
                            onNavigateBack = {
                                if (!viewModel.navigateBack()) {
                                    onNavigateBack()
                                }
                            },
                            onSetSortOption = { viewModel.setSortOption(it) },
                            onShowHiddenScreen = { showHiddenScreen = true },
                            onToggleDualPaneMode = {
                                dualPaneMode =
                                    if (dualPaneMode == DualPaneMode.DUAL_BROWSE) {
                                        DualPaneMode.PREVIEW
                                    } else {
                                        DualPaneMode.DUAL_BROWSE
                                    }
                            },
                            onShowNewFolderDialog = { showNewFolderDialog = true },
                        ),
                )
            },
            bottomBar = {
                BrowseBottomBar(
                    uiState = uiState,
                    isSelectionMode = isSelectionMode,
                    onCopySelected = { viewModel.copySelected() },
                    onCutSelected = { viewModel.cutSelected() },
                    onCompressSelected = { viewModel.compressSelected() },
                    onDeleteSelected = { confirmMultiDelete = true },
                    onShowDetails = { node ->
                        nodeForDetails = node
                        viewModel.clearSelection()
                    },
                    onClearClipboard = { viewModel.clearClipboard() },
                    onPaste = { viewModel.paste(CollisionPolicy.ASK) },
                )
            },
        ) { innerPadding ->
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullState,
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
            ) {
                if (isDualPane) {
                    DualPaneBrowseContent(
                        uiState = uiState,
                        secondaryUiState = secondaryUiState,
                        dualPaneMode = dualPaneMode,
                        viewModel = viewModel,
                        secondaryViewModel = secondaryViewModel,
                        dragController = dragController,
                        quickPeekController = quickPeekController,
                        nodeForPreview = nodeForPreview,
                        actions =
                            DualPaneItemActions(
                                onOpenFile = onOpenFile,
                                onPreviewNode = { node ->
                                    nodeForPreview = node
                                    if (dualPaneMode != DualPaneMode.PREVIEW) {
                                        dualPaneMode = DualPaneMode.PREVIEW
                                    }
                                },
                                onSecondaryPreviewNode = { node ->
                                    nodeForPreview = node
                                    dualPaneMode = DualPaneMode.PREVIEW
                                },
                                onClosePreview = { nodeForPreview = null },
                                onShowDetails = { nodeForDetails = it },
                                onRename = { nodeToRename = it },
                                onDelete = { nodeToDelete = it },
                                onHide = { nodeToHide = it },
                            ),
                    )
                } else {
                    FileListContent(
                        uiState = uiState,
                        isSelectionMode = isSelectionMode,
                        viewModel = viewModel,
                        dragController = dragController,
                        quickPeekController = quickPeekController,
                        onOpenFile = onOpenFile,
                        onPreviewNode = { nodeForPreview = it },
                        onShowDetails = { nodeForDetails = it },
                        onRename = { nodeToRename = it },
                        onDelete = { nodeToDelete = it },
                        onHide = { nodeToHide = it },
                    )
                }
            }
        }

        // Floating drag preview follows pointer
        DragFloatingPreview(controller = dragController)

        // Quick Peek overlay
        QuickPeekOverlay(
            controller = quickPeekController,
            imagePreviewHelper = app.container.imagePreviewHelper,
        )

        // Transfer Bubble Rail on the right edge
        TransferBubbleRail(
            bubbles = bubbles,
            dragController = dragController,
            onBubbleClick = { bubble -> bubbleForTransfer = bubble },
            onBubbleLongClick = { bubble -> bubbleForDetails = bubble },
            onCreateBubble = { viewModel.createBubble() },
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
        )

        BrowseDialogs(
            viewModel = viewModel,
            secondaryViewModel = secondaryViewModel,
            isDualPane = isDualPane,
            uiState = uiState,
            bubbles = bubbles,
            dragController = dragController,
            sheetState = sheetState,
            state =
                BrowseDialogState(
                    nodeToRename = nodeToRename,
                    nodeForDetails = nodeForDetails,
                    nodeToDelete = nodeToDelete,
                    confirmMultiDelete = confirmMultiDelete,
                    showNewFolderDialog = showNewFolderDialog,
                    nodeToHide = if (settings.defaultHideMode == null) nodeToHide else null,
                    bubbleForTransfer = bubbleForTransfer,
                    bubbleForDetails = bubbleForDetails,
                    nodeForPreview = if (!isDualPane) nodeForPreview else null,
                ),
            callbacks =
                BrowseDialogCallbacks(
                    onDismissRename = { nodeToRename = null },
                    onDismissDetails = { nodeForDetails = null },
                    onDismissDelete = { nodeToDelete = null },
                    onDismissMultiDelete = { confirmMultiDelete = false },
                    onDismissNewFolder = { showNewFolderDialog = false },
                    onDismissHide = { nodeToHide = null },
                    onDismissTransfer = { bubbleForTransfer = null },
                    onDismissBubbleDetails = { bubbleForDetails = null },
                    onDismissPreview = { nodeForPreview = null },
                    onReviewFiles = { bubble ->
                        bubbleForDetails = bubble
                        bubbleForTransfer = null
                    },
                    onHideNode = { node, mode -> hideNode(node, mode) },
                ),
        )
    }
}

private fun handleBrowseKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    viewModel: BrowseViewModel,
    isSelectionMode: Boolean,
    uiState: BrowseUiState,
    onOpenFile: ((FileNode) -> Unit)?,
    onRequestRename: (FileNode) -> Unit,
    onRequestPreview: (FileNode) -> Unit,
    onRequestMultiDelete: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.isCtrlPressed && event.key == Key.A -> {
            viewModel.selectAll()
            true
        }
        event.isCtrlPressed && event.key == Key.C -> {
            if (isSelectionMode) viewModel.copySelected()
            true
        }
        event.isCtrlPressed && event.key == Key.X -> {
            if (isSelectionMode) viewModel.cutSelected()
            true
        }
        event.isCtrlPressed && event.key == Key.V -> {
            viewModel.paste(CollisionPolicy.ASK)
            true
        }
        event.isCtrlPressed && event.key == Key.F -> {
            viewModel.toggleSearch(true)
            true
        }
        event.key == Key.Delete -> {
            if (isSelectionMode) onRequestMultiDelete()
            true
        }
        event.key == Key.F2 -> {
            if (uiState.selectedIds.size == 1) {
                val single = uiState.rawItems.firstOrNull { it.id in uiState.selectedIds }
                if (single != null) onRequestRename(single)
            }
            true
        }
        event.key == Key.Escape -> {
            if (isSelectionMode) {
                viewModel.clearSelection()
            } else if (uiState.isSearching) {
                viewModel.toggleSearch(false)
            }
            true
        }
        event.key == Key.Backspace || (event.isAltPressed && event.key == Key.DirectionLeft) -> {
            viewModel.navigateUp()
            true
        }
        event.key == Key.Enter -> {
            if (uiState.selectedIds.size == 1) {
                val single = uiState.rawItems.firstOrNull { it.id in uiState.selectedIds }
                if (single != null) {
                    if (single.isDirectory) {
                        viewModel.navigateTo(single)
                    } else if (onOpenFile != null) {
                        onOpenFile(single)
                    } else {
                        onRequestPreview(single)
                    }
                }
            }
            true
        }
        else -> false
    }
}

private data class BrowseTopBarActions(
    val onClearSelection: () -> Unit,
    val onSelectAll: () -> Unit,
    val onAddToBubble: (String) -> Unit,
    val onSearchQueryChanged: (String) -> Unit,
    val onToggleSearch: (Boolean) -> Unit,
    val onNavigateBack: () -> Unit,
    val onSetSortOption: (SortOption) -> Unit,
    val onShowHiddenScreen: () -> Unit,
    val onToggleDualPaneMode: () -> Unit,
    val onShowNewFolderDialog: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseTopBar(
    uiState: BrowseUiState,
    isSelectionMode: Boolean,
    bubbles: List<TransferBubble>,
    isDualPane: Boolean,
    dualPaneMode: DualPaneMode,
    showSortMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    showAddToBubbleMenu: Boolean,
    onShowAddToBubbleMenu: (Boolean) -> Unit,
    actions: BrowseTopBarActions,
) {
    when {
        isSelectionMode ->
            TopAppBar(
                title = { Text("${uiState.selectedIds.size} selected") },
                navigationIcon = {
                    IconButton(onClick = actions.onClearSelection) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                    }
                },
                actions = {
                    if (bubbles.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { onShowAddToBubbleMenu(true) }) {
                                Icon(Icons.Filled.AllInbox, contentDescription = "Add to Transfer Bubble")
                            }
                            DropdownMenu(
                                expanded = showAddToBubbleMenu,
                                onDismissRequest = { onShowAddToBubbleMenu(false) },
                            ) {
                                for (bubble in bubbles) {
                                    DropdownMenuItem(
                                        text = { Text("Add to ${bubble.displayName}") },
                                        onClick = {
                                            actions.onAddToBubble(bubble.id)
                                            onShowAddToBubbleMenu(false)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = actions.onSelectAll,
                        modifier = Modifier.testTag("select_all_button"),
                    ) {
                        Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                    }
                },
            )
        uiState.isSearching ->
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = actions.onSearchQueryChanged,
                        placeholder = { Text("Search this folder") },
                        singleLine = true,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("search_field"),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { actions.onToggleSearch(false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                    }
                },
            )
        else ->
            TopAppBar(
                title = {
                    Text(
                        uiState.currentFolderName.ifEmpty { "Browse" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { actions.onToggleSearch(true) },
                        modifier = Modifier.testTag("search_icon_button"),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search this folder")
                    }
                    Box {
                        IconButton(
                            onClick = { onShowSortMenu(true) },
                            modifier = Modifier.testTag("sort_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort by")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { onShowSortMenu(false) },
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = {
                                        if (uiState.sortOption == option) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = "Currently sorted by ${option.label}",
                                            )
                                        }
                                    },
                                    onClick = {
                                        actions.onSetSortOption(option)
                                        onShowSortMenu(false)
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Hidden files") },
                                onClick = {
                                    onShowSortMenu(false)
                                    actions.onShowHiddenScreen()
                                },
                            )
                        }
                    }
                    if (isDualPane) {
                        IconButton(onClick = actions.onToggleDualPaneMode) {
                            Icon(
                                imageVector =
                                    if (dualPaneMode == DualPaneMode.DUAL_BROWSE) {
                                        Icons.Filled.Preview
                                    } else {
                                        Icons.Filled.VerticalSplit
                                    },
                                contentDescription =
                                    if (dualPaneMode == DualPaneMode.DUAL_BROWSE) {
                                        "Switch to Preview Pane"
                                    } else {
                                        "Switch to Dual Browse"
                                    },
                            )
                        }
                    }
                    IconButton(
                        onClick = actions.onShowNewFolderDialog,
                        modifier = Modifier.testTag("new_folder_button"),
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                    }
                },
            )
    }
}

@Composable
private fun BrowseBottomBar(
    uiState: BrowseUiState,
    isSelectionMode: Boolean,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onCompressSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShowDetails: (FileNode) -> Unit,
    onClearClipboard: () -> Unit,
    onPaste: () -> Unit,
) {
    if (isSelectionMode) {
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SelectionAction(Icons.Filled.ContentCopy, "Copy", onCopySelected)
                SelectionAction(Icons.Filled.ContentCut, "Move", onCutSelected)
                SelectionAction(Icons.Filled.FolderZip, "Compress", onCompressSelected)
                SelectionAction(Icons.Filled.Delete, "Delete", onDeleteSelected)
                if (uiState.selectedIds.size == 1) {
                    SelectionAction(Icons.Filled.Info, "Info") {
                        val single = uiState.rawItems.firstOrNull { it.id in uiState.selectedIds }
                        if (single != null) onShowDetails(single)
                    }
                }
            }
        }
    } else if (uiState.clipboard != null) {
        val clip = uiState.clipboard!!
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        "${clip.items.size} item(s) ready to " +
                            if (clip.operation == ClipboardOp.COPY) "copy" else "move",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Row {
                    TextButton(onClick = onClearClipboard) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onPaste,
                        modifier = Modifier.testTag("paste_button"),
                    ) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste here")
                    }
                }
            }
        }
    }
}

private data class DualPaneItemActions(
    val onOpenFile: ((FileNode) -> Unit)?,
    val onPreviewNode: (FileNode) -> Unit,
    val onSecondaryPreviewNode: (FileNode) -> Unit,
    val onClosePreview: () -> Unit,
    val onShowDetails: (FileNode) -> Unit,
    val onRename: (FileNode) -> Unit,
    val onDelete: (FileNode) -> Unit,
    val onHide: (FileNode) -> Unit,
)

@Composable
private fun DualPaneBrowseContent(
    uiState: BrowseUiState,
    secondaryUiState: BrowseUiState,
    dualPaneMode: DualPaneMode,
    viewModel: BrowseViewModel,
    secondaryViewModel: BrowseViewModel,
    dragController: FileDragController,
    quickPeekController: QuickPeekController,
    nodeForPreview: FileNode?,
    actions: DualPaneItemActions,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .fileDropTarget(
                        controller = dragController,
                        target =
                            ActiveDropTarget(
                                id = uiState.currentFolderId.raw,
                                destinationId = uiState.currentFolderId,
                                type = DropTargetType.PANE,
                                displayName = uiState.breadcrumbs.lastOrNull()?.name ?: "Left Pane",
                                isWritable = true,
                            ),
                    ),
        ) {
            FileListContent(
                uiState = uiState,
                isSelectionMode = uiState.selectedIds.isNotEmpty(),
                viewModel = viewModel,
                dragController = dragController,
                quickPeekController = quickPeekController,
                onOpenFile = actions.onOpenFile,
                onPreviewNode = actions.onPreviewNode,
                onShowDetails = actions.onShowDetails,
                onRename = actions.onRename,
                onDelete = actions.onDelete,
                onHide = actions.onHide,
            )
        }
        VerticalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Box(
            modifier =
                Modifier
                    .weight(if (dualPaneMode == DualPaneMode.DUAL_BROWSE) 1f else 1.2f)
                    .fillMaxHeight(),
        ) {
            if (dualPaneMode == DualPaneMode.DUAL_BROWSE) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .fileDropTarget(
                                controller = dragController,
                                target =
                                    ActiveDropTarget(
                                        id = secondaryUiState.currentFolderId.raw,
                                        destinationId = secondaryUiState.currentFolderId,
                                        type = DropTargetType.PANE,
                                        displayName = secondaryUiState.breadcrumbs.lastOrNull()?.name ?: "Right Pane",
                                        isWritable = true,
                                    ),
                            ),
                ) {
                    FileListContent(
                        uiState = secondaryUiState,
                        isSelectionMode = secondaryUiState.selectedIds.isNotEmpty(),
                        viewModel = secondaryViewModel,
                        dragController = dragController,
                        quickPeekController = quickPeekController,
                        onOpenFile = actions.onOpenFile,
                        onPreviewNode = actions.onSecondaryPreviewNode,
                        onShowDetails = actions.onShowDetails,
                        onRename = actions.onRename,
                        onDelete = actions.onDelete,
                        onHide = actions.onHide,
                    )
                }
            } else {
                if (nodeForPreview != null) {
                    FilePreviewPane(
                        node = nodeForPreview,
                        onClose = actions.onClosePreview,
                    )
                } else {
                    EmptyPreviewPane()
                }
            }
        }
    }
}

private data class BrowseDialogState(
    val nodeToRename: FileNode?,
    val nodeForDetails: FileNode?,
    val nodeToDelete: FileNode?,
    val confirmMultiDelete: Boolean,
    val showNewFolderDialog: Boolean,
    val nodeToHide: FileNode?,
    val bubbleForTransfer: TransferBubble?,
    val bubbleForDetails: TransferBubble?,
    val nodeForPreview: FileNode?,
)

private data class BrowseDialogCallbacks(
    val onDismissRename: () -> Unit,
    val onDismissDetails: () -> Unit,
    val onDismissDelete: () -> Unit,
    val onDismissMultiDelete: () -> Unit,
    val onDismissNewFolder: () -> Unit,
    val onDismissHide: () -> Unit,
    val onDismissTransfer: () -> Unit,
    val onDismissBubbleDetails: () -> Unit,
    val onDismissPreview: () -> Unit,
    val onReviewFiles: (TransferBubble) -> Unit,
    val onHideNode: (FileNode, HideMode) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseDialogs(
    viewModel: BrowseViewModel,
    secondaryViewModel: BrowseViewModel,
    isDualPane: Boolean,
    uiState: BrowseUiState,
    bubbles: List<TransferBubble>,
    dragController: FileDragController,
    sheetState: androidx.compose.material3.SheetState,
    state: BrowseDialogState,
    callbacks: BrowseDialogCallbacks,
) {
    dragController.pendingDecision?.let { (payload, target) ->
        DropDecisionDialog(
            payload = payload,
            target = target,
            onDecision = { decision ->
                dragController.clearPendingDecision()
                viewModel.executeDrop(decision)
                if (isDualPane) {
                    secondaryViewModel.refresh()
                }
            },
        )
    }

    state.bubbleForTransfer?.let { bubble ->
        BubbleTransferDialog(
            bubble = bubble,
            targetDirectoryName = uiState.currentFolderName.ifEmpty { "current folder" },
            onMove = { clearAfter ->
                viewModel.transferBubble(bubble, isMove = true, clearAfter = clearAfter)
                callbacks.onDismissTransfer()
            },
            onCopy = { clearAfter ->
                viewModel.transferBubble(bubble, isMove = false, clearAfter = clearAfter)
                callbacks.onDismissTransfer()
            },
            onReviewFiles = {
                callbacks.onReviewFiles(bubble)
            },
            onDismiss = callbacks.onDismissTransfer,
        )
    }

    state.bubbleForDetails?.let { bubble ->
        val currentBubble = bubbles.find { it.id == bubble.id } ?: bubble
        BubbleDetailsSheet(
            bubble = currentBubble,
            sheetState = sheetState,
            onRemoveItem = { itemId -> viewModel.removeBubbleItem(currentBubble.id, itemId) },
            onClearBubble = { viewModel.clearBubble(currentBubble.id) },
            onDeleteBubble = {
                viewModel.deleteBubble(currentBubble.id)
                callbacks.onDismissBubbleDetails()
            },
            onDismiss = callbacks.onDismissBubbleDetails,
        )
    }

    if (state.showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = callbacks.onDismissNewFolder,
            onConfirm = { name ->
                viewModel.createFolder(name)
                callbacks.onDismissNewFolder()
            },
        )
    }

    state.nodeToRename?.let { node ->
        RenameDialog(
            initialName = node.name,
            onDismiss = callbacks.onDismissRename,
            onConfirm = { newName ->
                viewModel.rename(node.id, newName)
                callbacks.onDismissRename()
            },
        )
    }

    state.nodeForDetails?.let { node ->
        FileDetailsDialog(node = node, onDismiss = callbacks.onDismissDetails)
    }

    state.nodeToDelete?.let { node ->
        AlertDialog(
            onDismissRequest = callbacks.onDismissDelete,
            title = { Text("Delete file?") },
            text = { Text("Are you sure you want to delete \"${node.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNodes(listOf(node.id))
                        callbacks.onDismissDelete()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onDismissDelete) { Text("Cancel") }
            },
        )
    }

    state.nodeToHide?.let { node ->
        HideFileDialog(
            node = node,
            onConfirm = { mode -> callbacks.onHideNode(node, mode) },
            onDismiss = callbacks.onDismissHide,
        )
    }

    if (state.confirmMultiDelete) {
        val count = uiState.selectedIds.size
        AlertDialog(
            onDismissRequest = callbacks.onDismissMultiDelete,
            title = { Text("Delete $count items?") },
            text = { Text("Are you sure you want to delete $count selected items?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = uiState.selectedIds.toList()
                        viewModel.deleteNodes(ids)
                        viewModel.clearSelection()
                        callbacks.onDismissMultiDelete()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onDismissMultiDelete) { Text("Cancel") }
            },
        )
    }

    if (state.nodeForPreview != null) {
        FilePreviewDialog(
            node = state.nodeForPreview,
            onDismiss = callbacks.onDismissPreview,
        )
    }
}

@Composable
private fun FileListContent(
    uiState: BrowseUiState,
    isSelectionMode: Boolean,
    viewModel: BrowseViewModel,
    dragController: FileDragController,
    quickPeekController: QuickPeekController,
    onOpenFile: ((FileNode) -> Unit)?,
    onPreviewNode: (FileNode) -> Unit,
    onShowDetails: (FileNode) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit,
    onHide: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.breadcrumbs.isNotEmpty()) {
            BreadcrumbBar(
                breadcrumbs = uiState.breadcrumbs,
                onBreadcrumbClick = { viewModel.navigateToBreadcrumb(it) },
                dragController = dragController,
            )
        }

        // Active operations banner
        uiState.activeOperation?.let { op ->
            when (val status = op.status) {
                is OperationStatus.Running -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${op.operation.type}: ${status.progress.currentName.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                            Text(
                                text = "${status.progress.itemsDone}/${status.progress.itemsTotal}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val progressFraction =
                            if (status.progress.itemsTotal > 0) {
                                status.progress.itemsDone.toFloat() / status.progress.itemsTotal.toFloat()
                            } else {
                                0f
                            }
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> { /* other statuses handled or completed */ }
            }
        }

        when {
            uiState.isLoading ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.testTag("loading_indicator"))
                }
            uiState.errorMessage != null ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(uiState.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            uiState.filteredItems.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            if (uiState.searchQuery.isNotEmpty()) {
                                "No files match \"${uiState.searchQuery}\""
                            } else {
                                "This folder is empty"
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else -> {
                val lazyListState = rememberLazyListState()
                // Resolved once per selection change, not once per visible row (which was O(rows x items)).
                val allSelectedNodes =
                    remember(uiState.rawItems, uiState.selectedIds) {
                        if (uiState.selectedIds.isEmpty()) {
                            emptyList()
                        } else {
                            uiState.rawItems.filter { it.id in uiState.selectedIds }
                        }
                    }
                LazyColumn(
                    state = lazyListState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .edgeAutoScroll(dragController, lazyListState),
                ) {
                    items(uiState.filteredItems, key = { it.id.raw }) { node ->
                        val isSelected = node.id in uiState.selectedIds
                        val selectedNodes = if (isSelected) allSelectedNodes else listOf(node)

                        val isMedia =
                            node.mimeType?.startsWith("image/") == true ||
                                node.mimeType?.startsWith("video/") == true
                        val dragOrPeekMod =
                            if (isMedia && !isSelectionMode) {
                                Modifier.mediaGestureArbiter(
                                    node = node,
                                    onTap = {
                                        if (onOpenFile != null) {
                                            onOpenFile(node)
                                        } else {
                                            onPreviewNode(node)
                                        }
                                    },
                                    onQuickPeek = { quickPeekController.startPeek(it) },
                                    onDismissPeek = { quickPeekController.dismiss() },
                                )
                            } else {
                                Modifier.fileDragSource(
                                    controller = dragController,
                                    node = node,
                                    selectedNodes = selectedNodes,
                                    originLocation = uiState.currentFolderId,
                                )
                            }

                        val dropMod =
                            if (node.isDirectory) {
                                Modifier.fileDropTarget(
                                    controller = dragController,
                                    target =
                                        ActiveDropTarget(
                                            id = node.id.raw,
                                            destinationId = node.id,
                                            type = DropTargetType.FOLDER,
                                            displayName = node.name,
                                            isWritable = true,
                                        ),
                                    onHoverSpringOpen = { viewModel.navigateTo(node) },
                                )
                            } else {
                                Modifier
                            }

                        FileListItem(
                            node = node,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                viewModel.toggleSelection(node.id)
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(node.id)
                                } else if (node.isDirectory) {
                                    viewModel.navigateTo(node)
                                } else {
                                    if (onOpenFile != null) {
                                        onOpenFile(node)
                                    } else {
                                        onPreviewNode(node)
                                    }
                                }
                            },
                            onShowDetails = { onShowDetails(node) },
                            onRename = { onRename(node) },
                            onDelete = { onDelete(node) },
                            onExtract = { viewModel.extractArchive(node) },
                            onCompress = { viewModel.compressSingle(node) },
                            onQuickPeek = { quickPeekController.startPeek(node) },
                            onHide = { onHide(node) },
                            modifier =
                                Modifier
                                    .then(dragOrPeekMod)
                                    .then(dropMod),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPreviewPane(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Select a file to preview",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Images, documents, code, archives, and audio previews will appear here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label)
    }
}
