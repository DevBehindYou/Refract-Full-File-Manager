package com.devbehindyou.atomicfilemanager.data.operations

import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalDao
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalEntity
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory journal with the same state rules as the Room queries (OperationJournal.kt). */
internal class FakeOperationJournal : OperationJournalDao {
    val rows = linkedMapOf<String, OperationJournalEntity>()
    val log = mutableListOf<Pair<String, OperationJournalState>>()

    private val unfinishedStates =
        setOf(OperationJournalState.QUEUED, OperationJournalState.RUNNING, OperationJournalState.PAUSED)

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

    override suspend fun unfinished() = rows.values.filter { it.state in unfinishedStates }

    override suspend fun markInterrupted(now: Long): Int {
        val ids = rows.values.filter { it.state in unfinishedStates }.map { it.id }
        ids.forEach { rows[it] = rows.getValue(it).copy(state = OperationJournalState.INTERRUPTED, updatedAt = now) }
        return ids.size
    }

    override suspend fun pruneFinishedBefore(before: Long) = 0
}
