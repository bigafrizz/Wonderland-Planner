package com.wonderland.taschasplanner.data.model

import com.wonderland.taschasplanner.data.local.BuildingEntity
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.TaskEntity
import com.wonderland.taschasplanner.data.local.TaskPriority
import com.wonderland.taschasplanner.data.local.TaskStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val colorInt: Int,
    val isWritable: Boolean
)

data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val calendarName: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean = false,
    val colorInt: Int = 0xFF6A1B9A.toInt(),
    val isSamplePreview: Boolean = false
) {
    fun dateEpochDay(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate().toEpochDay()
    }

    fun startMinutesOfDay(zoneId: ZoneId = ZoneId.systemDefault()): Int {
        val dt = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalTime()
        return dt.hour * 60 + dt.minute
    }

    fun endMinutesOfDay(zoneId: ZoneId = ZoneId.systemDefault()): Int {
        val dt = Instant.ofEpochMilli(endMillis).atZone(zoneId).toLocalTime()
        return dt.hour * 60 + dt.minute
    }
}

enum class PlannerItemSource { GOOGLE_CALENDAR, TASK, SHIFT }

data class UnifiedPlannerItem(
    val id: String,
    val source: PlannerItemSource,
    val title: String,
    val subtitle: String,
    val dateEpochDay: Long,
    val startMinutes: Int?,
    val endMinutes: Int?,
    val allDay: Boolean,
    val accentColorHex: Long,
    val customTimeLabel: String? = null,
    val buildingId: Long? = null,
    val employeeId: Long? = null,
    val taskEntity: TaskEntity? = null,
    val shiftEntity: ShiftScheduleEntity? = null,
    val calendarEvent: CalendarEvent? = null
)

data class ShiftConflict(
    val shiftA: ShiftScheduleEntity,
    val shiftB: ShiftScheduleEntity
)

object ShiftConflictDetector {
    /**
     * Detects overlapping timed shifts for the same employee on the same date (especially across different buildings).
     * Flexible-hours shifts (no fixed time in/out) do not trigger clock-overlap conflicts.
     */
    fun findConflicts(shifts: List<ShiftScheduleEntity>): List<ShiftConflict> {
        val conflicts = mutableListOf<ShiftConflict>()
        val grouped = shifts
            .filter { !it.isFlexibleHours }
            .groupBy { it.employeeId to it.dateEpochDay }
        for ((_, employeeDayShifts) in grouped) {
            val sorted = employeeDayShifts.sortedBy { it.startMinutes }
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    val a = sorted[i]
                    val b = sorted[j]
                    if (a.startMinutes < b.endMinutes && b.startMinutes < a.endMinutes) {
                        conflicts.add(ShiftConflict(a, b))
                    }
                }
            }
        }
        return conflicts
    }

    fun conflictingShiftIds(shifts: List<ShiftScheduleEntity>): Set<Long> {
        return findConflicts(shifts).flatMap { listOf(it.shiftA.id, it.shiftB.id) }.toSet()
    }

    /**
     * Sums total scheduled hours (both timed and flexible-hours shifts) per employee for a given day.
     */
    fun totalHoursByEmployeeOnDate(
        shifts: List<ShiftScheduleEntity>,
        dateEpochDay: Long
    ): Map<Long, Double> {
        return shifts
            .filter { it.dateEpochDay == dateEpochDay }
            .groupBy { it.employeeId }
            .mapValues { (_, list) -> list.sumOf { it.effectiveHours() } }
    }
}

