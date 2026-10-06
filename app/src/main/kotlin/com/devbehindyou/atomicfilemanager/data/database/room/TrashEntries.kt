package com.devbehindyou.atomicfilemanager.data.database.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.TrashEntry
import com.devbehindyou.atomicfilemanager.domain.repository.TrashStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One Trash item (ALL_IN_ONE_PLAN.md 1.1, table `trash_entry`). Node ids are stored raw. */
@Entity(
    tableName = "trash_entry",
    indices = [Index("deletedAt"), Index("operationId"), Index(value = ["trashedId"], unique = true)],
)
data class TrashEntryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val originalParent: String,
    val trashedId: String,
    val holderId: String,
    val volumeRoot: String,
    val deletedAt: Long,
    val operationId: String,
)

@Dao
interface TrashEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TrashEntryEntity)

    @Query("DELETE FROM trash_entry WHERE id = :id")
    suspend fun delete(id: String)

    /** Newest first, for the Trash screen. */
    @Query("SELECT * FROM trash_entry ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashEntryEntity>>

    @Query("SELECT * FROM trash_entry WHERE trashedId = :trashedId")
    suspend fun byTrashedId(trashedId: String): TrashEntryEntity?

    @Query("SELECT * FROM trash_entry WHERE operationId = :operationId")
    suspend fun byOperation(operationId: String): List<TrashEntryEntity>

    @Query("SELECT * FROM trash_entry WHERE deletedAt < :cutoff")
    suspend fun deletedBefore(cutoff: Long): List<TrashEntryEntity>
}

/** [TrashStore] on Room. */
class RoomTrashStore(private val dao: TrashEntryDao) : TrashStore {
    override fun observeAll(): Flow<List<TrashEntry>> = dao.observeAll().map { rows -> rows.map { it.toEntry() } }

    override suspend fun insert(entry: TrashEntry) = dao.insert(entry.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun byTrashedId(trashedId: FileNodeId): TrashEntry? = dao.byTrashedId(trashedId.raw)?.toEntry()

    override suspend fun byOperation(operationId: String): List<TrashEntry> =
        dao.byOperation(operationId).map { it.toEntry() }

    override suspend fun deletedBefore(cutoff: Long): List<TrashEntry> = dao.deletedBefore(cutoff).map { it.toEntry() }
}

internal fun TrashEntry.toEntity() =
    TrashEntryEntity(
        id = id,
        name = name,
        isDirectory = isDirectory,
        size = size,
        originalParent = originalParent.raw,
        trashedId = trashedId.raw,
        holderId = holderId.raw,
        volumeRoot = volumeRoot.raw,
        deletedAt = deletedAt,
        operationId = operationId,
    )

internal fun TrashEntryEntity.toEntry() =
    TrashEntry(
        id = id,
        name = name,
        isDirectory = isDirectory,
        size = size,
        originalParent = FileNodeId(originalParent),
        trashedId = FileNodeId(trashedId),
        holderId = FileNodeId(holderId),
        volumeRoot = FileNodeId(volumeRoot),
        deletedAt = deletedAt,
        operationId = operationId,
    )
