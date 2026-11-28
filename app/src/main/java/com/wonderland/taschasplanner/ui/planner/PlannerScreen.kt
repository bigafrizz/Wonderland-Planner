package com.wonderland.taschasplanner.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderland.taschasplanner.data.local.ContactEntity
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.TaskEntity
import com.wonderland.taschasplanner.data.model.CalendarSyncStatus
import com.wonderland.taschasplanner.data.model.PlannerAggregator
import com.wonderland.taschasplanner.data.model.PlannerItemSource
import com.wonderland.taschasplanner.data.model.UnifiedPlannerItem
import com.wonderland.taschasplanner.data.model.WonderlandTimeFormat
import com.wonderland.taschasplanner.ui.WonderlandUiState
import com.wonderland.taschasplanner.ui.components.CheshireEmptyState
import com.wonderland.taschasplanner.ui.components.OrnateGoldDivider
import com.wonderland.taschasplanner.ui.components.WonderlandCard
import com.wonderland.taschasplanner.ui.theme.LocalWonderlandColors
import com.wonderland.taschasplanner.ui.today.UnifiedAgendaRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PlannerViewMode(val label: String) {
    MONTH("Monthly"),
    WEEK("Weekly"),
    DAY("Daily")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    state: WonderlandUiState,
    calendarSyncStatus: CalendarSyncStatus,
    showShiftsInMonthView: Boolean,
    selectedShiftEmployeeIds: Set<Long>?,
    isExpandedWidth: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onRefreshCalendars: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onToggleShowShiftsInMonth: (Boolean) -> Unit,
    onSelectAllShiftEmployees: () -> Unit,
    onToggleShiftEmployee: (Long) -> Unit,
    onClearShiftEmployees: () -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onAddShift: (LocalDate) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onCycleTaskStatus: (TaskEntity) -> Unit,
    onEditShift: (ShiftScheduleEntity) -> Unit
) {
    var mode by remember { mutableStateOf(PlannerViewMode.MONTH) }
    var buildingFilterId by remember { mutableStateOf<Long?>(null) }
    var itinerarySheetDate by remember { mutableStateOf<LocalDate?>(null) }

    val selectedDate = state.selectedDate
    val employeeNames = remember(state.contacts) { state.contacts.associate { it.id to it.fullName } }

    // Complete item set (used for Daily, Weekly, and the Daily Itinerary Card on day click)
    val fullUnified = remember(state, buildingFilterId, selectedDate.year) {
        PlannerAggregator.buildUnifiedItems(
            tasks = state.tasks,
            shifts = state.shifts,
            calendarEvents = state.calendarEvents,
            buildingsById = state.buildingsById,
            employeeNamesById = employeeNames,
            visibleCalendarIds = state.visibleCalendarIds,
            buildingFilterId = buildingFilterId,
            includeShifts = true,
            selectedShiftEmployeeIds = null,
            contactsWithBirthdays = state.contacts,
            birthdayYears = listOf(selectedDate.year - 1, selectedDate.year, selectedDate.year + 1)
        )
    }

    // Month-grid filtered item set (respects Show/Hide Shifts & All/Multi-select employee filter)
    val monthGridUnified = remember(
        state,
        buildingFilterId,
        selectedDate.year,
        showShiftsInMonthView,
        selectedShiftEmployeeIds
    ) {
        PlannerAggregator.buildUnifiedItems(
            tasks = state.tasks,
            shifts = state.shifts,
            calendarEvents = state.calendarEvents,
            buildingsById = state.buildingsById,
            employeeNamesById = employeeNames,
            visibleCalendarIds = state.visibleCalendarIds,
            buildingFilterId = buildingFilterId,
            includeShifts = showShiftsInMonthView,
            selectedShiftEmployeeIds = selectedShiftEmployeeIds,
            contactsWithBirthdays = state.contacts,
            birthdayYears = listOf(selectedDate.year - 1, selectedDate.year, selectedDate.year + 1)
        )
    }

    val fullItemsByDay = remember(fullUnified) { fullUnified.groupBy { it.dateEpochDay } }
    val monthGridItemsByDay = remember(monthGridUnified) { monthGridUnified.groupBy { it.dateEpochDay } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddTask(selectedDate) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add task")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Top bar: Title + Today + Refresh Google Calendar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Wonderland Planner",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    val subtitle = when (calendarSyncStatus) {
                        CalendarSyncStatus.Syncing -> "${WonderlandTimeFormat.formatMonthYear(selectedDate)} • Syncing calendars..."
                        is CalendarSyncStatus.Synced -> WonderlandTimeFormat.formatMonthYear(selectedDate)
                        CalendarSyncStatus.PermissionMissing -> "${WonderlandTimeFormat.formatMonthYear(selectedDate)} • Calendar disconnected"
                        is CalendarSyncStatus.Error -> "${WonderlandTimeFormat.formatMonthYear(selectedDate)} • Calendar sync issue"
                        CalendarSyncStatus.Idle -> WonderlandTimeFormat.formatMonthYear(selectedDate)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSelectDate(LocalDate.now()) }) {
                        Icon(Icons.Outlined.Today, contentDescription = "Jump to today")
                    }
                    IconButton(
                        onClick = onRefreshCalendars,
                        enabled = calendarSyncStatus !is CalendarSyncStatus.Syncing
                    ) {
                        if (calendarSyncStatus is CalendarSyncStatus.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Google Calendars")
                        }
                    }
                }
            }

            // Reconnect banner if calendar permission was lost or sync errored
            if (calendarSyncStatus is CalendarSyncStatus.PermissionMissing || calendarSyncStatus is CalendarSyncStatus.Error) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Outlined.SyncProblem,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (calendarSyncStatus is CalendarSyncStatus.PermissionMissing) {
                                    "Google Calendar permission needed"
                                } else {
                                    (calendarSyncStatus as CalendarSyncStatus.Error).message
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (calendarSyncStatus is CalendarSyncStatus.PermissionMissing) {
                            Row {
                                TextButton(onClick = onRequestCalendarPermission) {
                                    Text("Connect")
                                }
                                TextButton(onClick = onOpenAppSettings) {
                                    Text("Settings")
                                }
                            }
                        } else {
                            TextButton(onClick = onRefreshCalendars) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Monthly / Weekly / Daily Switcher
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PlannerViewMode.entries.forEachIndexed { index, viewMode ->
                    SegmentedButton(
                        selected = mode == viewMode,
                        onClick = { mode = viewMode },
                        shape = SegmentedButtonDefaults.itemShape(index, PlannerViewMode.entries.size)
                    ) {
                        Text(viewMode.label)
                    }
                }
            }

            // Building Filter Strip
            if (state.buildings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = buildingFilterId == null,
                        onClick = { buildingFilterId = null },
                        label = { Text("All Buildings & Cal") }
                    )
                    state.buildings.forEach { b ->
                        FilterChip(
                            selected = buildingFilterId == b.id,
                            onClick = { buildingFilterId = if (buildingFilterId == b.id) null else b.id },
                            label = { Text(b.name) }
                        )
                    }
                }
            }

            // Full Month Shift Visibility Toggle + All / Multi-Select Employee Dropdown
            if (mode == PlannerViewMode.MONTH) {
                Spacer(modifier = Modifier.height(4.dp))
                MonthShiftFilterRow(
                    showShifts = showShiftsInMonthView,
                    employees = state.employees,
                    buildingsById = state.buildingsById,
                    selectedEmployeeIds = selectedShiftEmployeeIds,
                    onToggleShowShifts = onToggleShowShiftsInMonth,
                    onSelectAllEmployees = onSelectAllShiftEmployees,
                    onClearEmployees = onClearShiftEmployees,
                    onToggleEmployee = onToggleShiftEmployee
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            when (mode) {
                PlannerViewMode.MONTH -> FullMonthPlannerView(
                    selectedDate = selectedDate,
                    itemsByDay = monthGridItemsByDay,
                    employeeNamesById = employeeNames,
                    isExpandedWidth = isExpandedWidth,
                    onNavigateMonth = { newMonthDate -> onSelectDate(newMonthDate) },
                    onDayClicked = { clickedDate ->
                        onSelectDate(clickedDate)
                        itinerarySheetDate = clickedDate
                    }
                )

                PlannerViewMode.WEEK -> WeeklyPlannerView(
                    selectedDate = selectedDate,
                    itemsByDay = fullItemsByDay,
                    onSelectDate = onSelectDate,
                    onEditTask = onEditTask,
                    onCycleTaskStatus = onCycleTaskStatus,
                    onEditShift = onEditShift
                )

                PlannerViewMode.DAY -> DailyPlannerView(
                    date = selectedDate,
                    items = fullItemsByDay[selectedDate.toEpochDay()].orEmpty(),
                    onPrevDay = { onSelectDate(selectedDate.minusDays(1)) },
                    onNextDay = { onSelectDate(selectedDate.plusDays(1)) },
                    onEditTask = onEditTask,
                    onCycleTaskStatus = onCycleTaskStatus,
                    onEditShift = onEditShift
                )
            }
        }
    }

    // Slide-up Daily Itinerary Card triggered when a day is clicked on the Full Month view
    val activeSheetDate = itinerarySheetDate
    if (activeSheetDate != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val dayItems = fullItemsByDay[activeSheetDate.toEpochDay()].orEmpty()
        ModalBottomSheet(
            onDismissRequest = { itinerarySheetDate = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DailyItineraryCardContent(
                date = activeSheetDate,
                items = dayItems,
                onPrevDay = {
                    val prev = activeSheetDate.minusDays(1)
                    itinerarySheetDate = prev
                    onSelectDate(prev)
                },
                onNextDay = {
                    val next = activeSheetDate.plusDays(1)
                    itinerarySheetDate = next
                    onSelectDate(next)
                },
                onClose = { itinerarySheetDate = null },
                onAddTask = { onAddTask(activeSheetDate) },
                onAddShift = { onAddShift(activeSheetDate) },
                onEditTask = onEditTask,
                onCycleTaskStatus = onCycleTaskStatus,
                onEditShift = onEditShift
            )
        }
    }
}

