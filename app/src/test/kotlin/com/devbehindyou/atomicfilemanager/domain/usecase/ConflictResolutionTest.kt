package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.Conflict
import com.devbehindyou.atomicfilemanager.domain.model.ConflictChoice
import com.devbehindyou.atomicfilemanager.domain.model.ConflictDecision
import com.devbehindyou.atomicfilemanager.domain.model.ConflictResolver
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/** CollisionPolicy.ASK: the engine waits for a decision instead of choosing (FILE_OPERATIONS.md §5). */
class ConflictResolutionTest {
    private val backend = InMemoryBackend()
    private val engine = FileOperationsEngine { backend }

    // FileNodeId is a value class, which can't be lateinit; JUnit makes a fresh instance per test.
    private var source = backend.rootId
    private var target = backend.rootId

    @BeforeEach
    fun setUp() =
        runTest {
            source = dir(backend.rootId, "source")
            target = dir(backend.rootId, "target")
        }

    private suspend fun dir(
        parent: FileNodeId,
        name: String,
    ): FileNodeId = (backend.createDirectory(parent, name) as FileResult.Success).value.id

    private suspend fun file(
        parent: FileNodeId,
        name: String,
        content: String,
    ): FileNodeId {
        val output = (backend.openOutput(parent, name, "text/plain") as FileResult.Success).value
        output.stream().use { it.write(content.toByteArray()) }
        output.sync()
        return (output.toNode() as FileResult.Success).value.id
    }

    private suspend fun children(parent: FileNodeId): List<FileNode> {
        val all = mutableListOf<FileNode>()
        backend.listChildren(parent).collect { if (it is FileResult.Success) all += it.value }
        return all
    }

    private suspend fun text(id: FileNodeId): String {
        val out = ByteArrayOutputStream()
        (backend.openInput(id) as FileResult.Success).value.stream().use { it.copyTo(out) }
        return out.toString(Charsets.UTF_8)
    }

    private suspend fun textOf(name: String): String = text(children(target).single { it.name == name }.id)

    private fun copy(
        ids: List<FileNodeId>,
        policy: CollisionPolicy = CollisionPolicy.ASK,
    ) = FileOperation(
        id = OperationId.random(),
        type = OperationType.COPY,
        sources = ids,
        destination = target,
        options = OperationOptions(collisionPolicy = policy),
        createdAt = 0L,
    )

    /** Answers every conflict with [choice] and records what it was asked. */
    private class Recorder(
        private val choice: ConflictChoice,
        private val applyToAll: Boolean = false,
    ) : ConflictResolver {
        val asked = mutableListOf<Conflict>()

        override suspend fun resolve(
            operation: FileOperation,
            conflict: Conflict,
        ): ConflictDecision {
            asked += conflict
            return ConflictDecision(choice, applyToAll)
        }
    }

    private fun List<OperationSnapshot>.finalStatus(): OperationStatus = last().status

    @Test
    fun `replace swaps in the new file and reports the clash first`() =
        runTest {
            val incoming = file(source, "notes.txt", "new")
            file(target, "notes.txt", "old")
            val resolver = Recorder(ConflictChoice.REPLACE)

            val snapshots = engine.execute(copy(listOf(incoming)), resolver).toList()

            val waiting = snapshots.map { it.status }.filterIsInstance<OperationStatus.AwaitingInput>().single()
            assertEquals("notes.txt", waiting.conflict.existingDestination.name)
            assertEquals(incoming, waiting.conflict.source.id)
            assertTrue(snapshots.finalStatus() is OperationStatus.Completed)
            assertEquals(listOf("notes.txt"), children(target).map { it.name })
            assertEquals("new", textOf("notes.txt"))
        }

    @Test
    fun `skip leaves the existing file alone and records the skip`() =
        runTest {
            val incoming = file(source, "notes.txt", "new")
            file(target, "notes.txt", "old")

            val status = engine.execute(copy(listOf(incoming)), Recorder(ConflictChoice.SKIP)).toList().finalStatus()

            assertTrue(status is OperationStatus.Completed)
            assertEquals(listOf(incoming), (status as OperationStatus.Completed).summary.skipped)
            assertEquals("old", textOf("notes.txt"))
        }

