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
        CalendarPrefEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(WonderlandTypeConverters::class)
abstract class WonderlandDatabase : RoomDatabase() {
    abstract fun buildingDao(): BuildingDao
    abstract fun contactDao(): ContactDao
    abstract fun taskDao(): TaskDao
    abstract fun shiftScheduleDao(): ShiftScheduleDao
    abstract fun calendarPrefDao(): CalendarPrefDao

    companion object {
        @Volatile
        private var INSTANCE: WonderlandDatabase? = null

        /**
         * Non-destructive migration from v1 to v2 adding Flexible-Hours shift columns.
         * Preserves 100% of existing buildings, contacts, tasks, and timed shifts.
         */
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

        fun getInstance(context: Context): WonderlandDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WonderlandDatabase::class.java,
                    "wonderland_planner.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