@Composable
private fun MonthShiftFilterRow(
    showShifts: Boolean,
    employees: List<ContactEntity>,
    buildingsById: Map<Long, com.wonderland.taschasplanner.data.local.BuildingEntity>,
    selectedEmployeeIds: Set<Long>?,
    onToggleShowShifts: (Boolean) -> Unit,
    onSelectAllEmployees: () -> Unit,
    onClearEmployees: () -> Unit,
    onToggleEmployee: (Long) -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    val activeSelectedIds = selectedEmployeeIds
    val isAllSelected = activeSelectedIds == null ||
        (employees.isNotEmpty() && employees.all { it.id in activeSelectedIds })

    val dropdownLabel = when {
        employees.isEmpty() -> "No Employees"
        isAllSelected -> "All Employees"
        activeSelectedIds.isNullOrEmpty() -> "No Staff Selected"
        activeSelectedIds.size == 1 -> {
            val id = activeSelectedIds.first()
            employees.find { it.id == id }?.fullName ?: "1 Employee"
        }
        else -> "${activeSelectedIds.size} Employees"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = showShifts,
            onClick = { onToggleShowShifts(!showShifts) },
            leadingIcon = {
                Icon(
                    imageVector = if (showShifts) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            label = { Text(if (showShifts) "Shifts: Shown" else "Shifts: Hidden") }
        )

        if (showShifts) {
            Box {
                FilterChip(
                    selected = !isAllSelected,
                    onClick = { dropdownExpanded = true },
                    label = {
                        Text(
                            text = dropdownLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = "Filter shifts by employee",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isAllSelected,
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "All Employees",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        },
                        onClick = {
                            if (isAllSelected) onClearEmployees() else onSelectAllEmployees()
                        }
                    )

                    if (employees.isNotEmpty()) {
                        HorizontalDivider()
                        employees.forEach { emp ->
                            val isChecked = isAllSelected || (activeSelectedIds != null && emp.id in activeSelectedIds)
                            val bldgColor = emp.buildingId?.let { buildingsById[it]?.colorHex }
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (bldgColor != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(bldgColor))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = emp.fullName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                onClick = { onToggleEmployee(emp.id) }
                            )
                        }
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onSelectAllEmployees) {
                            Text("Select All")
                        }
                        TextButton(onClick = { dropdownExpanded = false }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullMonthPlannerView(
    selectedDate: LocalDate,
    itemsByDay: Map<Long, List<UnifiedPlannerItem>>,
    employeeNamesById: Map<Long, String>,
    isExpandedWidth: Boolean,
    onNavigateMonth: (LocalDate) -> Unit,
    onDayClicked: (LocalDate) -> Unit
) {
    val ym = YearMonth.from(selectedDate)
    val firstDay = ym.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value % 7 // Sunday = 0
    val totalCells = leadingBlanks + ym.lengthOfMonth()
    val rows = (totalCells + 6) / 7
    val today = LocalDate.now()
    val maxPillsPerCell = if (isExpandedWidth) 4 else 3

    Column(modifier = Modifier.fillMaxSize()) {
        // Month navigation bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onNavigateMonth(selectedDate.minusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                text = WonderlandTimeFormat.formatMonthYear(selectedDate),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = { onNavigateMonth(selectedDate.plusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next month")
            }
        }

        // Weekday headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { d ->
                Text(
                    text = d,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Full-viewport month calendar card
        WonderlandCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (c in 0 until 7) {
                            val cellIdx = r * 7 + c
                            val dayNum = cellIdx - leadingBlanks + 1
                            if (dayNum in 1..ym.lengthOfMonth()) {
                                val cellDate = ym.atDay(dayNum)
                                val isSelected = cellDate == selectedDate
                                val isToday = cellDate == today
                                val dayItems = itemsByDay[cellDate.toEpochDay()].orEmpty()

                                FullMonthDayCell(
                                    dayNum = dayNum,
                                    isToday = isToday,
                                    isSelected = isSelected,
                                    dayItems = dayItems,
                                    employeeNamesById = employeeNamesById,
                                    maxPills = maxPillsPerCell,
                                    onClick = { onDayClicked(cellDate) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullMonthDayCell(
    dayNum: Int,
    isToday: Boolean,
    isSelected: Boolean,
    dayItems: List<UnifiedPlannerItem>,
    employeeNamesById: Map<Long, String>,
    maxPills: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.secondary
        else -> LocalWonderlandColors.current.cardBorder.copy(alpha = 0.45f)
    }
    val cellBg = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
        else -> Color.Transparent
    }

    // Prioritize visible shifts first so "who is on shift" stands out clearly in the month grid,
    // followed by birthdays, Google Calendar events, and tasks.
    val orderedPills = remember(dayItems) {
        dayItems.sortedBy { item ->
            when (item.source) {
                PlannerItemSource.SHIFT -> 0
                PlannerItemSource.BIRTHDAY -> 1
                PlannerItemSource.GOOGLE_CALENDAR -> 2
                PlannerItemSource.TASK -> 3
            }
        }
    }

    val visiblePills = orderedPills.take(maxPills)
    val overflowCount = (orderedPills.size - visiblePills.size).coerceAtLeast(0)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cellBg)
            .border(
                width = if (isSelected || isToday) 1.5.dp else 0.75.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Day number header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .padding(horizontal = if (isToday) 5.dp else 2.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayNum.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
            if (overflowCount > 0) {
                Text(
                    text = "+$overflowCount",
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Compact event & shift pills inside the day cell
        visiblePills.forEach { item ->
            val accent = Color(item.accentColorHex)
            val pillLabel = when (item.source) {
                PlannerItemSource.SHIFT -> {
                    val fullName = item.employeeId?.let { employeeNamesById[it] } ?: item.title
                    fullName.substringBefore(" ").ifBlank { fullName }
                }
                PlannerItemSource.BIRTHDAY -> "\uD83C\uDF82 ${item.title.removePrefix("\uD83C\uDF82 ").substringBefore("'s")}"
                PlannerItemSource.GOOGLE_CALENDAR -> item.title
                PlannerItemSource.TASK -> item.title
            }
            val isShift = item.source == PlannerItemSource.SHIFT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = if (isShift) 0.22f else 0.14f))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = pillLabel,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = if (isShift) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isShift) MaterialTheme.colorScheme.onSurface else accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DailyItineraryCardContent(
    date: LocalDate,
    items: List<UnifiedPlannerItem>,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onClose: () -> Unit,
    onAddTask: () -> Unit,
    onAddShift: () -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onCycleTaskStatus: (TaskEntity) -> Unit,
    onEditShift: (ShiftScheduleEntity) -> Unit
) {
    val shiftCount = items.count { it.source == PlannerItemSource.SHIFT }
    val taskCount = items.count { it.source == PlannerItemSource.TASK }
    val calCount = items.count { it.source == PlannerItemSource.GOOGLE_CALENDAR }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 640.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        // Itinerary Card Header with day steppers & close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onPrevDay) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous day")
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Daily Itinerary",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = WonderlandTimeFormat.formatFullDate(date),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onNextDay) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Next day")
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close itinerary")
            }
        }

        // Summary + quick add actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "$shiftCount ${if (shiftCount == 1) "Shift" else "Shifts"} • $taskCount ${if (taskCount == 1) "Task" else "Tasks"} • $calCount Cal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            AssistChip(
                onClick = onAddShift,
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Shift") }
            )
            AssistChip(
                onClick = onAddTask,
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Task") }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        OrnateGoldDivider()
        Spacer(modifier = Modifier.height(6.dp))

        if (items.isEmpty()) {
            CheshireEmptyState(
                title = "Quiet Day in Wonderland",
                message = "Nothing scheduled for ${WonderlandTimeFormat.formatShortDate(date)}. Tap + Shift or + Task above to plan this day."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { "sheet_${it.id}" }) { item ->
                    UnifiedAgendaRow(
                        item = item,
                        onClick = {
                            item.taskEntity?.let(onEditTask)
                            item.shiftEntity?.let(onEditShift)
                        },
                        onToggleTaskDone = { item.taskEntity?.let(onCycleTaskStatus) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyPlannerView(
    date: LocalDate,
    items: List<UnifiedPlannerItem>,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onCycleTaskStatus: (TaskEntity) -> Unit,
    onEditShift: (ShiftScheduleEntity) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevDay) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous day")
        }
        Text(
            text = WonderlandTimeFormat.formatFullDate(date),
            style = MaterialTheme.typography.titleLarge
        )
        IconButton(onClick = onNextDay) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next day")
        }
    }

    if (items.isEmpty()) {
        CheshireEmptyState(
            title = "Quiet Day in Wonderland",
            message = "No tasks, shifts, or calendar events scheduled for ${WonderlandTimeFormat.formatShortDate(date)}."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.id }) { item ->
                UnifiedAgendaRow(
                    item = item,
                    onClick = {
                        item.taskEntity?.let(onEditTask)
                        item.shiftEntity?.let(onEditShift)
                    },
                    onToggleTaskDone = { item.taskEntity?.let(onCycleTaskStatus) }
                )
            }
        }
    }
}

@Composable
private fun WeeklyPlannerView(
    selectedDate: LocalDate,
    itemsByDay: Map<Long, List<UnifiedPlannerItem>>,
    onSelectDate: (LocalDate) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onCycleTaskStatus: (TaskEntity) -> Unit,
    onEditShift: (ShiftScheduleEntity) -> Unit
) {
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val weekDays = remember(weekStart) { (0L..6L).map { weekStart.plusDays(it) } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onSelectDate(selectedDate.minusWeeks(1)) }) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous week")
        }
        Text(
            text = "${WonderlandTimeFormat.formatShortDate(weekDays.first())} – ${WonderlandTimeFormat.formatShortDate(weekDays.last())}",
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = { onSelectDate(selectedDate.plusWeeks(1)) }) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next week")
        }
    }

    // 7-day interactive selector strip
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        weekDays.forEach { day ->
            val isSelected = day == selectedDate
            val count = itemsByDay[day.toEpochDay()].orEmpty().size
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else LocalWonderlandColors.current.cardBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectDate(day) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                if (count > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
        }
    }

    // All days in this week that have items, starting with selected day highlight
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val selectedDayItems = itemsByDay[selectedDate.toEpochDay()].orEmpty()
        item {
            Text(
                text = WonderlandTimeFormat.formatFullDate(selectedDate),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (selectedDayItems.isEmpty()) {
            item {
                Text(
                    text = "No items scheduled on ${WonderlandTimeFormat.formatShortDate(selectedDate)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(selectedDayItems, key = { it.id }) { item ->
                UnifiedAgendaRow(
                    item = item,
                    onClick = {
                        item.taskEntity?.let(onEditTask)
                        item.shiftEntity?.let(onEditShift)
                    },
                    onToggleTaskDone = { item.taskEntity?.let(onCycleTaskStatus) }
                )
            }
        }

        // Remaining days in week summary
        val otherDaysWithItems = weekDays.filter { it != selectedDate && !itemsByDay[it.toEpochDay()].isNullOrEmpty() }
        if (otherDaysWithItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Rest of This Week",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            otherDaysWithItems.forEach { day ->
                item(key = "hdr_${day.toEpochDay()}") {
                    Text(
                        text = WonderlandTimeFormat.formatShortDate(day),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(itemsByDay[day.toEpochDay()].orEmpty(), key = { "wk_${it.id}" }) { item ->
                    UnifiedAgendaRow(
                        item = item,
                        onClick = {
                            item.taskEntity?.let(onEditTask)
                            item.shiftEntity?.let(onEditShift)
                        },
                        onToggleTaskDone = { item.taskEntity?.let(onCycleTaskStatus) }
                    )
                }
            }
        }
    }
}
