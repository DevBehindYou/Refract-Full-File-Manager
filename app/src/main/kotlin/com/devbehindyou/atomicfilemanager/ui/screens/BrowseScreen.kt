package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicProgressBar
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicChoiceCard
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicErrorState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSettingsRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicTitleRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicActionStrip
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicStripAction
import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.RemovableNames
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.ui.components.BatchRenameSheet
import com.devbehindyou.atomicfilemanager.ui.components.BreadcrumbBar
import com.devbehindyou.atomicfilemanager.ui.components.CompareSheet
import com.devbehindyou.atomicfilemanager.ui.components.DeleteSheet
import com.devbehindyou.atomicfilemanager.ui.components.FavouriteToggle
import com.devbehindyou.atomicfilemanager.ui.components.FileDetailsDialog
import com.devbehindyou.atomicfilemanager.ui.components.FileListItem
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewDialog
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewPane
import com.devbehindyou.atomicfilemanager.ui.components.HideFileDialog
import com.devbehindyou.atomicfilemanager.ui.components.NewFolderDialog
import com.devbehindyou.atomicfilemanager.ui.components.RenameDialog
import com.devbehindyou.atomicfilemanager.ui.interaction.bubble.BubbleDetailsSheet
import com.devbehindyou.atomicfilemanager.ui.interaction.bubble.BubbleTransferDialog
import com.devbehindyou.atomicfilemanager.ui.interaction.bubble.TransferBubbleRail
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.ActiveDropTarget
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.DragFloatingPreview
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.DropDecisionDialog
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.DropTargetType
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.FileDragController
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.edgeAutoScroll
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.fileDragSource
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.fileDropTarget
import com.devbehindyou.atomicfilemanager.ui.interaction.peek.QuickPeekController
import com.devbehindyou.atomicfilemanager.ui.interaction.peek.QuickPeekOverlay
import com.devbehindyou.atomicfilemanager.ui.interaction.peek.mediaGestureArbiter
import com.devbehindyou.atomicfilemanager.ui.security.AuthGate
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
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
    /** Changes each time the caller explicitly opens [initialFolderId]; see [BrowseViewModel.onOpenRequest]. */
    openRequest: Int = 0,
    onOpenFile: ((FileNode) -> Unit)? = null,
    /** Shows feedback in the app snackbar. */
    onNotify: (String) -> Unit = {},
    /** Opens the Operations screen from the progress banner. */
    onOpenOperations: () -> Unit = {},
    /** Keeps each Browse tab's view model apart (ALL_IN_ONE_PLAN.md 2.4); empty for the first tab. */
    tabKey: String = "",
    /** Shown above the top bar; the tab strip when several tabs are open. */
    tabStrip: @Composable () -> Unit = {},
    /** Reports the folder shown, for the tab label. */
    onLocationChange: (String) -> Unit = {},
    /** Opens a selected folder in a new tab; null hides the action. */
    onOpenInNewTab: ((FileNode) -> Unit)? = null,
    /** Compares two folders (ALL_IN_ONE_PLAN.md 4.1); null hides the actions. */
    onCompareFolders: ((FileNodeId, FileNodeId) -> Unit)? = null,
    viewModel: BrowseViewModel =
        run {
            val app = LocalContext.current.applicationContext as AtomicApp
            val owner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner())
            remember(owner, tabKey, initialFolderId.raw) {
                ViewModelProvider(
                    owner,
                    BrowseViewModel.provideFactory(
                        initialFolderId = initialFolderId,
                        getDirectoryListingUseCase = app.container.getDirectoryListingUseCase,
                        getNodeUseCase = app.container.getNodeUseCase,
                        createDirectoryUseCase = app.container.createDirectoryUseCase,
                        renameFileUseCase = app.container.renameFileUseCase,
                        deleteFileUseCase = app.container.deleteFileUseCase,
                        operationQueue = app.container.operationQueue,
                        transferBubbleRepository = app.container.transferBubbleRepository,
                        folderSorts = app.container.folderSortMemory,
                    ),
                )["browse:$tabKey${initialFolderId.raw}", BrowseViewModel::class.java]
            }
        },
) {
    val app = LocalContext.current.applicationContext as AtomicApp
    val uiState by viewModel.uiState.collectAsState()
    val settings by app.container.settingsRepository.settings.collectAsState()

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var nodeToRename by remember { mutableStateOf<FileNode?>(null) }
    var nodeForDetails by remember { mutableStateOf<FileNode?>(null) }
    var nodeForPreview by remember { mutableStateOf<FileNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<FileNode?>(null) }
    var confirmMultiDelete by remember { mutableStateOf(false) }
    var batchRename by remember { mutableStateOf<List<FileNode>?>(null) }
    var comparePair by remember { mutableStateOf<Pair<FileNode, FileNode>?>(null) }
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
                operationQueue = app.container.operationQueue,
                transferBubbleRepository = app.container.transferBubbleRepository,
                folderSorts = app.container.folderSortMemory,
            )
        }
    val secondaryUiState by secondaryViewModel.uiState.collectAsState()

    val bubbles by viewModel.bubbles.collectAsState()
    var bubbleForDetails by remember { mutableStateOf<TransferBubble?>(null) }
    var bubbleForTransfer by remember { mutableStateOf<TransferBubble?>(null) }
    var showAddToBubbleMenu by remember { mutableStateOf(false) }

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
                    "Hidden ${node.name}."
                } else {
                    "Couldn't hide ${node.name}: ${error.message ?: "unknown error"}"
                }
            onNotify(message)
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

    LaunchedEffect(viewModel, openRequest) {
        viewModel.onOpenRequest(openRequest)
    }

    LaunchedEffect(uiState.currentFolderName) {
        if (uiState.currentFolderName.isNotBlank()) onLocationChange(uiState.currentFolderName)
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
        Column(Modifier.fillMaxSize().background(Atomic.colors.background)) {
            tabStrip()
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
                        onInvertSelection = { viewModel.invertSelection() },
                        onSelectSameType = { viewModel.selectSameType() },
                    ),
            )
            OperationBanner(uiState.activeOperation, onOpen = onOpenOperations)
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                                onNewFolder = { showNewFolderDialog = true },
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
                        onNewFolder = { showNewFolderDialog = true },
                    )
                }
            }
            BrowseBottomBar(
                uiState = uiState,
                isSelectionMode = isSelectionMode,
                onCopySelected = { viewModel.copySelected() },
                onCutSelected = { viewModel.cutSelected() },
                onCompressSelected = { viewModel.compressSelected() },
                onDeleteSelected = { confirmMultiDelete = true },
                onRenameSelected = { nodes -> batchRename = nodes },
                onOpenInNewTab =
                    onOpenInNewTab?.let { open ->
                        { folder: FileNode ->
                            open(folder)
                            viewModel.clearSelection()
                        }
                    },
                onShowDetails = { node ->
                    nodeForDetails = node
                    viewModel.clearSelection()
                },
                onClearClipboard = { viewModel.clearClipboard() },
                onPaste = { viewModel.paste(CollisionPolicy.ASK) },
                onCompare = { a, b -> comparePair = a to b },
                onCompareFolders =
                    onCompareFolders?.let { compare ->
                        { left: FileNodeId, right: FileNodeId ->
                            viewModel.clearSelection()
                            compare(left, right)
                        }
                    },
            )
            comparePair?.let { (a, b) -> CompareSheet(first = a, second = b, onDismiss = { comparePair = null }) }
        }

        batchRename?.let { nodes ->
            BatchRenameSheet(
                nodes = nodes,
                folderNames = uiState.rawItems.map { it.name },
                onChanged = {
                    viewModel.clearSelection()
                    viewModel.refresh()
                },
                onDismiss = { batchRename = null },
            )
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
    val onInvertSelection: () -> Unit = {},
    val onSelectSameType: () -> Unit = {},
)

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
        isSelectionMode -> SelectionHeader(uiState, bubbles, onShowAddToBubbleMenu, actions)
        uiState.isSearching -> SearchHeader(uiState, actions)
        else -> FolderHeader(uiState, isDualPane, dualPaneMode, onShowSortMenu, actions)
    }

    if (showSortMenu) {
        AtomicSheet(label = "Sort by", onDismiss = { onShowSortMenu(false) }) {
            SortOption.entries.forEach { option ->
                AtomicChoiceCard(
                    title = option.label,
                    selected = uiState.sortOption == option,
                    onSelect = {
                        actions.onSetSortOption(option)
                        onShowSortMenu(false)
                    },
                )
            }
            AtomicSettingsRow(
                title = "Hidden files",
                value = "Hidden, obscured and private files",
                onClick = {
                    onShowSortMenu(false)
                    actions.onShowHiddenScreen()
                },
            )
        }
    }

    if (showAddToBubbleMenu) {
        AtomicSheet(label = "Add to bubble", onDismiss = { onShowAddToBubbleMenu(false) }) {
            Column {
                bubbles.forEach { bubble ->
                    AtomicSettingsRow(
                        title = bubble.displayName,
                        onClick = {
                            actions.onAddToBubble(bubble.id)
                            onShowAddToBubbleMenu(false)
                        },
                    )
                }
            }
        }
    }
}

