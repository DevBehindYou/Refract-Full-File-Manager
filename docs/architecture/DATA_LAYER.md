# Data Layer

## 1. Responsibilities

* Implement every `domain.repository` interface.
* Own the three storage backends and the selector.
* Own Room and DataStore.
* Map platform types → domain models, and platform exceptions → `FileError`.
* **Never** leak `Cursor`, `File`, `Uri`, `DocumentFile`, or `Throwable` upward.

## 2. Room schema

```kotlin
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val nodeId: String,
    val name: String, val isDirectory: Boolean, val volumeId: String,
    val addedAt: Long, val sortOrder: Int, val lastSeenAt: Long,
)

@Entity(tableName = "recent_opened")
data class RecentEntity(
    @PrimaryKey val nodeId: String,
    val name: String, val mimeType: String?, val openedAt: Long,
)

@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey val id: String,
    val type: String, val status: String,
    val sourcesJson: String, val destination: String?, val optionsJson: String,
    val itemsDone: Int, val itemsTotal: Int,
    val bytesDone: Long, val bytesTotal: Long,
    val errorCode: String?, val summaryJson: String?, val undoTokenJson: String?,
    val createdAt: Long, val updatedAt: Long,
)

@Entity(tableName = "trash")
data class TrashEntity(
    @PrimaryKey val id: String,
    val originalParentId: String, val originalName: String,
    val trashPath: String, val size: Long, val deletedAt: Long,
)

@Entity(tableName = "folder_size_cache")
data class FolderSizeEntity(
    @PrimaryKey val nodeId: String,
    val sizeBytes: Long, val fileCount: Int, val modifiedAt: Long, val computedAt: Long,
)

@Entity(tableName = "storage_scan")
data class StorageScanEntity(
    @PrimaryKey val volumeId: String,
    val breakdownJson: String, val largestFilesJson: String, val largestFoldersJson: String,
    val scannedAt: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(@PrimaryKey val query: String, val usedAt: Long)

// V1
@Entity(tableName = "file_meta") data class FileMetaEntity(...)
@Fts4(contentEntity = FileMetaEntity::class) @Entity(tableName = "file_index") data class FileIndexEntity(...)
```

**Migration policy:** every schema change ships a written `Migration`. `fallbackToDestructive‑
Migration` is **banned** — losing a user's favourites and trash records is data loss.
Schemas are exported to `schemas/` and committed; a test asserts every migration path from
version 1.

## 3. Repository implementation pattern

```kotlin
class FileRepositoryImpl @Inject constructor(
    private val selector: StorageBackendSelector,
    private val sizeCache: FolderSizeDao,
    private val mimeResolver: MimeResolver,
    @IoDispatcher private val io: CoroutineDispatcher,
) : FileRepository {

    override fun observeDirectory(id: FileNodeId, sort: SortSpec, showHidden: Boolean) =
        selector.forNode(id)
            .listChildren(id)
            .map { result -> result.map { nodes -> nodes.filterHidden(showHidden).sortedWith(sort) } }
            .map { it.toDirectoryState() }
            .onStart { emit(DirectoryState.Loading) }
            .catch { emit(DirectoryState.Failed(it.toFileError(), emptyList())) }
            .flowOn(io)
}
```

Every repository method: `flowOn(io)` or `withContext(io)`, `catch` mapping to `FileError`,
and never a bare `try { } catch (e: Exception) { }` that swallows.

## 4. Exception mapping (the boundary)

```kotlin
fun Throwable.toFileError(context: ErrorContext? = null): FileError = when (this) {
    is FileNotFoundException     -> FileError.FileNotFound(context?.name)
    is SecurityException         -> FileError.AccessDenied(context?.name)
    is ErrnoException            -> when (errno) {
        OsConstants.ENOSPC -> FileError.DiskFull()
        OsConstants.EACCES -> FileError.AccessDenied(context?.name)
        OsConstants.EROFS  -> FileError.ReadOnlyStorage
        OsConstants.ENOENT -> FileError.FileNotFound(context?.name)
        OsConstants.ENAMETOOLONG -> FileError.PathTooLong(context?.name)
        else -> FileError.Unknown(errno.toString())
    }
    is ZipException              -> FileError.CorruptedArchive(context?.name)
    is OutOfMemoryError          -> FileError.OutOfMemory
    is CancellationException     -> throw this        // never swallow cancellation
    is IOException               -> FileError.IoFailure(context?.name)
    else                         -> FileError.Unknown(this::class.simpleName ?: "?")
}
```

`CancellationException` is rethrown. Catching it is the single most common coroutine bug and
it makes cancellation silently fail.

## 5. Backend notes that matter in practice

### `FileSystemBackend`
* `File.listFiles()` returns `null` on permission failure — treat `null` as `AccessDenied`,
  never as an empty directory. Showing an empty folder when access was denied is a bug users
  report as data loss.
* Use `File.walkTopDown()` only with `maxDepth` and `onFail`.
* Check `canRead()`/`canWrite()` to populate `AccessFlags`, but do not trust them on all
  filesystems — a probe on write failure is the real answer.

### `SafBackend`
* Listing: one `ContentResolver.query()` on
  `DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)` projecting
  `DOCUMENT_ID, DISPLAY_NAME, MIME_TYPE, SIZE, LAST_MODIFIED, FLAGS`. Never
  `DocumentFile.listFiles()`.
* `FLAG_SUPPORTS_WRITE / DELETE / RENAME` map directly onto `AccessFlags`.
* `DocumentsContract.moveDocument` exists but is provider-optional
  (`FLAG_SUPPORTS_MOVE`); check the flag and fall back to copy+delete.
* Always close the `Cursor` (`use { }`). A leaked cursor on a 40k-entry directory is an OOM.
* Provider crashes surface as `DeadObjectException` inside `RuntimeException` — map to
  `FileError.ProviderUnavailable` and offer a retry, do not crash.

### `MediaStoreBackend`
* Query with a projection, `selection`, and `sortOrder` — never fetch all rows and filter in
  Kotlin.
* API 29+ prefers `ContentResolver.loadThumbnail()`; below that, `MediaStore.Images.Thumbnails`.
* Deletes on API 30+ may throw `RecoverableSecurityException` — catch it, surface the
  `IntentSender` to the UI, and let the user confirm.
* Results can be stale. Verify existence before acting.

## 6. DataStore

```kotlin
class PreferencesRepositoryImpl(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toUserPreferences() }
}
```
The `catch` for `IOException` is required; a corrupt preferences file must not crash the app
on launch.

## 7. Caching rules

* Every cache has an explicit invalidation trigger documented next to it.
* No cache is a source of truth. Existence is re-verified before any operation.
* Caches are bounded: directory LRU 20, thumbnails 250 MB, index 15 MB, trash 2 GB / 7 days.
* Every cache is clearable from Settings, and clearing is instant and non-destructive.
