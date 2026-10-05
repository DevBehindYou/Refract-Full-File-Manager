package com.devbehindyou.atomicfilemanager.data.database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's Room database (ALL_IN_ONE_PLAN.md Phase 0.2). It starts with the operation journal;
 * hidden files and transfer bubbles move here from their SQLiteOpenHelpers in later steps, each
 * with a migration test. Schemas are exported to `app/schemas`.
 */
@Database(entities = [OperationJournalEntity::class, TrashEntryEntity::class], version = 2, exportSchema = true)
@TypeConverters(AtomicConverters::class)
abstract class AtomicDatabase : RoomDatabase() {
    abstract fun operationJournal(): OperationJournalDao

    abstract fun trashEntries(): TrashEntryDao

    companion object {
        const val NAME = "atomic.db"

        fun create(context: Context): AtomicDatabase =
            Room
                .databaseBuilder(context.applicationContext, AtomicDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Version 2 adds the Trash (ALL_IN_ONE_PLAN.md 1.1). Matches the exported 2.json schema. */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `trash_entry` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                            "`isDirectory` INTEGER NOT NULL, `size` INTEGER NOT NULL, " +
                            "`originalParent` TEXT NOT NULL, `trashedId` TEXT NOT NULL, `holderId` TEXT NOT NULL, " +
                            "`volumeRoot` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, `operationId` TEXT NOT NULL, " +
                            "PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_trash_entry_deletedAt` ON `trash_entry` (`deletedAt`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_trash_entry_operationId` ON `trash_entry` (`operationId`)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_trash_entry_trashedId` " +
                            "ON `trash_entry` (`trashedId`)",
                    )
                }
            }
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
