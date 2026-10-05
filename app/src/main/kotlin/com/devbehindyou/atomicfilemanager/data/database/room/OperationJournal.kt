package com.devbehindyou.atomicfilemanager.data.database.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import kotlinx.coroutines.flow.Flow

/**
 * Where an operation got to. QUEUED and RUNNING rows found at start-up belong to a process that
 * died mid-operation and are moved to INTERRUPTED so the Operations screen can offer Resume
 * (ALL_IN_ONE_PLAN.md Phase 0.1).
 */
enum class OperationJournalState {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    PARTIAL,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    ;

    val isFinished: Boolean get() = this in setOf(COMPLETED, PARTIAL, FAILED, CANCELLED)
}

/** One requested file operation and its latest progress. */
@Entity(tableName = "operation_journal", indices = [Index("state"), Index("updatedAt")])
data class OperationJournalEntity(
    @PrimaryKey val id: String,
    val type: OperationType,
    val sources: List<String>,
    val destination: String?,
    val collisionPolicy: CollisionPolicy,
    val deleteSourceAfterCopy: Boolean,
    val preserveTimestamps: Boolean,
    val useTrash: Boolean,
    val compressionLevel: Int?,
    val state: OperationJournalState,
    val itemsDone: Int = 0,
    val itemsTotal: Int = 0,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface OperationJournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: OperationJournalEntity)

    @Query("SELECT * FROM operation_journal WHERE id = :id")
    suspend fun get(id: String): OperationJournalEntity?

    @Query(
        "UPDATE operation_journal SET state = :state, itemsDone = :itemsDone, itemsTotal = :itemsTotal, " +
            "bytesDone = :bytesDone, bytesTotal = :bytesTotal, errorMessage = :errorMessage, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateProgress(
        id: String,
        state: OperationJournalState,
        itemsDone: Int,
        itemsTotal: Int,
        bytesDone: Long,
        bytesTotal: Long,
        errorMessage: String?,
        updatedAt: Long,
    )

    /** Newest first, for the Operations screen. */
    @Query("SELECT * FROM operation_journal ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<OperationJournalEntity>>

    @Query("SELECT * FROM operation_journal WHERE state IN ('QUEUED', 'RUNNING', 'PAUSED') ORDER BY createdAt")
    suspend fun unfinished(): List<OperationJournalEntity>

    /** Run once at start-up: nothing can still be running in a fresh process. */
    @Query("UPDATE operation_journal SET state = 'INTERRUPTED', updatedAt = :now WHERE state IN ('QUEUED', 'RUNNING')")
    suspend fun markInterrupted(now: Long): Int

    @Query(
        "DELETE FROM operation_journal WHERE updatedAt < :before " +
            "AND state IN ('COMPLETED', 'PARTIAL', 'FAILED', 'CANCELLED')",
    )
    suspend fun pruneFinishedBefore(before: Long): Int
}

/** A journal row for a newly requested [operation]. */
fun FileOperation.toJournalEntry(
    state: OperationJournalState = OperationJournalState.QUEUED,
    now: Long = createdAt,
): OperationJournalEntity =
    OperationJournalEntity(
        id = id.raw,
        type = type,
        sources = sources.map { it.raw },
        destination = destination?.raw,
        collisionPolicy = options.collisionPolicy,
        deleteSourceAfterCopy = options.deleteSourceAfterCopy,
        preserveTimestamps = options.preserveTimestamps,
        useTrash = options.useTrash,
        compressionLevel = options.compressionLevel,
        state = state,
        createdAt = createdAt,
        updatedAt = now,
    )

/** The operation a journal row describes, for resuming after a restart. */
fun OperationJournalEntity.toFileOperation(): FileOperation =
    FileOperation(
        id = OperationId(id),
        type = type,
        sources = sources.map(::FileNodeId),
        destination = destination?.let(::FileNodeId),
        options =
            OperationOptions(
                collisionPolicy = collisionPolicy,
                deleteSourceAfterCopy = deleteSourceAfterCopy,
                preserveTimestamps = preserveTimestamps,
                useTrash = useTrash,
                compressionLevel = compressionLevel,
            ),
        createdAt = createdAt,
    )
