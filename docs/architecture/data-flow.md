# Data Flow

## 1. Read path — opening a folder

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant S as BrowseScreen
    participant VM as BrowseViewModel
    participant UC as ObserveDirectoryUseCase
    participant R as FileRepository
    participant SEL as BackendSelector
    participant B as StorageBackend
    participant P as Android API

    U->>S: tap folder
    S->>VM: onEvent(OpenFolder(id))
    VM->>VM: emit Effect.Navigate(FolderRoute(id))
    Note over VM: new BrowseViewModel for the new route
    VM->>UC: invoke(id, sort, showHidden)
    UC->>R: observeDirectory(...)
    R->>SEL: forNode(id)
    SEL-->>R: FileSystemBackend | SafBackend
    R->>B: listChildren(id)
    B->>P: File.listFiles() / ContentResolver.query()
    P-->>B: entries
    B-->>R: Flow<chunk of 200 FileNode>
    R->>R: filter hidden, sort (Dispatchers.Default)
    R-->>UC: DirectoryState.Partial
    UC-->>VM: Partial
    VM-->>S: state.copy(nodes, Partial)
    S-->>U: first 200 rows painted (~80ms)
    B-->>R: remaining chunks
    R-->>VM: DirectoryState.Complete
    VM-->>S: full list
```

## 2. Write path — copying files

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant S as Screen
    participant VM as ViewModel
    participant UC as CopyFilesUseCase
    participant OR as FileOperationRepository
    participant DB as Room
    participant SV as FileOperationService
    participant EX as Executor
    participant B as Backends

    U->>S: select 3 files, tap Copy, pick destination
    S->>VM: onEvent(Copy(ids, dest))
    VM->>UC: invoke(ids, dest, ASK)
    UC->>UC: validate: not recursive, free space, names
    UC->>OR: enqueue(FileOperation)
    OR->>DB: insert(status = Queued)
    OR->>SV: startForegroundService(intent)
    SV->>EX: run(operation)
    loop per file
        EX->>B: openInput / openOutput
        EX->>EX: stream 256KB buffers, ensureActive()
        EX->>DB: progress (throttled 4Hz)
    end
    DB-->>OR: Flow<OperationEntity>
    OR-->>VM: OperationStatus.Running(progress)
    VM-->>S: operation pill updates
    EX->>DB: status = Completed(summary)
    SV->>SV: stopSelf() when queue empty
    VM-->>S: Effect.ShowMessage("3 files copied", undo)
```

## 3. State flow within a screen

```mermaid
flowchart LR
    subgraph SRC["Sources (cold Flows, flowOn(IO))"]
        A[directory listing]
        B[preferences]
        C[operations affecting this path]
        D[storage access state]
    end
    A & B & C & D --> COMB[combine]
    COMB --> MAP["map → BrowseUiState<br/>(Dispatchers.Default for sort)"]
    MAP --> SIN["stateIn(viewModelScope,<br/>WhileSubscribed(5000), initial)"]
    SIN --> UI["collectAsStateWithLifecycle()"]
    UI --> COMPOSE[Compose recomposition]

    EV[User event] --> VMH[ViewModel.onEvent]
    VMH --> UCC[Use case]
    VMH --> EFF["Channel effects<br/>navigate · snackbar · intent"]
    EFF --> LE[LaunchedEffect in Route]
```

## 4. Rules encoded here

* Reads are **streams**, not one-shot calls, so the UI paints before data is complete.
* Writes are **enqueued**, never executed inline, so they survive the screen.
* Progress travels **through Room**, not through a direct callback, so it survives process
  death and any observer can see it.
* Effects use a `Channel`, so no navigation or snackbar is duplicated on recomposition or
  lost on rotation.
