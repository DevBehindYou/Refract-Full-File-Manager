package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InterruptedTransferCleanupTest {
    private val backend = InMemoryBackend()

    private suspend fun file(
        name: String,
        content: String = "x",
        parent: FileNodeId = backend.rootId,
    ) {
        val output = (backend.openOutput(parent, name, null) as FileResult.Success).value
        output.stream().use { it.write(content.toByteArray()) }
        output.sync()
    }

    private suspend fun names(): Set<String> {
        val all = mutableSetOf<String>()
        backend.listChildren(backend.rootId).collect {
            if (it is FileResult.Success) {
                all +=
                    it.value.map {
                            n ->
                        n.name
                    }
            }
        }
        return all
    }

    @Test
    fun `backup names keep the original name and are recognised`() {
        val backup = InterruptedTransferCleanup.backupName("report (final).pdf")
        assertTrue(backup.startsWith(".atomic-orig-"), backup)
        assertEquals("report (final).pdf", InterruptedTransferCleanup.originalNameOf(backup))
        assertTrue(InterruptedTransferCleanup.isStaging(InterruptedTransferCleanup.stagingName()))
        assertFalse(InterruptedTransferCleanup.isStaging(".atomic-notes.partial"))
    }

    @Test
    fun `a name too long to embed gets an anonymous backup that is never restored`() {
        val backup = InterruptedTransferCleanup.backupName("é".repeat(120) + ".txt")
        assertTrue(backup.toByteArray().size <= 255)
        assertNull(InterruptedTransferCleanup.originalNameOf(backup))
    }

    @Test
    fun `a crash mid-replace puts the original back and drops the unverified copy`() =
        runTest {
            file(InterruptedTransferCleanup.backupName("notes.txt"), "original")
            file(InterruptedTransferCleanup.stagingName(), "half")
            file("other.txt")

            val report = InterruptedTransferCleanup.clean(backend.rootId, backend)

            assertEquals(CleanupReport(removedPartials = 1, restoredOriginals = 1), report)
            assertEquals(setOf("notes.txt", "other.txt"), names())
        }

    @Test
    fun `a backup whose name is taken again is kept, not deleted`() =
        runTest {
            val backup = InterruptedTransferCleanup.backupName("notes.txt")
            file(backup, "original")
            file("notes.txt", "new copy")

            val report = InterruptedTransferCleanup.clean(backend.rootId, backend)

            assertEquals(CleanupReport(keptBackups = 1), report)
            assertEquals(setOf(backup, "notes.txt"), names())
        }

    @Test
    fun `ordinary dot files and old anonymous backups are left alone`() =
        runTest {
            val old = ".atomic-0b5c7e1a-2f3d-4c5b-9a8e-1234567890ab.backup"
            file(old)
            file(".nomedia")
            file(".atomic-orig-notes.txt")

            val report = InterruptedTransferCleanup.clean(backend.rootId, backend)

            assertTrue(report.isEmpty, "$report")
            assertEquals(setOf(old, ".nomedia", ".atomic-orig-notes.txt"), names())
        }
}
