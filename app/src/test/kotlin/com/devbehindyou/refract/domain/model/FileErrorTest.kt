package com.devbehindyou.refract.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileErrorTest {

    @Test
    fun `PlatformRestricted is explicitly non-recoverable with a Dismiss action, matching the no-retry doc note`() {
        val error: FileError = FileError.PlatformRestricted

        assertFalse(error.recoverable)
        assertEquals(RecoveryAction.Dismiss, error.action)
    }

    @Test
    fun `PartialFailure carries a ViewFailures action`() {
        val error: FileError = FileError.PartialFailure(failedCount = 3, totalCount = 500)

        assertEquals(RecoveryAction.ViewFailures, error.action)
        assertTrue(error.recoverable)
    }

    @Test
    fun `FileAlreadyExists has a null action, since it is routed through a conflict dialog, not the generic error UI`() {
        val error: FileError = FileError.FileAlreadyExists("photo.jpg")

        assertNull(error.action)
    }

    @Test
    fun `RequestAccess carries the AccessTarget it was raised for`() {
        val target = AccessTarget.Volume(volumeId = "sd-1", volumeLabel = "SD Card")
        val error: FileError = FileError.PermissionDenied(target)

        val action = error.action
        assertTrue(action is RecoveryAction.RequestAccess)
        assertEquals(target, (action as RecoveryAction.RequestAccess).target)
    }

    @Test
    fun `every FileError variant has a severity`() {
        // A representative one from each of ERROR_MODEL.md's seven categories, not all 22 —
        // this is a sanity check that the sealed hierarchy's contract is honoured
        // everywhere, not a re-assertion of every individual mapping decision.
        val samples: List<FileError> = listOf(
            FileError.AccessDenied("a"),
            FileError.FileNotFound("b"),
            FileError.DiskFull(required = 100, available = 10),
            FileError.InvalidName("c?", NameProblem.IllegalCharacters(setOf('?'))),
            FileError.IoFailure("d"),
            FileError.CorruptedArchive("e.zip"),
            FileError.Unknown("SomeUnexpectedException"),
        )

        samples.forEach { error ->
            // Access alone is the assertion: a missing `override` would fail to compile,
            // not fail at runtime, but this still guards against a severity that silently
            // throws or is somehow left uninitialised.
            assertTrue(error.severity in Severity.entries)
        }
    }
}