/** "3 selected · All · Cancel" header (canvas "Files · selection"). */
@Composable
private fun SelectionHeader(
    uiState: BrowseUiState,
    bubbles: List<TransferBubble>,
    onShowAddToBubbleMenu: (Boolean) -> Unit,
    actions: BrowseTopBarActions,
) {
    var showSelectMenu by remember { mutableStateOf(false) }
    if (showSelectMenu) {
        AtomicSheet(label = "Select", onDismiss = { showSelectMenu = false }) {
            AtomicText("Tip: long-press a second file to select everything between.", AtomicTextRole.BodySecondary)
            listOf(
                "Select all" to actions.onSelectAll,
                "Invert selection" to actions.onInvertSelection,
                "Select same type" to actions.onSelectSameType,
            ).forEach { (label, action) ->
                AtomicSettingsRow(
                    title = label,
                    onClick = {
                        showSelectMenu = false
                        action()
                    },
                )
            }
        }
    }
    val selectedBytes =
        remember(uiState.rawItems, uiState.selectedIds) {
            uiState.rawItems.filter { it.id in uiState.selectedIds && !it.isDirectory }.sumOf { it.size }
        }
    Column(Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AtomicText(
                "${uiState.selectedIds.size} selected",
                AtomicTextRole.DisplayPushed,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (bubbles.isNotEmpty()) {
                AtomicIconButton(
                    AtomicIcons.Bubble,
                    "Add to Transfer Bubble",
                    onClick = { onShowAddToBubbleMenu(true) },
                )
            }
            AtomicButton(
                "All",
                onClick = actions.onSelectAll,
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.testTag("select_all_button"),
            )
            AtomicIconButton(
                AtomicIcons.More,
                "More ways to select",
                onClick = { showSelectMenu = true },
                modifier = Modifier.testTag("select_more_button"),
            )
            AtomicButton("Cancel", onClick = actions.onClearSelection, variant = AtomicButtonVariant.Text)
        }
        AtomicText(
            "${uiState.currentFolderName.ifEmpty { "Files" }} · ${FileUtils.formatBytes(selectedBytes)} selected",
            AtomicTextRole.MonoMeta,
            maxLines = 1,
        )
        AtomicDivider(strong = true, modifier = Modifier.padding(top = AtomicSpacing.s8))
    }
}

