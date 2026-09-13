package com.devbehindyou.refract.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.devbehindyou.refract.domain.model.HiddenItem
import com.devbehindyou.refract.domain.model.HideJournalEntry
import com.devbehindyou.refract.domain.model.HideMode
import com.devbehindyou.refract.domain.model.JournalState

class HiddenFilesDatabaseHelper(
    context: Context,
    dbName: String? = DATABASE_NAME,
) : SQLiteOpenHelper(context, dbName, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_HIDDEN_ITEMS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_ORIGINAL_LOC TEXT NOT NULL,
                $COL_CURRENT_LOC TEXT NOT NULL,
                $COL_ORIGINAL_NAME TEXT NOT NULL,
                $COL_SIZE INTEGER NOT NULL,
                $COL_MODE TEXT NOT NULL,
                $COL_HIDDEN_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_JOURNAL (
                $COL_J_OP_ID TEXT PRIMARY KEY,
                $COL_J_ORIG_PATH TEXT NOT NULL,
                $COL_J_CURR_PATH TEXT NOT NULL,
                $COL_J_ORIG_NAME TEXT NOT NULL,
                $COL_J_STATE TEXT NOT NULL,
                $COL_J_MODE TEXT NOT NULL,
                $COL_J_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        // Schema migrations
    }

    fun getAllHiddenItems(): List<HiddenItem> {
        val db = readableDatabase
        val list = mutableListOf<HiddenItem>()
        val cursor = db.query(TABLE_HIDDEN_ITEMS, null, null, null, null, null, "$COL_HIDDEN_AT DESC")
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_ID)
            val origLocIdx = it.getColumnIndexOrThrow(COL_ORIGINAL_LOC)
            val currLocIdx = it.getColumnIndexOrThrow(COL_CURRENT_LOC)
            val nameIdx = it.getColumnIndexOrThrow(COL_ORIGINAL_NAME)
            val sizeIdx = it.getColumnIndexOrThrow(COL_SIZE)
            val modeIdx = it.getColumnIndexOrThrow(COL_MODE)
            val timeIdx = it.getColumnIndexOrThrow(COL_HIDDEN_AT)

            while (it.moveToNext()) {
                val mode =
                    try {
                        HideMode.valueOf(it.getString(modeIdx))
                    } catch (e: Exception) {
                        HideMode.FAST_OBSCURE
                    }
                list.add(
                    HiddenItem(
                        id = it.getString(idIdx),
                        originalLocation = it.getString(origLocIdx),
                        currentLocation = it.getString(currLocIdx),
                        originalName = it.getString(nameIdx),
                        size = it.getLong(sizeIdx),
                        mode = mode,
                        hiddenAt = it.getLong(timeIdx),
                    ),
                )
            }
        }
        return list
    }

    fun insertHiddenItem(item: HiddenItem): Boolean {
        val db = writableDatabase
        val values =
            ContentValues().apply {
                put(COL_ID, item.id)
                put(COL_ORIGINAL_LOC, item.originalLocation)
                put(COL_CURRENT_LOC, item.currentLocation)
                put(COL_ORIGINAL_NAME, item.originalName)
                put(COL_SIZE, item.size)
                put(COL_MODE, item.mode.name)
                put(COL_HIDDEN_AT, item.hiddenAt)
            }
        return db.insertWithOnConflict(TABLE_HIDDEN_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    fun deleteHiddenItem(id: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_HIDDEN_ITEMS, "$COL_ID = ?", arrayOf(id))
    }

    fun logJournal(entry: HideJournalEntry): Boolean {
        val db = writableDatabase
        val values =
            ContentValues().apply {
                put(COL_J_OP_ID, entry.operationId)
                put(COL_J_ORIG_PATH, entry.originalPath)
                put(COL_J_CURR_PATH, entry.currentPath)
                put(COL_J_ORIG_NAME, entry.originalName)
                put(COL_J_STATE, entry.state.name)
                put(COL_J_MODE, entry.mode.name)
                put(COL_J_TIMESTAMP, entry.timestamp)
            }
        return db.insertWithOnConflict(TABLE_JOURNAL, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    fun getUnfinishedJournalEntries(): List<HideJournalEntry> {
        val db = readableDatabase
        val list = mutableListOf<HideJournalEntry>()
        val cursor =
            db.query(
                TABLE_JOURNAL,
                null,
                "$COL_J_STATE NOT IN (?, ?)",
                arrayOf(JournalState.COMMITTED.name, JournalState.RESTORED.name),
                null,
                null,
                "$COL_J_TIMESTAMP ASC",
            )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_J_OP_ID)
            val origIdx = it.getColumnIndexOrThrow(COL_J_ORIG_PATH)
            val currIdx = it.getColumnIndexOrThrow(COL_J_CURR_PATH)
            val nameIdx = it.getColumnIndexOrThrow(COL_J_ORIG_NAME)
            val stateIdx = it.getColumnIndexOrThrow(COL_J_STATE)
            val modeIdx = it.getColumnIndexOrThrow(COL_J_MODE)
            val timeIdx = it.getColumnIndexOrThrow(COL_J_TIMESTAMP)

            while (it.moveToNext()) {
                list.add(
                    HideJournalEntry(
                        operationId = it.getString(idIdx),
                        originalPath = it.getString(origIdx),
                        currentPath = it.getString(currIdx),
                        originalName = it.getString(nameIdx),
                        state = JournalState.valueOf(it.getString(stateIdx)),
                        mode = HideMode.valueOf(it.getString(modeIdx)),
                        timestamp = it.getLong(timeIdx),
                    ),
                )
            }
        }
        return list
    }

    fun clearJournal(operationId: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_JOURNAL, "$COL_J_OP_ID = ?", arrayOf(operationId))
    }

    companion object {
        const val DATABASE_NAME = "refract_hidden_files.db"
        const val DATABASE_VERSION = 1

        const val TABLE_HIDDEN_ITEMS = "hidden_items"
        const val COL_ID = "id"
        const val COL_ORIGINAL_LOC = "original_location"
        const val COL_CURRENT_LOC = "current_location"
        const val COL_ORIGINAL_NAME = "original_name"
        const val COL_SIZE = "size"
        const val COL_MODE = "mode"
        const val COL_HIDDEN_AT = "hidden_at"

        const val TABLE_JOURNAL = "hide_journal"
        const val COL_J_OP_ID = "operation_id"
        const val COL_J_ORIG_PATH = "original_path"
        const val COL_J_CURR_PATH = "current_path"
        const val COL_J_ORIG_NAME = "original_name"
        const val COL_J_STATE = "state"
        const val COL_J_MODE = "mode"
        const val COL_J_TIMESTAMP = "timestamp"
    }
}
