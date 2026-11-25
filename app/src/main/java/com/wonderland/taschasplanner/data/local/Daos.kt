package com.wonderland.taschasplanner.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildingDao {
    @Query("SELECT * FROM buildings WHERE isActive = 1 ORDER BY name ASC")
    fun observeActiveBuildings(): Flow<List<BuildingEntity>>

    @Query("SELECT * FROM buildings ORDER BY name ASC")
    fun observeAllBuildings(): Flow<List<BuildingEntity>>

    @Query("SELECT * FROM buildings ORDER BY id ASC")
    suspend fun getAllBuildingsOnce(): List<BuildingEntity>

    @Query("SELECT COUNT(*) FROM buildings")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(building: BuildingEntity): Long

    @Update
    suspend fun update(building: BuildingEntity)

    @Delete
    suspend fun delete(building: BuildingEntity)

    @Query("DELETE FROM buildings")
    suspend fun deleteAll()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY isFavorite DESC, fullName ASC")
    fun observeAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY id ASC")
    suspend fun getAllContactsOnce(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE type = :type ORDER BY isFavorite DESC, fullName ASC")
    fun observeContactsByType(type: ContactType): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueDateEpochDay ASC, startTimeMinutes ASC, id ASC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE dueDateEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY dueDateEpochDay ASC, startTimeMinutes ASC")
    fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface ShiftScheduleDao {
    @Query("SELECT * FROM shift_schedules ORDER BY dateEpochDay ASC, startMinutes ASC")
    fun observeAllShifts(): Flow<List<ShiftScheduleEntity>>

    @Query("SELECT * FROM shift_schedules ORDER BY id ASC")
    suspend fun getAllShiftsOnce(): List<ShiftScheduleEntity>

    @Query("SELECT * FROM shift_schedules WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY dateEpochDay ASC, startMinutes ASC")
    fun observeShiftsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<ShiftScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shift: ShiftScheduleEntity): Long

    @Update
    suspend fun update(shift: ShiftScheduleEntity)

    @Delete
    suspend fun delete(shift: ShiftScheduleEntity)

    @Query("DELETE FROM shift_schedules")
    suspend fun deleteAll()
}

@Dao
interface CalendarPrefDao {
    @Query("SELECT * FROM calendar_prefs ORDER BY displayName ASC")
    fun observeCalendarPrefs(): Flow<List<CalendarPrefEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAll(prefs: List<CalendarPrefEntity>)

    @Query("UPDATE calendar_prefs SET isVisible = :isVisible WHERE calendarId = :calendarId")
    suspend fun setVisibility(calendarId: Long, isVisible: Boolean)
}
