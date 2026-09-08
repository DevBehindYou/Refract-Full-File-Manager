# Domain Layer

Pure Kotlin. **Zero** `android.*` imports — enforced by the `NoAndroidInDomain` Lint rule.
Fully unit-testable on the JVM with no Robolectric.

## 1. Contents

```text
domain/
├── model/       FileNode, FileNodeId, StorageVolumeInfo, FileOperation, FileError,
│                SearchQuery, SortSpec, UserPreferences, StorageBreakdown, ...
├── repository/  interfaces only
└── usecase/     one class per real business operation
```

## 2. Use cases that exist (and why each earns its keep)

| Use case | Why it is not just a repository call |
|---|---|
| `CopyFilesUseCase` | Pre-flight validation (recursive destination, free space, name legality), builds the operation, enqueues it |
| `MoveFilesUseCase` | Chooses atomic rename vs copy+verify+delete per source/destination pair |
| `DeleteFilesUseCase` | Decides trash vs permanent per item (volume, size, platform), builds the undo token |
| `SearchFilesUseCase` | Merges three sources, dedupes, ranks by relevance, applies filters |
| `AnalyseStorageUseCase` | Walks, categorises, aggregates, computes top-N, caches |
| `FindDuplicatesUseCase` (V1) | Three-stage: group by size → partial hash (first+last 64 KB) → full hash |
| `ExtractArchiveUseCase` | Path validation, collision policy, streaming, progress |
| `CreateArchiveUseCase` | Tree walk, compression, progress |
| `ResolveStorageAccessUseCase` | Turns an intent ("browse this volume") into an ordered list of access steps for the current API level |
| `ObserveDirectoryUseCase` | Combines listing + sort + hidden filter + operation-driven invalidation |
| `ComputeFolderSizeUseCase` | Cancellable recursive size with caching and a depth guard |
| `ResolveMimeTypeUseCase` | Extension map → content sniffing (first 16 bytes) → `application/octet-stream` |

Anything not on this list is a direct repository call from the ViewModel. **Do not create a
use case per repository method.**

## 3. Example: `MoveFilesUseCase`

```kotlin
class MoveFilesUseCase @Inject constructor(
    private val files: FileRepository,
    private val operations: FileOperationRepository,
) {
    suspend operator fun invoke(
        sources: List<FileNodeId>,
        destination: FileNodeId,
        policy: CollisionPolicy,
    ): FileResult<OperationId> {
        if (sources.isEmpty()) return Failure(FileError.NothingSelected)

        // Refuse to move a folder into itself or a descendant — before anything happens.
        sources.forEach { src ->
            if (files.isAncestorOf(src, destination)) {
                return Failure(FileError.InvalidDestination(src))
            }
        }
        val totalBytes = files.estimateSize(sources)
        if (files.freeSpaceAt(destination) < totalBytes * 1.02) {
            return Failure(FileError.DiskFull(required = totalBytes))
        }
        return operations.enqueue(
            FileOperation(
                id = OperationId.random(),
                type = OperationType.MOVE,
                sources = sources,
                destination = destination,
                options = OperationOptions(collisionPolicy = policy),
                createdAt = clock.now(),
            )
        )
    }
}
```

Note what this does *not* do: it does not move anything. It validates and enqueues. The
executor moves. That separation is why the validation is testable without a filesystem.

## 4. `FindDuplicatesUseCase` (V1) — the three-stage algorithm

```text
Stage 1  group by exact byte size          → discard all groups of size 1   (cheap, no I/O)
Stage 2  hash first 64KB + last 64KB       → regroup                        (2 reads/file)
Stage 3  full SHA-256 on surviving groups  → confirm                        (full read)
```

Stage 1 typically eliminates 95%+ of candidates with zero file reads. Stage 3 runs on a tiny
remainder. Cancellable at every file boundary, progress reported per stage.

**Safety rule:** the result never pre-selects every copy in a group. The UI selects all but
one per group, and the user confirms. Auto-selecting everything is how duplicate finders
destroy photo libraries.

## 5. Testing

Every use case has unit tests with fake repositories:
* happy path
* empty input
* each validation failure
* cancellation mid-way
* error propagation from the repository

No use case test may require an Android device or Robolectric. If one does, the use case has
an Android dependency it should not have.
