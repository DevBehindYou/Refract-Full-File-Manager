package com.devbehindyou.refract.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.refract.domain.model.HiddenItem
import com.devbehindyou.refract.domain.model.HideJournalEntry
import com.devbehindyou.refract.domain.model.HideMode
import com.devbehindyou.refract.domain.model.JournalState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Phase 17 Database Integration Tests for [HiddenFilesDatabaseHelper].
 *
 * Covers:
 * 1. Schema validation for both hidden_items and hide_journal tables.
 * 2. HiddenItem CRUD lifecycle.
 * 3. HideJournalEntry CRUD and state transition queries.
 * 4. [getUnfinishedJournalEntries] only returns PREPARING/HEADER_TRANSFORMED/RENAMED/ROLLBACK_REQUIRED,
 *    not COMMITTED or RESTORED — this is the process-death recovery gate.
 * 5. [CONFLICT_REPLACE] semantics on insert.
 * 6. onUpgrade no-op at v1.
 *
 * Terminology note: HideMode.PRIVATE_STORAGE moves to context.filesDir (app sandbox),
 * which is OS isolation — NOT biometric protection or encryption-at-rest.
 */
@RunWith(RobolectricTestRunner::class)
class HiddenFilesDatabaseHelperTest {
    private lateinit var context: Context
    private lateinit var dbHelper: HiddenFilesDatabaseHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // null dbName = in-memory SQLite database (cross-platform; avoids ':' colon in path on Windows)
        dbHelper = HiddenFilesDatabaseHelper(context, null)
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    private fun makeHiddenItem(
        id: String = UUID.randomUUID().toString(),
        mode: HideMode = HideMode.FAST_OBSCURE,
    ) = HiddenItem(
        id = id,
        originalLocation = "/storage/emulated/0/DCIM/photo.jpg",
        currentLocation = "/storage/emulated/0/DCIM/.RefractHidden/photo.jpg",
        originalName = "photo.jpg",
        size = 1_024_000L,
        mode = mode,
        hiddenAt = System.currentTimeMillis(),
    )

    private fun makeJournalEntry(
        operationId: String = UUID.randomUUID().toString(),
        state: JournalState = JournalState.PREPARING,
        mode: HideMode = HideMode.FAST_OBSCURE,
    ) = HideJournalEntry(
        operationId = operationId,
        originalPath = "/storage/emulated/0/DCIM/photo.jpg",
        currentPath = "/storage/emulated/0/DCIM/.RefractHidden/photo.jpg",
        originalName = "photo.jpg",
        state = state,
        mode = mode,
        timestamp = System.currentTimeMillis(),
    )

    @Test
    fun `database version is 1`() {
        assertEquals(1, HiddenFilesDatabaseHelper.DATABASE_VERSION)
    }

    @Test
    fun `schema creates hidden_items table with expected columns`() {
        val db = dbHelper.readableDatabase
        val cursor =
            db.rawQuery(
                "PRAGMA table_info(${HiddenFilesDatabaseHelper.TABLE_HIDDEN_ITEMS})",
                null,
            )
        val columns = mutableListOf<String>()
        cursor.use {
            val nameIndex = it.getColumnIndexOrThrow("name")
            while (it.moveToNext()) columns.add(it.getString(nameIndex))
        }
        assertTrue(columns.contains(HiddenFilesDatabaseHelper.COL_ID))
        assertTrue(columns.contains(HiddenFilesDatabaseHelper.COL_ORIGINAL_LOC))
        assertTrue(columns.contains(HiddenFilesDatabaseHelper.COL_CURRENT_LOC))
        assertTrue(columns.contains(HiddenFilesDatabaseHelper.COL_ORIGINAL_NAME))
        assertTrue(columns.contains(HiddenFilesDatabaseHelper.COL_SIZE))
        assertTrue(columns.contains(HiddenFilesDatabaseHelper.COL_MODE))
        assertTrue(columns.contains(HiddenFilesDatabaseHelper.COL_HIDDEN_AT))
    }

    @Test
    fun `schema creates hide_journal table with expected columns`() {
        val db = dbHelper.readableDatabase
        val cursor =
            db.rawQuery(
                "PRAGMA table_info(${HiddenFilesDatabaseHelper.TABLE_JOURNAL})",
                null,
            )
        val columns = mutableListOf<String>()
        cursor.use {
            val nameIndex = it.getColumnIndexOrThrow("name")
            while (it.moveToNext()) columns.add(it.getString(nameIndex))
        }
        assertTrue(columns.contains(HiddenFilesDatabaseHelper.COL_J_OP_ID))
        assertTrue(columns.contains(HiddenFilesDatabaseHelper.COL_J_STATE))
        assertTrue(columns.contains(HiddenFilesDatabaseHelper.COL_J_MODE))
    }

