package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BatchRenameTest {
    private val backend = InMemoryBackend()
    private val useCase = BatchRenameUseCase { backend }

    private suspend fun files(vararg names: String): List<FileNode> =
        names.map { name ->
            val id = backend.putFile(backend.rootId, name, byteArrayOf(1))
            (backend.getNode(id) as FileResult.Success).value
        }

    private suspend fun names(): Set<String> =
        (backend.listChildren(backend.rootId).first() as FileResult.Success).value.map { it.name }.toSet()

    @Test
    fun `pattern steps keep the extension and number in selection order`() {
        val pattern = RenamePattern(newBase = "Holiday", numbering = Numbering(start = 1, digits = 2))
        assertEquals("Holiday 01.jpg", BatchRenameRules.newName(pattern, "IMG_4410.jpg", 0))
        assertEquals("Holiday 02.JPG", BatchRenameRules.newName(pattern, "IMG_4411.JPG", 1))

        val tidy = RenamePattern(find = "img_", replace = "", prefix = "2026 ", case = NameCase.TITLE)
        assertEquals("2026 4410 Beach.png", BatchRenameRules.newName(tidy, "IMG_4410 beach.png", 0))
        assertEquals("notes v2", BatchRenameRules.newName(RenamePattern(suffix = " v2"), "notes", 0))
        assertEquals("archive.tar v2", BatchRenameRules.newName(RenamePattern(suffix = " v2"), "archive.tar", 0, true))
        assertEquals(
            "REPORT",
            BatchRenameRules.newName(RenamePattern(case = NameCase.UPPER, keepExtension = false), "report.pdf", 0),
        )
    }

    @Test
    fun `preview flags empty, invalid, duplicate and taken names`() =
        runTest {
            val nodes = files("a.txt", "b.txt")
            val clash = BatchRenameRules.preview(nodes, RenamePattern(newBase = "same"), listOf("a.txt", "b.txt"))
            assertTrue(clash.all { it.problem == RenameProblem.DUPLICATE_IN_BATCH })

            val taken =
                BatchRenameRules.preview(
                    nodes.take(1),
                    RenamePattern(newBase = "keep"),
                    listOf("a.txt", "KEEP.txt"),
                )
            assertEquals(RenameProblem.EXISTS, taken.single().problem)

            val slash = BatchRenameRules.preview(nodes.take(1), RenamePattern(prefix = "x/"), listOf("a.txt"))
            assertEquals(RenameProblem.INVALID_CHARACTER, slash.single().problem)

            val blank =
                BatchRenameRules.preview(
                    nodes.take(1),
                    RenamePattern(find = "a", keepExtension = false),
                    listOf(),
                )
            assertEquals(RenameProblem.EMPTY, blank.single().problem)

            val swap =
                BatchRenameRules.preview(
                    nodes,
                    RenamePattern(find = "a", replace = "c"),
                    listOf("a.txt", "b.txt"),
                )
            assertEquals(listOf(null, null), swap.map { it.problem })
            assertEquals(listOf(true, false), swap.map { it.changes })
        }

    @Test
    fun `renames every item and undo puts the names back`() =
        runTest {
            val nodes = files("IMG_1.jpg", "IMG_2.jpg", "keep.txt")

            val result = useCase(listOf(nodes[0] to "Trip 1.jpg", nodes[1] to "Trip 2.jpg", nodes[2] to "keep.txt"))

            assertEquals(2, result.renamed.size)
            assertTrue(result.failed.isEmpty())
            assertEquals(setOf("Trip 1.jpg", "Trip 2.jpg", "keep.txt"), names())

            useCase.undo(result)
            assertEquals(setOf("IMG_1.jpg", "IMG_2.jpg", "keep.txt"), names())
        }

    @Test
    fun `a swap goes through temporary names and leaves none behind`() =
        runTest {
            val (a, b) = files("a.txt", "b.txt")

            val result = useCase(listOf(a to "b.txt", b to "a.txt"))

            assertTrue(result.failed.isEmpty(), "failed: ${result.failed}")
            assertEquals(setOf("a.txt", "b.txt"), names())
            val byId = result.renamed.associate { it.previousName to it.node.name }
            assertEquals(mapOf("a.txt" to "b.txt", "b.txt" to "a.txt"), byId)
        }

    @Test
    fun `an item that can't take its name is put back, never left on a temporary name`() =
        runTest {
            val (a, b) = files("a.txt", "b.txt")
            files("taken.txt")

            val result = useCase(listOf(a to "taken.txt", b to "a.txt"))

            // a goes back to its name, so b can no longer take it: both keep their old names, nothing is lost.
            assertEquals(listOf("a.txt", "b.txt"), result.failed.map { it.name })
            assertEquals(setOf("a.txt", "b.txt", "taken.txt"), names())
        }
}
