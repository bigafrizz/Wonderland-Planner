package com.wonderland.taschasplanner.ui.buildings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.wonderland.taschasplanner.data.model.PlannerAggregator
import com.wonderland.taschasplanner.data.model.ScheduleBatchMatcher
import com.wonderland.taschasplanner.data.model.ShiftConflictDetector
import com.wonderland.taschasplanner.data.model.WonderlandTimeFormat
import com.wonderland.taschasplanner.ui.WonderlandUiState
import com.wonderland.taschasplanner.ui.components.CheshireEmptyState
import com.wonderland.taschasplanner.ui.components.PocketWatchBadge
import com.wonderland.taschasplanner.ui.components.WonderlandCard
import com.wonderland.taschasplanner.ui.theme.LocalWonderlandColors
import java.time.LocalDate

private enum class BuildingTab(val label: String) {
    SHIFTS("Shift Tracker"),
    TASKS("Task Board")
}

@Composable
fun BuildingsScreen(
    state: WonderlandUiState,
    initialBuildingId: Long?,
    onAddBuilding: () -> Unit,
    onEditBuilding: (BuildingEntity) -> Unit,
    onAddShift: () -> Unit,
    onEditShift: (ShiftScheduleEntity) -> Unit,
    onCycleShiftStatus: (ShiftScheduleEntity) -> Unit,
    onDeleteShifts: (List<ShiftScheduleEntity>) -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onCycleTaskStatus: (TaskEntity) -> Unit,
    onDeleteTasks: (List<TaskEntity>) -> Unit
) {
    var selectedBuildingId by remember(initialBuildingId) { mutableStateOf(initialBuildingId) }
    var activeTab by remember { mutableStateOf(BuildingTab.SHIFTS) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedShiftIds by remember { mutableStateOf(emptySet<Long>()) }
    var selectedTaskIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    val selectedBuilding = selectedBuildingId?.let { state.buildingsById[it] }
    val filteredShifts = remember(state.shifts, selectedBuildingId) {
        state.shifts.filter { selectedBuildingId == null || it.buildingId == selectedBuildingId }
            .sortedWith(compareBy<ShiftScheduleEntity> { it.dateEpochDay }.thenBy { it.startMinutes })
    }
    val filteredTasks = remember(state.tasks, selectedBuildingId) {
        PlannerAggregator.sortTasksByPriorityAndStatus(
            state.tasks.filter { selectedBuildingId == null || it.buildingId == selectedBuildingId }
        )
    }

    val selectedCount = if (activeTab == BuildingTab.SHIFTS) selectedShiftIds.size else selectedTaskIds.size

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedShiftIds = emptySet()
        selectedTaskIds = emptySet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = {
                        if (activeTab == BuildingTab.SHIFTS) onAddShift() else onAddTask()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add schedule or task")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Looking-Glass Locations", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${state.buildings.size} buildings tracked",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {
                            if (isSelectionMode) exitSelectionMode() else isSelectionMode = true
                        },
                        leadingIcon = {
                            Icon(
                                if (isSelectionMode) Icons.Outlined.Close else Icons.Outlined.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(if (isSelectionMode) "Cancel" else "Select") }
                    )
                    if (!isSelectionMode) {
                        AssistChip(
                            onClick = onAddBuilding,
                            leadingIcon = { Icon(Icons.Outlined.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Building") }
                        )
                    }
                }
            }

            // Multi-select bulk action bar
            if (isSelectionMode) {
                Spacer(modifier = Modifier.height(8.dp))
                WonderlandCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = MaterialTheme.colorScheme.primary
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$selectedCount selected",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                enabled = selectedCount > 0,
                                onClick = { showBulkDeleteConfirm = true }
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Delete ($selectedCount)",
                                    color = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    if (activeTab == BuildingTab.SHIFTS) {
                                        selectedShiftIds = if (selectedShiftIds.size == filteredShifts.size) emptySet()
                                        else filteredShifts.map { it.id }.toSet()
                                    } else {
                                        selectedTaskIds = if (selectedTaskIds.size == filteredTasks.size) emptySet()
                                        else filteredTasks.map { it.id }.toSet()
                                    }
                                },
                                label = {
                                    val allSelected = if (activeTab == BuildingTab.SHIFTS) {
                                        filteredShifts.isNotEmpty() && selectedShiftIds.size == filteredShifts.size
                                    } else {
                                        filteredTasks.isNotEmpty() && selectedTaskIds.size == filteredTasks.size
                                    }
                                    Text(if (allSelected) "Deselect All" else "Select All")
                                }
                            )
                            if (selectedCount == 1) {
                                AssistChip(
                                    onClick = {
                                        if (activeTab == BuildingTab.SHIFTS) {
                                            val anchor = filteredShifts.find { it.id in selectedShiftIds }
                                            if (anchor != null) {
                                                selectedShiftIds = ScheduleBatchMatcher.matchingShifts(anchor, filteredShifts)
                                                    .map { it.id }.toSet()
                                            }
                                        } else {
                                            val anchor = filteredTasks.find { it.id in selectedTaskIds }
                                            if (anchor != null) {
                                                selectedTaskIds = ScheduleBatchMatcher.matchingTasks(anchor, filteredTasks)
                                                    .map { it.id }.toSet()
                                            }
                                        }
                                    },
                                    label = { Text("Select All Similar") }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-building selector chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedBuildingId == null,
                    onClick = { selectedBuildingId = null },
                    label = { Text("All Buildings") }
                )
                state.buildings.forEach { bldg ->
                    FilterChip(
                        selected = selectedBuildingId == bldg.id,
                        onClick = { selectedBuildingId = bldg.id },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(bldg.colorHex))
                            )
                        },
                        label = { Text("${bldg.code} • ${bldg.name}") }
                    )
                }
            }

            // Selected building info card
            if (selectedBuilding != null && !isSelectionMode) {
                Spacer(modifier = Modifier.height(8.dp))
                WonderlandCard(accentColor = Color(selectedBuilding.colorHex)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedBuilding.name, style = MaterialTheme.typography.titleMedium)
                            if (selectedBuilding.address.isNotBlank()) {
                                Text(
                                    selectedBuilding.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selectedBuilding.notes.isNotBlank()) {
                                Text(
                                    selectedBuilding.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onEditBuilding(selectedBuilding) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit building")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Shift Tracker vs Task Board
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BuildingTab.entries.forEachIndexed { idx, tab ->
                    SegmentedButton(
                        selected = activeTab == tab,
                        onClick = {
                            activeTab = tab
                            selectedShiftIds = emptySet()
                            selectedTaskIds = emptySet()
                        },
                        shape = SegmentedButtonDefaults.itemShape(idx, BuildingTab.entries.size)
                    ) {
                        Text(tab.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (activeTab == BuildingTab.SHIFTS) {
                val todayEpoch = LocalDate.now().toEpochDay()
                val dailyHoursMap = remember(filteredShifts, todayEpoch) {
                    ShiftConflictDetector.totalHoursByEmployeeOnDate(filteredShifts, todayEpoch)
                }
                if (dailyHoursMap.isNotEmpty() && !isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dailyHoursMap.forEach { (empId, hrs) ->
                            val empName = state.contactsById[empId]?.fullName ?: "Employee"
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "$empName: ${WonderlandTimeFormat.formatHours(hrs)} today",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                if (filteredShifts.isEmpty()) {
                    CheshireEmptyState(
                        title = "No Shifts Scheduled",
                        message = "Tap + to assign an employee shift to a building."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredShifts, key = { it.id }) { shift ->
                            val emp = state.contactsById[shift.employeeId]
                            val bldg = state.buildingsById[shift.buildingId]
                            val hasConflict = shift.id in state.conflictingShiftIds
                            val isSelected = shift.id in selectedShiftIds
                            val accent = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                hasConflict -> MaterialTheme.colorScheme.error
                                else -> Color(bldg?.colorHex ?: 0xFF1A5F6BL)
                            }

                            WonderlandCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = accent,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedShiftIds = if (isSelected) selectedShiftIds - shift.id else selectedShiftIds + shift.id
                                    } else {
                                        onEditShift(shift)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedShiftIds = if (checked) selectedShiftIds + shift.id else selectedShiftIds - shift.id
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = emp?.fullName ?: "Employee",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                if (hasConflict) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        Icons.Outlined.WarningAmber,
                                                        contentDescription = "Overlapping shift conflict",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            if (!isSelectionMode) {
                                                AssistChip(
                                                    onClick = { onCycleShiftStatus(shift) },
                                                    label = { Text(shift.status.displayLabel) }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PocketWatchBadge(
                                                timeText = "${WonderlandTimeFormat.formatShortDate(LocalDate.ofEpochDay(shift.dateEpochDay))} • ${WonderlandTimeFormat.formatShiftSchedule(shift)}"
                                            )
                                            if (bldg != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = Color(bldg.colorHex).copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = bldg.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(bldg.colorHex),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (shift.stationOrRole.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = shift.stationOrRole,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (hasConflict) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Double-booked: overlaps another shift for this employee.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (filteredTasks.isEmpty()) {
                    CheshireEmptyState(
                        title = "No Tasks for This Location",
                        message = "Tap + to assign an employee task here."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTasks, key = { it.id }) { task ->
                            val bldg = task.buildingId?.let { state.buildingsById[it] }
                            val emp = task.assignedEmployeeId?.let { state.contactsById[it] }
                            val isDone = task.status == TaskStatus.DONE
                            val isSelected = task.id in selectedTaskIds
                            val accent = if (isSelected) MaterialTheme.colorScheme.primary else Color(bldg?.colorHex ?: 0xFF6A1B9AL)

                            WonderlandCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = accent,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedTaskIds = if (isSelected) selectedTaskIds - task.id else selectedTaskIds + task.id
                                    } else {
                                        onEditTask(task)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = accent.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = "${task.priority.displayLabel} • ${task.status.displayLabel}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = accent,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (bldg != null) {
                                                Text(
                                                    text = bldg.code,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val meta = listOfNotNull(
                                            WonderlandTimeFormat.formatShortDate(LocalDate.ofEpochDay(task.dueDateEpochDay)),
                                            emp?.fullName,
                                            task.roomOrZone.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ")
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!isSelectionMode) {
                                        IconButton(onClick = { onCycleTaskStatus(task) }) {
                                            Icon(
                                                imageVector = if (isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                                contentDescription = "Advance task status",
                                                tint = if (isDone) LocalWonderlandColors.current.successAccent else accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBulkDeleteConfirm) {
        val noun = if (activeTab == BuildingTab.SHIFTS) "shift" else "task"
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete $selectedCount ${noun}s?") },
            text = {
                Text("This will permanently delete the $selectedCount selected ${noun}s (and remove any linked Google Calendar events). Continue?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (activeTab == BuildingTab.SHIFTS) {
                            val toDelete = filteredShifts.filter { it.id in selectedShiftIds }
                            onDeleteShifts(toDelete)
                        } else {
                            val toDelete = filteredTasks.filter { it.id in selectedTaskIds }
                            onDeleteTasks(toDelete)
                        }
                        showBulkDeleteConfirm = false
                        exitSelectionMode()
                    }
                ) {
                    Text("Delete All $selectedCount", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