    @Test
    fun `keep both adds a numbered copy`() =
        runTest {
            val incoming = file(source, "notes.txt", "new")
            file(target, "notes.txt", "old")

            engine.execute(copy(listOf(incoming)), Recorder(ConflictChoice.KEEP_BOTH)).toList()

            assertEquals(setOf("notes.txt", "notes (1).txt"), children(target).map { it.name }.toSet())
            assertEquals("old", textOf("notes.txt"))
            assertEquals("new", textOf("notes (1).txt"))
        }

    @Test
    fun `apply to all asks once for several clashes`() =
        runTest {
            val a = file(source, "a.txt", "new a")
            val b = file(source, "b.txt", "new b")
            val c = file(source, "c.txt", "new c")
            file(target, "a.txt", "old a")
            file(target, "b.txt", "old b")
            val resolver = Recorder(ConflictChoice.SKIP, applyToAll = true)

            val status = engine.execute(copy(listOf(a, b, c)), resolver).toList().finalStatus()

            assertEquals(1, resolver.asked.size)
            assertEquals(listOf(a, b), (status as OperationStatus.Completed).summary.skipped)
            assertEquals("old b", textOf("b.txt"))
            assertEquals("new c", textOf("c.txt"))
        }

    @Test
    fun `without apply to all every clash is asked`() =
        runTest {
            val a = file(source, "a.txt", "new a")
            val b = file(source, "b.txt", "new b")
            file(target, "a.txt", "old a")
            file(target, "b.txt", "old b")
            val resolver = Recorder(ConflictChoice.SKIP)

            engine.execute(copy(listOf(a, b)), resolver).toList()

            assertEquals(listOf("a.txt", "b.txt"), resolver.asked.map { it.source.name })
        }

    @Test
    fun `a folder is never replaced, it is kept alongside`() =
        runTest {
            val incoming = dir(source, "photos")
            file(incoming, "1.jpg", "new photo")
            val existing = dir(target, "photos")
            file(existing, "keep.jpg", "old photo")

            val status = engine.execute(copy(listOf(incoming)), Recorder(ConflictChoice.REPLACE)).toList().finalStatus()

            assertTrue(status is OperationStatus.Completed, "was $status")
            assertEquals(setOf("photos", "photos (1)"), children(target).map { it.name }.toSet())
            assertEquals(listOf("keep.jpg"), children(existing).map { it.name })
        }

    @Test
    fun `no clash means no question`() =
        runTest {
            val incoming = file(source, "fresh.txt", "new")
            val resolver = Recorder(ConflictChoice.SKIP)

            val snapshots = engine.execute(copy(listOf(incoming)), resolver).toList()

            assertTrue(resolver.asked.isEmpty())
            assertTrue(snapshots.none { it.status is OperationStatus.AwaitingInput })
            assertEquals("new", textOf("fresh.txt"))
        }

    @Test
    fun `copying into the same folder duplicates without asking`() =
        runTest {
            val incoming = file(target, "notes.txt", "only")
            val resolver = Recorder(ConflictChoice.REPLACE)

            engine.execute(copy(listOf(incoming)), resolver).toList()

            assertTrue(resolver.asked.isEmpty())
            assertEquals(setOf("notes.txt", "notes (1).txt"), children(target).map { it.name }.toSet())
        }

    @Test
    fun `without a resolver ask keeps both as before`() =
        runTest {
            val incoming = file(source, "notes.txt", "new")
            file(target, "notes.txt", "old")

            val snapshots = engine.execute(copy(listOf(incoming))).toList()

            assertTrue(snapshots.none { it.status is OperationStatus.AwaitingInput })
            assertEquals(setOf("notes.txt", "notes (1).txt"), children(target).map { it.name }.toSet())
        }

    @Test
    fun `an explicit policy is never turned into a question`() =
        runTest {
            val incoming = file(source, "notes.txt", "new")
            file(target, "notes.txt", "old")
            val resolver = Recorder(ConflictChoice.REPLACE)

            engine.execute(copy(listOf(incoming), policy = CollisionPolicy.SKIP), resolver).toList()

            assertTrue(resolver.asked.isEmpty())
            assertEquals("old", textOf("notes.txt"))
        }
}
