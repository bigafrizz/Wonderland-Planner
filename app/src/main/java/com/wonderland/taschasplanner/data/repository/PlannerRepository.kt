package com.wonderland.taschasplanner.data.repository

import android.content.Context
import com.wonderland.taschasplanner.data.calendar.GoogleCalendarRepository
import com.wonderland.taschasplanner.data.local.BuildingEntity
import com.wonderland.taschasplanner.data.local.CalendarPrefEntity
import com.wonderland.taschasplanner.data.local.CompTimeEntryEntity
import com.wonderland.taschasplanner.data.local.CompTimeEntryType
import com.wonderland.taschasplanner.data.local.ContactEntity
import com.wonderland.taschasplanner.data.local.RecurrenceRule
import com.wonderland.taschasplanner.data.local.SampleDataSeeder
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.TaskEntity
import com.wonderland.taschasplanner.data.local.ThemeMode
import com.wonderland.taschasplanner.data.local.WonderlandDatabase
import com.wonderland.taschasplanner.data.model.CalendarEvent
import com.wonderland.taschasplanner.data.model.DeviceCalendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class PlannerRepository(
    context: Context,
    private val db: WonderlandDatabase,
    val googleCalendarRepository: GoogleCalendarRepository
) {
    private val prefs = context.getSharedPreferences("wonderland_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.STORYBOOK_LIGHT.name) ?: ThemeMode.STORYBOOK_LIGHT.name)
        }.getOrDefault(ThemeMode.STORYBOOK_LIGHT)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _showSampleCalendarPreview = MutableStateFlow(
        prefs.getBoolean(KEY_SAMPLE_CALENDAR, true)
    )
    val showSampleCalendarPreview: StateFlow<Boolean> = _showSampleCalendarPreview.asStateFlow()

    private val _deviceCalendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    val deviceCalendars: StateFlow<List<DeviceCalendar>> = _deviceCalendars.asStateFlow()

    private val _calendarEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEvent>> = _calendarEvents.asStateFlow()

    private val _hasCalendarPermission = MutableStateFlow(googleCalendarRepository.hasReadPermission())
    val hasCalendarPermission: StateFlow<Boolean> = _hasCalendarPermission.asStateFlow()

    val buildings: Flow<List<BuildingEntity>> = db.buildingDao().observeActiveBuildings()
    val contacts: Flow<List<ContactEntity>> = db.contactDao().observeAllContacts()
    val tasks: Flow<List<TaskEntity>> = db.taskDao().observeAllTasks()
    val shifts: Flow<List<ShiftScheduleEntity>> = db.shiftScheduleDao().observeAllShifts()
    val calendarPrefs: Flow<List<CalendarPrefEntity>> = db.calendarPrefDao().observeCalendarPrefs()
    val compTimeEntries: Flow<List<CompTimeEntryEntity>> = db.compTimeDao().observeAllEntries()

    private val _dailyBaselineHours = MutableStateFlow(
        prefs.getFloat(KEY_COMP_BASELINE, 8.0f).toDouble()
    )
    val dailyBaselineHours: StateFlow<Double> = _dailyBaselineHours.asStateFlow()

    fun setDailyBaselineHours(hours: Double) {
        val clamped = hours.coerceIn(4.0, 12.0)
        _dailyBaselineHours.value = clamped
        prefs.edit().putFloat(KEY_COMP_BASELINE, clamped.toFloat()).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun toggleQuickTheme() {
        val next = if (_themeMode.value == ThemeMode.CHESHIRE_DARK) {
            ThemeMode.STORYBOOK_LIGHT
        } else {
            ThemeMode.CHESHIRE_DARK
        }
        setThemeMode(next)
    }

    fun setShowSampleCalendarPreview(enabled: Boolean) {
        _showSampleCalendarPreview.value = enabled
        prefs.edit().putBoolean(KEY_SAMPLE_CALENDAR, enabled).apply()
    }

    suspend fun ensureSeeded() {
        if (!prefs.getBoolean(KEY_INITIAL_SEED_DONE, false)) {
            SampleDataSeeder.seedIfEmpty(db)
            prefs.edit().putBoolean(KEY_INITIAL_SEED_DONE, true).apply()
        }
    }

    suspend fun loadSampleData() = SampleDataSeeder.seedSampleData(db)
    suspend fun clearAllLocalData() = SampleDataSeeder.clearAllData(db)

    // --- Buildings ---
    suspend fun saveBuilding(building: BuildingEntity): Long {
        return if (building.id == 0L) db.buildingDao().insert(building)
        else {
            db.buildingDao().update(building)
            building.id
        }
    }

    suspend fun deleteBuilding(building: BuildingEntity) = db.buildingDao().delete(building)

    // --- Contacts ---
    suspend fun saveContact(
        contact: ContactEntity,
        pushBirthdayCalendarId: Long? = null,
        buildingName: String = ""
    ): Long {
        var linkedBdayId = contact.linkedBirthdayEventId
        val hasBirthday = contact.birthdayMonth != null && contact.birthdayDay != null

        if (googleCalendarRepository.hasWritePermission()) {
            if (!hasBirthday && linkedBdayId != null) {
                googleCalendarRepository.deleteEvent(linkedBdayId)
                linkedBdayId = null
            } else if (hasBirthday) {
                val month = contact.birthdayMonth!!
                val day = contact.birthdayDay!!
                val year = LocalDate.now().year
                val maxDay = java.time.YearMonth.of(year, month).lengthOfMonth()
                val bdayDate = LocalDate.of(year, month, day.coerceAtMost(maxDay))
                val title = "\uD83C\uDF82 ${contact.fullName}'s Birthday"
                val desc = listOfNotNull(
                    contact.roleOrCompany.takeIf { it.isNotBlank() },
                    "Added from Tascha's Wonderland Planner"
                ).joinToString(" • ")

                if (linkedBdayId != null) {
                    googleCalendarRepository.updateEvent(
                        eventId = linkedBdayId,
                        title = title,
                        description = desc,
                        location = buildingName,
                        date = bdayDate,
                        startMinutes = null,
                        endMinutes = null,
                        recurrence = RecurrenceRule.YEARLY
                    )
                } else if (pushBirthdayCalendarId != null) {
                    setLastPushCalendarId(pushBirthdayCalendarId)
                    linkedBdayId = googleCalendarRepository.insertEvent(
                        calendarId = pushBirthdayCalendarId,
                        title = title,
                        description = desc,
                        location = buildingName,
                        date = bdayDate,
                        startMinutes = null,
                        endMinutes = null,
                        recurrence = RecurrenceRule.YEARLY
                    )
                }
            }
        }

        val toSave = contact.copy(linkedBirthdayEventId = linkedBdayId)
        return if (toSave.id == 0L) db.contactDao().insert(toSave)
        else {
            db.contactDao().update(toSave)
            toSave.id
        }
    }

    suspend fun deleteContact(contact: ContactEntity) {
        contact.linkedBirthdayEventId?.let { eventId ->
            googleCalendarRepository.deleteEvent(eventId)
        }
        db.contactDao().delete(contact)
    }

    fun getLastPushCalendarId(): Long? {
        val id = prefs.getLong(KEY_LAST_PUSH_CALENDAR, -1L)
        return if (id > 0L) id else null
    }

    fun setLastPushCalendarId(calendarId: Long) {
        prefs.edit().putLong(KEY_LAST_PUSH_CALENDAR, calendarId).apply()
    }

    // --- Tasks ---
    suspend fun saveTask(
        task: TaskEntity,
        pushToCalendarId: Long? = null,
        locationName: String = "",
        extraDetails: String = ""
    ): Long {
        val location = listOfNotNull(
            locationName.takeIf { it.isNotBlank() },
            task.roomOrZone.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
        val fullDescription = listOfNotNull(
            task.description.takeIf { it.isNotBlank() },
            extraDetails.takeIf { it.isNotBlank() }
        ).joinToString("\n\n")

        var linkedId = task.linkedCalendarEventId
        if (googleCalendarRepository.hasWritePermission()) {
            if (linkedId != null) {
                googleCalendarRepository.updateEvent(
                    eventId = linkedId,
                    title = task.title,
                    description = fullDescription,
                    location = location,
                    date = LocalDate.ofEpochDay(task.dueDateEpochDay),
                    startMinutes = task.startTimeMinutes,
                    endMinutes = task.endTimeMinutes,
                    recurrence = task.recurrence
                )
            } else if (pushToCalendarId != null) {
                setLastPushCalendarId(pushToCalendarId)
                linkedId = googleCalendarRepository.insertEvent(
                    calendarId = pushToCalendarId,
                    title = task.title,
                    description = fullDescription,
                    location = location,
                    date = LocalDate.ofEpochDay(task.dueDateEpochDay),
                    startMinutes = task.startTimeMinutes,
                    endMinutes = task.endTimeMinutes,
                    recurrence = task.recurrence
                )
            }
        }
        val toSave = task.copy(linkedCalendarEventId = linkedId)
        return if (toSave.id == 0L) db.taskDao().insert(toSave)
        else {
            db.taskDao().update(toSave)
            toSave.id
        }
    }

    suspend fun saveTasksForDates(
        template: TaskEntity,
        dates: List<LocalDate>,
        pushToCalendarId: Long? = null,
        locationName: String = "",
        extraDetails: String = ""
    ) {
        val uniqueDates = dates.distinct().sorted()
        uniqueDates.forEachIndexed { idx, date ->
            val taskForDay = template.copy(
                id = if (idx == 0) template.id else 0L,
                dueDateEpochDay = date.toEpochDay(),
                linkedCalendarEventId = if (idx == 0) template.linkedCalendarEventId else null
            )
            saveTask(taskForDay, pushToCalendarId, locationName, extraDetails)
        }
    }

    suspend fun deleteTask(task: TaskEntity) {
        task.linkedCalendarEventId?.let { eventId ->
            googleCalendarRepository.deleteEvent(eventId)
        }
        db.taskDao().delete(task)
    }

    suspend fun deleteTasks(tasks: List<TaskEntity>) {
        tasks.forEach { deleteTask(it) }
    }

    // --- Shifts ---
    suspend fun saveShift(shift: ShiftScheduleEntity): Long {
        return if (shift.id == 0L) db.shiftScheduleDao().insert(shift)
        else {
            db.shiftScheduleDao().update(shift)
            shift.id
        }
    }

    suspend fun saveShiftsForDates(
        template: ShiftScheduleEntity,
        dates: List<LocalDate>
    ) {
        val uniqueDates = dates.distinct().sorted()
        uniqueDates.forEachIndexed { idx, date ->
            val shiftForDay = template.copy(
                id = if (idx == 0) template.id else 0L,
                dateEpochDay = date.toEpochDay()
            )
            saveShift(shiftForDay)
        }
    }

    suspend fun deleteShift(shift: ShiftScheduleEntity) = db.shiftScheduleDao().delete(shift)

    suspend fun deleteShifts(shifts: List<ShiftScheduleEntity>) {
        shifts.forEach { db.shiftScheduleDao().delete(it) }
    }

    // --- Comp Time ---
    suspend fun saveCompTimeEntry(entry: CompTimeEntryEntity): Long {
        return if (entry.id == 0L) db.compTimeDao().insert(entry)
        else {
            db.compTimeDao().update(entry)
            entry.id
        }
    }

    suspend fun deleteCompTimeEntry(entry: CompTimeEntryEntity) = db.compTimeDao().delete(entry)

    suspend fun clockInOrOutToday(nowMinutes: Int, todayEpochDay: Long = LocalDate.now().toEpochDay()) {
        val existing = db.compTimeDao().getWorkEntryForDate(todayEpochDay)
        if (existing == null) {
            db.compTimeDao().insert(
                CompTimeEntryEntity(
                    dateEpochDay = todayEpochDay,
                    entryType = CompTimeEntryType.WORK_DAY,
                    timeInMinutes = nowMinutes,
                    timeOutMinutes = null,
                    breakMinutes = 30
                )
            )
        } else if (existing.timeOutMinutes == null) {
            db.compTimeDao().update(existing.copy(timeOutMinutes = nowMinutes.coerceAtLeast((existing.timeInMinutes ?: 0) + 1)))
        } else {
            // Start a second work block today if already clocked out
            db.compTimeDao().insert(
                CompTimeEntryEntity(
                    dateEpochDay = todayEpochDay,
                    entryType = CompTimeEntryType.WORK_DAY,
                    timeInMinutes = nowMinutes,
                    timeOutMinutes = null,
                    breakMinutes = 0
                )
            )
        }
    }

    // --- Google Calendar Sync ---
    suspend fun refreshGoogleCalendars(centerDate: LocalDate = LocalDate.now()) {
        val granted = googleCalendarRepository.hasReadPermission()
        _hasCalendarPermission.value = granted
        if (!granted) {
            _deviceCalendars.value = emptyList()
            _calendarEvents.value = if (_showSampleCalendarPreview.value) {
                googleCalendarRepository.buildSampleCalendarEvents(centerDate)
            } else {
                emptyList()
            }
            return
        }

        val calendars = googleCalendarRepository.fetchDeviceCalendars(googleOnly = false)
        _deviceCalendars.value = calendars

        if (calendars.isNotEmpty()) {
            val existingPrefs = calendars.map {
                CalendarPrefEntity(
                    calendarId = it.id,
                    accountName = it.accountName,
                    displayName = it.displayName,
                    colorInt = it.colorInt,
                    isVisible = true
                )
            }
            db.calendarPrefDao().upsertAll(existingPrefs)
        }

        val rangeStart = centerDate.minusMonths(1).withDayOfMonth(1)
        val rangeEnd = centerDate.plusMonths(2).withDayOfMonth(1)
        val nameMap = calendars.associate { it.id to it.displayName }
        val realEvents = googleCalendarRepository.fetchEventsInRange(rangeStart, rangeEnd, nameMap)

        _calendarEvents.value = if (realEvents.isEmpty() && _showSampleCalendarPreview.value) {
            googleCalendarRepository.buildSampleCalendarEvents(centerDate)
        } else {
            realEvents
        }
    }

    suspend fun setCalendarVisibility(calendarId: Long, visible: Boolean) {
        db.calendarPrefDao().setVisibility(calendarId, visible)
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SAMPLE_CALENDAR = "sample_calendar_preview"
        private const val KEY_INITIAL_SEED_DONE = "initial_seed_done"
        private const val KEY_LAST_PUSH_CALENDAR = "last_push_calendar_id"
        private const val KEY_COMP_BASELINE = "comp_daily_baseline_hours"
    }
}
