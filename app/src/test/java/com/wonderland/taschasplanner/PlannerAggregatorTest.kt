package com.wonderland.taschasplanner

import com.wonderland.taschasplanner.data.local.BuildingEntity
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.TaskEntity
import com.wonderland.taschasplanner.data.local.TaskPriority
import com.wonderland.taschasplanner.data.local.TaskStatus
import com.wonderland.taschasplanner.data.model.CalendarEvent
import com.wonderland.taschasplanner.data.model.PlannerAggregator
import com.wonderland.taschasplanner.data.model.PlannerItemSource
import com.wonderland.taschasplanner.data.model.WonderlandTimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PlannerAggregatorTest {

    private val today = LocalDate.of(2026, 11, 25)
    private val todayEpoch = today.toEpochDay()
    private val manor = BuildingEntity(id = 1L, name = "Main Manor", code = "MNR", colorHex = 0xFF6A1B9AL)
    private val teaHouse = BuildingEntity(id = 2L, name = "Tea House", code = "TEA", colorHex = 0xFF1A5F6BL)

    @Test
    fun mergesCalendarTasksAndShiftsSortedChronologically() {
        val gcalEvent = CalendarEvent(
            id = 50L,
            calendarId = 5L,
            calendarName = "Work Google Cal",
            title = "Leadership Standup",
            startMillis = today.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            endMillis = today.atTime(9, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        val task = TaskEntity(
            id = 1L,
            title = "Inspect Lobby",
            assignedEmployeeId = 10L,
            buildingId = manor.id,
            dueDateEpochDay = todayEpoch,
            startTimeMinutes = 11 * 60,
            endTimeMinutes = 12 * 60
        )
        val shift = ShiftScheduleEntity(
            id = 2L,
            employeeId = 10L,
            buildingId = teaHouse.id,
            dateEpochDay = todayEpoch,
            startMinutes = 8 * 60,
            endMinutes = 16 * 60
        )

        val unified = PlannerAggregator.buildUnifiedItems(
            tasks = listOf(task),
            shifts = listOf(shift),
            calendarEvents = listOf(gcalEvent),
            buildingsById = mapOf(manor.id to manor, teaHouse.id to teaHouse),
            employeeNamesById = mapOf(10L to "Alice"),
            visibleCalendarIds = setOf(5L),
            zoneId = ZoneOffset.UTC
        )

        assertEquals(3, unified.size)
        assertEquals(PlannerItemSource.SHIFT, unified[0].source)
        assertEquals(PlannerItemSource.GOOGLE_CALENDAR, unified[1].source)
        assertEquals(PlannerItemSource.TASK, unified[2].source)
    }

    @Test
    fun filtersHiddenGoogleCalendarsAndSpecificBuildings() {
        val gcalEvent = CalendarEvent(
            id = 50L,
            calendarId = 5L,
            calendarName = "Hidden Cal",
            title = "Secret Tea",
            startMillis = today.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            endMillis = today.atTime(11, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        val manorTask = TaskEntity(id = 1L, title = "Manor Task", buildingId = manor.id, dueDateEpochDay = todayEpoch)
        val teaTask = TaskEntity(id = 2L, title = "Tea Task", buildingId = teaHouse.id, dueDateEpochDay = todayEpoch)

        val filtered = PlannerAggregator.buildUnifiedItems(
            tasks = listOf(manorTask, teaTask),
            shifts = emptyList(),
            calendarEvents = listOf(gcalEvent),
            buildingsById = mapOf(manor.id to manor, teaHouse.id to teaHouse),
            employeeNamesById = emptyMap(),
            visibleCalendarIds = emptySet(),
            buildingFilterId = teaHouse.id,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(1, filtered.size)
        assertEquals("Tea Task", filtered.first().title)
    }

    @Test
    fun sortsTasksByCompletionThenPriorityAndFormatsTime() {
        val doneUrgent = TaskEntity(id = 1L, title = "Done", dueDateEpochDay = todayEpoch, priority = TaskPriority.URGENT, status = TaskStatus.DONE)
        val openLow = TaskEntity(id = 2L, title = "Low", dueDateEpochDay = todayEpoch, priority = TaskPriority.LOW, status = TaskStatus.TODO)
        val openUrgent = TaskEntity(id = 3L, title = "Urgent", dueDateEpochDay = todayEpoch, priority = TaskPriority.URGENT, status = TaskStatus.IN_PROGRESS)

        val sorted = PlannerAggregator.sortTasksByPriorityAndStatus(listOf(doneUrgent, openLow, openUrgent))
        assertEquals(listOf("Urgent", "Low", "Done"), sorted.map { it.title })

        assertEquals("9 AM – 10:30 AM", WonderlandTimeFormat.formatRange(9 * 60, 10 * 60 + 30))
        assertEquals("All Day", WonderlandTimeFormat.formatRange(null, null))
        assertEquals("4 hrs", WonderlandTimeFormat.formatHours(4.0))
        assertEquals("4.5 hrs", WonderlandTimeFormat.formatHours(4.5))
        val flexShift = ShiftScheduleEntity(
            id = 9L,
            employeeId = 10L,
            buildingId = manor.id,
            dateEpochDay = todayEpoch,
            isFlexibleHours = true,
            allocatedHours = 4.5
        )
        assertEquals("4.5 hrs • Flexible", WonderlandTimeFormat.formatShiftSchedule(flexShift))
    }

    @Test
    fun deduplicatesCalendarEventLinkedToSyncedTask() {
        val syncedEvent = CalendarEvent(
            id = 777L,
            calendarId = 5L,
            calendarName = "Work Google Cal",
            title = "Synced Task",
            startMillis = today.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            endMillis = today.atTime(11, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        val syncedTask = TaskEntity(
            id = 1L,
            title = "Synced Task",
            dueDateEpochDay = todayEpoch,
            startTimeMinutes = 10 * 60,
            endTimeMinutes = 11 * 60,
            linkedCalendarEventId = 777L
        )

        val unified = PlannerAggregator.buildUnifiedItems(
            tasks = listOf(syncedTask),
            shifts = emptyList(),
            calendarEvents = listOf(syncedEvent),
            buildingsById = emptyMap(),
            employeeNamesById = emptyMap(),
            visibleCalendarIds = setOf(5L),
            zoneId = ZoneOffset.UTC
        )

        assertEquals(1, unified.size)
        assertEquals(PlannerItemSource.TASK, unified.first().source)
    }

    @Test
    fun expandsMultiDayMultiWeekScheduleDatesAccurately() {
        // 2026-11-25 is a Wednesday
        val dates = com.wonderland.taschasplanner.data.model.ScheduleDateExpander.expandDates(
            startDate = today,
            selectedDays = setOf(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.FRIDAY
            ),
            weeksCount = 2
        )
        assertEquals(6, dates.size)
        assertEquals(LocalDate.of(2026, 11, 25), dates.first())
        assertEquals(LocalDate.of(2026, 12, 7), dates.last())

        val weekdaysFourWeeks = com.wonderland.taschasplanner.data.model.ScheduleDateExpander.expandDates(
            startDate = today,
            selectedDays = setOf(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.TUESDAY,
                java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY,
                java.time.DayOfWeek.FRIDAY
            ),
            weeksCount = 4
        )
        assertEquals(20, weekdaysFourWeeks.size)
    }

    @Test
    fun batchMatcherFindsMatchingTasksAndShiftsAcrossWeeks() {
        val t1 = TaskEntity(id = 1L, title = "Morning Check", assignedEmployeeId = 10L, buildingId = manor.id, dueDateEpochDay = todayEpoch, startTimeMinutes = 540, endTimeMinutes = 600)
        val t2 = t1.copy(id = 2L, dueDateEpochDay = todayEpoch + 7)
        val otherTask = t1.copy(id = 3L, title = "Different Task")

        val matchedTasks = com.wonderland.taschasplanner.data.model.ScheduleBatchMatcher.matchingTasks(t1, listOf(t1, t2, otherTask))
        assertEquals(listOf(1L, 2L), matchedTasks.map { it.id })

        val s1 = ShiftScheduleEntity(id = 10L, employeeId = 10L, buildingId = manor.id, dateEpochDay = todayEpoch, isFlexibleHours = true, allocatedHours = 4.5)
        val s2 = s1.copy(id = 11L, dateEpochDay = todayEpoch + 7)
        val otherShift = s1.copy(id = 12L, allocatedHours = 6.0)

        val matchedShifts = com.wonderland.taschasplanner.data.model.ScheduleBatchMatcher.matchingShifts(s1, listOf(s1, s2, otherShift))
        assertEquals(listOf(10L, 11L), matchedShifts.map { it.id })
    }

    @Test
    fun expandsYearlyEmployeeBirthdaysAndDeduplicatesSyncedCalendarEvent() {
        val alice = com.wonderland.taschasplanner.data.local.ContactEntity(
            id = 50L,
            type = com.wonderland.taschasplanner.data.local.ContactType.EMPLOYEE,
            fullName = "Alice Liddell",
            buildingId = manor.id,
            birthdayMonth = 2,
            birthdayDay = 29,
            linkedBirthdayEventId = 900L
        )
        val syncedBdayEvent = CalendarEvent(
            id = 900L,
            calendarId = 5L,
            calendarName = "Work Cal",
            title = "Alice's Birthday",
            startMillis = LocalDate.of(2026, 2, 28).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            endMillis = LocalDate.of(2026, 3, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            allDay = true
        )

        val unified = PlannerAggregator.buildUnifiedItems(
            tasks = emptyList(),
            shifts = emptyList(),
            calendarEvents = listOf(syncedBdayEvent),
            buildingsById = mapOf(manor.id to manor),
            employeeNamesById = mapOf(50L to "Alice Liddell"),
            visibleCalendarIds = setOf(5L),
            contactsWithBirthdays = listOf(alice),
            birthdayYears = listOf(2026, 2028),
            zoneId = ZoneOffset.UTC
        )

        assertEquals(2, unified.size)
        assertEquals(PlannerItemSource.BIRTHDAY, unified[0].source)
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), unified[0].dateEpochDay)
        assertEquals(LocalDate.of(2028, 2, 29).toEpochDay(), unified[1].dateEpochDay)
    }
}
