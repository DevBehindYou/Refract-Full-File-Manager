package com.devbehindyou.refract.domain.model

/**
 * The data layer never throws across its boundary (`ARCHITECTURE.md` §5, `CODING_RULES.md`
 * #34); every backend method returns this instead. Platform exceptions are caught and mapped
 * to a [FileError] once, at the backend edge.
 */
sealed interface FileResult<out T> {
    data class Success<T>(val value: T) : FileResult<T>
    data class Failure(val error: FileError) : FileResult<Nothing>
}

inline fun <T, R> FileResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (FileError) -> R,
): R = when (this) {
    is FileResult.Success -> onSuccess(value)
    is FileResult.Failure -> onFailure(error)
}

inline fun <T, R> FileResult<T>.map(transform: (T) -> R): FileResult<R> = when (this) {
    is FileResult.Success -> FileResult.Success(transform(value))
    is FileResult.Failure -> this
}

fun <T> FileResult<T>.getOrNull(): T? = when (this) {
    is FileResult.Success -> value
    is FileResult.Failure -> null
}

inline fun <T> FileResult<T>.getOrElse(onFailure: (FileError) -> T): T = when (this) {
    is FileResult.Success -> value
    is FileResult.Failure -> onFailure(error)
}

/**
 * `architecture/FILE_OPERATIONS.md` §4's `copyFile` example calls this bare — `.getOrReturn()`
 * with no lambda — to early-return `Failure` from the enclosing function on failure. That
 * exact call shape isn't reproducible in real Kotlin (a non-local `return` needs to happen
 * inside a lambda actually passed to an inline function; there's no implicit "return from
 * whoever called me" mechanism). Confirmed here as illustrative pseudocode rather than a
 * spec to match literally, alongside that same example's `clock` reference with no
 * corresponding constructor parameter, and its `DiskFull(required = ...)` call missing the
 * `available` argument [ERROR_MODEL.md] requires — this one function is not meant to compile
 * verbatim. The idiomatic equivalent, used the same way, is:
 * ```
 * val input = backend.openInput(id).getOrReturn { return FileResult.Failure(it) }
 * ```
 */
inline fun <T> FileResult<T>.getOrReturn(onFailure: (FileError) -> Nothing): T = when (this) {
    is FileResult.Success -> value
    is FileResult.Failure -> onFailure(error)
}