object PlannerAggregator {
    fun buildUnifiedItems(
        tasks: List<TaskEntity>,
        shifts: List<ShiftScheduleEntity>,
        calendarEvents: List<CalendarEvent>,
        buildingsById: Map<Long, BuildingEntity>,
        employeeNamesById: Map<Long, String>,
        visibleCalendarIds: Set<Long>?,
        buildingFilterId: Long? = null,
        employeeFilterId: Long? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<UnifiedPlannerItem> {
        val items = mutableListOf<UnifiedPlannerItem>()
        val linkedTaskEventIds = tasks.mapNotNull { it.linkedCalendarEventId }.toSet()

        calendarEvents
            .filter { it.id !in linkedTaskEventIds }
            .filter { visibleCalendarIds == null || it.calendarId in visibleCalendarIds }
            .filter { buildingFilterId == null && employeeFilterId == null }
            .forEach { ev ->
                items.add(
                    UnifiedPlannerItem(
                        id = "gcal_${ev.id}_${ev.startMillis}",
                        source = PlannerItemSource.GOOGLE_CALENDAR,
                        title = ev.title,
                        subtitle = listOfNotNull(
                            ev.calendarName.takeIf { it.isNotBlank() },
                            ev.location.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        dateEpochDay = ev.dateEpochDay(zoneId),
                        startMinutes = if (ev.allDay) null else ev.startMinutesOfDay(zoneId),
                        endMinutes = if (ev.allDay) null else ev.endMinutesOfDay(zoneId),
                        allDay = ev.allDay,
                        accentColorHex = ev.colorInt.toLong() and 0xFFFFFFFFL,
                        calendarEvent = ev
                    )
                )
            }

        tasks
            .filter { buildingFilterId == null || it.buildingId == buildingFilterId }
            .filter { employeeFilterId == null || it.assignedEmployeeId == employeeFilterId }
            .forEach { task ->
                val bldg = task.buildingId?.let { buildingsById[it] }
                val emp = task.assignedEmployeeId?.let { employeeNamesById[it] }
                val subtitle = listOfNotNull(
                    bldg?.name,
                    task.roomOrZone.takeIf { it.isNotBlank() },
                    emp
                ).joinToString(" • ")

                items.add(
                    UnifiedPlannerItem(
                        id = "task_${task.id}",
                        source = PlannerItemSource.TASK,
                        title = task.title,
                        subtitle = subtitle.ifBlank { task.priority.wonderlandLabel },
                        dateEpochDay = task.dueDateEpochDay,
                        startMinutes = task.startTimeMinutes,
                        endMinutes = task.endTimeMinutes,
                        allDay = task.startTimeMinutes == null,
                        accentColorHex = bldg?.colorHex ?: 0xFF6A1B9AL,
                        buildingId = task.buildingId,
                        employeeId = task.assignedEmployeeId,
                        taskEntity = task
                    )
                )
            }

        shifts
            .filter { buildingFilterId == null || it.buildingId == buildingFilterId }
            .filter { employeeFilterId == null || it.employeeId == employeeFilterId }
            .forEach { shift ->
                val bldg = buildingsById[shift.buildingId]
                val emp = employeeNamesById[shift.employeeId] ?: "Team Member"
                items.add(
                    UnifiedPlannerItem(
                        id = "shift_${shift.id}",
                        source = PlannerItemSource.SHIFT,
                        title = "$emp — ${bldg?.name ?: "Building"}",
                        subtitle = listOfNotNull(
                            shift.stationOrRole.takeIf { it.isNotBlank() },
                            shift.status.displayLabel
                        ).joinToString(" • "),
                        dateEpochDay = shift.dateEpochDay,
                        startMinutes = if (shift.isFlexibleHours) null else shift.startMinutes,
                        endMinutes = if (shift.isFlexibleHours) null else shift.endMinutes,
                        allDay = shift.isFlexibleHours,
                        accentColorHex = bldg?.colorHex ?: 0xFF1A5F6BL,
                        customTimeLabel = WonderlandTimeFormat.formatShiftSchedule(shift),
                        buildingId = shift.buildingId,
                        employeeId = shift.employeeId,
                        shiftEntity = shift
                    )
                )
            }

        return items.sortedWith(
            compareBy<UnifiedPlannerItem> { it.dateEpochDay }
                .thenBy { if (it.allDay) -1 else (it.startMinutes ?: 0) }
                .thenBy { it.title }
        )
    }

    fun sortTasksByPriorityAndStatus(tasks: List<TaskEntity>): List<TaskEntity> {
        val priorityRank = mapOf(
            TaskPriority.URGENT to 0,
            TaskPriority.HIGH to 1,
            TaskPriority.MEDIUM to 2,
            TaskPriority.LOW to 3
        )
        return tasks.sortedWith(
            compareBy<TaskEntity> { it.status == TaskStatus.DONE }
                .thenBy { priorityRank[it.priority] ?: 2 }
                .thenBy { it.dueDateEpochDay }
                .thenBy { it.startTimeMinutes ?: Int.MAX_VALUE }
        )
    }
}

object WonderlandTimeFormat {
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    fun formatMinutes(minutes: Int): String {
        val clamped = minutes.coerceIn(0, 24 * 60 - 1)
        val hour24 = clamped / 60
        val min = clamped % 60
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return if (min == 0) "$hour12 $amPm" else "$hour12:${min.toString().padStart(2, '0')} $amPm"
    }

    fun formatRange(startMinutes: Int?, endMinutes: Int?): String {
        if (startMinutes == null) return "All Day"
        return if (endMinutes != null && endMinutes > startMinutes) {
            "${formatMinutes(startMinutes)} – ${formatMinutes(endMinutes)}"
        } else {
            formatMinutes(startMinutes)
        }
    }

    fun formatHours(hours: Double): String {
        val rounded = kotlin.math.round(hours * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) {
            val whole = rounded.toInt()
            if (whole == 1) "1 hr" else "$whole hrs"
        } else {
            "$rounded hrs"
        }
    }

    fun formatShiftSchedule(shift: ShiftScheduleEntity): String {
        return if (shift.isFlexibleHours) {
            "${formatHours(shift.allocatedHours)} • Flexible"
        } else {
            formatRange(shift.startMinutes, shift.endMinutes)
        }
    }

    fun formatShortDate(date: LocalDate): String = date.format(dateFormatter)
    fun formatFullDate(date: LocalDate): String = date.format(fullDateFormatter)
    fun formatMonthYear(date: LocalDate): String = date.format(monthYearFormatter)
}

object ScheduleDateExpander {
    /**
     * Expands [startDate] across [weeksCount] rolling 7-day weeks, keeping every date whose
     * DayOfWeek is in [selectedDays]. Each selected weekday appears exactly [weeksCount] times.
     */
    fun expandDates(
        startDate: LocalDate,
        selectedDays: Set<DayOfWeek>,
        weeksCount: Int
    ): List<LocalDate> {
        val safeWeeks = weeksCount.coerceIn(1, 12)
        if (selectedDays.isEmpty()) return listOf(startDate)
        val totalDays = safeWeeks * 7L
        val results = (0L until totalDays)
            .map { startDate.plusDays(it) }
            .filter { it.dayOfWeek in selectedDays }
        return results.ifEmpty { listOf(startDate) }
    }
}