@Composable
private fun SearchHeader(
    uiState: BrowseUiState,
    actions: BrowseTopBarActions,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = AtomicSpacing.s10, end = AtomicSpacing.s16, top = AtomicSpacing.s8),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            AtomicIconButton(
                AtomicIcons.Back,
                "Close search",
                onClick = { actions.onToggleSearch(false) },
                variant = AtomicIconButtonVariant.Back,
            )
            AtomicTextField(
                value = uiState.searchQuery,
                onValueChange = actions.onSearchQueryChanged,
                label = "Search this folder",
                placeholder = "Name contains…",
                modifier = Modifier.weight(1f).testTag("search_field"),
            )
        }
        AtomicDivider(strong = true, modifier = Modifier.padding(top = AtomicSpacing.s8))
    }
}

/**
 * Pushed header with the volume as eyebrow, then the folder name as stored (`NameLarge`, never
 * uppercased) with an item counter, then the mono breadcrumb (ATOMIC_UI_PLAN.md §7.2).
 */
@Composable
private fun FolderHeader(
    uiState: BrowseUiState,
    isDualPane: Boolean,
    dualPaneMode: DualPaneMode,
    onShowSortMenu: (Boolean) -> Unit,
    actions: BrowseTopBarActions,
) {
    Column(Modifier.fillMaxWidth()) {
        AtomicPushedHeader(
            onBack = actions.onNavigateBack,
            eyebrow = uiState.breadcrumbs.firstOrNull()?.name ?: "Files",
            actions = {
                AtomicIconButton(
                    AtomicIcons.Search,
                    "Search this folder",
                    onClick = { actions.onToggleSearch(true) },
                    modifier = Modifier.testTag("search_icon_button"),
                )
                AtomicIconButton(
                    AtomicIcons.Sort,
                    "Sort by",
                    onClick = { onShowSortMenu(true) },
                    modifier = Modifier.testTag("sort_button"),
                )
                if (isDualPane) {
                    AtomicIconButton(
                        if (dualPaneMode == DualPaneMode.DUAL_BROWSE) AtomicIcons.Preview else AtomicIcons.DualPane,
                        if (dualPaneMode == DualPaneMode.DUAL_BROWSE) {
                            "Switch to preview pane"
                        } else {
                            "Switch to dual browse"
                        },
                        onClick = actions.onToggleDualPaneMode,
                    )
                }
                AtomicIconButton(
                    AtomicIcons.NewFolder,
                    "New folder",
                    onClick = actions.onShowNewFolderDialog,
                    modifier = Modifier.testTag("new_folder_button"),
                )
            },
        )
        AtomicTitleRow(
            title = uiState.currentFolderName.ifEmpty { "Files" },
            counter = "${uiState.filteredItems.size} items · ${uiState.sortOption.label}",
            titleRole = AtomicTextRole.NameLarge,
            modifier = Modifier.padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8),
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
    onRenameSelected: (List<FileNode>) -> Unit = {},
    onOpenInNewTab: ((FileNode) -> Unit)? = null,
    onPaste: () -> Unit,
    onCompare: (FileNode, FileNode) -> Unit = { _, _ -> },
    onCompareFolders: ((FileNodeId, FileNodeId) -> Unit)? = null,
) {
    val clip = uiState.clipboard
    val selectedFolders =
        uiState.rawItems.filter { it.id in uiState.selectedIds && it.isDirectory }.takeIf {
            it.size == 2 && uiState.selectedIds.size == 2
        }
    // A copied folder can be compared with the folder shown, on any storage.
    val clipFolder =
        clip?.items?.singleOrNull()?.takeIf { it.isDirectory && it.id != uiState.currentFolderId }
    val selectedFiles =
        if (uiState.selectedIds.size == 2) {
            uiState.rawItems.filter {
                it.id in uiState.selectedIds && !it.isDirectory
            }
        } else {
            emptyList()
        }
    if (isSelectionMode) {
        AtomicActionStrip(
            actions =
                listOfNotNull(
                    AtomicStripAction("Copy", AtomicIcons.Copy, onCopySelected),
                    AtomicStripAction("Move", AtomicIcons.Move, onCutSelected),
                    AtomicStripAction("Zip", AtomicIcons.Archive, onCompressSelected),
                    AtomicStripAction("Delete", AtomicIcons.Trash, onDeleteSelected, destructive = true),
                    if (uiState.selectedIds.size > 1) {
                        // In listing order, so numbering follows what the user sees.
                        AtomicStripAction("Rename", AtomicIcons.Document, {
                            onRenameSelected(uiState.filteredItems.filter { it.id in uiState.selectedIds })
                        })
                    } else {
                        null
                    },
                    uiState.filteredItems
                        .singleOrNull { it.id in uiState.selectedIds }
                        ?.takeIf { it.isDirectory && uiState.selectedIds.size == 1 && onOpenInNewTab != null }
                        ?.let {
                                folder ->
                            AtomicStripAction("New tab", AtomicIcons.DualPane, { onOpenInNewTab?.invoke(folder) })
                        },
                    if (uiState.selectedIds.size == 1) {
                        AtomicStripAction(
                            label = "Info",
                            icon = AtomicIcons.Info,
                            onClick = {
                                val single = uiState.rawItems.firstOrNull { it.id in uiState.selectedIds }
                                if (single != null) onShowDetails(single)
                            },
                        )
                    } else {
                        null
                    },
                    if (selectedFiles.size == 2) {
                        AtomicStripAction(
                            "Compare",
                            AtomicIcons.CheckCircle,
                            { onCompare(selectedFiles[0], selectedFiles[1]) },
                        )
                    } else {
                        null
                    },
                    if (selectedFolders != null && onCompareFolders != null) {
                        AtomicStripAction(
                            "Compare",
                            AtomicIcons.DualPane,
                            { onCompareFolders(selectedFolders[0].id, selectedFolders[1].id) },
                        )
                    } else {
                        null
                    },
                ),
        )
    } else if (clip != null) {
        Column(Modifier.fillMaxWidth().background(Atomic.colors.background)) {
            AtomicDivider(strong = true)
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(
                        horizontal = AtomicSpacing.s16,
                        vertical = AtomicSpacing.s10,
                    ),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtomicText(
                    "${clip.items.size} ready to " + if (clip.operation == ClipboardOp.COPY) "copy" else "move",
                    AtomicTextRole.MonoLabel,
                    modifier = Modifier.weight(1f),
                )
                AtomicButton("Cancel", onClick = onClearClipboard, variant = AtomicButtonVariant.Text)
                if (clipFolder != null && onCompareFolders != null) {
                    AtomicButton(
                        "Compare",
                        onClick = { onCompareFolders(clipFolder.id, uiState.currentFolderId) },
                        variant = AtomicButtonVariant.Text,
                        modifier = Modifier.testTag("compare_with_clip_button"),
                    )
                }
                AtomicButton(
                    "Paste here",
                    onClick = onPaste,
                    variant = AtomicButtonVariant.Solid,
                    leadingIcon = AtomicIcons.Paste,
                    modifier = Modifier.testTag("paste_button"),
                )
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
    val onNewFolder: () -> Unit,
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
                onNewFolder = actions.onNewFolder,
            )
        }
        Box(Modifier.fillMaxHeight().width(AtomicBorder.rule).background(Atomic.colors.borderStrong))
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
                        onNewFolder = actions.onNewFolder,
                    )
                }
            } else {
                if (nodeForPreview != null) {
                    FilePreviewPane(
                        node = nodeForPreview,
                        onClose = actions.onClosePreview,
                        gallery = uiState.filteredItems,
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
            parentName = uiState.currentFolderName.ifEmpty { null },
            removable = RemovableNames.appliesTo(uiState.currentFolderId),
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
            removable = RemovableNames.appliesTo(node.id),
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
        DeleteSheet(
            nodes = listOf(node),
            onConfirm = { toTrash ->
                viewModel.deleteNodes(listOf(node.id), toTrash)
                callbacks.onDismissDelete()
            },
            onDismiss = callbacks.onDismissDelete,
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
        val selected =
            remember(uiState.selectedIds, uiState.rawItems) {
                uiState.rawItems.filter { it.id in uiState.selectedIds }
            }
        DeleteSheet(
            nodes = selected,
            onConfirm = { toTrash ->
                viewModel.deleteNodes(selected.map { it.id }, toTrash)
                viewModel.clearSelection()
                callbacks.onDismissMultiDelete()
            },
            onDismiss = callbacks.onDismissMultiDelete,
        )
    }

    if (state.nodeForPreview != null) {
        FilePreviewDialog(
            node = state.nodeForPreview,
            onDismiss = callbacks.onDismissPreview,
            gallery = uiState.filteredItems,
        )
    }
}

/**
 * The running operation as one tappable line that opens Operations (ALL_IN_ONE_PLAN.md 0.1):
 * progress while it runs, "waiting for your decision" while a name clash is open.
 */
@Composable
private fun OperationBanner(
    snapshot: OperationSnapshot?,
    onOpen: () -> Unit,
) {
    if (snapshot == null) return
    val status = snapshot.status
    val label = OperationText.verb(snapshot.operation.type)
    val progress = (status as? OperationStatus.Running)?.progress
    val waiting = status is OperationStatus.AwaitingInput
    val fraction =
        if (progress != null && progress.itemsTotal > 0) progress.itemsDone.toFloat() / progress.itemsTotal else 0f
    val line =
        when {
            waiting -> "$label · waiting for your decision · tap to open"
            progress != null ->
                "$label · ${progress.itemsDone} / ${progress.itemsTotal} · ${progress.currentName.orEmpty()}"
            else -> "$label · preparing"
        }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open operations", role = Role.Button, onClick = onOpen)
            .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8)
            .testTag("operation_banner"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
    ) {
        AtomicProgressBar(
            fraction = fraction,
            contentDescription =
                if (progress != null) "$label, ${progress.itemsDone} of ${progress.itemsTotal}" else line,
        )
        AtomicText(
            line,
            AtomicTextRole.MonoMeta,
            color = if (waiting) Atomic.colors.accentText else Atomic.colors.contentSecondary,
            maxLines = 1,
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
    onNewFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favourites = (LocalContext.current.applicationContext as AtomicApp).container.favouritesRepository
    val favouriteFlow = remember(favourites) { favourites.observe() }
    val favouriteList by favouriteFlow.collectAsState(initial = emptyList())
    val favouriteIds = remember(favouriteList) { favouriteList.mapTo(HashSet()) { it.id } }
    val favouriteScope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.breadcrumbs.isNotEmpty()) {
            BreadcrumbBar(
                breadcrumbs = uiState.breadcrumbs,
                onBreadcrumbClick = { viewModel.navigateToBreadcrumb(it) },
                dragController = dragController,
            )
        }

        when {
            uiState.isLoading ->
                AtomicLoading(
                    "Loading folder…",
                    modifier = Modifier.padding(AtomicSpacing.s16).testTag("loading_indicator"),
                )
            uiState.errorMessage != null ->
                AtomicErrorState(
                    title = "Can't open this folder",
                    message = uiState.errorMessage.orEmpty(),
                    actionLabel = "Go up",
                    onAction = { viewModel.navigateUp() },
                    modifier = Modifier.padding(AtomicSpacing.s16),
                )
            uiState.filteredItems.isEmpty() ->
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AtomicSpacing.s16)) {
                    if (uiState.searchQuery.isNotEmpty()) {
                        AtomicEmptyState(
                            message = "Nothing here matches \"${uiState.searchQuery}\".",
                            detail = "Search looks at names in this folder only.",
                        )
                    } else {
                        AtomicEmptyState(
                            message = "This folder is empty.",
                            actionLabel = "New folder",
                            onAction = onNewFolder,
                        )
                    }
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
                                viewModel.longPressSelect(node.id)
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
                            favourite =
                                FavouriteToggle(node.id in favouriteIds) {
                                    favouriteScope.launch {
                                        if (node.id in favouriteIds) {
                                            favourites.remove(
                                                node.id,
                                            )
                                        } else {
                                            favourites.add(node)
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPreviewPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(AtomicSpacing.s24)) {
        AtomicEmptyState(
            message = "Select a file to preview.",
            detail = "Images, documents, code, archives and audio appear here.",
        )
    }
}
