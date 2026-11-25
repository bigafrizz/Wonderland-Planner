package com.wonderland.taschasplanner

import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.model.ShiftConflictDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftConflictDetectorTest {

    @Test
    fun detectsOverlappingShiftsAcrossDifferentBuildingsForSameEmployee() {
        val shiftManor = ShiftScheduleEntity(
            id = 1L,
            employeeId = 10L,
            buildingId = 100L,
            dateEpochDay = 20000L,
            startMinutes = 9 * 60,
            endMinutes = 13 * 60
        )
        val shiftTeaHouse = ShiftScheduleEntity(
            id = 2L,
            employeeId = 10L,
            buildingId = 200L,
            dateEpochDay = 20000L,
            startMinutes = 12 * 60,
            endMinutes = 16 * 60
        )

        val conflicts = ShiftConflictDetector.findConflicts(listOf(shiftManor, shiftTeaHouse))
        assertEquals(1, conflicts.size)
        assertEquals(setOf(1L, 2L), ShiftConflictDetector.conflictingShiftIds(listOf(shiftManor, shiftTeaHouse)))
    }

    @Test
    fun adjacentBackToBackShiftsAreNotConflicts() {
        val morning = ShiftScheduleEntity(
            id = 1L,
            employeeId = 10L,
            buildingId = 100L,
            dateEpochDay = 20000L,
            startMinutes = 8 * 60,
            endMinutes = 12 * 60
        )
        val afternoon = ShiftScheduleEntity(
            id = 2L,
            employeeId = 10L,
            buildingId = 200L,
            dateEpochDay = 20000L,
            startMinutes = 12 * 60,
            endMinutes = 16 * 60
        )

        assertTrue(ShiftConflictDetector.findConflicts(listOf(morning, afternoon)).isEmpty())
    }

    @Test
    fun overlappingShiftsForDifferentEmployeesInSameBuildingAreAllowed() {
        val emp1 = ShiftScheduleEntity(
            id = 1L,
            employeeId = 10L,
            buildingId = 100L,
            dateEpochDay = 20000L,
            startMinutes = 9 * 60,
            endMinutes = 17 * 60
        )
        val emp2 = ShiftScheduleEntity(
            id = 2L,
            employeeId = 11L,
            buildingId = 100L,
            dateEpochDay = 20000L,
            startMinutes = 9 * 60,
            endMinutes = 17 * 60
        )

        assertTrue(ShiftConflictDetector.findConflicts(listOf(emp1, emp2)).isEmpty())
    }

    @Test
    fun flexibleHoursShiftDoesNotTriggerClockOverlapAndSumsDailyHours() {
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

        val shifts = listOf(timedMorning, flexibleAfternoon)
        assertTrue(ShiftConflictDetector.findConflicts(shifts).isEmpty())

        val totals = ShiftConflictDetector.totalHoursByEmployeeOnDate(shifts, 20000L)
        assertEquals(6.5, totals[10L] ?: 0.0, 0.001)
    }
}
