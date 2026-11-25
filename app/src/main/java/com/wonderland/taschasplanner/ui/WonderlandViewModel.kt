package com.wonderland.taschasplanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wonderland.taschasplanner.data.backup.WonderlandBackupManager
import com.wonderland.taschasplanner.data.local.BuildingEntity
import com.wonderland.taschasplanner.data.local.CalendarPrefEntity
import com.wonderland.taschasplanner.data.local.ContactEntity
import com.wonderland.taschasplanner.data.local.ContactType
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.TaskEntity
import com.wonderland.taschasplanner.data.local.ThemeMode
import com.wonderland.taschasplanner.data.model.CalendarEvent
import com.wonderland.taschasplanner.data.model.DeviceCalendar
import com.wonderland.taschasplanner.data.model.ShiftConflictDetector
import com.wonderland.taschasplanner.data.repository.PlannerRepository
import com.wonderland.taschasplanner.data.update.AppUpdateManager
import com.wonderland.taschasplanner.data.update.RemoteAppVersion
import com.wonderland.taschasplanner.data.update.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WonderlandUiState(
    val themeMode: ThemeMode = ThemeMode.STORYBOOK_LIGHT,
    val buildings: List<BuildingEntity> = emptyList(),
    val contacts: List<ContactEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val shifts: List<ShiftScheduleEntity> = emptyList(),
    val deviceCalendars: List<DeviceCalendar> = emptyList(),
    val calendarPrefs: List<CalendarPrefEntity> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val hasCalendarPermission: Boolean = false,
    val showSampleCalendarPreview: Boolean = true,
    val selectedDate: LocalDate = LocalDate.now(),
    val conflictingShiftIds: Set<Long> = emptySet()
) {
    val employees: List<ContactEntity> get() = contacts.filter { it.type == ContactType.EMPLOYEE }
    val customers: List<ContactEntity> get() = contacts.filter { it.type == ContactType.CUSTOMER }
    val buildingsById: Map<Long, BuildingEntity> get() = buildings.associateBy { it.id }
    val contactsById: Map<Long, ContactEntity> get() = contacts.associateBy { it.id }
    val visibleCalendarIds: Set<Long>?
        get() = if (calendarPrefs.isEmpty()) null
        else calendarPrefs.filter { it.isVisible }.map { it.calendarId }.toSet() + (-1L)
}

