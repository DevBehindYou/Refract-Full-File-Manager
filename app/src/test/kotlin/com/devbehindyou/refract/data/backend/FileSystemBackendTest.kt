package com.devbehindyou.refract.data.backend

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Specific cases beyond [StorageBackendContractTest]'s generic behaviour, per
 * `testing/TEST_STRATEGY.md` §4's explicit list for this backend: chunk boundaries at
 * 200/201/0 items, deep nesting, and names with spaces/emoji/RTL characters.
 */
@RunWith(RobolectricTestRunner::class)
class FileSystemBackendTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun backend(): FileSystemBackend =
        FileSystemBackend(ApplicationProvider.getApplicationContext<Context>())

    private fun seedFiles(dir: File, count: Int) {
        repeat(count) { i -> File(dir, "file-%04d.txt".format(i)).writeText("x") }
    }

    @Test
    fun `an empty directory emits exactly one empty success chunk`() = runTest {
        val dir = tempFolder.newFolder("empty")
        val backend = backend()

        val chunks = backend.listChildren(FileNodeId.file(dir.absolutePath)).toList()

        assertEquals(1, chunks.size)
        assertTrue(chunks[0] is FileResult.Success)
        assertTrue((chunks[0] as FileResult.Success).value.isEmpty())
    }

    @Test
    fun `exactly one chunk's worth of items (200) emits as a single chunk`() = runTest {
        val dir = tempFolder.newFolder("exactly-200")
        seedFiles(dir, LISTING_CHUNK_SIZE)
        val backend = backend()

        val chunks = backend.listChildren(FileNodeId.file(dir.absolutePath)).toList()
        val totalItems = chunks.sumOf { (it as FileResult.Success).value.size }

        assertEquals(1, chunks.size)
        assertEquals(LISTING_CHUNK_SIZE, totalItems)
    }

    @Test
    fun `one item over a full chunk (201) emits two chunks`() = runTest {
        val dir = tempFolder.newFolder("chunk-plus-one")
        seedFiles(dir, LISTING_CHUNK_SIZE + 1)
        val backend = backend()

        val chunks = backend.listChildren(FileNodeId.file(dir.absolutePath)).toList()
        val sizes = chunks.map { (it as FileResult.Success).value.size }

        assertEquals(2, chunks.size)
        assertEquals(listOf(LISTING_CHUNK_SIZE, 1), sizes)
    }

    @Test
    fun `deep nesting is listed correctly at every level`() = runTest {
        val backend = backend()
        var current = tempFolder.newFolder("deep-root")
        // 25 levels — deep enough to exercise real recursion/path-length behaviour without
        // risking the ~260-char path limits some filesystems still enforce.
        repeat(25) { i ->
            current = File(current, "level-$i").apply { mkdir() }
        }
        File(current, "leaf.txt").writeText("bottom")

        val leafParentId = FileNodeId.file(current.absolutePath)
        val children = (backend.listChildren(leafParentId).toList().first() as FileResult.Success).value

        assertEquals(1, children.size)
        assertEquals("leaf.txt", children.first().name)
    }

    @Test
    fun `names with spaces, emoji, and RTL characters round-trip correctly`() = runTest {
        val dir = tempFolder.newFolder("special-names")
        val names = listOf(
            "a file with spaces.txt",
            "emoji file \uD83D\uDCC1.txt", // contains a folder emoji
            "\u0645\u0644\u0641 \u0639\u0631\u0628\u064A.txt", // Arabic RTL name
        )
        names.forEach { File(dir, it).writeText("x") }
        val backend = backend()

        val children = (backend.listChildren(FileNodeId.file(dir.absolutePath)).toList().first()
            as FileResult.Success).value

        assertEquals(names.toSet(), children.map { it.name }.toSet())
        // Each name must also survive a full getNode round-trip through its own id, not
        // just appear correctly in a listing.
        children.forEach { child ->
            val fetched = backend.getNode(child.id)
            assertTrue(fetched is FileResult.Success)
            assertEquals(child.name, (fetched as FileResult.Success).value.name)
        }
    }
}
