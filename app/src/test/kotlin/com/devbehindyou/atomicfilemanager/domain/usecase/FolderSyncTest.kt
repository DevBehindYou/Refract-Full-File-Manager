package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FolderSyncTest {
    private val backend = InMemoryBackend()
    private val comparer = FolderComparer { backend }

    private suspend fun dir(
        parent: FileNodeId,
        name: String,
    ): FileNodeId = (backend.createDirectory(parent, name) as FileResult.Success).value.id

    private suspend fun put(
        parent: FileNodeId,
        name: String,
        text: String,
    ): FileNodeId = backend.putFile(parent, name, text.toByteArray())

    private suspend fun compare(
        left: FileNodeId,
        right: FileNodeId,
        toleranceMs: Long = SyncPlanner.DEFAULT_TOLERANCE_MS,
    ): Map<String, CompareState> =
        (comparer.compare(left, right, toleranceMs) as FileResult.Success).value.associate { it.path to it.state }

    @Test
    fun `lists each path once with how it differs`() =
        runTest {
            val left = dir(backend.rootId, "left")
            val right = dir(backend.rootId, "right")
            put(left, "same.txt", "a")
            put(right, "same.txt", "a")
            put(left, "size.txt", "short")
            put(right, "size.txt", "much longer")
            put(left, "only-left.txt", "x")
            put(right, "only-right.txt", "y")
            dir(left, "new-folder")
            val leftSub = dir(left, "photos")
            val rightSub = dir(right, "photos")
            put(leftSub, "a.jpg", "1")
            put(rightSub, "b.jpg", "2")

            assertEquals(
                mapOf(
                    "new-folder" to CompareState.ONLY_LEFT,
                    "only-left.txt" to CompareState.ONLY_LEFT,
                    "only-right.txt" to CompareState.ONLY_RIGHT,
                    "photos/a.jpg" to CompareState.ONLY_LEFT,
                    "photos/b.jpg" to CompareState.ONLY_RIGHT,
                    "same.txt" to CompareState.SAME,
                    "size.txt" to CompareState.DIFFERENT,
                ),
                compare(left, right),
            )
        }

    @Test
    fun `later writes count as newer once past the tolerance`() =
        runTest {
            val left = dir(backend.rootId, "left")
            val right = dir(backend.rootId, "right")
            put(left, "doc.txt", "v1")
            put(right, "doc.txt", "v1")
            assertEquals(CompareState.SAME, compare(left, right)["doc.txt"])
            assertEquals(CompareState.NEWER_RIGHT, compare(left, right, toleranceMs = 0)["doc.txt"])
            assertEquals(CompareState.NEWER_LEFT, compare(right, left, toleranceMs = 0)["doc.txt"])
        }

    @Test
    fun `copy-new never deletes and mirror trashes extras`() =
        runTest {
            val left = dir(backend.rootId, "left")
            val right = dir(backend.rootId, "right")
            put(right, "old.txt", "v1")
            put(left, "old.txt", "v2")
            put(left, "new.txt", "n")
            put(right, "extra.txt", "e")
            val entries = (comparer.compare(left, right, 0) as FileResult.Success).value

            val copyNew = SyncPlanner.plan(entries, SyncMode.COPY_NEW)
            assertEquals(listOf("new.txt"), copyNew.copies.map { it.path })
            assertEquals(listOf("old.txt"), copyNew.replacements.map { it.path })
            assertTrue(copyNew.trash.isEmpty())

            val mirror = SyncPlanner.plan(entries, SyncMode.MIRROR)
            assertEquals(listOf("extra.txt"), mirror.trash.map { it.name })

            var n = 0
            val ops = SyncPlanner.operations(mirror, now = 5, newId = { OperationId("op${n++}") })
            assertEquals(listOf(OperationType.COPY, OperationType.COPY, OperationType.TRASH), ops.map { it.type })
            assertEquals(CollisionPolicy.KEEP_BOTH, ops[0].options.collisionPolicy)
            assertEquals(CollisionPolicy.OVERWRITE, ops[1].options.collisionPolicy)
            assertTrue(ops.take(2).all { it.destination == right })
            assertTrue(ops[2].options.useTrash)
        }

    @Test
    fun `mirror replaces differing files but not a file against a folder`() =
        runTest {
            val left = dir(backend.rootId, "left")
            val right = dir(backend.rootId, "right")
            put(left, "notes", "file on the left")
            dir(right, "notes")
            put(left, "a.txt", "1")
            put(right, "a.txt", "1")
            val entries = (comparer.compare(left, right, 0) as FileResult.Success).value
            val plan = SyncPlanner.plan(entries, SyncMode.MIRROR)
            assertEquals(listOf("a.txt"), plan.replacements.map { it.path })
            assertEquals(CompareState.DIFFERENT, entries.single { it.path == "notes" }.state)
        }

    @Test
    fun `an empty plan makes no operations`() {
        val plan = SyncPlan(emptyList(), emptyList(), emptyList())
        assertTrue(plan.isEmpty)
        assertTrue(SyncPlanner.operations(plan, now = 0).isEmpty())
    }
}
