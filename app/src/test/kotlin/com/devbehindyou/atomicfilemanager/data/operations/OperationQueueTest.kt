package com.devbehindyou.atomicfilemanager.data.operations

import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalDao
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalEntity
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalState
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.Conflict
import com.devbehindyou.atomicfilemanager.domain.model.ConflictChoice
import com.devbehindyou.atomicfilemanager.domain.model.ConflictDecision
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationProgress
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationSummary
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationQueueTest {
    private class FakeJournal : OperationJournalDao {
        val rows = linkedMapOf<String, OperationJournalEntity>()
        val log = mutableListOf<Pair<String, OperationJournalState>>()

        override suspend fun upsert(entry: OperationJournalEntity) {
            rows[entry.id] = entry
            log += entry.id to entry.state
        }

        override suspend fun get(id: String) = rows[id]

        override suspend fun updateProgress(
            id: String,
            state: OperationJournalState,
            itemsDone: Int,
            itemsTotal: Int,
            bytesDone: Long,
            bytesTotal: Long,
            errorMessage: String?,
            updatedAt: Long,
        ) {
            rows[id] = rows.getValue(id).copy(state = state, itemsDone = itemsDone, errorMessage = errorMessage)
            log += id to state
        }

        override fun observeRecent(limit: Int): Flow<List<OperationJournalEntity>> =
            MutableStateFlow(rows.values.toList()).map { it.take(limit) }

        override suspend fun unfinished() = rows.values.filter { !it.state.isFinished }

        override suspend fun markInterrupted(now: Long) = 0

        override suspend fun pruneFinishedBefore(before: Long) = 0
    }

    private fun op(id: String) =
        FileOperation(
            id = OperationId(id),
            type = OperationType.DELETE,
            sources = listOf(FileNodeId.file("/s/$id")),
            destination = null,
            options = OperationOptions(),
            createdAt = 0L,
        )

    private val done = OperationSummary(emptyList(), emptyList(), emptyList(), null)

    private fun progress(items: Int) = OperationProgress(items, 2, 0L, 0L, null, 0L, null)

    /** An engine that runs each operation for 100 ms of virtual time and records start order. */
    private fun TestScope.queue(
        started: MutableList<String>,
        journal: FakeJournal? = FakeJournal(),
        failing: Set<String> = emptySet(),
    ): OperationQueue =
        OperationQueue(
            engine = { operation, _ ->
                flow {
                    started += operation.id.raw
                    emit(OperationSnapshot(operation, OperationStatus.Running(progress(1))))
                    delay(100)
                    if (operation.id.raw in failing) error("disk gone")
                    emit(OperationSnapshot(operation, OperationStatus.Completed(done)))
                }
            },
            journal = journal,
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
        )

    @Test
    fun `a second operation waits for the first instead of cancelling it`() =
        runTest {
            val started = mutableListOf<String>()
            val queue = queue(started)

            val first = async { queue.runAndAwait(op("a")) }
            val second = async { queue.runAndAwait(op("b")) }
            runCurrent()
            assertEquals(listOf("a"), started)
            assertEquals(listOf("b"), queue.queued.value.map { it.id.raw })

            assertTrue(first.await().status is OperationStatus.Completed)
            assertTrue(second.await().status is OperationStatus.Completed)
            assertEquals(listOf("a", "b"), started)
            assertNull(queue.active.value)
        }

    @Test
    fun `the journal sees queued, running and completed in order`() =
        runTest {
            val journal = FakeJournal()
            val queue = queue(mutableListOf(), journal)

            queue.runAndAwait(op("a"))
            advanceUntilIdle()

            assertEquals(
                listOf(OperationJournalState.QUEUED, OperationJournalState.RUNNING, OperationJournalState.COMPLETED),
                journal.log.filter { it.first == "a" }.map { it.second }.distinct(),
            )
            assertEquals(OperationJournalState.COMPLETED, journal.rows.getValue("a").state)
        }

    @Test
    fun `a failing operation is recorded and the next one still runs`() =
        runTest {
            val started = mutableListOf<String>()
            val journal = FakeJournal()
            val queue = queue(started, journal, failing = setOf("a"))

            val failed = async { queue.runAndAwait(op("a")) }
            val next = async { queue.runAndAwait(op("b")) }

            assertTrue(failed.await().status is OperationStatus.Failed)
            assertTrue(next.await().status is OperationStatus.Completed)
            advanceUntilIdle()
            assertEquals(OperationJournalState.FAILED, journal.rows.getValue("a").state)
        }

    @Test
    fun `cancel stops the running operation and drops a waiting one`() =
        runTest {
            val started = mutableListOf<String>()
            val queue = queue(started)

            val running = async { queue.runAndAwait(op("a")) }
            val waiting = async { queue.runAndAwait(op("b")) }
            runCurrent()
            queue.cancel(OperationId("b"))
            queue.cancel(OperationId("a"))

            assertEquals(OperationStatus.Cancelled, running.await().status)
            assertEquals(OperationStatus.Cancelled, waiting.await().status)
            assertEquals(listOf("a"), started)
        }

    @Test
    fun `works without a journal`() =
        runTest {
            val queue = queue(mutableListOf(), journal = null)
            assertTrue(queue.runAndAwait(op("a")).status is OperationStatus.Completed)
        }

    private fun node(name: String) =
        FileNode(
            id = FileNodeId.file("/s/$name"),
            name = name,
            displayName = name,
            mimeType = null,
            size = 1L,
            modifiedAt = 0L,
            isDirectory = false,
            isHidden = false,
            parentId = null,
            storageType = StorageType.INTERNAL_SHARED,
            access = AccessFlags.FULL,
            childCount = null,
            extras = null,
        )

    /** An engine that hits one name clash and completes with whatever it was told. */
    private fun TestScope.askingQueue(answers: MutableList<ConflictDecision>): OperationQueue =
        OperationQueue(
            engine = { operation, resolver ->
                flow {
                    val conflict = Conflict(node("a.txt"), node("a.txt"))
                    emit(OperationSnapshot(operation, OperationStatus.AwaitingInput(conflict)))
                    answers += resolver.resolve(operation, conflict)
                    emit(OperationSnapshot(operation, OperationStatus.Completed(done)))
                }
            },
            journal = FakeJournal(),
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
        )

    @Test
    fun `a name clash waits for an answer and then carries on`() =
        runTest {
            val answers = mutableListOf<ConflictDecision>()
            val queue = askingQueue(answers)

            val result = async { queue.runAndAwait(op("a")) }
            runCurrent()
            val pending = queue.pendingConflict.value
            assertEquals("a", pending?.operation?.id?.raw)
            assertTrue(queue.active.value?.status is OperationStatus.AwaitingInput)
            assertTrue(answers.isEmpty())

            queue.resolveConflict(checkNotNull(pending), ConflictDecision(ConflictChoice.SKIP, applyToAll = true))

            assertTrue(result.await().status is OperationStatus.Completed)
            assertEquals(listOf(ConflictDecision(ConflictChoice.SKIP, applyToAll = true)), answers)
            assertNull(queue.pendingConflict.value)
        }

    @Test
    fun `a stale answer is ignored`() =
        runTest {
            val answers = mutableListOf<ConflictDecision>()
            val queue = askingQueue(answers)

            val result = async { queue.runAndAwait(op("a")) }
            runCurrent()
            val stale = PendingConflict(op("a"), Conflict(node("a.txt"), node("a.txt")))
            queue.resolveConflict(stale, ConflictDecision(ConflictChoice.REPLACE))
            runCurrent()

            assertTrue(answers.isEmpty())
            assertTrue(queue.pendingConflict.value != null)
            queue.cancel(OperationId("a"))
            result.await()
        }

    @Test
    fun `cancelling while waiting for an answer clears the question`() =
        runTest {
            val queue = askingQueue(mutableListOf())

            val result = async { queue.runAndAwait(op("a")) }
            runCurrent()
            queue.cancel(OperationId("a"))

            assertEquals(OperationStatus.Cancelled, result.await().status)
            assertNull(queue.pendingConflict.value)
        }
}