    @Test
    fun hiddenItem_insertAndRetrieve_roundTrip() {
        val item = makeHiddenItem("item-1", HideMode.GALLERY)
        val inserted = dbHelper.insertHiddenItem(item)
        assertTrue(inserted)

        val all = dbHelper.getAllHiddenItems()
        assertEquals(1, all.size)
        assertEquals("item-1", all[0].id)
        assertEquals(HideMode.GALLERY, all[0].mode)
        assertEquals("photo.jpg", all[0].originalName)
        assertEquals(1_024_000L, all[0].size)
    }

    @Test
    fun hiddenItem_delete_removesItem() {
        dbHelper.insertHiddenItem(makeHiddenItem("del-1"))
        assertEquals(1, dbHelper.getAllHiddenItems().size)

        dbHelper.deleteHiddenItem("del-1")
        assertEquals(0, dbHelper.getAllHiddenItems().size)
    }

    @Test
    fun hiddenItem_insert_withConflictReplace_updatesExistingRow() {
        // CONFLICT_REPLACE means inserting same PK overwrites cleanly
        val item = makeHiddenItem("dup-id", HideMode.FAST_OBSCURE)
        dbHelper.insertHiddenItem(item)
        val updated = item.copy(mode = HideMode.PRIVATE_STORAGE, size = 999L)
        val result = dbHelper.insertHiddenItem(updated)
        assertTrue(result)

        val all = dbHelper.getAllHiddenItems()
        assertEquals(1, all.size) // Not 2 — replaced
        assertEquals(HideMode.PRIVATE_STORAGE, all[0].mode)
        assertEquals(999L, all[0].size)
    }

    @Test
    fun hiddenItem_HideMode_FAST_OBSCURE_persists_and_reads_correctly() {
        dbHelper.insertHiddenItem(makeHiddenItem("obscure-1", HideMode.FAST_OBSCURE))
        assertEquals(HideMode.FAST_OBSCURE, dbHelper.getAllHiddenItems()[0].mode)
    }

    @Test
    fun hiddenItem_HideMode_PRIVATE_STORAGE_persists_and_reads_correctly() {
        // Confirms PRIVATE_STORAGE (app-sandbox isolation) round-trips via SQLite
        dbHelper.insertHiddenItem(makeHiddenItem("private-1", HideMode.PRIVATE_STORAGE))
        assertEquals(HideMode.PRIVATE_STORAGE, dbHelper.getAllHiddenItems()[0].mode)
    }

    @Test
    fun journalEntry_logAndRetrieve_roundTrip() {
        val entry = makeJournalEntry("op-1", JournalState.PREPARING)
        val logged = dbHelper.logJournal(entry)
        assertTrue(logged)

        val unfinished = dbHelper.getUnfinishedJournalEntries()
        assertEquals(1, unfinished.size)
        assertEquals("op-1", unfinished[0].operationId)
        assertEquals(JournalState.PREPARING, unfinished[0].state)
    }

    @Test
    fun journalEntry_COMMITTED_isNotReturnedByGetUnfinished() {
        dbHelper.logJournal(makeJournalEntry("op-committed", JournalState.COMMITTED))
        val unfinished = dbHelper.getUnfinishedJournalEntries()
        assertTrue(
            "COMMITTED journal entries should not be returned by getUnfinishedJournalEntries",
            unfinished.none { it.operationId == "op-committed" },
        )
    }

    @Test
    fun journalEntry_RESTORED_isNotReturnedByGetUnfinished() {
        dbHelper.logJournal(makeJournalEntry("op-restored", JournalState.RESTORED))
        val unfinished = dbHelper.getUnfinishedJournalEntries()
        assertTrue(
            "RESTORED journal entries should not be returned by getUnfinishedJournalEntries",
            unfinished.none { it.operationId == "op-restored" },
        )
    }

    @Test
    fun journalEntry_pendingStates_areReturnedByGetUnfinished() {
        val pendingStates =
            listOf(
                JournalState.PREPARING,
                JournalState.HEADER_TRANSFORMED,
                JournalState.RENAMED,
                JournalState.ROLLBACK_REQUIRED,
            )
        pendingStates.forEachIndexed { i, state ->
            dbHelper.logJournal(makeJournalEntry("op-$i", state))
        }

        val unfinished = dbHelper.getUnfinishedJournalEntries()
        assertEquals(
            "All 4 pending states should be returned for process-death recovery",
            pendingStates.size,
            unfinished.size,
        )
    }

    @Test
    fun journalEntry_clearJournal_removesEntry() {
        dbHelper.logJournal(makeJournalEntry("op-clear", JournalState.PREPARING))
        assertEquals(1, dbHelper.getUnfinishedJournalEntries().size)

        dbHelper.clearJournal("op-clear")
        assertEquals(0, dbHelper.getUnfinishedJournalEntries().size)
    }

    @Test
    fun onUpgrade_noOp_does_not_crash_at_v1() {
        val db = dbHelper.writableDatabase
        dbHelper.onUpgrade(db, 1, 1) // must not throw
    }
}
