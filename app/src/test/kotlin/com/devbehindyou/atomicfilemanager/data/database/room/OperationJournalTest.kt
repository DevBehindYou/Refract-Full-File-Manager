package com.devbehindyou.atomicfilemanager.data.database.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperationJournalTest {
    private lateinit var database: AtomicDatabase
    private lateinit var dao: OperationJournalDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AtomicDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.operationJournal()
    }

    @After
    fun tearDown() = database.close()

    private fun operation(
        id: String,
        createdAt: Long = 1_000L,
    ) = FileOperation(
        id = OperationId(id),
        type = OperationType.COPY,
        // Paths with spaces, a newline and a SAF URI with an encoded colon all survive the round trip.
        sources =
            listOf(
                FileNodeId.file("/storage/emulated/0/Download/a b.pdf"),
                FileNodeId.file("/storage/emulated/0/odd\nname.txt"),
                FileNodeId.saf("content://com.android.externalstorage.documents/tree/primary:Docs"),
            ),
        destination = FileNodeId.file("/storage/emulated/0/Documents"),
        options = OperationOptions(collisionPolicy = CollisionPolicy.KEEP_BOTH, useTrash = false),
        createdAt = createdAt,
    )

    @Test
    fun operationRoundTripsThroughTheJournal() =
        runBlocking {
            val original = operation("op-1")
            dao.upsert(original.toJournalEntry())

            assertEquals(original, dao.get("op-1")!!.toFileOperation())
            assertNull(dao.get("missing"))
        }

    @Test
    fun progressUpdatesAndRecentOrdering() =
        runBlocking {
            dao.upsert(operation("old").toJournalEntry(now = 1_000L))
            dao.upsert(operation("new").toJournalEntry(now = 2_000L))
            dao.updateProgress("old", OperationJournalState.RUNNING, 3, 10, 300L, 1_000L, null, updatedAt = 3_000L)

            val recent = dao.observeRecent(limit = 10).first()
            assertEquals(listOf("old", "new"), recent.map { it.id })
            assertEquals(OperationJournalState.RUNNING, recent.first().state)
            assertEquals(300L, recent.first().bytesDone)
        }

    @Test
    fun startUpMarksQueuedAndRunningAsInterruptedButKeepsPaused() =
        runBlocking {
            dao.upsert(operation("queued").toJournalEntry(OperationJournalState.QUEUED))
            dao.upsert(operation("running").toJournalEntry(OperationJournalState.RUNNING))
            dao.upsert(operation("paused").toJournalEntry(OperationJournalState.PAUSED))
            dao.upsert(operation("done").toJournalEntry(OperationJournalState.COMPLETED))

            assertEquals(2, dao.markInterrupted(now = 5_000L))
            assertEquals(listOf("paused"), dao.unfinished().map { it.id })
            assertEquals(OperationJournalState.INTERRUPTED, dao.get("running")!!.state)
        }

    @Test
    fun pruningRemovesOnlyOldFinishedRows() =
        runBlocking {
            dao.upsert(operation("old-done").toJournalEntry(OperationJournalState.COMPLETED, now = 1_000L))
            dao.upsert(operation("old-paused").toJournalEntry(OperationJournalState.PAUSED, now = 1_000L))
            dao.upsert(operation("new-done").toJournalEntry(OperationJournalState.COMPLETED, now = 9_000L))

            assertEquals(1, dao.pruneFinishedBefore(5_000L))
            assertNull(dao.get("old-done"))
            assertEquals(
                listOf("old-paused", "new-done"),
                dao.observeRecent(10).first().map { it.id }.sorted().reversed(),
            )
        }

    @Test
    fun idListConverterKeepsEmptyAndSingleLists() {
        val converters = AtomicConverters()
        assertEquals(emptyList<String>(), converters.toIds(converters.fromIds(emptyList())))
        assertEquals(listOf("file:/a"), converters.toIds(converters.fromIds(listOf("file:/a"))))
    }
}
