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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.TaskEntity
import com.wonderland.taschasplanner.data.model.PlannerAggregator
import com.wonderland.taschasplanner.data.model.UnifiedPlannerItem
import com.wonderland.taschasplanner.data.model.WonderlandTimeFormat
import com.wonderland.taschasplanner.ui.WonderlandUiState
import com.wonderland.taschasplanner.ui.components.CheshireEmptyState
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
    DAY("Daily"),
    WEEK("Weekly"),
    MONTH("Monthly")
}

@Composable
fun PlannerScreen(
    state: WonderlandUiState,
    isExpandedWidth: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onRefreshCalendars: () -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onCycleTaskStatus: (TaskEntity) -> Unit,
    onEditShift: (ShiftScheduleEntity) -> Unit
) {
    var mode by remember { mutableStateOf(PlannerViewMode.DAY) }
    var buildingFilterId by remember { mutableStateOf<Long?>(null) }
    val selectedDate = state.selectedDate
    val employeeNames = remember(state.contacts) { state.contacts.associate { it.id to it.fullName } }

    val allUnified = remember(state, buildingFilterId, selectedDate.year) {
        PlannerAggregator.buildUnifiedItems(
            tasks = state.tasks,
            shifts = state.shifts,
            calendarEvents = state.calendarEvents,
            buildingsById = state.buildingsById,
            employeeNamesById = employeeNames,
            visibleCalendarIds = state.visibleCalendarIds,
            buildingFilterId = buildingFilterId,
            contactsWithBirthdays = state.contacts,
            birthdayYears = listOf(selectedDate.year - 1, selectedDate.year, selectedDate.year + 1)
        )
    }

    val itemsByDay = remember(allUnified) { allUnified.groupBy { it.dateEpochDay } }

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
                .padding(horizontal = 16.dp, vertical = 10.dp)
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
                    Text(
                        text = WonderlandTimeFormat.formatMonthYear(selectedDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = { onSelectDate(LocalDate.now()) }) {
                        Icon(Icons.Outlined.Today, contentDescription = "Jump to today")
                    }
                    IconButton(onClick = onRefreshCalendars) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Google Calendars")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Daily / Weekly / Monthly Switcher
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
                Spacer(modifier = Modifier.height(8.dp))
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

            Spacer(modifier = Modifier.height(10.dp))

            when (mode) {
                PlannerViewMode.DAY -> DailyPlannerView(
                    date = selectedDate,
                    items = itemsByDay[selectedDate.toEpochDay()].orEmpty(),
                    onPrevDay = { onSelectDate(selectedDate.minusDays(1)) },
                    onNextDay = { onSelectDate(selectedDate.plusDays(1)) },
                    onEditTask = onEditTask,
                    onCycleTaskStatus = onCycleTaskStatus,
                    onEditShift = onEditShift
                )

                PlannerViewMode.WEEK -> WeeklyPlannerView(
                    selectedDate = selectedDate,
                    itemsByDay = itemsByDay,
                    onSelectDate = onSelectDate,
                    onEditTask = onEditTask,
                    onCycleTaskStatus = onCycleTaskStatus,
                    onEditShift = onEditShift
                )

                PlannerViewMode.MONTH -> MonthlyPlannerView(
                    selectedDate = selectedDate,
                    itemsByDay = itemsByDay,
                    isExpandedWidth = isExpandedWidth,
                    onSelectDate = onSelectDate,
                    onEditTask = onEditTask,
                    onCycleTaskStatus = onCycleTaskStatus,
                    onEditShift = onEditShift
                )
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

@Composable
private fun MonthlyPlannerView(
    selectedDate: LocalDate,
    itemsByDay: Map<Long, List<UnifiedPlannerItem>>,
    isExpandedWidth: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onCycleTaskStatus: (TaskEntity) -> Unit,
    onEditShift: (ShiftScheduleEntity) -> Unit
) {
    val calendarPane: @Composable (Modifier) -> Unit = { mod ->
        Column(modifier = mod) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSelectDate(selectedDate.minusMonths(1)) }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    text = WonderlandTimeFormat.formatMonthYear(selectedDate),
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { onSelectDate(selectedDate.plusMonths(1)) }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Next month")
                }
            }

            // Weekday headers
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            val ym = YearMonth.from(selectedDate)
            val firstDay = ym.atDay(1)
            val leadingBlanks = firstDay.dayOfWeek.value % 7 // Sunday = 0
            val totalCells = leadingBlanks + ym.lengthOfMonth()
            val rows = (totalCells + 6) / 7
            val today = LocalDate.now()

            WonderlandCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(6.dp)) {
                    for (r in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val cellIdx = r * 7 + c
                                val dayNum = cellIdx - leadingBlanks + 1
                                if (dayNum in 1..ym.lengthOfMonth()) {
                                    val cellDate = ym.atDay(dayNum)
                                    val isSelected = cellDate == selectedDate
                                    val isToday = cellDate == today
                                    val dayItems = itemsByDay[cellDate.toEpochDay()].orEmpty()
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isToday -> MaterialTheme.colorScheme.primaryContainer
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .clickable { onSelectDate(cellDate) },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (dayItems.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                dayItems.take(3).forEach { it ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                                else Color(it.accentColorHex)
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val agendaPane: @Composable (Modifier) -> Unit = { mod ->
        val dayItems = itemsByDay[selectedDate.toEpochDay()].orEmpty()
        Column(modifier = mod) {
            Text(
                text = WonderlandTimeFormat.formatFullDate(selectedDate),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (dayItems.isEmpty()) {
                CheshireEmptyState(
                    title = "No events on this day",
                    message = "Tap + to schedule a task for ${WonderlandTimeFormat.formatShortDate(selectedDate)}."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dayItems, key = { "mo_${it.id}" }) { item ->
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

    if (isExpandedWidth) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            calendarPane(Modifier.weight(1.1f).fillMaxHeight())
            agendaPane(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            calendarPane(Modifier.fillMaxWidth())
            agendaPane(Modifier.weight(1f).fillMaxWidth())
        }
    }
}