class WonderlandViewModel(
    private val repository: PlannerRepository,
    private val backupManager: WonderlandBackupManager,
    private val updateManager: AppUpdateManager
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _updateManifestUrl = MutableStateFlow(updateManager.getManifestUrl())
    val updateManifestUrl: StateFlow<String> = _updateManifestUrl.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    val updateState: StateFlow<UpdateCheckResult> = _updateState.asStateFlow()

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    private val dataFlow = combine(
        repository.buildings,
        repository.contacts,
        repository.tasks,
        repository.shifts,
        repository.calendarPrefs
    ) { buildings, contacts, tasks, shifts, calPrefs ->
        DataBundle(buildings, contacts, tasks, shifts, calPrefs)
    }

    private val calMetaFlow = combine(
        repository.deviceCalendars,
        repository.calendarEvents,
        repository.hasCalendarPermission,
        repository.showSampleCalendarPreview,
        repository.themeMode
    ) { deviceCals, events, hasPerm, showSample, theme ->
        CalMetaBundle(deviceCals, events, hasPerm, showSample, theme)
    }

    val uiState: StateFlow<WonderlandUiState> = combine(
        dataFlow,
        calMetaFlow,
        _selectedDate
    ) { data, meta, selectedDate ->
        WonderlandUiState(
            themeMode = meta.themeMode,
            buildings = data.buildings,
            contacts = data.contacts,
            tasks = data.tasks,
            shifts = data.shifts,
            deviceCalendars = meta.deviceCalendars,
            calendarPrefs = data.calendarPrefs,
            calendarEvents = meta.calendarEvents,
            hasCalendarPermission = meta.hasCalendarPermission,
            showSampleCalendarPreview = meta.showSampleCalendarPreview,
            selectedDate = selectedDate,
            conflictingShiftIds = ShiftConflictDetector.conflictingShiftIds(data.shifts)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WonderlandUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            repository.refreshGoogleCalendars(_selectedDate.value)
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun refreshCalendars() {
        viewModelScope.launch {
            repository.refreshGoogleCalendars(_selectedDate.value)
        }
    }

    fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)
    fun toggleQuickTheme() = repository.toggleQuickTheme()

    fun setShowSampleCalendarPreview(enabled: Boolean) {
        repository.setShowSampleCalendarPreview(enabled)
        refreshCalendars()
    }

    fun setCalendarVisibility(calendarId: Long, visible: Boolean) {
        viewModelScope.launch { repository.setCalendarVisibility(calendarId, visible) }
    }

    fun saveBuilding(building: BuildingEntity) {
        viewModelScope.launch { repository.saveBuilding(building) }
    }

    fun deleteBuilding(building: BuildingEntity) {
        viewModelScope.launch { repository.deleteBuilding(building) }
    }

    fun saveContact(contact: ContactEntity) {
        viewModelScope.launch { repository.saveContact(contact) }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch { repository.deleteContact(contact) }
    }

    fun toggleFavoriteContact(contact: ContactEntity) {
        viewModelScope.launch { repository.saveContact(contact.copy(isFavorite = !contact.isFavorite)) }
    }

    fun lastPushCalendarId(): Long? = repository.getLastPushCalendarId()

    fun saveTask(
        task: TaskEntity,
        pushToCalendarId: Long? = null,
        buildingName: String = "",
        extraDetails: String = ""
    ) {
        viewModelScope.launch {
            repository.saveTask(task, pushToCalendarId, buildingName, extraDetails)
            if (pushToCalendarId != null || task.linkedCalendarEventId != null) {
                repository.refreshGoogleCalendars(_selectedDate.value)
            }
        }
    }

    fun saveTasksForDates(
        template: TaskEntity,
        dates: List<LocalDate>,
        pushToCalendarId: Long? = null,
        buildingName: String = "",
        extraDetails: String = ""
    ) {
        viewModelScope.launch {
            repository.saveTasksForDates(template, dates, pushToCalendarId, buildingName, extraDetails)
            if (pushToCalendarId != null || template.linkedCalendarEventId != null) {
                repository.refreshGoogleCalendars(_selectedDate.value)
            }
        }
    }

    fun cycleTaskStatus(task: TaskEntity) {
        viewModelScope.launch { repository.saveTask(task.copy(status = task.status.next())) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
            if (task.linkedCalendarEventId != null) {
                repository.refreshGoogleCalendars(_selectedDate.value)
            }
        }
    }

    fun saveShift(shift: ShiftScheduleEntity) {
        viewModelScope.launch { repository.saveShift(shift) }
    }

    fun saveShiftsForDates(template: ShiftScheduleEntity, dates: List<LocalDate>) {
        viewModelScope.launch { repository.saveShiftsForDates(template, dates) }
    }

    fun cycleShiftStatus(shift: ShiftScheduleEntity) {
        viewModelScope.launch { repository.saveShift(shift.copy(status = shift.status.next())) }
    }

    fun deleteShift(shift: ShiftScheduleEntity) {
        viewModelScope.launch { repository.deleteShift(shift) }
    }

    fun loadSampleData() {
        viewModelScope.launch { repository.loadSampleData() }
    }

    fun clearAllLocalData() {
        viewModelScope.launch { repository.clearAllLocalData() }
    }

    // --- Backup & Restore ---
    suspend fun exportBackupJson(): String = backupManager.exportJson()

    fun notifyBackupExported(success: Boolean, message: String) {
        _backupStatusMessage.value = if (success) message else "Backup failed: $message"
    }

    fun restoreBackupJson(jsonString: String) {
        viewModelScope.launch {
            runCatching {
                val restored = backupManager.restoreFromJson(jsonString)
                "Restored ${restored.buildings.size} buildings, ${restored.contacts.size} contacts, ${restored.tasks.size} tasks, and ${restored.shifts.size} shifts."
            }.onSuccess { msg ->
                _backupStatusMessage.value = msg
            }.onFailure { err ->
                _backupStatusMessage.value = "Restore failed: ${err.localizedMessage ?: "Invalid backup file"}"
            }
        }
    }

    // --- In-App Updates ---
    fun setUpdateManifestUrl(url: String) {
        _updateManifestUrl.value = url
        updateManager.saveManifestUrl(url)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateCheckResult.Checking
            _updateState.value = updateManager.checkForUpdate(_updateManifestUrl.value)
        }
    }

    fun downloadAndInstallUpdate(remote: RemoteAppVersion) {
        viewModelScope.launch {
            _updateState.value = UpdateCheckResult.Downloading(0)
            val result = updateManager.downloadApk(remote.apkUrl) { pct ->
                _updateState.value = UpdateCheckResult.Downloading(pct)
            }
            result.onSuccess { apkFile ->
                _updateState.value = UpdateCheckResult.UpdateAvailable(remote)
                updateManager.launchInPlaceInstaller(apkFile)
            }.onFailure { err ->
                _updateState.value = UpdateCheckResult.Error(err.localizedMessage ?: "Download failed.")
            }
        }
    }

    private data class DataBundle(
        val buildings: List<BuildingEntity>,
        val contacts: List<ContactEntity>,
        val tasks: List<TaskEntity>,
        val shifts: List<ShiftScheduleEntity>,
        val calendarPrefs: List<CalendarPrefEntity>
    )

    private data class CalMetaBundle(
        val deviceCalendars: List<DeviceCalendar>,
        val calendarEvents: List<CalendarEvent>,
        val hasCalendarPermission: Boolean,
        val showSampleCalendarPreview: Boolean,
        val themeMode: ThemeMode
    )

    class Factory(
        private val repository: PlannerRepository,
        private val backupManager: WonderlandBackupManager,
        private val updateManager: AppUpdateManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WonderlandViewModel(repository, backupManager, updateManager) as T
        }
    }
}
