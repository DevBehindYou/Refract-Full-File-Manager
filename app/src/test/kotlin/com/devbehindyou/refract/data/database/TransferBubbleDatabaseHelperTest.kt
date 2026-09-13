package com.devbehindyou.refract.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.refract.domain.model.AccessFlags
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.StorageType
import com.devbehindyou.refract.domain.model.TransferBubble
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Phase 17 Database Integration Tests for [TransferBubbleDatabaseHelper].
 *
 * These tests run on the JVM via Robolectric and verify:
 * 1. FK ON DELETE CASCADE is actually enforced (requires PRAGMA foreign_keys=ON via onConfigure).
 * 2. Database schema creates expected tables and columns.
 * 3. Bubble + item lifecycle: insert, query, delete.
 * 4. onUpgrade no-op is documented and version is 1.
 *
 * They do NOT replace device testing for SQLite PRAGMA behaviour on real firmware,
 * but do catch regressions in schema or connection configuration.
 */
@RunWith(RobolectricTestRunner::class)
class TransferBubbleDatabaseHelperTest {
    private lateinit var context: Context
    private lateinit var dbHelper: TransferBubbleDatabaseHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // null dbName = in-memory SQLite database (cross-platform; avoids ':' colon in path on Windows)
        dbHelper = TransferBubbleDatabaseHelper(context, null)
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    private fun makeBubble(
        id: String,
        name: String = "Bubble $id",
    ) = TransferBubble(
        id = id,
        displayName = name,
        createdAt = System.currentTimeMillis(),
        sortOrder = 0,
        preferredOperation = null,
        items = emptyList(),
    )

    private fun makeNode(fileName: String) =
        FileNode(
            id = FileNodeId.file("/storage/emulated/0/Documents/$fileName"),
            name = fileName,
            displayName = fileName,
            mimeType = "text/plain",
            size = 512L,
            modifiedAt = System.currentTimeMillis(),
            isDirectory = false,
            isHidden = false,
            parentId = FileNodeId.file("/storage/emulated/0/Documents"),
            storageType = StorageType.INTERNAL_SHARED,
            access = AccessFlags.FULL,
            childCount = null,
            extras = null,
        )

    @Test
    fun `database version is 1`() {
        assertEquals(1, TransferBubbleDatabaseHelper.DATABASE_VERSION)
    }

    @Test
    fun `schema creates transfer_bubbles table with expected columns`() {
        val db = dbHelper.readableDatabase
        val cursor =
            db.rawQuery(
                "PRAGMA table_info(${TransferBubbleDatabaseHelper.TABLE_BUBBLES})",
                null,
            )
        val columns = mutableListOf<String>()
        cursor.use {
            val nameIndex = it.getColumnIndexOrThrow("name")
            while (it.moveToNext()) {
                columns.add(it.getString(nameIndex))
            }
        }
        assertTrue("id column missing", columns.contains(TransferBubbleDatabaseHelper.COL_BUBBLE_ID))
        assertTrue("display_name column missing", columns.contains(TransferBubbleDatabaseHelper.COL_DISPLAY_NAME))
        assertTrue("created_at column missing", columns.contains(TransferBubbleDatabaseHelper.COL_CREATED_AT))
        assertTrue("sort_order column missing", columns.contains(TransferBubbleDatabaseHelper.COL_SORT_ORDER))
        assertTrue(
            "preferred_operation column missing",
            columns.contains(TransferBubbleDatabaseHelper.COL_PREFERRED_OPERATION),
        )
    }

    @Test
    fun `schema creates transfer_bubble_items table with expected columns`() {
        val db = dbHelper.readableDatabase
        val cursor =
            db.rawQuery(
                "PRAGMA table_info(${TransferBubbleDatabaseHelper.TABLE_ITEMS})",
                null,
            )
        val columns = mutableListOf<String>()
        cursor.use {
            val nameIndex = it.getColumnIndexOrThrow("name")
            while (it.moveToNext()) {
                columns.add(it.getString(nameIndex))
            }
        }
        assertTrue("bubble_id column missing", columns.contains(TransferBubbleDatabaseHelper.COL_ITEM_BUBBLE_ID))
        assertTrue("node_id column missing", columns.contains(TransferBubbleDatabaseHelper.COL_ITEM_NODE_ID))
        assertTrue("display_name column missing", columns.contains(TransferBubbleDatabaseHelper.COL_ITEM_DISPLAY_NAME))
        assertTrue("size column missing", columns.contains(TransferBubbleDatabaseHelper.COL_ITEM_SIZE))
    }

