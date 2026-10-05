package com.devbehindyou.atomicfilemanager.data.database.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.TrashEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a real version 1 database from the committed `app/schemas/.../1.json` statements, then
 * opens it with the current code. Room validates every table and index against version 2 after
 * the migration, so a mistake in MIGRATION_1_2's SQL fails here, not on a user's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AtomicDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Before
    fun createVersion1() {
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `operation_journal` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`sources` TEXT NOT NULL, `destination` TEXT, `collisionPolicy` TEXT NOT NULL, " +
                "`deleteSourceAfterCopy` INTEGER NOT NULL, `preserveTimestamps` INTEGER NOT NULL, " +
                "`useTrash` INTEGER NOT NULL, `compressionLevel` INTEGER, `state` TEXT NOT NULL, " +
                "`itemsDone` INTEGER NOT NULL, `itemsTotal` INTEGER NOT NULL, `bytesDone` INTEGER NOT NULL, " +
                "`bytesTotal` INTEGER NOT NULL, `errorMessage` TEXT, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_operation_journal_state` ON `operation_journal` (`state`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_operation_journal_updatedAt` ON `operation_journal` (`updatedAt`)",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, 'bfd19b29ef28352b4b70182e5b97ea89')",
        )
        db.execSQL(
            "INSERT INTO operation_journal VALUES ('kept', 'COPY', 'file:/a', 'file:/b', 'ASK', 0, 1, 1, NULL, " +
                "'COMPLETED', 1, 1, 10, 10, NULL, 1000, 2000)",
        )
        db.version = 1
        db.close()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun version1MigratesToVersion2AndKeepsTheJournal() =
        runBlocking {
            val database =
                Room
                    .databaseBuilder(context, AtomicDatabase::class.java, name)
                    .addMigrations(AtomicDatabase.MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build()
            try {
                assertEquals("COMPLETED", database.operationJournal().get("kept")?.state?.name)

                val entry =
                    TrashEntry(
                        id = "t1",
                        name = "notes.txt",
                        isDirectory = false,
                        size = 5,
                        originalParent = FileNodeId.file("/storage/emulated/0/Docs"),
                        trashedId = FileNodeId.file("/storage/emulated/0/.Atomic File Manager/Trash/t1/notes.txt"),
                        holderId = FileNodeId.file("/storage/emulated/0/.Atomic File Manager/Trash/t1"),
                        volumeRoot = FileNodeId.file("/storage/emulated/0"),
                        deletedAt = 3_000L,
                        operationId = "op",
                    )
                val store = RoomTrashStore(database.trashEntries())
                store.insert(entry)
                assertEquals(entry, store.byTrashedId(entry.trashedId))
                assertEquals(listOf(entry), store.byOperation("op"))
                assertEquals(listOf(entry), store.deletedBefore(4_000L))
            } finally {
                database.close()
            }
        }
}
