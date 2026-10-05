package com.devbehindyou.atomicfilemanager.data.operations

import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalDao
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalEntity
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalState
import com.devbehindyou.atomicfilemanager.data.database.room.toFileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.CleanupReport
import com.devbehindyou.atomicfilemanager.domain.usecase.InterruptedTransferCleanup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Operations a previous process left unfinished, and what start-up cleaned up after them. */
data class RecoveredOperations(
    val entries: List<OperationJournalEntity>,
    val cleanup: CleanupReport,
)

/**
 * Start-up half of the durable queue (ALL_IN_ONE_PLAN.md 0.1): finds operations that were queued,
 * running or waiting when the process died, marks them INTERRUPTED, tidies their destination
 * folders and offers them once as "Resume or discard". Choosing later keeps them in Operations,
 * where Resume stays available.
 */
class OperationRecovery(
    private val journal: OperationJournalDao,
    private val queue: OperationQueue,
    private val backendFor: (FileNodeId) -> StorageBackend,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _pending = MutableStateFlow<RecoveredOperations?>(null)

    /** Set once after start-up when something was interrupted; null after the user chose. */
    val pending: StateFlow<RecoveredOperations?> = _pending.asStateFlow()

    /** Run once per process, before anything new is enqueued. */
    suspend fun sweep() {
        val unfinished = journal.unfinished()
        journal.markInterrupted(clock())
        if (unfinished.isEmpty()) return
        var cleanup = CleanupReport()
        for (folder in unfinished.mapNotNull { it.destinationFolder() }.distinct()) {
            cleanup +=
                try {
                    InterruptedTransferCleanup.clean(folder, backendFor(folder))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A folder that can't be read (card removed, access revoked) is left as it is.
                    CleanupReport()
                }
        }
        _pending.value =
            RecoveredOperations(unfinished.map { it.copy(state = OperationJournalState.INTERRUPTED) }, cleanup)
    }

    /** Runs every interrupted operation again from the start; name clashes ask as usual. */
    fun resumeAll() {
        val recovered = _pending.value ?: return
        _pending.value = null
        recovered.entries.forEach { queue.enqueue(it.toFileOperation()) }
    }

    /** Marks them cancelled so they no longer offer Resume. Files already copied stay where they are. */
    fun discardAll() {
        val recovered = _pending.value ?: return
        _pending.value = null
        val now = clock()
        scope.launch {
            recovered.entries.forEach {
                journal.updateProgress(
                    id = it.id,
                    state = OperationJournalState.CANCELLED,
                    itemsDone = it.itemsDone,
                    itemsTotal = it.itemsTotal,
                    bytesDone = it.bytesDone,
                    bytesTotal = it.bytesTotal,
                    errorMessage = "Discarded after restart",
                    updatedAt = now,
                )
            }
        }
    }

    /** Hides the prompt; the operations stay INTERRUPTED in Operations with Resume. */
    fun later() {
        _pending.value = null
    }

    private fun OperationJournalEntity.destinationFolder(): FileNodeId? =
        when (type) {
            OperationType.DELETE, OperationType.RENAME -> null
            else -> destination?.let(::FileNodeId)
        }
}
