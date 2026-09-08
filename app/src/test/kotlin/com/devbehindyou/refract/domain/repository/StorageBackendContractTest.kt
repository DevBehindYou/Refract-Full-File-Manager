package com.devbehindyou.refract.domain.repository

import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A [StorageBackend] implementation is correct if it behaves this way, regardless of what's
 * underneath it — file, SAF, MediaStore, or a map. `InMemoryBackendContractTest` proves
 * [com.devbehindyou.refract.domain.testing.InMemoryBackend] against this now (Phase 2 AC3);
 * Phase 3's `FileSystemBackend`/`SafBackend`/`MediaStoreBackend` subclass this same class
 * rather than write their own parallel test suite.
 *
 * **JUnit4, not JUnit5** — a correction made in Phase 3, not the original Phase 2 design.
 * Robolectric's JUnit4 integration (`@RunWith(RobolectricTestRunner::class)`) is far more
 * mature than its JUnit5 support, and Phase 3's `FileSystemBackendContractTest` needs
 * Robolectric to test real file I/O. Since this abstract class's `@Test` methods are what
 * actually run, they need to be JUnit4-compatible for any Robolectric-based subclass to work
 * at all — the same reasoning that led Phase 1's `MainActivitySmokeTest` to use JUnit4
 * deliberately (see `PHASE_1_NOTES.md`). `InMemoryBackendContractTest` (Phase 2, no
 * Robolectric needed) runs fine under JUnit4 too, via the vintage engine already wired into
 * this project's Gradle setup for exactly this reason.
 *
 * [rootId] must name a writable directory freshly created (or otherwise isolated) per test —
 * subclasses are responsible for test isolation between runs.
 */
abstract class StorageBackendContractTest {

    protected abstract fun backend(): StorageBackend
    protected abstract suspend fun rootId(backend: StorageBackend): FileNodeId

    @Test
    fun `getNode on the root succeeds`() = runTest {
        val backend = backend()
        val root = rootId(backend)

        val result = backend.getNode(root)

        assertTrue("expected Success, got $result", result is FileResult.Success)
    }

    @Test
    fun `getNode on an unknown id fails with FileNotFound`() = runTest {
        val backend = backend()
        val root = rootId(backend)
        val bogus = FileNodeId("${root.raw}-does-not-exist")

        val result = backend.getNode(bogus)

        assertTrue("expected Failure, got $result", result is FileResult.Failure)
        assertTrue((result as FileResult.Failure).error is FileError.FileNotFound)
    }

    @Test
    fun `listChildren on a freshly created empty directory returns an empty list`() = runTest {
        val backend = backend()
        val root = rootId(backend)

        val dir = (backend.createDirectory(root, "empty-dir") as FileResult.Success).value
        val children = backend.listChildren(dir.id).first()

        assertTrue(children is FileResult.Success)
        assertTrue((children as FileResult.Success).value.isEmpty())
    }

    @Test
    fun `createDirectory then listChildren on the parent finds it`() = runTest {
        val backend = backend()
        val root = rootId(backend)

        val created = (backend.createDirectory(root, "subdir") as FileResult.Success).value
        val children = (backend.listChildren(root).first() as FileResult.Success).value

        assertTrue(children.any { it.id == created.id && it.name == "subdir" })
    }

    @Test
    fun `creating a directory with a name that already exists fails with FileAlreadyExists`() = runTest {
        val backend = backend()
        val root = rootId(backend)
        backend.createDirectory(root, "dup")

        val result = backend.createDirectory(root, "dup")

        assertTrue(result is FileResult.Failure)
        assertTrue((result as FileResult.Failure).error is FileError.FileAlreadyExists)
    }

    @Test
    fun `write then read round-trips the exact bytes`() = runTest {
        val backend = backend()
        val root = rootId(backend)
        val payload = "refract".encodeToByteArray()

        val target = (backend.openOutput(root, "note.txt", "text/plain") as FileResult.Success).value
        target.stream().use { it.write(payload) }
        val written = target.toNode()

        assertTrue(written is FileResult.Success)
        val node = (written as FileResult.Success).value
        assertEquals(payload.size.toLong(), node.size)

        val input = (backend.openInput(node.id) as FileResult.Success).value
        val readBack = input.stream().use { it.readBytes() }
        assertTrue(payload.contentEquals(readBack))
    }

    @Test
    fun `discard leaves no committed node behind`() = runTest {
        val backend = backend()
        val root = rootId(backend)

        val target = (backend.openOutput(root, "abandoned.txt", "text/plain") as FileResult.Success).value
        target.stream().use { it.write("partial".encodeToByteArray()) }
        target.discard()

        val children = (backend.listChildren(root).first() as FileResult.Success).value
        assertFalse(children.any { it.name == "abandoned.txt" })
    }

    @Test
    fun `delete removes the node from its parent's children`() = runTest {
        val backend = backend()
        val root = rootId(backend)
        val created = (backend.createDirectory(root, "to-delete") as FileResult.Success).value

        val deleteResult = backend.delete(created.id)
        val children = (backend.listChildren(root).first() as FileResult.Success).value

        assertTrue(deleteResult is FileResult.Success)
        assertFalse(children.any { it.id == created.id })
    }

    @Test
    fun `rename changes the name but keeps the same id`() = runTest {
        val backend = backend()
        val root = rootId(backend)
        val created = (backend.createDirectory(root, "old-name") as FileResult.Success).value

        val renamed = backend.rename(created.id, "new-name")

        assertTrue(renamed is FileResult.Success)
        val node = (renamed as FileResult.Success).value
        assertEquals(created.id, node.id)
        assertEquals("new-name", node.name)
    }

    @Test
    fun `moveWithin changes the parent and is reachable there`() = runTest {
        val backend = backend()
        val root = rootId(backend)
        val destination = (backend.createDirectory(root, "destination") as FileResult.Success).value
        val moving = (backend.createDirectory(root, "moving") as FileResult.Success).value

        val moved = backend.moveWithin(moving.id, destination.id)
        val oldParentChildren = (backend.listChildren(root).first() as FileResult.Success).value
        val newParentChildren = (backend.listChildren(destination.id).first() as FileResult.Success).value

        assertTrue(moved is FileResult.Success)
        assertFalse(oldParentChildren.any { it.id == moving.id })
        assertTrue(newParentChildren.any { it.id == moving.id })
    }

    @Test
    fun `exists reports true only after creation`() = runTest {
        val backend = backend()
        val root = rootId(backend)

        val before = backend.exists(root, "will-exist")
        backend.createDirectory(root, "will-exist")
        val after = backend.exists(root, "will-exist")

        assertFalse(before)
        assertTrue(after)
    }
}
