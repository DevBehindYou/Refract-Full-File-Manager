package com.devbehindyou.atomicfilemanager.domain.repository

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.TrashEntry
import kotlinx.coroutines.flow.Flow

/** Records of what is in the Trash; the files themselves are moved by `TrashManager`. */
interface TrashStore {
    fun observeAll(): Flow<List<TrashEntry>>

    suspend fun insert(entry: TrashEntry)

    suspend fun delete(id: String)

    suspend fun byTrashedId(trashedId: FileNodeId): TrashEntry?

    suspend fun byOperation(operationId: String): List<TrashEntry>

    suspend fun deletedBefore(cutoff: Long): List<TrashEntry>
}
