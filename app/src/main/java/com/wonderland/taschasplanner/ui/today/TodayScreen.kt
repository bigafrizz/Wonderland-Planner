package com.wonderland.taschasplanner.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wonderland.taschasplanner.data.local.BuildingEntity
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.TaskEntity
import com.wonderland.taschasplanner.data.local.TaskStatus
import com.wonderland.taschasplanner.data.local.ThemeMode
import com.wonderland.taschasplanner.data.model.PlannerAggregator
import com.wonderland.taschasplanner.data.model.PlannerItemSource
import com.wonderland.taschasplanner.data.model.UnifiedPlannerItem
import com.wonderland.taschasplanner.data.model.WonderlandTimeFormat
import com.wonderland.taschasplanner.ui.WonderlandUiState
import com.wonderland.taschasplanner.ui.components.CheshireEmptyState
import com.wonderland.taschasplanner.ui.components.CheshireGreetingBanner
import com.wonderland.taschasplanner.ui.components.OrnateGoldDivider
import com.wonderland.taschasplanner.ui.components.PocketWatchBadge
import com.wonderland.taschasplanner.ui.components.WonderlandCard
import com.wonderland.taschasplanner.ui.theme.LocalWonderlandColors
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(
    state: WonderlandUiState,
    onToggleTheme: () -> Unit,
    onAddTask: () -> Unit,
    onAddShift: () -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onCycleTaskStatus: (TaskEntity) -> Unit,
    onEditShift: (ShiftScheduleEntity) -> Unit,
    onOpenBuilding: (Long) -> Unit
) {
    val today = LocalDate.now()
    val todayEpoch = today.toEpochDay()
    val employeeNames = remember(state.contacts) { state.contacts.associate { it.id to it.fullName } }

    val todayItems = remember(state, todayEpoch) {
        PlannerAggregator.buildUnifiedItems(
            tasks = state.tasks.filter { it.dueDateEpochDay == todayEpoch },
            shifts = state.shifts.filter { it.dateEpochDay == todayEpoch },
            calendarEvents = state.calendarEvents.filter { it.dateEpochDay() == todayEpoch },
            buildingsById = state.buildingsById,
            employeeNamesById = employeeNames,
            visibleCalendarIds = state.visibleCalendarIds
        )
    }

    val openTasksToday = state.tasks.count { it.dueDateEpochDay == todayEpoch && it.status != TaskStatus.DONE }
    val shiftsToday = state.shifts.count { it.dateEpochDay == todayEpoch }
    val gcalToday = todayItems.count { it.source == PlannerItemSource.GOOGLE_CALENDAR }

    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good Morning, Tascha!"
        hour < 17 -> "Good Afternoon, Tascha!"
        else -> "Good Evening, Tascha!"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add task")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CheshireGreetingBanner(
                    greeting = greeting,
                    subtitle = "\"We're all madly organized here!\"",
                    dateLabel = WonderlandTimeFormat.formatFullDate(today),
                    isDarkTheme = state.themeMode == ThemeMode.CHESHIRE_DARK,
                    onToggleTheme = onToggleTheme
                )
            }

            // Summary Stats Ribbon
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatRibbonCard(
                        label = "Open Tasks",
                        value = openTasksToday.toString(),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatRibbonCard(
                        label = "Active Shifts",
                        value = shiftsToday.toString(),
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    StatRibbonCard(
                        label = "Google Cal",
                        value = gcalToday.toString(),
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Shift conflict banner if any exist today
            val todayConflicts = state.shifts.count {
                it.dateEpochDay == todayEpoch && it.id in state.conflictingShiftIds
            }
            if (todayConflicts > 0) {
                item {
                    WonderlandCard(accentColor = MaterialTheme.colorScheme.error) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Shift overlap detected across buildings today! Check the Buildings tab to resolve.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Buildings at a Glance
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Buildings at a Glance",
                        style = MaterialTheme.typography.titleLarge
                    )
                    AssistChip(
                        onClick = onAddShift,
                        label = { Text("+ Shift") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (state.buildings.isEmpty()) {
                    Text(
                        "No buildings added yet. Visit the Buildings tab to add locations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.buildings, key = { it.id }) { bldg ->
                            val staffCount = state.shifts.count {
                                it.buildingId == bldg.id && it.dateEpochDay == todayEpoch
                            }
                            val taskCount = state.tasks.count {
                                it.buildingId == bldg.id && it.dueDateEpochDay == todayEpoch && it.status != TaskStatus.DONE
                            }
                            BuildingGlanceCard(
                                building = bldg,
                                staffCount = staffCount,
                                openTaskCount = taskCount,
                                onClick = { onOpenBuilding(bldg.id) }
                            )
                        }
                    }
                }
            }

            item { OrnateGoldDivider() }

            // Today's Unified Agenda
            item {
                Text(
                    text = "Today's Pocket-Watch Agenda",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (todayItems.isEmpty()) {
                item {
                    CheshireEmptyState(
                        title = "Nothing here but a grin...",
                        message = "Your schedule for today is wide open. Tap + to add a task or sync your Google Calendars in Looking Glass."
                    )
                }
            } else {
                items(todayItems, key = { it.id }) { item ->
                    UnifiedAgendaRow(
                        item = item,
                        hasConflict = item.shiftEntity?.id in state.conflictingShiftIds,
                        onClick = {
                            item.taskEntity?.let(onEditTask)
                            item.shiftEntity?.let(onEditShift)
                        },
                        onToggleTaskDone = { item.taskEntity?.let(onCycleTaskStatus) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun StatRibbonCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    WonderlandCard(modifier = modifier, accentColor = accent) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BuildingGlanceCard(
    building: BuildingEntity,
    staffCount: Int,
    openTaskCount: Int,
    onClick: () -> Unit
) {
    val accent = Color(building.colorHex)
    WonderlandCard(
        modifier = Modifier.width(176.dp),
        accentColor = accent,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Apartment,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = building.code,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = building.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$staffCount on shift • $openTaskCount tasks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun UnifiedAgendaRow(
    item: UnifiedPlannerItem,
    hasConflict: Boolean,
    onClick: () -> Unit,
    onToggleTaskDone: () -> Unit
) {
    val accent = Color(item.accentColorHex)
    val isDone = item.taskEntity?.status == TaskStatus.DONE
    val badgeText = when (item.source) {
        PlannerItemSource.GOOGLE_CALENDAR -> "Google Cal"
        PlannerItemSource.TASK -> item.taskEntity?.priority?.displayLabel ?: "Task"
        PlannerItemSource.SHIFT -> "Shift"
    }

    WonderlandCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = if (hasConflict) MaterialTheme.colorScheme.error else accent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (hasConflict) MaterialTheme.colorScheme.error else accent)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PocketWatchBadge(
                        timeText = item.customTimeLabel ?: WonderlandTimeFormat.formatRange(item.startMinutes, item.endMinutes)
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (hasConflict) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = "Conflict",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (item.taskEntity != null) {
                IconButton(onClick = onToggleTaskDone) {
                    Icon(
                        imageVector = if (isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = "Cycle task status",
                        tint = if (isDone) LocalWonderlandColors.current.successAccent else accent
                    )
                }
            } else if (item.source == PlannerItemSource.GOOGLE_CALENDAR) {
                Icon(
                    Icons.Outlined.Event,
                    contentDescription = "Google Calendar event",
                    tint = accent,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}
