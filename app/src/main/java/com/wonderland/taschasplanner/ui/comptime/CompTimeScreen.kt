package com.wonderland.taschasplanner.ui.comptime

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.wonderland.taschasplanner.data.local.CompTimeEntryEntity
import com.wonderland.taschasplanner.data.local.CompTimeEntryType
import com.wonderland.taschasplanner.data.model.CompTimeCalculator
import com.wonderland.taschasplanner.data.model.WonderlandTimeFormat
import com.wonderland.taschasplanner.ui.components.CheshireEmptyState
import com.wonderland.taschasplanner.ui.components.OrnateGoldDivider
import com.wonderland.taschasplanner.ui.components.PocketWatchBadge
import com.wonderland.taschasplanner.ui.components.WonderlandCard
import com.wonderland.taschasplanner.ui.theme.LocalWonderlandColors
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun CompTimeScreen(
    entries: List<CompTimeEntryEntity>,
    dailyBaselineHours: Double,
    onSetBaselineHours: (Double) -> Unit,
    onClockInOrOutNow: () -> Unit,
    onSaveEntry: (CompTimeEntryEntity) -> Unit,
    onDeleteEntry: (CompTimeEntryEntity) -> Unit
) {
    val todayEpoch = LocalDate.now().toEpochDay()
    val summary = remember(entries, dailyBaselineHours) {
        CompTimeCalculator.summarize(entries, dailyBaselineHours)
    }
    val openTodayEntry = remember(entries, todayEpoch) {
        entries.firstOrNull {
            it.dateEpochDay == todayEpoch &&
                it.entryType == CompTimeEntryType.WORK_DAY &&
                it.timeInMinutes != null &&
                it.timeOutMinutes == null
        }
    }

    var showEditor by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<CompTimeEntryEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingEntry = null
                    showEditor = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Log comp time entry")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Tascha's Comp Time", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Pocket-watch daily time in/out & comp balance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Hero Balance & Quick Clock In / Out Card
            item {
                val balanceColor = if (summary.netBalanceHours >= 0.0) {
                    LocalWonderlandColors.current.successAccent
                } else {
                    MaterialTheme.colorScheme.error
                }
                WonderlandCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = MaterialTheme.colorScheme.primary
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Running Comp Balance",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CompTimeCalculator.formatSignedHours(summary.netBalanceHours),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = balanceColor
                                )
                            }
                            Button(onClick = onClockInOrOutNow) {
                                Icon(
                                    imageVector = if (openTodayEntry != null) Icons.AutoMirrored.Outlined.Logout else Icons.AutoMirrored.Outlined.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (openTodayEntry != null) "Clock Out Now" else "Clock In Now")
                            }
                        }

                        if (openTodayEntry != null) {
                            Text(
                                text = "Currently clocked in since ${WonderlandTimeFormat.formatMinutes(openTodayEntry.timeInMinutes ?: 0)} (tap Clock Out Now or tap the entry below to edit).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        OrnateGoldDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("This Week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(WonderlandTimeFormat.formatHours(summary.weekWorkedHours), style = MaterialTheme.typography.titleMedium)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Earned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "+${WonderlandTimeFormat.formatHours(summary.totalEarnedHours)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LocalWonderlandColors.current.successAccent
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "-${WonderlandTimeFormat.formatHours(summary.totalUsedHours)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            // Daily Standard Workday Baseline Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Standard Workday Baseline (${WonderlandTimeFormat.formatHours(dailyBaselineHours)}/day)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(7.5, 8.0, 8.5, 9.0, 10.0).forEach { hrs ->
                            FilterChip(
                                selected = dailyBaselineHours == hrs,
                                onClick = { onSetBaselineHours(hrs) },
                                label = { Text(WonderlandTimeFormat.formatHours(hrs)) }
                            )
                        }
                    }
                }
            }

            item {
                Text("Daily Timecard Log", style = MaterialTheme.typography.titleLarge)
            }

            if (entries.isEmpty()) {
                item {
                    CheshireEmptyState(
                        title = "No Timecards Logged Yet",
                        message = "Tap Clock In Now to start today, or tap + to log any day's Time In / Out or Comp Time Used."
                    )
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    val delta = CompTimeCalculator.compDeltaHours(entry, dailyBaselineHours)
                    val worked = CompTimeCalculator.workedHours(entry)
                    val deltaColor = when {
                        delta > 0.0 -> LocalWonderlandColors.current.successAccent
                        delta < 0.0 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    WonderlandCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = if (entry.entryType == CompTimeEntryType.COMP_USED) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        onClick = {
                            editingEntry = entry
                            showEditor = true
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = WonderlandTimeFormat.formatFullDate(LocalDate.ofEpochDay(entry.dateEpochDay)),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (entry.entryType == CompTimeEntryType.WORK_DAY) {
                                    val timeLabel = when {
                                        entry.timeInMinutes != null && entry.timeOutMinutes != null ->
                                            WonderlandTimeFormat.formatRange(entry.timeInMinutes, entry.timeOutMinutes)
                                        entry.timeInMinutes != null ->
                                            "In: ${WonderlandTimeFormat.formatMinutes(entry.timeInMinutes)} (Active)"
                                        else -> "Not clocked in"
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PocketWatchBadge(timeText = timeLabel)
                                        if (entry.breakMinutes > 0 && entry.timeOutMinutes != null) {
                                            Text(
                                                text = "-${entry.breakMinutes}m break",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (entry.timeOutMinutes != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Worked: ${WonderlandTimeFormat.formatHours(worked)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            text = "Comp Time Used (${WonderlandTimeFormat.formatHours(entry.usedCompHours)})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (entry.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = entry.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (entry.entryType == CompTimeEntryType.WORK_DAY && entry.timeOutMinutes == null) {
                                    "In"
                                } else {
                                    CompTimeCalculator.formatSignedHours(delta)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = deltaColor
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showEditor) {
        CompTimeEditorDialog(
            initial = editingEntry,
            dailyBaselineHours = dailyBaselineHours,
            onDismiss = { showEditor = false },
            onSave = onSaveEntry,
            onDelete = onDeleteEntry
        )
    }
}

@Composable
private fun CompTimeEditorDialog(
    initial: CompTimeEntryEntity?,
    dailyBaselineHours: Double,
    onDismiss: () -> Unit,
    onSave: (CompTimeEntryEntity) -> Unit,
    onDelete: (CompTimeEntryEntity) -> Unit
) {
    val nowMins = remember {
        val now = LocalTime.now()
        (now.hour * 60 + (now.minute / 15) * 15).coerceIn(0, 23 * 60 + 45)
    }
    var dateEpochDay by remember { mutableLongStateOf(initial?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var entryType by remember { mutableStateOf(initial?.entryType ?: CompTimeEntryType.WORK_DAY) }
    var timeInMinutes by remember { mutableIntStateOf(initial?.timeInMinutes ?: (8 * 60)) }
    var hasTimeOut by remember { mutableStateOf(initial == null || initial.timeOutMinutes != null) }
    var timeOutMinutes by remember { mutableIntStateOf(initial?.timeOutMinutes ?: maxOf(nowMins, 17 * 60)) }
    var breakMinutes by remember { mutableIntStateOf(initial?.breakMinutes ?: 30) }
    var usedCompHours by remember { mutableStateOf(if (initial != null && initial.usedCompHours > 0) initial.usedCompHours else 4.0) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    val previewEntity = CompTimeEntryEntity(
        id = initial?.id ?: 0L,
        dateEpochDay = dateEpochDay,
        entryType = entryType,
        timeInMinutes = if (entryType == CompTimeEntryType.WORK_DAY) timeInMinutes else null,
        timeOutMinutes = if (entryType == CompTimeEntryType.WORK_DAY && hasTimeOut) timeOutMinutes else null,
        breakMinutes = if (entryType == CompTimeEntryType.WORK_DAY) breakMinutes else 0,
        usedCompHours = if (entryType == CompTimeEntryType.COMP_USED) usedCompHours else 0.0,
        notes = notes.trim()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (initial == null) "Log Comp Time" else "Edit Timecard")
                if (initial != null) {
                    IconButton(onClick = { onDelete(initial); onDismiss() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.error)
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
                // Entry type selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompTimeEntryType.entries.forEach { t ->
                        FilterChip(
                            selected = entryType == t,
                            onClick = { entryType = t },
                            label = { Text(t.displayLabel) }
                        )
                    }
                }

                // Date stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { dateEpochDay -= 1L }) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous day")
                        }
                        Text(
                            WonderlandTimeFormat.formatShortDate(LocalDate.ofEpochDay(dateEpochDay)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { dateEpochDay += 1L }) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next day")
                        }
                    }
                }

                if (entryType == CompTimeEntryType.WORK_DAY) {
                    // Time In stepper (15-min increments)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Time In", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { timeInMinutes = (timeInMinutes - 15).coerceAtLeast(0) }) {
                                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Earlier")
                            }
                            Text(WonderlandTimeFormat.formatMinutes(timeInMinutes), style = MaterialTheme.typography.bodyMedium)
                            IconButton(
                                onClick = {
                                    timeInMinutes = (timeInMinutes + 15).coerceAtMost(23 * 60 + 30)
                                    if (timeOutMinutes <= timeInMinutes) {
                                        timeOutMinutes = (timeInMinutes + 60).coerceAtMost(23 * 60 + 45)
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "Later")
                            }
                        }
                    }

                    // Time Out toggle + stepper
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = hasTimeOut,
                            onClick = { hasTimeOut = true },
                            label = { Text("Set Time Out") }
                        )
                        FilterChip(
                            selected = !hasTimeOut,
                            onClick = { hasTimeOut = false },
                            label = { Text("Still Clocked In") }
                        )
                    }

                    if (hasTimeOut) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Time Out", style = MaterialTheme.typography.labelLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { timeOutMinutes = (timeOutMinutes - 15).coerceAtLeast(timeInMinutes + 15) }
                                ) {
                                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Earlier")
                                }
                                Text(WonderlandTimeFormat.formatMinutes(timeOutMinutes), style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = { timeOutMinutes = (timeOutMinutes + 15).coerceAtMost(23 * 60 + 45) }
                                ) {
                                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Later")
                                }
                            }
                        }

                        Text("Unpaid Break", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 30, 45, 60).forEach { b ->
                                FilterChip(
                                    selected = breakMinutes == b,
                                    onClick = { breakMinutes = b },
                                    label = { Text(if (b == 0) "None" else "${b}m") }
                                )
                            }
                        }

                        val worked = CompTimeCalculator.workedHours(previewEntity)
                        val delta = CompTimeCalculator.compDeltaHours(previewEntity, dailyBaselineHours)
                        Text(
                            text = "Worked: ${WonderlandTimeFormat.formatHours(worked)} • Comp: ${CompTimeCalculator.formatSignedHours(delta)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // Comp Time Used
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hours Used", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { usedCompHours = (usedCompHours - 0.5).coerceAtLeast(0.5) }) {
                                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Less")
                            }
                            Text(WonderlandTimeFormat.formatHours(usedCompHours), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { usedCompHours = (usedCompHours + 0.5).coerceAtMost(16.0) }) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "More")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1.0, 2.0, 4.0, 8.0).forEach { h ->
                            FilterChip(
                                selected = usedCompHours == h,
                                onClick = { usedCompHours = h },
                                label = { Text(WonderlandTimeFormat.formatHours(h)) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(previewEntity)
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