    @Test
    fun insertBubble_and_getAllBubbles_roundTrip() {
        val bubble = makeBubble("bubble-001", "Test Bubble")
        val inserted = dbHelper.insertBubble(bubble)
        assertTrue("insertBubble should return true", inserted)

        val all = dbHelper.getAllBubbles()
        assertEquals(1, all.size)
        assertEquals("bubble-001", all[0].id)
        assertEquals("Test Bubble", all[0].displayName)
    }

    @Test
    fun deleteBubble_removes_the_bubble() {
        dbHelper.insertBubble(makeBubble("bubble-del"))
        assertEquals(1, dbHelper.getAllBubbles().size)

        dbHelper.deleteBubble("bubble-del")
        assertEquals(0, dbHelper.getAllBubbles().size)
    }

    @Test
    fun addItems_stores_items_and_can_be_retrieved_via_getAllBubbles() {
        dbHelper.insertBubble(makeBubble("bubble-items"))

        val node = makeNode("note.txt")
        dbHelper.addItems("bubble-items", listOf(node))

        val retrieved = dbHelper.getAllBubbles()
        assertEquals(1, retrieved.size)
        assertEquals(1, retrieved[0].items.size)
        assertEquals("note.txt", retrieved[0].items[0].displayNameSnapshot)
        assertEquals(512L, retrieved[0].items[0].sizeSnapshot)
    }

    @Test
    fun foreignKey_cascade_deleting_bubble_removes_its_items() {
        // Verifies that onConfigure(db) { setForeignKeyConstraintsEnabled(true) } is active.
        // Without it, the FK CASCADE silently does nothing on SQLite.
        dbHelper.insertBubble(makeBubble("bubble-fk"))
        dbHelper.addItems("bubble-fk", listOf(makeNode("big.zip")))

        val before = dbHelper.getAllBubbles()
        assertEquals(1, before[0].items.size)

        // Delete the parent bubble — FK CASCADE should delete all child items
        dbHelper.deleteBubble("bubble-fk")

        // Verify no orphaned items remain
        val db = dbHelper.readableDatabase
        val cursor =
            db.rawQuery(
                "SELECT COUNT(*) FROM ${TransferBubbleDatabaseHelper.TABLE_ITEMS} " +
                    "WHERE ${TransferBubbleDatabaseHelper.COL_ITEM_BUBBLE_ID} = 'bubble-fk'",
                null,
            )
        val orphanCount =
            cursor.use {
                it.moveToFirst()
                it.getInt(0)
            }
        assertEquals(
            "FK ON DELETE CASCADE should remove all items when parent bubble is deleted; " +
                "if this fails, check that onConfigure() calls setForeignKeyConstraintsEnabled(true)",
            0,
            orphanCount,
        )
    }

    @Test
    fun countBubbles_returns_correct_count() {
        assertEquals(0, dbHelper.countBubbles())
        dbHelper.insertBubble(makeBubble("b1"))
        dbHelper.insertBubble(makeBubble("b2"))
        assertEquals(2, dbHelper.countBubbles())
        dbHelper.deleteBubble("b1")
        assertEquals(1, dbHelper.countBubbles())
    }

    @Test
    fun onUpgrade_noOp_does_not_crash_at_v1() {
        // Documents that onUpgrade is intentionally a no-op at v1.
        // When schema migrations are needed (v1 -> v2), this test must be updated
        // to verify that the migration is applied correctly.
        val db = dbHelper.writableDatabase
        dbHelper.onUpgrade(db, 1, 1) // must not throw
    }

    @Test
    fun bubble_with_500_items_persists_and_queries_efficiently() {
        val bubbleId = "bubble-heavy"
        dbHelper.insertBubble(makeBubble(bubbleId, "Heavy Bubble"))

        val items =
            (1..500).map { i ->
                makeNode("file_%03d.dat".format(i))
            }
        dbHelper.addItems(bubbleId, items)

        val queryStart = System.currentTimeMillis()
        val retrieved = dbHelper.getAllBubbles()
        val queryDuration = System.currentTimeMillis() - queryStart

        assertEquals(1, retrieved.size)
        assertEquals(500, retrieved[0].items.size)
        assertTrue("Querying 500 items took ${queryDuration}ms, expected < 1000ms", queryDuration < 1000)
    }
}
