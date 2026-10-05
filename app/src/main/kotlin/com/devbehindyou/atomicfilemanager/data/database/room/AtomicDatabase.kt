package com.devbehindyou.atomicfilemanager.data.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * The app's Room database (ALL_IN_ONE_PLAN.md Phase 0.2). It starts with the operation journal;
 * hidden files and transfer bubbles move here from their SQLiteOpenHelpers in later steps, each
 * with a migration test. Schemas are exported to `app/schemas`.
 */
@Database(entities = [OperationJournalEntity::class], version = 1, exportSchema = true)
@TypeConverters(AtomicConverters::class)
abstract class AtomicDatabase : RoomDatabase() {
    abstract fun operationJournal(): OperationJournalDao

    companion object {
        const val NAME = "atomic.db"

        fun create(context: Context): AtomicDatabase =
            Room.databaseBuilder(context.applicationContext, AtomicDatabase::class.java, NAME).build()
    }
}

/** Lists of raw node ids are joined with NUL, which no file path or URI can contain. */
class AtomicConverters {
    @TypeConverter
    fun fromIds(ids: List<String>): String = ids.joinToString(SEPARATOR)

    @TypeConverter
    fun toIds(joined: String): List<String> = if (joined.isEmpty()) emptyList() else joined.split(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\u0000"
    }
}
