package com.devbehindyou.refract.data.mapping

import android.system.ErrnoException
import android.system.OsConstants
import com.devbehindyou.refract.domain.model.FileError
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException as NioAccessDeniedException
import java.util.zip.ZipException

/**
 * Every branch of [toFileError], per Phase 3 AC5: "every mapped exception has a test; no
 * silent `else -> Unknown`." Needs Robolectric — `ErrnoException`/`OsConstants` are Android
 * platform types, not available on a plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
class ExceptionMappingTest {

    @Test
    fun `FileNotFoundException maps to FileError FileNotFound`() {
        val error = FileNotFoundException("gone").toFileError(ErrorContext("gone.txt"))

        assertTrue(error is FileError.FileNotFound)
        assertEquals("gone.txt", (error as FileError.FileNotFound).name)
    }

    @Test
    fun `SecurityException maps to FileError AccessDenied`() {
        val error = SecurityException("nope").toFileError(ErrorContext("secret.txt"))

        assertTrue(error is FileError.AccessDenied)
        assertEquals("secret.txt", (error as FileError.AccessDenied).name)
    }

    @Test
    fun `java nio file AccessDeniedException maps to FileError AccessDenied`() {
        val error = NioAccessDeniedException("/some/path").toFileError(ErrorContext("path"))

        assertTrue(error is FileError.AccessDenied)
    }

    @Test
    fun `ErrnoException ENOSPC maps to FileError DiskFull`() {
        val error = ErrnoException("write", OsConstants.ENOSPC).toFileError()

        assertTrue(error is FileError.DiskFull)
    }

    @Test
    fun `ErrnoException EACCES maps to FileError AccessDenied`() {
        val error = ErrnoException("open", OsConstants.EACCES).toFileError(ErrorContext("locked.txt"))

        assertTrue(error is FileError.AccessDenied)
    }

    @Test
    fun `ErrnoException EROFS maps to FileError ReadOnlyStorage`() {
        val error = ErrnoException("write", OsConstants.EROFS).toFileError()

        assertTrue(error is FileError.ReadOnlyStorage)
    }

    @Test
    fun `ErrnoException ENOENT maps to FileError FileNotFound`() {
        val error = ErrnoException("stat", OsConstants.ENOENT).toFileError(ErrorContext("missing.txt"))

        assertTrue(error is FileError.FileNotFound)
    }

    @Test
    fun `ErrnoException ENAMETOOLONG maps to FileError PathTooLong`() {
        val error = ErrnoException("open", OsConstants.ENAMETOOLONG).toFileError(ErrorContext("x".repeat(300)))

        assertTrue(error is FileError.PathTooLong)
    }

    @Test
    fun `ErrnoException EEXIST maps to FileError FileAlreadyExists`() {
        val error = ErrnoException("open", OsConstants.EEXIST).toFileError(ErrorContext("dup.txt"))

        assertTrue(error is FileError.FileAlreadyExists)
    }

    @Test
    fun `an ErrnoException with an unrecognised errno maps to FileError Unknown, carrying the errno`() {
        val obscureErrno = OsConstants.EPIPE
        val error = ErrnoException("write", obscureErrno).toFileError()

        assertTrue(error is FileError.Unknown)
        assertTrue((error as FileError.Unknown).marker.contains(obscureErrno.toString()))
    }

    @Test
    fun `ZipException maps to FileError CorruptedArchive`() {
        val error = ZipException("bad zip").toFileError(ErrorContext("archive.zip"))

        assertTrue(error is FileError.CorruptedArchive)
    }

    @Test
    fun `OutOfMemoryError maps to FileError OutOfMemory`() {
        val error = OutOfMemoryError().toFileError()

        assertTrue(error is FileError.OutOfMemory)
    }

    @Test
    fun `generic IOException maps to FileError IoFailure`() {
        val error = IOException("disk hiccup").toFileError(ErrorContext("file.bin"))

        assertTrue(error is FileError.IoFailure)
    }

    @Test
    fun `an entirely unrecognised Throwable maps to FileError Unknown, carrying its class name`() {
        val error = IllegalStateException("something odd").toFileError()

        assertTrue(error is FileError.Unknown)
        assertEquals("IllegalStateException", (error as FileError.Unknown).marker)
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException is rethrown, never mapped`() {
        val cancellation = CancellationException("cancelled")

        // toFileError() must rethrow, not return a FileError — this call is expected to
        // throw, which @Test(expected = ...) verifies.
        cancellation.toFileError()
    }
}
