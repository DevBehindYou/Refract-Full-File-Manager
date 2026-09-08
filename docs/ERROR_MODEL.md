# Error Model

One sealed hierarchy. Every failure in the app is one of these. The UI never sees a
`Throwable`, an errno, or a platform message.

```kotlin
sealed interface FileError {
    val severity: Severity          // BLOCKING | TRANSIENT | SILENT
    val recoverable: Boolean
    val action: RecoveryAction?     // what the button in the error UI does

    // Access
    data class PermissionDenied(val what: AccessTarget) : FileError
    data class AccessDenied(val name: String?) : FileError
    data object PlatformRestricted : FileError          // /Android/data on API 30+
    data class ProviderUnavailable(val authority: String?) : FileError

    // Presence
    data class FileNotFound(val name: String?) : FileError
    data class StorageUnavailable(val volumeLabel: String?) : FileError
    data object ReadOnlyStorage : FileError

    // Capacity
    data class DiskFull(val required: Long, val available: Long) : FileError
    data object OutOfMemory : FileError
    data class PathTooLong(val name: String?) : FileError

    // Conflict
    data class FileAlreadyExists(val name: String) : FileError
    data class InvalidDestination(val name: String?) : FileError
    data class InvalidName(val name: String, val reason: NameProblem) : FileError

    // Operation
    data object OperationCancelled : FileError
    data class IncompleteWrite(val name: String, val written: Long, val expected: Long) : FileError
    data class PartialFailure(val failedCount: Int, val totalCount: Int) : FileError
    data class IoFailure(val name: String?) : FileError

    // Content
    data class UnsupportedFormat(val mimeType: String?) : FileError
    data class CorruptedArchive(val name: String?) : FileError
    data class SuspiciousArchive(val name: String?, val reason: String) : FileError
    data class FileTooLarge(val name: String, val limit: Long) : FileError

    // Fallback
    data class Unknown(val marker: String) : FileError
}

enum class Severity { BLOCKING, TRANSIENT, SILENT }
```

* **BLOCKING** — replaces the screen content with an `ErrorState`.
* **TRANSIENT** — snackbar; the screen stays usable.
* **SILENT** — logged only (e.g. a thumbnail failing to decode).

---

## Message mapping

Every message: says what happened, names the object, and gives the next step. No error codes,
no exception names, no "please try again later".

| Error | Title | Body | Action |
|---|---|---|---|
| `PermissionDenied` | Access needed | Refract needs permission to see your files. | **Grant access** → the correct flow for this API level |
| `AccessDenied(name)` | Can't open *name* | Android is blocking access to this item. | **Check access** → troubleshooting |
| `PlatformRestricted` | Android blocks this folder | Since Android 11, no file manager can open app data folders. This is a system restriction. | **Got it** (no retry) |
| `ProviderUnavailable` | This storage isn't responding | The app providing this folder stopped. | **Retry** |
| `FileNotFound(name)` | *name* is gone | It may have been moved or deleted. | **Refresh** |
| `StorageUnavailable(label)` | *SD card* isn't available | It may have been removed. | **Refresh** |
| `ReadOnlyStorage` | This storage is read-only | You can view files here but not change them. | — |
| `DiskFull` | Not enough space | You need 1.4 GB more on this storage. | **Free up space** → Storage |
| `OutOfMemory` | Ran out of memory | Try again with fewer items at once. | **Retry** |
| `PathTooLong(name)` | Name is too long | *name* is too long for this storage. | **Rename** |
| `FileAlreadyExists(name)` | *name* already exists | (Rendered as the conflict dialog, not an error) | Overwrite / Keep both / Skip |
| `InvalidDestination` | Can't move here | You can't move a folder into itself. | — |
| `InvalidName(name, reason)` | That name won't work | Names can't contain / \\ : * ? " < > \| | **Fix name** |
| `OperationCancelled` | Cancelled | (snackbar only, no title) | — |
| `IncompleteWrite(name)` | *name* didn't copy fully | The partial copy was removed. | **Retry** |
| `PartialFailure(3, 500)` | 497 of 500 copied | 3 items couldn't be copied. | **View failures** |
| `IoFailure(name)` | Couldn't read *name* | The file or storage may be damaged. | **Retry** |
| `UnsupportedFormat` | Can't preview this file | Try opening it with another app. | **Open with** |
| `CorruptedArchive(name)` | *name* is damaged | The archive couldn't be read. | — |
| `SuspiciousArchive(name)` | This archive looks unsafe | It contains entries that would write outside the folder you chose. | **Extract safely** (skips them) |
| `FileTooLarge(name, limit)` | Too big to preview | Files over 2 MB open faster in another app. | **Open with** |
| `Unknown` | Something went wrong | Try again. If it keeps happening, restart the app. | **Retry** |

---

## Recovery actions

```kotlin
sealed interface RecoveryAction {
    data object Retry : RecoveryAction
    data object Refresh : RecoveryAction
    data class RequestAccess(val target: AccessTarget) : RecoveryAction
    data object OpenTroubleshooting : RecoveryAction
    data object OpenStorage : RecoveryAction
    data object OpenWith : RecoveryAction
    data object ViewFailures : RecoveryAction
    data object Rename : RecoveryAction
    data object Dismiss : RecoveryAction
}
```

An error with `action = null` renders without a button. `PlatformRestricted` deliberately has
no retry — offering one on a permanent platform restriction trains users to keep tapping.

---

## Rules

1. **Data layer maps, UI renders.** Exception → `FileError` happens exactly once, at the
   backend boundary (`architecture/DATA_LAYER.md` §4).
2. `CancellationException` is **rethrown**, never mapped. It is not an error.
3. `Unknown` carries a short opaque marker (class name), never a stack trace or a message
   that could contain a path.
4. Per-item failures in a batch never abort the batch; they aggregate into `PartialFailure`.
5. Every `BLOCKING` error offers a route forward. A dead-end error state is a bug.
6. Strings live in `strings.xml` with placeholders; the ViewModel passes structured data and
   the composable resolves it. Never build a user-facing string in a ViewModel.
7. Every `FileError` variant has a screenshot test of its rendered state.
