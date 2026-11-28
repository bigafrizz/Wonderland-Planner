package com.wonderland.taschasplanner.ui.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wonderland.taschasplanner.data.local.BuildingEntity
import com.wonderland.taschasplanner.data.local.ContactEntity
import com.wonderland.taschasplanner.data.local.ContactType
import com.wonderland.taschasplanner.data.local.NoteTodoEntity
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.TaskEntity
import com.wonderland.taschasplanner.data.model.ScheduleBatchMatcher
import com.wonderland.taschasplanner.ui.WonderlandViewModel
import com.wonderland.taschasplanner.ui.buildings.BuildingsScreen
import com.wonderland.taschasplanner.ui.components.BuildingEditorDialog
import com.wonderland.taschasplanner.ui.components.ContactEditorDialog
import com.wonderland.taschasplanner.ui.components.ShiftEditorDialog
import com.wonderland.taschasplanner.ui.components.TaskEditorDialog
import com.wonderland.taschasplanner.ui.comptime.CompTimeScreen
import com.wonderland.taschasplanner.ui.contacts.ContactsScreen
import com.wonderland.taschasplanner.ui.notes.NotesScreen
import com.wonderland.taschasplanner.ui.planner.PlannerScreen
import com.wonderland.taschasplanner.ui.settings.SettingsScreen
import com.wonderland.taschasplanner.ui.today.TodayScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class WonderlandDestination(val label: String, val icon: ImageVector) {
    PLANNER("Planner", Icons.Outlined.CalendarMonth),
    TODAY("Today", Icons.Outlined.AutoAwesome),
    NOTES("Notes", Icons.Outlined.EditNote),
    BUILDINGS("Buildings", Icons.Outlined.Apartment),
    COMP_TIME("Comp", Icons.Outlined.AccessTime),
    CONTACTS("Contacts", Icons.Outlined.Contacts),
    SETTINGS("Settings", Icons.Outlined.Settings)
}

