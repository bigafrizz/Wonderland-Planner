package com.wonderland.taschasplanner

import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.model.ShiftConflictDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftConflictDetectorTest {

    @Test
    fun sumsDailyHoursAcrossTimedAndFlexibleShifts() {
        val timedMorning = ShiftScheduleEntity(
            id = 1L,
            employeeId = 10L,
            buildingId = 100L,
            dateEpochDay = 20000L,
            startMinutes = 8 * 60,
            endMinutes = 12 * 60
        )
        val flexibleAfternoon = ShiftScheduleEntity(
            id = 2L,
            employeeId = 10L,
            buildingId = 200L,
            dateEpochDay = 20000L,
            isFlexibleHours = true,
            allocatedHours = 2.5
        )
        val otherEmployee = ShiftScheduleEntity(
            id = 3L,
            employeeId = 11L,
            buildingId = 100L,
            dateEpochDay = 20000L,
            startMinutes = 9 * 60,
            endMinutes = 17 * 60
        )

        val totals = ShiftConflictDetector.totalHoursByEmployeeOnDate(
            listOf(timedMorning, flexibleAfternoon, otherEmployee),
            20000L
        )
        assertEquals(6.5, totals[10L] ?: 0.0, 0.001)
        assertEquals(8.0, totals[11L] ?: 0.0, 0.001)
    }

    @Test
    fun compTimeCalculatorComputesWorkedEarnedAndUsedBalance() {
        val today = java.time.LocalDate.of(2026, 11, 25)
        val day1 = com.wonderland.taschasplanner.data.local.CompTimeEntryEntity(
            id = 1L,
            dateEpochDay = today.toEpochDay(),
            entryType = com.wonderland.taschasplanner.data.local.CompTimeEntryType.WORK_DAY,
            timeInMinutes = 8 * 60,
            timeOutMinutes = 18 * 60, // 10h gross - 0.5h break = 9.5h worked -> +1.5h comp
            breakMinutes = 30
        )
        val day2Used = com.wonderland.taschasplanner.data.local.CompTimeEntryEntity(
            id = 2L,
            dateEpochDay = today.plusDays(1).toEpochDay(),
            entryType = com.wonderland.taschasplanner.data.local.CompTimeEntryType.COMP_USED,
            usedCompHours = 1.0
        )

        val summary = com.wonderland.taschasplanner.data.model.CompTimeCalculator.summarize(
            entries = listOf(day1, day2Used),
            dailyBaselineHours = 8.0,
            today = today
        )
        assertEquals(9.5, summary.weekWorkedHours, 0.001)
        assertEquals(1.5, summary.totalEarnedHours, 0.001)
        assertEquals(1.0, summary.totalUsedHours, 0.001)
        assertEquals(0.5, summary.netBalanceHours, 0.001)
    }
}
