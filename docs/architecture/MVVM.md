# MVVM Specification

## 1. Contract

```kotlin
@Immutable data class XUiState(...)          // everything the screen renders
sealed interface XEvent                       // everything the user can do
sealed interface XEffect                      // one-shot: navigate, snackbar, launch intent
```

* State is exposed as `StateFlow<XUiState>` with `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
* Effects are a `Channel(BUFFERED).receiveAsFlow()` — never a `StateFlow`, never a
  `SharedFlow(replay=0)` that can drop on configuration change.
* The screen composable receives `state` and a single `onEvent: (XEvent) -> Unit`.
* Nothing else crosses the boundary. No ViewModel reference in a child composable.

```kotlin
@Composable
fun BrowseRoute(viewModel: BrowseViewModel = hiltViewModel(), navigate: (Route) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.effects.flowWithLifecycle(lifecycle).collect { effect ->
            when (effect) {
                is BrowseEffect.Navigate -> navigate(effect.route)
                is BrowseEffect.ShowMessage -> snackbar.showSnackbar(...)
            }
        }
    }
    BrowseScreen(state = state, onEvent = viewModel::onEvent, snackbar = snackbar)
}
```

---

## 2. Model layer

```kotlin
// Files
FileNode, FileNodeId, StorageType, AccessFlags, NodeExtras
FileCategory { IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, DOWNLOAD, OTHER }
SortSpec(field: NAME|SIZE|DATE|TYPE, ascending: Boolean, foldersFirst: Boolean)
ViewMode { LIST, GRID }

// Storage
StorageVolumeInfo, StorageAccessState, AccessLevel, TreeGrant
StorageBreakdown(categories: Map<FileCategory, Long>, free: Long, total: Long, scannedAt: Long)

// Operations
FileOperation, OperationType, OperationOptions, OperationStatus, OperationProgress,
OperationSummary, Conflict, CollisionPolicy, UndoToken

// Search
SearchQuery, SearchScope, SearchState

// Preferences
UserPreferences(theme, dynamicColor, glassTier, defaultView, defaultSort, showHidden,
                confirmDelete, useTrash, indexEnabled, crashReporting)

// Errors
FileError  // see ../ERROR_MODEL.md
```

All models are `@Immutable` `data class`es or `value class`es. No mutable collections,
no `var`, no platform types.

---

## 3. ViewModels

### `HomeViewModel`

```kotlin
data class HomeUiState(
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val primaryStorage: StorageMeterUi? = null,
    val categories: List<CategoryTileUi> = FileCategory.defaults(),
    val favorites: List<FileNodeUi> = emptyList(),
    val recents: List<FileNodeUi> = emptyList(),
    val accessLevel: AccessLevel = AccessLevel.NONE,
    val isLoading: Boolean = true,
    val error: FileError? = null,
)

