package com.wonderland.taschasplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wonderland.taschasplanner.data.local.BuildingEntity
import com.wonderland.taschasplanner.data.local.CardSuit
import com.wonderland.taschasplanner.data.local.ContactEntity
import com.wonderland.taschasplanner.data.local.ContactType
import com.wonderland.taschasplanner.data.local.RecurrenceRule
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.ShiftStatus
import com.wonderland.taschasplanner.data.local.TaskEntity
import com.wonderland.taschasplanner.data.local.TaskPriority
import com.wonderland.taschasplanner.data.local.TaskStatus
import com.wonderland.taschasplanner.data.model.DeviceCalendar
import com.wonderland.taschasplanner.data.model.ScheduleDateExpander
import com.wonderland.taschasplanner.data.model.WonderlandTimeFormat
import com.wonderland.taschasplanner.ui.theme.BuildingAccentColors
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun TaskEditorDialog(
    initial: TaskEntity?,
    defaultDate: LocalDate,
    buildings: List<BuildingEntity>,
    employees: List<ContactEntity>,
    customers: List<ContactEntity>,
    writableCalendars: List<DeviceCalendar>,
    defaultCalendarId: Long?,
    onDismiss: () -> Unit,
    onSave: (TaskEntity, List<LocalDate>, Long?) -> Unit,
    onDelete: ((TaskEntity) -> Unit)? = null
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var roomOrZone by remember { mutableStateOf(initial?.roomOrZone ?: "") }
    var selectedEmployeeId by remember { mutableStateOf(initial?.assignedEmployeeId ?: employees.firstOrNull()?.id) }
    var selectedCustomerId by remember { mutableStateOf(initial?.customerId) }
    var selectedBuildingId by remember { mutableStateOf(initial?.buildingId ?: buildings.firstOrNull()?.id) }
    var dateEpochDay by remember { mutableLongStateOf(initial?.dueDateEpochDay ?: defaultDate.toEpochDay()) }
    var selectedWeekdays by remember { mutableStateOf(setOf(LocalDate.ofEpochDay(dateEpochDay).dayOfWeek)) }
    var repeatWeeks by remember { mutableIntStateOf(1) }
    var isTimed by remember { mutableStateOf(initial == null || initial.startTimeMinutes != null) }
    var startMinutes by remember { mutableIntStateOf(initial?.startTimeMinutes ?: (9 * 60)) }
    var durationMinutes by remember {
        mutableIntStateOf(
            if (initial?.startTimeMinutes != null && initial.endTimeMinutes != null) {
                (initial.endTimeMinutes - initial.startTimeMinutes).coerceAtLeast(30)
            } else 60
        )
    }
    var priority by remember { mutableStateOf(initial?.priority ?: TaskPriority.MEDIUM) }
    var status by remember { mutableStateOf(initial?.status ?: TaskStatus.TODO) }
    var recurrence by remember { mutableStateOf(initial?.recurrence ?: RecurrenceRule.NONE) }
    var pushToCalendar by remember { mutableStateOf(false) }
    var selectedCalendarId by remember {
        mutableStateOf(
            defaultCalendarId?.takeIf { id -> writableCalendars.any { it.id == id } }
                ?: writableCalendars.firstOrNull()?.id
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (initial == null) "New Wonderland Task" else "Edit Task")
                if (initial != null && onDelete != null) {
                    IconButton(onClick = { onDelete(initial); onDismiss() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes / instructions") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date stepper
                DateStepperRow(
                    label = if (initial == null) "Start Date" else "Due Date",
                    date = LocalDate.ofEpochDay(dateEpochDay),
                    onDateChange = { newDate ->
                        val oldDow = LocalDate.ofEpochDay(dateEpochDay).dayOfWeek
                        dateEpochDay = newDate.toEpochDay()
                        if (selectedWeekdays == setOf(oldDow)) {
                            selectedWeekdays = setOf(newDate.dayOfWeek)
                        }
                    }
                )

                // Multi-day & multi-week picker when creating a new task
                if (initial == null) {
                    MultiWeekDayPicker(
                        startDate = LocalDate.ofEpochDay(dateEpochDay),
                        selectedDays = selectedWeekdays,
                        weeksCount = repeatWeeks,
                        itemNoun = "task",
                        onSelectedDaysChange = { selectedWeekdays = it },
                        onWeeksCountChange = { repeatWeeks = it }
                    )
                }

                // Timed toggle & time stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Set specific time", style = MaterialTheme.typography.labelLarge)
                    Switch(checked = isTimed, onCheckedChange = { isTimed = it })
                }
                if (isTimed) {
                    TimeStepperRow(
                        label = "Start",
                        minutes = startMinutes,
                        onMinutesChange = { startMinutes = it }
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(30, 60, 90, 120).forEach { mins ->
                            FilterChip(
                                selected = durationMinutes == mins,
                                onClick = { durationMinutes = mins },
                                label = { Text("${mins}m") }
                            )
                        }
                    }
                }

                // Building selector
                if (buildings.isNotEmpty()) {
                    Text("Building / Location", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        buildings.forEach { b ->
                            FilterChip(
                                selected = selectedBuildingId == b.id,
                                onClick = { selectedBuildingId = b.id },
                                label = { Text(b.name) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = roomOrZone,
                        onValueChange = { roomOrZone = it },
                        label = { Text("Room / Zone (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Employee assignee
                if (employees.isNotEmpty()) {
                    Text("Assign Employee", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedEmployeeId == null,
                            onClick = { selectedEmployeeId = null },
                            label = { Text("Unassigned") }
                        )
                        employees.forEach { emp ->
                            FilterChip(
                                selected = selectedEmployeeId == emp.id,
                                onClick = { selectedEmployeeId = emp.id },
                                label = { Text(emp.fullName) }
                            )
                        }
                    }
                }

                // Customer link
                if (customers.isNotEmpty()) {
                    Text("Related Customer (optional)", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCustomerId == null,
                            onClick = { selectedCustomerId = null },
                            label = { Text("None") }
                        )
                        customers.forEach { cust ->
                            FilterChip(
                                selected = selectedCustomerId == cust.id,
                                onClick = { selectedCustomerId = cust.id },
                                label = { Text(cust.fullName) }
                            )
                        }
                    }
                }

                // Priority
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.displayLabel) }
                        )
                    }
                }

                // Status & Recurrence
                Text("Status", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskStatus.entries.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s.displayLabel) }
                        )
                    }
                }

                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RecurrenceRule.entries.forEach { r ->
                        FilterChip(
                            selected = recurrence == r,
                            onClick = { recurrence = r },
                            label = { Text(r.displayLabel) }
                        )
                    }
                }

                if (initial?.linkedCalendarEventId != null) {
                    Text(
                        text = "✓ Synced to Google Calendar (edits & deletion sync automatically)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else if (writableCalendars.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Push to Google Calendar",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = pushToCalendar, onCheckedChange = { pushToCalendar = it })
                    }
                    if (pushToCalendar) {
                        Text(
                            "Choose target calendar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            writableCalendars.forEach { cal ->
                                FilterChip(
                                    selected = selectedCalendarId == cal.id,
                                    onClick = { selectedCalendarId = cal.id },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(cal.colorInt))
                                        )
                                    },
                                    label = { Text(cal.displayName) }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Connect Google Calendars in Settings to push tasks to a calendar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val entity = TaskEntity(
                        id = initial?.id ?: 0L,
                        title = title.trim(),
                        description = description.trim(),
                        assignedEmployeeId = selectedEmployeeId,
                        customerId = selectedCustomerId,
                        buildingId = selectedBuildingId,
                        roomOrZone = roomOrZone.trim(),
                        dueDateEpochDay = dateEpochDay,
                        startTimeMinutes = if (isTimed) startMinutes else null,
                        endTimeMinutes = if (isTimed) (startMinutes + durationMinutes).coerceAtMost(23 * 60 + 45) else null,
                        priority = priority,
                        status = status,
                        recurrence = recurrence,
                        linkedCalendarEventId = initial?.linkedCalendarEventId
                    )
                    val targetDates = if (initial == null) {
                        ScheduleDateExpander.expandDates(
                            startDate = LocalDate.ofEpochDay(dateEpochDay),
                            selectedDays = selectedWeekdays,
                            weeksCount = repeatWeeks
                        )
                    } else {
                        listOf(LocalDate.ofEpochDay(dateEpochDay))
                    }
                    onSave(entity, targetDates, if (pushToCalendar) selectedCalendarId else null)
                    onDismiss()
                }
            ) {
                val count = if (initial == null) {
                    ScheduleDateExpander.expandDates(
                        LocalDate.ofEpochDay(dateEpochDay),
                        selectedWeekdays,
                        repeatWeeks
                    ).size
                } else 1
                Text(if (count > 1) "Save $count Tasks" else "Save Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ShiftEditorDialog(
    initial: ShiftScheduleEntity?,
    defaultDate: LocalDate,
    buildings: List<BuildingEntity>,
    employees: List<ContactEntity>,
    onDismiss: () -> Unit,
    onSave: (ShiftScheduleEntity, List<LocalDate>) -> Unit,
    onDelete: ((ShiftScheduleEntity) -> Unit)? = null
) {
    var selectedEmployeeId by remember { mutableStateOf(initial?.employeeId ?: employees.firstOrNull()?.id) }
    var selectedBuildingId by remember { mutableStateOf(initial?.buildingId ?: buildings.firstOrNull()?.id) }
    var dateEpochDay by remember { mutableLongStateOf(initial?.dateEpochDay ?: defaultDate.toEpochDay()) }
    var selectedWeekdays by remember { mutableStateOf(setOf(LocalDate.ofEpochDay(dateEpochDay).dayOfWeek)) }
    var repeatWeeks by remember { mutableIntStateOf(1) }
    var isFlexibleHours by remember { mutableStateOf(initial?.isFlexibleHours ?: false) }
    var allocatedHours by remember {
        mutableStateOf(
            if (initial != null && initial.isFlexibleHours && initial.allocatedHours > 0.0) {
                initial.allocatedHours
            } else 4.0
        )
    }
    var startMinutes by remember { mutableIntStateOf(initial?.startMinutes ?: (9 * 60)) }
    var endMinutes by remember { mutableIntStateOf(initial?.endMinutes ?: (17 * 60)) }
    var station by remember { mutableStateOf(initial?.stationOrRole ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: ShiftStatus.SCHEDULED) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (initial == null) "Schedule Employee Shift" else "Edit Shift")
                if (initial != null && onDelete != null) {
                    IconButton(onClick = { onDelete(initial); onDismiss() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete shift", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (employees.isEmpty() || buildings.isEmpty()) {
                    Text(
                        "Add at least one Employee and one Building first to schedule shifts.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Employee", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        employees.forEach { emp ->
                            FilterChip(
                                selected = selectedEmployeeId == emp.id,
                                onClick = { selectedEmployeeId = emp.id },
                                label = { Text(emp.fullName) }
                            )
                        }
                    }

                    Text("Building / Location", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        buildings.forEach { b ->
                            FilterChip(
                                selected = selectedBuildingId == b.id,
                                onClick = { selectedBuildingId = b.id },
                                label = { Text(b.name) }
                            )
                        }
                    }

                    DateStepperRow(
                        label = if (initial == null) "Start Date" else "Shift Date",
                        date = LocalDate.ofEpochDay(dateEpochDay),
                        onDateChange = { newDate ->
                            val oldDow = LocalDate.ofEpochDay(dateEpochDay).dayOfWeek
                            dateEpochDay = newDate.toEpochDay()
                            if (selectedWeekdays == setOf(oldDow)) {
                                selectedWeekdays = setOf(newDate.dayOfWeek)
                            }
                        }
                    )

                    if (initial == null) {
                        MultiWeekDayPicker(
                            startDate = LocalDate.ofEpochDay(dateEpochDay),
                            selectedDays = selectedWeekdays,
                            weeksCount = repeatWeeks,
                            itemNoun = "shift",
                            onSelectedDaysChange = { selectedWeekdays = it },
                            onWeeksCountChange = { repeatWeeks = it }
                        )
                    }

                    Text("Schedule Type", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isFlexibleHours,
                            onClick = { isFlexibleHours = false },
                            label = { Text("Time In / Out") }
                        )
                        FilterChip(
                            selected = isFlexibleHours,
                            onClick = { isFlexibleHours = true },
                            label = { Text("Flexible Hours") }
                        )
                    }

                    if (isFlexibleHours) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Hours Assigned", style = MaterialTheme.typography.labelLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { allocatedHours = (allocatedHours - 0.5).coerceAtLeast(0.5) }
                                ) {
                                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Decrease half hour")
                                }
                                Text(
                                    WonderlandTimeFormat.formatHours(allocatedHours),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                IconButton(
                                    onClick = { allocatedHours = (allocatedHours + 0.5).coerceAtMost(16.0) }
                                ) {
                                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Increase half hour")
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(2.0, 4.0, 4.5, 6.0, 8.0).forEach { hrs ->
                                FilterChip(
                                    selected = allocatedHours == hrs,
                                    onClick = { allocatedHours = hrs },
                                    label = { Text(WonderlandTimeFormat.formatHours(hrs)) }
                                )
                            }
                        }
                    } else {
                        TimeStepperRow(
                            label = "Start",
                            minutes = startMinutes,
                            onMinutesChange = {
                                startMinutes = it
                                if (endMinutes <= startMinutes) endMinutes = (startMinutes + 60).coerceAtMost(23 * 60 + 30)
                            }
                        )
                        TimeStepperRow(
                            label = "End",
                            minutes = endMinutes,
                            onMinutesChange = { endMinutes = it.coerceAtLeast(startMinutes + 30) }
                        )
                    }

                    OutlinedTextField(
                        value = station,
                        onValueChange = { station = it },
                        label = { Text("Station / Role") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Shift Status", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShiftStatus.entries.forEach { s ->
                            FilterChip(
                                selected = status == s,
                                onClick = { status = s },
                                label = { Text(s.displayLabel) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedEmployeeId != null && selectedBuildingId != null,
                onClick = {
                    val empId = selectedEmployeeId ?: return@TextButton
                    val bldgId = selectedBuildingId ?: return@TextButton
                    val targetDates = if (initial == null) {
                        ScheduleDateExpander.expandDates(
                            startDate = LocalDate.ofEpochDay(dateEpochDay),
                            selectedDays = selectedWeekdays,
                            weeksCount = repeatWeeks
                        )
                    } else {
                        listOf(LocalDate.ofEpochDay(dateEpochDay))
                    }
                    onSave(
                        ShiftScheduleEntity(
                            id = initial?.id ?: 0L,
                            employeeId = empId,
                            buildingId = bldgId,
                            dateEpochDay = dateEpochDay,
                            startMinutes = startMinutes,
                            endMinutes = endMinutes,
                            isFlexibleHours = isFlexibleHours,
                            allocatedHours = if (isFlexibleHours) allocatedHours else 0.0,
                            stationOrRole = station.trim(),
                            status = status,
                            notes = notes.trim()
                        ),
                        targetDates
                    )
                    onDismiss()
                }
            ) {
                val count = if (initial == null) {
                    ScheduleDateExpander.expandDates(
                        LocalDate.ofEpochDay(dateEpochDay),
                        selectedWeekdays,
                        repeatWeeks
                    ).size
                } else 1
                Text(if (count > 1) "Save $count Shifts" else "Save Shift")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BuildingEditorDialog(
    initial: BuildingEntity?,
    onDismiss: () -> Unit,
    onSave: (BuildingEntity) -> Unit,
    onDelete: ((BuildingEntity) -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var colorHex by remember { mutableLongStateOf(initial?.colorHex ?: BuildingAccentColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (initial == null) "Add Building / Location" else "Edit Building")
                if (initial != null && onDelete != null) {
                    IconButton(onClick = { onDelete(initial); onDismiss() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete building", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Building Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(5).uppercase() },
                    label = { Text("Short Code (e.g. MNR)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Floors / Zones / Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Wonderland Accent Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BuildingAccentColors.forEach { hex ->
                        val selected = colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val autoCode = code.ifBlank { name.trim().take(3).uppercase() }
                    onSave(
                        BuildingEntity(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            code = autoCode,
                            address = address.trim(),
                            notes = notes.trim(),
                            colorHex = colorHex,
                            isActive = true
                        )
                    )
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ContactEditorDialog(
    initial: ContactEntity?,
    defaultType: ContactType,
    buildings: List<BuildingEntity>,
    onDismiss: () -> Unit,
    onSave: (ContactEntity) -> Unit,
    onDelete: ((ContactEntity) -> Unit)? = null
) {
    var type by remember { mutableStateOf(initial?.type ?: defaultType) }
    var fullName by remember { mutableStateOf(initial?.fullName ?: "") }
    var roleOrCompany by remember { mutableStateOf(initial?.roleOrCompany ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var buildingId by remember { mutableStateOf(initial?.buildingId) }
    var suit by remember { mutableStateOf(initial?.cardSuit ?: CardSuit.HEART) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var isFavorite by remember { mutableStateOf(initial?.isFavorite ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (initial == null) "New Contact" else "Edit Contact")
                if (initial != null && onDelete != null) {
                    IconButton(onClick = { onDelete(initial); onDismiss() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete contact", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == ContactType.EMPLOYEE,
                        onClick = { type = ContactType.EMPLOYEE },
                        label = { Text("Employee") }
                    )
                    FilterChip(
                        selected = type == ContactType.CUSTOMER,
                        onClick = { type = ContactType.CUSTOMER },
                        label = { Text("Customer") }
                    )
                }
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = roleOrCompany,
                    onValueChange = { roleOrCompany = it },
                    label = { Text(if (type == ContactType.EMPLOYEE) "Role / Job Title" else "Company / Organization") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (buildings.isNotEmpty()) {
                    Text("Primary Building", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = buildingId == null,
                            onClick = { buildingId = null },
                            label = { Text("None") }
                        )
                        buildings.forEach { b ->
                            FilterChip(
                                selected = buildingId == b.id,
                                onClick = { buildingId = b.id },
                                label = { Text(b.name) }
                            )
                        }
                    }
                }
                Text("Wonderland Card Suit", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardSuit.entries.forEach { s ->
                        val sym = when (s) {
                            CardSuit.SPADE -> "♠"
                            CardSuit.HEART -> "♥"
                            CardSuit.DIAMOND -> "♦"
                            CardSuit.CLUB -> "♣"
                        }
                        FilterChip(
                            selected = suit == s,
                            onClick = { suit = s },
                            label = { Text(sym) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pin as Favorite", style = MaterialTheme.typography.labelLarge)
                    Switch(checked = isFavorite, onCheckedChange = { isFavorite = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = fullName.isNotBlank(),
                onClick = {
                    onSave(
                        ContactEntity(
                            id = initial?.id ?: 0L,
                            type = type,
                            fullName = fullName.trim(),
                            roleOrCompany = roleOrCompany.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            buildingId = buildingId,
                            cardSuit = suit,
                            notes = notes.trim(),
                            isFavorite = isFavorite
                        )
                    )
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MultiWeekDayPicker(
    startDate: LocalDate,
    selectedDays: Set<DayOfWeek>,
    weeksCount: Int,
    itemNoun: String,
    onSelectedDaysChange: (Set<DayOfWeek>) -> Unit,
    onWeeksCountChange: (Int) -> Unit
) {
    val weekdaysOrder = listOf(
        DayOfWeek.MONDAY to "Mon",
        DayOfWeek.TUESDAY to "Tue",
        DayOfWeek.WEDNESDAY to "Wed",
        DayOfWeek.THURSDAY to "Thu",
        DayOfWeek.FRIDAY to "Fri",
        DayOfWeek.SATURDAY to "Sat",
        DayOfWeek.SUNDAY to "Sun"
    )
    val monToFri = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )
    val previewCount = ScheduleDateExpander.expandDates(startDate, selectedDays, weeksCount).size

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Days of Week", style = MaterialTheme.typography.labelLarge)
            TextButton(
                onClick = {
                    onSelectedDaysChange(
                        if (selectedDays == monToFri) setOf(startDate.dayOfWeek) else monToFri
                    )
                }
            ) {
                Text(if (selectedDays == monToFri) "Single Day" else "Weekdays (M–F)")
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekdaysOrder.forEach { (dow, label) ->
                val isSelected = dow in selectedDays
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val next = if (isSelected) {
                            if (selectedDays.size > 1) selectedDays - dow else selectedDays
                        } else {
                            selectedDays + dow
                        }
                        onSelectedDaysChange(next)
                    },
                    label = { Text(label) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Repeat Across Weeks", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onWeeksCountChange((weeksCount - 1).coerceAtLeast(1)) }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Fewer weeks")
                }
                Text(
                    if (weeksCount == 1) "1 wk" else "$weeksCount wks",
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = { onWeeksCountChange((weeksCount + 1).coerceAtMost(12)) }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "More weeks")
                }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(1, 2, 4, 6, 8).forEach { wk ->
                FilterChip(
                    selected = weeksCount == wk,
                    onClick = { onWeeksCountChange(wk) },
                    label = { Text(if (wk == 1) "1 wk" else "$wk wks") }
                )
            }
        }

        Text(
            text = if (previewCount == 1) {
                "Will schedule 1 $itemNoun on ${WonderlandTimeFormat.formatShortDate(startDate)}"
            } else {
                "Will schedule $previewCount ${itemNoun}s across $weeksCount ${if (weeksCount == 1) "week" else "weeks"} keeping your selected times/hours"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DateStepperRow(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous day")
            }
            Text(WonderlandTimeFormat.formatShortDate(date), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onDateChange(date.plusDays(1)) }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next day")
            }
        }
    }
}

@Composable
private fun TimeStepperRow(
    label: String,
    minutes: Int,
    onMinutesChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMinutesChange((minutes - 30).coerceAtLeast(0)) }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Earlier")
            }
            Text(WonderlandTimeFormat.formatMinutes(minutes), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onMinutesChange((minutes + 30).coerceAtMost(23 * 60 + 30)) }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Later")
            }
        }
    }
}
