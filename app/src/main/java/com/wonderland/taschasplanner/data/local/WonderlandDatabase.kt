package com.wonderland.taschasplanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BuildingEntity::class,
        ContactEntity::class,
        TaskEntity::class,
        ShiftScheduleEntity::class,
        CalendarPrefEntity::class,
        CompTimeEntryEntity::class,
        NoteTodoEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(WonderlandTypeConverters::class)
abstract class WonderlandDatabase : RoomDatabase() {
    abstract fun buildingDao(): BuildingDao
    abstract fun contactDao(): ContactDao
    abstract fun taskDao(): TaskDao
    abstract fun shiftScheduleDao(): ShiftScheduleDao
    abstract fun calendarPrefDao(): CalendarPrefDao
    abstract fun compTimeDao(): CompTimeDao
    abstract fun noteTodoDao(): NoteTodoDao

    companion object {
        @Volatile
        private var INSTANCE: WonderlandDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE shift_schedules ADD COLUMN isFlexibleHours INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE shift_schedules ADD COLUMN allocatedHours REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `comp_time_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `entryType` TEXT NOT NULL,
                        `timeInMinutes` INTEGER,
                        `timeOutMinutes` INTEGER,
                        `breakMinutes` INTEGER NOT NULL,
                        `usedCompHours` REAL NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_comp_time_entries_dateEpochDay` ON `comp_time_entries` (`dateEpochDay`)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN birthdayMonth INTEGER")
                db.execSQL("ALTER TABLE contacts ADD COLUMN birthdayDay INTEGER")
                db.execSQL("ALTER TABLE contacts ADD COLUMN linkedBirthdayEventId INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_todos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `header` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `completedAtMillis` INTEGER,
                        `isPinned` INTEGER NOT NULL,
                        `accentColorHex` INTEGER NOT NULL,
                        `linkedTaskId` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_note_todos_isCompleted` ON `note_todos` (`isCompleted`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_note_todos_updatedAtMillis` ON `note_todos` (`updatedAtMillis`)"
                )
            }
        }

        fun getInstance(context: Context): WonderlandDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WonderlandDatabase::class.java,
                    "wonderland_planner.db"
                )
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