@Composable
fun WonderlandApp(
    viewModel: WonderlandViewModel,
    widthSizeClass: WindowWidthSizeClass
) {
    val state by viewModel.uiState.collectAsState()
    val compTimeEntries by viewModel.compTimeEntries.collectAsState()
    val noteTodos by viewModel.noteTodos.collectAsState()
    val dailyBaselineHours by viewModel.dailyBaselineHours.collectAsState()
    val updateManifestUrl by viewModel.updateManifestUrl.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsState()
    val calendarSyncStatus by viewModel.calendarSyncStatus.collectAsState()
    val lastAutoBackupAtMillis by viewModel.lastAutoBackupAtMillis.collectAsState()
    val autoRecoveryMessage by viewModel.autoRecoveryMessage.collectAsState()
    val showShiftsInMonthView by viewModel.showShiftsInMonthView.collectAsState()
    val selectedShiftEmployeeIds by viewModel.selectedShiftEmployeeIds.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Re-sync calendars, check permissions/rollover on resume; flush safety backup on stop
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onAppResumed()
                Lifecycle.Event.ON_STOP -> viewModel.onAppStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Default directly to the Full Month Planner main view
    var currentDest by remember { mutableStateOf(WonderlandDestination.PLANNER) }
    var focusedBuildingId by remember { mutableStateOf<Long?>(null) }

    val openAppSettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = viewModel.exportBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open destination file.")
                }.onSuccess {
                    viewModel.notifyBackupExported(true, "Wonderland backup exported successfully!")
                }.onFailure { err ->
                    viewModel.notifyBackupExported(false, err.localizedMessage ?: "Export error")
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to read backup file.")
                }.onSuccess { json ->
                    viewModel.restoreBackupJson(json)
                }.onFailure { err ->
                    viewModel.notifyBackupExported(false, err.localizedMessage ?: "Read error")
                }
            }
        }
    }

    // Dialog states
    var showTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var taskDefaultDate by remember { mutableStateOf(LocalDate.now()) }
    var taskPrefillTitle by remember { mutableStateOf("") }
    var taskPrefillDescription by remember { mutableStateOf("") }
    var assigningSourceNote by remember { mutableStateOf<NoteTodoEntity?>(null) }

    var showShiftDialog by remember { mutableStateOf(false) }
    var editingShift by remember { mutableStateOf<ShiftScheduleEntity?>(null) }
    var shiftDefaultDate by remember { mutableStateOf(LocalDate.now()) }

    var showBuildingDialog by remember { mutableStateOf(false) }
    var editingBuilding by remember { mutableStateOf<BuildingEntity?>(null) }

    var showContactDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<ContactEntity?>(null) }
    var contactDefaultType by remember { mutableStateOf(ContactType.EMPLOYEE) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshCalendars(forceSync = true)
    }

    val requestCalendarPermissions: () -> Unit = {
        calendarPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        )
    }

    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded

    val screenContent: @Composable (Modifier) -> Unit = { mod ->
        Box(modifier = mod) {
            when (currentDest) {
                WonderlandDestination.PLANNER -> PlannerScreen(
                    state = state,
                    calendarSyncStatus = calendarSyncStatus,
                    showShiftsInMonthView = showShiftsInMonthView,
                    selectedShiftEmployeeIds = selectedShiftEmployeeIds,
                    isExpandedWidth = isExpanded,
                    onSelectDate = viewModel::selectDate,
                    onRefreshCalendars = { viewModel.refreshCalendars(forceSync = true) },
                    onRequestCalendarPermission = requestCalendarPermissions,
                    onOpenAppSettings = openAppSettings,
                    onToggleShowShiftsInMonth = viewModel::setShowShiftsInMonthView,
                    onSelectAllShiftEmployees = viewModel::selectAllShiftEmployees,
                    onToggleShiftEmployee = { empId ->
                        viewModel.toggleShiftEmployeeFilter(empId, state.employees.map { it.id })
                    },
                    onClearShiftEmployees = {
                        viewModel.clearShiftEmployeeSelection()
                    },
                    onAddTask = { date ->
                        editingTask = null
                        taskDefaultDate = date
                        showTaskDialog = true
                    },
                    onAddShift = { date ->
                        editingShift = null
                        shiftDefaultDate = date
                        showShiftDialog = true
                    },
                    onEditTask = {
                        editingTask = it
                        taskDefaultDate = LocalDate.ofEpochDay(it.dueDateEpochDay)
                        showTaskDialog = true
                    },
                    onCycleTaskStatus = viewModel::cycleTaskStatus,
                    onEditShift = {
                        editingShift = it
                        shiftDefaultDate = LocalDate.ofEpochDay(it.dateEpochDay)
                        showShiftDialog = true
                    }
                )

                WonderlandDestination.TODAY -> TodayScreen(
                    state = state,
                    onToggleTheme = viewModel::toggleQuickTheme,
                    onAddTask = {
                        editingTask = null
                        taskDefaultDate = LocalDate.now()
                        showTaskDialog = true
                    },
                    onAddShift = {
                        editingShift = null
                        shiftDefaultDate = LocalDate.now()
                        showShiftDialog = true
                    },
                    onEditTask = {
                        editingTask = it
                        taskDefaultDate = LocalDate.ofEpochDay(it.dueDateEpochDay)
                        showTaskDialog = true
                    },
                    onCycleTaskStatus = viewModel::cycleTaskStatus,
                    onEditShift = {
                        editingShift = it
                        shiftDefaultDate = LocalDate.ofEpochDay(it.dateEpochDay)
                        showShiftDialog = true
                    },
                    onOpenBuilding = { bldgId ->
                        focusedBuildingId = bldgId
                        currentDest = WonderlandDestination.BUILDINGS
                    }
                )

                WonderlandDestination.NOTES -> NotesScreen(
                    notes = noteTodos,
                    isExpandedWidth = isExpanded,
                    onCreateNote = { onCreated ->
                        viewModel.createNoteTodo(onCreated = onCreated)
                    },
                    onSaveNote = viewModel::saveNoteTodo,
                    onToggleCompleted = viewModel::toggleNoteTodoCompleted,
                    onTogglePinned = viewModel::toggleNoteTodoPinned,
                    onDeleteNote = viewModel::deleteNoteTodo,
                    onAssignToCalendar = { note ->
                        editingTask = null
                        taskDefaultDate = state.selectedDate
                        taskPrefillTitle = note.header.trim()
                        taskPrefillDescription = note.body.trim()
                        assigningSourceNote = note
                        showTaskDialog = true
                    }
                )

                WonderlandDestination.BUILDINGS -> BuildingsScreen(
                    state = state,
                    initialBuildingId = focusedBuildingId,
                    onAddBuilding = {
                        editingBuilding = null
                        showBuildingDialog = true
                    },
                    onEditBuilding = {
                        editingBuilding = it
                        showBuildingDialog = true
                    },
                    onAddShift = {
                        editingShift = null
                        shiftDefaultDate = state.selectedDate
                        showShiftDialog = true
                    },
                    onEditShift = {
                        editingShift = it
                        shiftDefaultDate = LocalDate.ofEpochDay(it.dateEpochDay)
                        showShiftDialog = true
                    },
                    onCycleShiftStatus = viewModel::cycleShiftStatus,
                    onDeleteShifts = viewModel::deleteShifts,
                    onAddTask = {
                        editingTask = null
                        taskDefaultDate = state.selectedDate
                        showTaskDialog = true
                    },
                    onEditTask = {
                        editingTask = it
                        taskDefaultDate = LocalDate.ofEpochDay(it.dueDateEpochDay)
                        showTaskDialog = true
                    },
                    onCycleTaskStatus = viewModel::cycleTaskStatus,
                    onDeleteTasks = viewModel::deleteTasks
                )

                WonderlandDestination.COMP_TIME -> CompTimeScreen(
                    entries = compTimeEntries,
                    dailyBaselineHours = dailyBaselineHours,
                    onSetBaselineHours = viewModel::setDailyBaselineHours,
                    onClockInOrOutNow = viewModel::clockInOrOutToday,
                    onSaveEntry = viewModel::saveCompTimeEntry,
                    onDeleteEntry = viewModel::deleteCompTimeEntry
                )

                WonderlandDestination.CONTACTS -> ContactsScreen(
                    state = state,
                    isExpandedWidth = isExpanded,
                    onAddContact = { type ->
                        editingContact = null
                        contactDefaultType = type
                        showContactDialog = true
                    },
                    onEditContact = {
                        editingContact = it
                        contactDefaultType = it.type
                        showContactDialog = true
                    },
                    onToggleFavorite = viewModel::toggleFavoriteContact
                )

                WonderlandDestination.SETTINGS -> SettingsScreen(
                    state = state,
                    calendarSyncStatus = calendarSyncStatus,
                    lastAutoBackupAtMillis = lastAutoBackupAtMillis,
                    autoRecoveryMessage = autoRecoveryMessage,
                    updateManifestUrl = updateManifestUrl,
                    updateState = updateState,
                    backupStatusMessage = backupStatusMessage,
                    onSelectTheme = viewModel::setThemeMode,
                    onRequestCalendarPermission = requestCalendarPermissions,
                    onOpenAppSettings = openAppSettings,
                    onRefreshCalendars = { viewModel.refreshCalendars(forceSync = true) },
                    onToggleCalendarVisibility = viewModel::setCalendarVisibility,
                    onToggleSampleCalendarPreview = viewModel::setShowSampleCalendarPreview,
                    onUpdateManifestUrlChange = viewModel::setUpdateManifestUrl,
                    onCheckForUpdates = viewModel::checkForUpdates,
                    onDownloadAndInstallUpdate = viewModel::downloadAndInstallUpdate,
                    onExportBackup = {
                        exportBackupLauncher.launch("taschas-wonderland-backup-${LocalDate.now()}.json")
                    },
                    onImportBackup = {
                        importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    onLoadSampleData = viewModel::loadSampleData,
                    onClearAllData = viewModel::clearAllLocalData
                )
            }
        }
    }

    if (useNavRail) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        WonderlandDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = currentDest == dest,
                                onClick = { currentDest = dest },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }
                screenContent(Modifier.weight(1f).fillMaxSize())
            }
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    WonderlandDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentDest == dest,
                            onClick = { currentDest = dest },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            screenContent(Modifier.fillMaxSize().padding(innerPadding))
        }
    }

    // --- Modal Editors ---
    if (showTaskDialog) {
        val matchingTasks = editingTask?.let { ScheduleBatchMatcher.matchingTasks(it, state.tasks) }.orEmpty()
        TaskEditorDialog(
            initial = editingTask,
            defaultDate = taskDefaultDate,
            buildings = state.buildings,
            employees = state.employees,
            customers = state.customers,
            writableCalendars = state.deviceCalendars.filter { it.isWritable },
            defaultCalendarId = viewModel.lastPushCalendarId(),
            matchingCount = matchingTasks.size.coerceAtLeast(1),
            prefillTitle = taskPrefillTitle,
            prefillDescription = taskPrefillDescription,
            onDismiss = {
                showTaskDialog = false
                taskPrefillTitle = ""
                taskPrefillDescription = ""
                assigningSourceNote = null
            },
            onSave = { task, targetDates, pushCalId ->
                val bldgName = task.buildingId?.let { state.buildingsById[it]?.name } ?: ""
                val empName = task.assignedEmployeeId?.let { state.contactsById[it]?.fullName }
                val custName = task.customerId?.let { state.contactsById[it]?.fullName }
                val extraDetails = listOfNotNull(
                    empName?.let { "Assigned: $it" },
                    custName?.let { "Customer: $it" },
                    "Priority: ${task.priority.displayLabel}"
                ).joinToString(" | ")
                viewModel.saveTasksForDates(
                    template = task,
                    dates = targetDates,
                    pushToCalendarId = pushCalId,
                    buildingName = bldgName,
                    extraDetails = extraDetails,
                    sourceNote = assigningSourceNote
                )
            },
            onDelete = viewModel::deleteTask,
            onDeleteMatching = { target ->
                viewModel.deleteTasks(ScheduleBatchMatcher.matchingTasks(target, state.tasks))
            }
        )
    }

    if (showShiftDialog) {
        val matchingShifts = editingShift?.let { ScheduleBatchMatcher.matchingShifts(it, state.shifts) }.orEmpty()
        ShiftEditorDialog(
            initial = editingShift,
            defaultDate = shiftDefaultDate,
            buildings = state.buildings,
            employees = state.employees,
            matchingCount = matchingShifts.size.coerceAtLeast(1),
            onDismiss = { showShiftDialog = false },
            onSave = { shift, targetDates ->
                viewModel.saveShiftsForDates(shift, targetDates)
            },
            onDelete = viewModel::deleteShift,
            onDeleteMatching = { target ->
                viewModel.deleteShifts(ScheduleBatchMatcher.matchingShifts(target, state.shifts))
            }
        )
    }

    if (showBuildingDialog) {
        BuildingEditorDialog(
            initial = editingBuilding,
            onDismiss = { showBuildingDialog = false },
            onSave = viewModel::saveBuilding,
            onDelete = viewModel::deleteBuilding
        )
    }

    if (showContactDialog) {
        ContactEditorDialog(
            initial = editingContact,
            defaultType = contactDefaultType,
            buildings = state.buildings,
            writableCalendars = state.deviceCalendars.filter { it.isWritable },
            defaultCalendarId = viewModel.lastPushCalendarId(),
            onDismiss = { showContactDialog = false },
            onSave = { contact, pushBdayCalId ->
                val bldgName = contact.buildingId?.let { state.buildingsById[it]?.name } ?: ""
                viewModel.saveContact(contact, pushBdayCalId, bldgName)
            },
            onDelete = viewModel::deleteContact
        )
    }
}
