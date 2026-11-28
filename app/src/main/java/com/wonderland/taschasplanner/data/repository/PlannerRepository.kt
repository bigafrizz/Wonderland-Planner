package com.wonderland.taschasplanner.data.repository

import android.content.Context
import com.wonderland.taschasplanner.data.backup.AutoBackupStore
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
import com.wonderland.taschasplanner.data.model.CalendarSyncStatus
import com.wonderland.taschasplanner.data.model.DeviceCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

class PlannerRepository(
    context: Context,
    private val db: WonderlandDatabase,
    val googleCalendarRepository: GoogleCalendarRepository,
    private val autoBackupStore: AutoBackupStore,
    private val appScope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("wonderland_prefs", Context.MODE_PRIVATE)
    private val calendarSyncMutex = Mutex()
    private var loadedRangeStart: LocalDate? = null
    private var loadedRangeEnd: LocalDate? = null

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

    private val _showShiftsInMonthView = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_SHIFTS_IN_MONTH, true)
    )
    val showShiftsInMonthView: StateFlow<Boolean> = _showShiftsInMonthView.asStateFlow()

    private val _deviceCalendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    val deviceCalendars: StateFlow<List<DeviceCalendar>> = _deviceCalendars.asStateFlow()

    private val _calendarEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEvent>> = _calendarEvents.asStateFlow()

    private val _hasCalendarPermission = MutableStateFlow(googleCalendarRepository.hasReadPermission())
    val hasCalendarPermission: StateFlow<Boolean> = _hasCalendarPermission.asStateFlow()

    private val _calendarSyncStatus = MutableStateFlow<CalendarSyncStatus>(CalendarSyncStatus.Idle)
    val calendarSyncStatus: StateFlow<CalendarSyncStatus> = _calendarSyncStatus.asStateFlow()

    val lastAutoBackupAtMillis: StateFlow<Long?> = autoBackupStore.lastBackupAtMillis

    private val _autoRecoveryMessage = MutableStateFlow<String?>(null)
    val autoRecoveryMessage: StateFlow<String?> = _autoRecoveryMessage.asStateFlow()

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

    fun setShowShiftsInMonthView(enabled: Boolean) {
        _showShiftsInMonthView.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_SHIFTS_IN_MONTH, enabled).apply()
    }

    private fun markUserHasData() {
        if (prefs.getBoolean(KEY_USER_CLEARED_DATA, false)) {
            prefs.edit().putBoolean(KEY_USER_CLEARED_DATA, false).apply()
        }
        autoBackupStore.scheduleSnapshot()
    }

    suspend fun initializePersistenceAndSeed() = withContext(appScope.coroutineContext + NonCancellable) {
        val userCleared = prefs.getBoolean(KEY_USER_CLEARED_DATA, false)
        val recovered = autoBackupStore.tryAutoRecoverIfEmpty(userExplicitlyCleared = userCleared)
        if (recovered != null) {
            prefs.edit().putBoolean(KEY_INITIAL_SEED_DONE, true).apply()
            _autoRecoveryMessage.value =
                "Auto-recovered ${recovered.buildings.size} buildings, ${recovered.contacts.size} contacts, ${recovered.tasks.size} tasks, and ${recovered.shifts.size} shifts from local safety backup."
            return@withContext
        }

        if (!prefs.getBoolean(KEY_INITIAL_SEED_DONE, false)) {
            SampleDataSeeder.seedIfEmpty(db)
            prefs.edit()
                .putBoolean(KEY_INITIAL_SEED_DONE, true)
                .putBoolean(KEY_USER_CLEARED_DATA, false)
                .apply()
            autoBackupStore.snapshotNow()
        } else if (!autoBackupStore.hasAnyBackupFile() && !userCleared) {
            autoBackupStore.snapshotNow()
        }
    }

    fun flushAutoBackupNow() {
        appScope.launch {
            autoBackupStore.snapshotNow()
        }
    }

    suspend fun onBackupRestored() = withContext(appScope.coroutineContext + NonCancellable) {
        prefs.edit()
            .putBoolean(KEY_INITIAL_SEED_DONE, true)
            .putBoolean(KEY_USER_CLEARED_DATA, false)
            .apply()
        autoBackupStore.snapshotNow()
    }

    suspend fun loadSampleData() = withContext(appScope.coroutineContext + NonCancellable) {
        SampleDataSeeder.seedSampleData(db)
        prefs.edit()
            .putBoolean(KEY_INITIAL_SEED_DONE, true)
            .putBoolean(KEY_USER_CLEARED_DATA, false)
            .apply()
        autoBackupStore.snapshotNow()
    }

    suspend fun clearAllLocalData() = withContext(appScope.coroutineContext + NonCancellable) {
        SampleDataSeeder.clearAllData(db)
        db.compTimeDao().deleteAll()
        prefs.edit()
            .putBoolean(KEY_INITIAL_SEED_DONE, true)
            .putBoolean(KEY_USER_CLEARED_DATA, true)
            .apply()
        autoBackupStore.clearBackups()
    }

    // --- Buildings ---
    suspend fun saveBuilding(building: BuildingEntity): Long =
        withContext(appScope.coroutineContext + NonCancellable) {
            val id = if (building.id == 0L) db.buildingDao().insert(building)
            else {
                db.buildingDao().update(building)
                building.id
            }
            markUserHasData()
            id
        }

    suspend fun deleteBuilding(building: BuildingEntity) =
        withContext(appScope.coroutineContext + NonCancellable) {
            db.buildingDao().delete(building)
            autoBackupStore.scheduleSnapshot()
        }

    // --- Contacts ---
    suspend fun saveContact(
        contact: ContactEntity,
        pushBirthdayCalendarId: Long? = null,
        buildingName: String = ""
    ): Long = withContext(appScope.coroutineContext + NonCancellable) {
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
        val id = if (toSave.id == 0L) db.contactDao().insert(toSave)
        else {
            db.contactDao().update(toSave)
            toSave.id
        }
        markUserHasData()
        id
    }

    suspend fun deleteContact(contact: ContactEntity) =
        withContext(appScope.coroutineContext + NonCancellable) {
            contact.linkedBirthdayEventId?.let { eventId ->
                googleCalendarRepository.deleteEvent(eventId)
            }
            db.contactDao().delete(contact)
            autoBackupStore.scheduleSnapshot()
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
    ): Long = withContext(appScope.coroutineContext + NonCancellable) {
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
        val id = if (toSave.id == 0L) db.taskDao().insert(toSave)
        else {
            db.taskDao().update(toSave)
            toSave.id
        }
        markUserHasData()
        id
    }

    suspend fun saveTasksForDates(
        template: TaskEntity,
        dates: List<LocalDate>,
        pushToCalendarId: Long? = null,
        locationName: String = "",
        extraDetails: String = ""
    ) = withContext(appScope.coroutineContext + NonCancellable) {
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

    suspend fun deleteTask(task: TaskEntity) =
        withContext(appScope.coroutineContext + NonCancellable) {
            task.linkedCalendarEventId?.let { eventId ->
                googleCalendarRepository.deleteEvent(eventId)
            }
            db.taskDao().delete(task)
            autoBackupStore.scheduleSnapshot()
        }

    suspend fun deleteTasks(tasks: List<TaskEntity>) =
        withContext(appScope.coroutineContext + NonCancellable) {
            tasks.forEach { deleteTask(it) }
        }

    // --- Shifts ---
    suspend fun saveShift(shift: ShiftScheduleEntity): Long =
        withContext(appScope.coroutineContext + NonCancellable) {
            val id = if (shift.id == 0L) db.shiftScheduleDao().insert(shift)
            else {
                db.shiftScheduleDao().update(shift)
                shift.id
            }
            markUserHasData()
            id
        }

    suspend fun saveShiftsForDates(
        template: ShiftScheduleEntity,
        dates: List<LocalDate>
    ) = withContext(appScope.coroutineContext + NonCancellable) {
        val uniqueDates = dates.distinct().sorted()
        uniqueDates.forEachIndexed { idx, date ->
            val shiftForDay = template.copy(
                id = if (idx == 0) template.id else 0L,
                dateEpochDay = date.toEpochDay()
            )
            saveShift(shiftForDay)
        }
    }

    suspend fun deleteShift(shift: ShiftScheduleEntity) =
        withContext(appScope.coroutineContext + NonCancellable) {
            db.shiftScheduleDao().delete(shift)
            autoBackupStore.scheduleSnapshot()
        }

    suspend fun deleteShifts(shifts: List<ShiftScheduleEntity>) =
        withContext(appScope.coroutineContext + NonCancellable) {
            shifts.forEach { db.shiftScheduleDao().delete(it) }
            autoBackupStore.scheduleSnapshot()
        }

    // --- Comp Time ---
    suspend fun saveCompTimeEntry(entry: CompTimeEntryEntity): Long =
        withContext(appScope.coroutineContext + NonCancellable) {
            val id = if (entry.id == 0L) db.compTimeDao().insert(entry)
            else {
                db.compTimeDao().update(entry)
                entry.id
            }
            markUserHasData()
            id
        }

    suspend fun deleteCompTimeEntry(entry: CompTimeEntryEntity) =
        withContext(appScope.coroutineContext + NonCancellable) {
            db.compTimeDao().delete(entry)
            autoBackupStore.scheduleSnapshot()
        }

    suspend fun clockInOrOutToday(nowMinutes: Int, todayEpochDay: Long = LocalDate.now().toEpochDay()) =
        withContext(appScope.coroutineContext + NonCancellable) {
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
            markUserHasData()
        }

    // --- Google Calendar Sync ---
    fun isDateNearEdgeOrOutsideLoadedRange(date: LocalDate): Boolean {
        val start = loadedRangeStart ?: return true
        val end = loadedRangeEnd ?: return true
        return date.isBefore(start.plusDays(14)) || date.isAfter(end.minusDays(14))
    }

    suspend fun refreshGoogleCalendars(
        centerDate: LocalDate = LocalDate.now(),
        forceSync: Boolean = false
    ) = calendarSyncMutex.withLock {
        val granted = googleCalendarRepository.hasReadPermission()
        _hasCalendarPermission.value = granted
        if (!granted) {
            loadedRangeStart = null
            loadedRangeEnd = null
            _deviceCalendars.value = emptyList()
            _calendarSyncStatus.value = CalendarSyncStatus.PermissionMissing
            _calendarEvents.value = if (_showSampleCalendarPreview.value) {
                googleCalendarRepository.buildSampleCalendarEvents(centerDate)
            } else {
                emptyList()
            }
            return@withLock
        }

        _calendarSyncStatus.value = CalendarSyncStatus.Syncing
        try {
            val calendars = googleCalendarRepository.fetchDeviceCalendars(googleOnly = false)
            _deviceCalendars.value = calendars

            if (forceSync && calendars.isNotEmpty()) {
                googleCalendarRepository.requestAccountCalendarSync(calendars)
                delay(600L)
            }

            reconcileCalendarPrefs(calendars)

            val rangeStart = centerDate.minusMonths(2).withDayOfMonth(1)
            val rangeEnd = centerDate.plusMonths(3).withDayOfMonth(1)
            val nameMap = calendars.associate { it.id to it.displayName }
            val realEvents = googleCalendarRepository.fetchEventsInRange(rangeStart, rangeEnd, nameMap)

            loadedRangeStart = rangeStart
            loadedRangeEnd = rangeEnd

            _calendarEvents.value = if (calendars.isEmpty() && _showSampleCalendarPreview.value) {
                googleCalendarRepository.buildSampleCalendarEvents(centerDate)
            } else {
                realEvents
            }

            _calendarSyncStatus.value = CalendarSyncStatus.Synced(
                calendarCount = calendars.size,
                eventCount = realEvents.size,
                syncedAtMillis = System.currentTimeMillis()
            )
        } catch (_: SecurityException) {
            _hasCalendarPermission.value = false
            _calendarSyncStatus.value = CalendarSyncStatus.PermissionMissing
        } catch (e: Exception) {
            _calendarSyncStatus.value = CalendarSyncStatus.Error(
                e.localizedMessage ?: "Unable to refresh Google Calendars right now."
            )
        }
    }

    /**
     * Reconciles stored calendar visibility preferences with live Android device calendars.
     * Matches existing user visibility by calendarId first, falling back to (accountName, displayName)
     * in case Android's CalendarProvider reassigned calendar _IDs during an overnight account re-sync.
     */
    private suspend fun reconcileCalendarPrefs(liveCalendars: List<DeviceCalendar>) {
        if (liveCalendars.isEmpty()) {
            return
        }
        val existing = db.calendarPrefDao().getAllPrefsOnce()
        val byId = existing.associateBy { it.calendarId }
        val byIdentity = existing.associateBy {
            it.accountName.trim().lowercase() to it.displayName.trim().lowercase()
        }

        val reconciled = liveCalendars.map { cal ->
            val prev = byId[cal.id]
                ?: byIdentity[cal.accountName.trim().lowercase() to cal.displayName.trim().lowercase()]
            CalendarPrefEntity(
                calendarId = cal.id,
                accountName = cal.accountName,
                displayName = cal.displayName,
                colorInt = cal.colorInt,
                isVisible = prev?.isVisible ?: true
            )
        }
        db.calendarPrefDao().upsertReplacing(reconciled)
        db.calendarPrefDao().deleteStaleNotIn(liveCalendars.map { it.id })
    }

    suspend fun setCalendarVisibility(calendarId: Long, visible: Boolean) {
        db.calendarPrefDao().setVisibility(calendarId, visible)
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SAMPLE_CALENDAR = "sample_calendar_preview"
        private const val KEY_SHOW_SHIFTS_IN_MONTH = "show_shifts_in_month_view"
        private const val KEY_INITIAL_SEED_DONE = "initial_seed_done"
        private const val KEY_USER_CLEARED_DATA = "user_explicitly_cleared_data"
        private const val KEY_LAST_PUSH_CALENDAR = "last_push_calendar_id"
        private const val KEY_COMP_BASELINE = "comp_daily_baseline_hours"
    }
}
