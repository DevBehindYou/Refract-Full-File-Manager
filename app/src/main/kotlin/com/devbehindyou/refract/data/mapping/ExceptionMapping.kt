package com.devbehindyou.refract.data.mapping

import android.system.ErrnoException
import android.system.OsConstants
import com.devbehindyou.refract.domain.model.FileError
import kotlinx.coroutines.CancellationException
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipException
import java.nio.file.AccessDeniedException as NioAccessDeniedException

/**
 * The exception -> [FileError] boundary (`architecture/DATA_LAYER.md` §4). Every backend
 * catches platform exceptions here — nothing above the data layer ever sees a `Throwable`
 * (`architecture/ARCHITECTURE.md` §5's "the data layer never throws across its boundary").
 *
 * `CancellationException` is rethrown, never mapped — `architecture/ARCHITECTURE.md` §5 calls
 * catching it "the single most common coroutine bug." A cancelled operation must keep
 * cancelling, not silently turn into a reported failure.
 *
 * The `java.nio.file.AccessDeniedException` branch is this file's own addition, not in
 * `DATA_LAYER.md`'s original example — `FileSystemBackend` uses `java.nio.file.Files` (NIO.2)
 * for genuine incremental directory streaming (Phase 3 AC4), which can throw NIO.2's checked
 * exceptions. It's listed before the general `IOException` branch deliberately, since it's a
 * subtype and `when` matches in order.
 *
 * [FileError.DiskFull]'s `required`/`available` fields are `0L` placeholders here — the
 * generic mapping boundary sees only an errno, not how many bytes an operation actually
 * needed or how much space is actually free. A call site that already knows both (a write
 * that tracked its own byte count, `StatFs` queried separately) should construct
 * [FileError.DiskFull] directly rather than route through this generic mapper.
 */
fun Throwable.toFileError(context: ErrorContext? = null): FileError =
    when (this) {
        is CancellationException -> throw this
        is FileNotFoundException -> FileError.FileNotFound(context?.name)
        is NioAccessDeniedException -> FileError.AccessDenied(context?.name)
        is SecurityException -> FileError.AccessDenied(context?.name)
        is ErrnoException -> errnoToFileError(this, context)
        is ZipException -> FileError.CorruptedArchive(context?.name)
        is OutOfMemoryError -> FileError.OutOfMemory
        is IOException -> FileError.IoFailure(context?.name)
        else -> FileError.Unknown(this::class.simpleName ?: "UnknownThrowable")
    }

private fun errnoToFileError(
    exception: ErrnoException,
    context: ErrorContext?,
): FileError =
    when (exception.errno) {
        OsConstants.ENOSPC -> FileError.DiskFull(required = 0L, available = 0L)
        OsConstants.EACCES -> FileError.AccessDenied(context?.name)
        OsConstants.EROFS -> FileError.ReadOnlyStorage
        OsConstants.ENOENT -> FileError.FileNotFound(context?.name)
        OsConstants.ENAMETOOLONG -> FileError.PathTooLong(context?.name)
        OsConstants.EEXIST -> FileError.FileAlreadyExists(context?.name.orEmpty())
        else -> FileError.Unknown("errno=${exception.errno}")
    }
