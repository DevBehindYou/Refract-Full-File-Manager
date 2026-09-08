# Search Architecture

## 1. Three sources, merged

| Source | Speed | Coverage | Used for |
|---|---|---|---|
| **MediaStore** | Fast (indexed) | Media + `MediaStore.Files` on the primary volume | First results, categories, size/date filters |
| **Room index** (V1) | Fastest | Whatever has been scanned | Instant results, filename FTS |
| **Live walk** | Slow | Everything reachable | Correctness backstop, SAF trees, SD cards |

They run **concurrently** and merge into one deduplicated stream keyed by `FileNodeId`.
The user sees results from the fastest source within ~120 ms and the walk fills in behind it.

```kotlin
fun search(query: SearchQuery): Flow<SearchState> = channelFlow {
    val seen = ConcurrentHashMap.newKeySet<FileNodeId>()
    val results = mutableListOf<FileNode>()

    launch { indexSource(query).collect { emitNew(it) } }      // V1
    launch { mediaStoreSource(query).collect { emitNew(it) } }
    launch { walkSource(query).collect { emitNew(it) } }       // chunked, cancellable
}
```

`emitNew` filters by `seen`, applies the sort, and emits at most **10 times per second** —
never per result, or a search matching 40,000 files locks the UI.

## 2. Query model

```kotlin
data class SearchQuery(
    val text: String,
    val scope: SearchScope,           // CurrentFolder(id) | Volume(id) | AllStorage
    val categories: Set<FileCategory> = emptySet(),
    val extensions: Set<String> = emptySet(),
    val sizeRange: LongRange? = null,
    val dateRange: LongRange? = null,
    val includeHidden: Boolean = false,
    val sort: SortSpec = SortSpec.Relevance,
)
```

Matching: case-insensitive substring by default. A leading `*` or a `?` switches to glob.
A quoted string is exact. This is discoverable via a hint chip, not documented-only.

**Relevance ordering:** exact name match → prefix match → word-boundary match → substring;
ties broken by recency. Users overwhelmingly want the file they just touched.

## 3. Behaviour per API level

| API | Strategy |
|---|---|
| 27–28 | `File` walk from volume roots, plus MediaStore for media. Fast, because there is no SAF overhead |
| 29 | MediaStore for media; SAF walk over granted trees only. **Explicitly tell the user** that search covers "folders you've given access to" and offer to add more |
| 30+ with All Files | `File` walk + MediaStore. Best coverage |
| 30+ without All Files | MediaStore + granted trees, same messaging as API 29 |

The scope chip therefore shows what is *actually* searchable, not an aspirational "All
storage". Honesty here prevents the most common one-star review for file managers.

## 4. The walk

```kotlin
suspend fun walk(root: FileNodeId, onBatch: suspend (List<FileNode>) -> Unit) {
    val queue = ArrayDeque<FileNodeId>().apply { add(root) }
    val batch = ArrayList<FileNode>(200)
    var depth = 0
    while (queue.isNotEmpty()) {
        coroutineContext.ensureActive()             // cancellation point per directory
        val dir = queue.removeFirst()
        backend(dir).listChildren(dir).collectLatest { result -> /* ... */ }
        if (batch.size >= 200) { onBatch(batch.toList()); batch.clear(); yield() }
    }
}
```

Guards:
* **Breadth-first**, so shallow (likely) results arrive first.
* Symlink loop protection via a visited-inode set on `File` backends.
* Depth cap of 32; beyond that, stop and report (a real tree never needs more, a loop does).
* Skips `/Android/data`, `/Android/obb`, `.thumbnails`, and app cache dirs by default.
* `yield()` every batch so cancellation and other coroutines are responsive.
* Cancelled and restarted on every query change after a **250 ms debounce**.

## 5. Index (V1)

```sql
CREATE VIRTUAL TABLE file_index USING fts4(name, tokenize=unicode61);
CREATE TABLE file_meta(
  node_id TEXT PRIMARY KEY, parent_id TEXT, name TEXT, ext TEXT,
  size INTEGER, modified INTEGER, is_dir INTEGER, category TEXT, volume TEXT,
  indexed_at INTEGER
);
CREATE INDEX idx_meta_parent   ON file_meta(parent_id);
CREATE INDEX idx_meta_size     ON file_meta(size);
CREATE INDEX idx_meta_modified ON file_meta(modified);
CREATE INDEX idx_meta_category ON file_meta(category);
```

* Built by a `WorkManager` job on charge + idle, and incrementally after any operation.
* Invalidated per directory by mtime comparison; `MediaStore.getVersion()` is used as an
  internal token only (it is per-app-unique from API 36, so never share or persist it across
  installs).
* The index is a **cache, never a source of truth.** Every result is verified to still exist
  before being acted upon; stale entries are removed on discovery.
* Fully user-clearable from Settings, and skipped entirely if the user disables it.
* Storage cost target: < 15 MB for 200,000 files.

## 6. Search UI behaviour

* Debounce 250 ms. Empty query shows recent searches and suggested filters, not results.
* Results stream in with a stable sort so items do not jump under the user's finger — new
  results append below the current viewport unless the list is at the top.
* A live counter ("142 results, still searching…") with a stop button.
* Each result shows the containing folder; tapping the folder chip jumps there with the item
  highlighted.
* Zero results distinguishes three cases: no match, no access to that scope, and
  still-searching.
* Recent searches are stored locally, capped at 20, clearable.

## 7. Performance targets

| Metric | Target |
|---|---|
| First result, indexed | < 120 ms |
| First result, MediaStore | < 300 ms |
| First result, cold walk | < 900 ms |
| Full walk, 200k files, All Files Access | < 20 s |
| Full walk, SAF tree | < 90 s (SAF is inherently slower; show progress) |
| Memory during search | < 40 MB above baseline |
| Results held in memory | cap 5,000, then "refine your search" |