sealed interface HomeEvent {
    data class CategoryClicked(val category: FileCategory) : HomeEvent
    data class FileClicked(val id: FileNodeId) : HomeEvent
    data class VolumeClicked(val volumeId: String) : HomeEvent
    data object GrantAccessClicked : HomeEvent
    data object RefreshRequested : HomeEvent
    data object SeeAllRecentsClicked : HomeEvent
}
```
Combines four flows (`volumes`, `breakdown`, `favorites`, `recents`) with `combine`, each
independently loading so one slow source never blocks the screen.

### `BrowseViewModel`

```kotlin
data class BrowseUiState(
    val path: List<BreadcrumbSegment> = emptyList(),
    val nodes: List<FileNodeUi> = emptyList(),
    val listState: ListLoadState = ListLoadState.Loading,   // Loading | Partial | Complete | Failed
    val sort: SortSpec, val viewMode: ViewMode, val showHidden: Boolean,
    val selection: Set<FileNodeId> = emptySet(),
    val selectionMode: Boolean = false,
    val clipboardCount: Int = 0,
    val requiresGrant: Boolean = false,
)
```
* `currentPath` lives in `SavedStateHandle` — survives process death.
* Selection lives in `SavedStateHandle` (capped at 500 ids).
* `observeDirectory` is a `flatMapLatest` on `(path, sort, showHidden)`, so changing sort
  cancels the previous listing cleanly.
* Sorting happens on `Dispatchers.Default`, not in the composable.

### `SearchViewModel`

```kotlin
data class SearchUiState(
    val query: String = "", val filters: SearchFilters = SearchFilters(),
    val scope: SearchScope = SearchScope.AllStorage,
    val results: List<FileNodeUi> = emptyList(),
    val isSearching: Boolean = false, val resultCount: Int = 0,
    val recentQueries: List<String> = emptyList(),
    val coverageWarning: CoverageWarning? = null,   // "only granted folders are searched"
)
```
* Query text → `MutableStateFlow` → `debounce(250)` → `distinctUntilChanged()` →
  `flatMapLatest { search(it) }`. `flatMapLatest` gives free cancellation of the old walk.

### `StorageViewModel`
Holds `StorageBreakdown`, largest folders/files, scan progress, and the cached `scannedAt`.
Scan runs in a use case with a cancellable `Job` held by the ViewModel; navigating away
keeps it running (it is cheap) but rotating never restarts it.

### `FilePreviewViewModel`
One state per preview type via a sealed `PreviewContent`. Owns the Media3 `Player` lifecycle
(created in `init`, released in `onCleared`) and the text-file streaming reader with its
2 MB cap.

### `FileOperationViewModel`
Screen-agnostic; injected into Browse, Search, Storage and Operations. Exposes
`Flow<List<OperationUi>>` from Room and forwards user commands (cancel, retry, undo,
resolve conflict) to `FileOperationRepository`. It never executes anything itself.

### `SettingsViewModel`
Straight mapping over `PreferencesRepository` + `StorageAccessManager.state`. Every toggle
writes through DataStore; there is no local mutable copy.

### `PermissionsViewModel`
Owns the onboarding/troubleshooting flow. Exposes the ordered `List<AccessStep>` for the
current API level and the results of each. Re-checks `isExternalStorageManager()` on every
`ON_RESUME`.

---

## 4. Flow usage rules

| Use | Type |
|---|---|
| Screen state | `StateFlow` via `stateIn(WhileSubscribed(5_000))` |
| One-shot effects | `Channel(BUFFERED).receiveAsFlow()` |
| Data-layer streams | cold `Flow`, `flowOn(io)` |
| Progressive results (directory listing, search) | cold `Flow` emitting `Partial`/`Complete` |
| Cross-screen shared state (clipboard, operations) | repository-owned `StateFlow` in `SingletonComponent` |

`WhileSubscribed(5_000)` specifically: it keeps the flow alive across a rotation
(which takes < 5 s) without holding it forever in the background.

## 5. Error handling in ViewModels

```kotlin
when (val result = useCase(...)) {
    is Success -> _state.update { it.copy(...) }
    is Failure -> when (result.error.severity) {
        BLOCKING  -> _state.update { it.copy(error = result.error) }      // inline error state
        TRANSIENT -> _effects.send(ShowMessage(result.error))             // snackbar
        SILENT    -> logger.w(result.error)                               // log only
    }
}
```

A ViewModel never sees a `Throwable`. If one escapes into `viewModelScope`, a
`CoroutineExceptionHandler` logs it with the current operation id and shows a generic error —
and that is treated as a bug to be fixed, not a normal path.

## 6. What ViewModels must never do

* Import `java.io.File`, `Uri`, `ContentResolver`, `DocumentFile`, `Context` (except via
  `@ApplicationContext` in a rare, reviewed case).
* Perform I/O directly.
* Format strings for display that depend on locale-sensitive resources — pass structured data
  and let the composable resolve `stringResource`.
* Hold a reference to a composable, a `View`, or a `NavController`.
* Start a coroutine outside `viewModelScope`.
* Contain `if (Build.VERSION.SDK_INT ...)`.
