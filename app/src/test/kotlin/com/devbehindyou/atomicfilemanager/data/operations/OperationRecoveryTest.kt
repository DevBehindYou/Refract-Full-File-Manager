package com.devbehindyou.atomicfilemanager.data.operations

import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalState
import com.devbehindyou.atomicfilemanager.data.database.room.toJournalEntry
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationSummary
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.InterruptedTransferCleanup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// runCurrent, not advanceUntilIdle: the queue and recovery live in backgroundScope, which advanceUntilIdle skips.
@OptIn(ExperimentalCoroutinesApi::class)
class OperationRecoveryTest {
    private val backend = InMemoryBackend()
    private val journal = FakeOperationJournal()
    private val started = mutableListOf<String>()

    private fun copyInto(
        id: String,
        destination: FileNodeId?,
        type: OperationType = OperationType.COPY,
    ) = FileOperation(
        id = OperationId(id),
        type = type,
        sources = listOf(FileNodeId.file("/s/$id")),
        destination = destination,
        options = OperationOptions(),
        createdAt = 0L,
    )

    private fun TestScope.recovery(): OperationRecovery {
        lateinit var recovery: OperationRecovery
        val queue =
            OperationQueue(
                engine = { operation, _ ->
                    flow {
                        started += operation.id.raw
                        val done = OperationSummary(emptyList(), emptyList(), emptyList(), null)
                        emit(OperationSnapshot(operation, OperationStatus.Completed(done)))
                    }
                },
                journal = journal,
                scope = backgroundScope,
                clock = { 7_000L },
                startup = { recovery.sweep() },
            )
        recovery = OperationRecovery(journal, queue, { backend }, backgroundScope, clock = { 7_000L })
        return recovery
    }

    @Test
    fun `unfinished work is offered once and its destination is tidied`() =
        runTest {
            val target = backend.rootId
            val staging = InterruptedTransferCleanup.stagingName()
            (backend.openOutput(target, staging, null) as FileResult.Success).value.run {
                stream().use { it.write(1) }
                sync()
            }
            journal.upsert(copyInto("copy", target).toJournalEntry(OperationJournalState.RUNNING))
            journal.upsert(copyInto("delete", null, OperationType.DELETE).toJournalEntry(OperationJournalState.QUEUED))
            journal.upsert(copyInto("done", target).toJournalEntry(OperationJournalState.COMPLETED))

            val recovery = recovery()
            runCurrent()

            val pending = recovery.pending.value
            assertEquals(listOf("copy", "delete"), pending?.entries?.map { it.id })
            assertEquals(1, pending?.cleanup?.removedPartials)
            assertEquals(OperationJournalState.INTERRUPTED, journal.rows.getValue("copy").state)
            assertEquals(OperationJournalState.COMPLETED, journal.rows.getValue("done").state)
        }

    @Test
    fun `resume runs them again through the queue`() =
        runTest {
            journal.upsert(copyInto("copy", backend.rootId).toJournalEntry(OperationJournalState.RUNNING))
            val recovery = recovery()
            runCurrent()

            recovery.resumeAll()
            runCurrent()

            assertEquals(listOf("copy"), started)
            assertNull(recovery.pending.value)
            assertEquals(OperationJournalState.COMPLETED, journal.rows.getValue("copy").state)
        }

    @Test
    fun `discard marks them cancelled so Operations stops offering resume`() =
        runTest {
            journal.upsert(copyInto("copy", backend.rootId).toJournalEntry(OperationJournalState.PAUSED))
            val recovery = recovery()
            runCurrent()

            recovery.discardAll()
            runCurrent()

            assertEquals(emptyList<String>(), started)
            assertEquals(OperationJournalState.CANCELLED, journal.rows.getValue("copy").state)
            assertEquals("Discarded after restart", journal.rows.getValue("copy").errorMessage)
        }

    @Test
    fun `a clean start offers nothing`() =
        runTest {
            journal.upsert(copyInto("done", backend.rootId).toJournalEntry(OperationJournalState.COMPLETED))
            val recovery = recovery()
            runCurrent()

            assertNull(recovery.pending.value)
        }
}
