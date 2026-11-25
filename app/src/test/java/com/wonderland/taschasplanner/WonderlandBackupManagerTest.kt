package com.wonderland.taschasplanner

import com.wonderland.taschasplanner.data.backup.WonderlandBackupCodec
import com.wonderland.taschasplanner.data.backup.WonderlandBackupSnapshot
import com.wonderland.taschasplanner.data.local.BuildingEntity
import com.wonderland.taschasplanner.data.local.ContactEntity
import com.wonderland.taschasplanner.data.local.ContactType
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WonderlandBackupManagerTest {

    @Test
    fun backupJsonRoundTripPreservesAllEntitiesAndFlexibleShifts() {
        val snapshot = WonderlandBackupSnapshot(
            buildings = listOf(BuildingEntity(id = 1L, name = "Main Manor", code = "MNR")),
            contacts = listOf(
                ContactEntity(id = 10L, type = ContactType.EMPLOYEE, fullName = "Alice Liddell", buildingId = 1L)
            ),
            tasks = listOf(
                TaskEntity(id = 100L, title = "Key Check", assignedEmployeeId = 10L, buildingId = 1L, dueDateEpochDay = 20000L)
            ),
            shifts = listOf(
                ShiftScheduleEntity(
                    id = 200L,
                    employeeId = 10L,
                    buildingId = 1L,
                    dateEpochDay = 20000L,
                    isFlexibleHours = true,
                    allocatedHours = 4.5,
                    stationOrRole = "Flexible Restock"
                )
            )
        )

        val encoded = WonderlandBackupCodec.encode(snapshot)
        val decoded = WonderlandBackupCodec.decode(encoded)

        assertEquals(1, decoded.buildings.size)
        assertEquals("Main Manor", decoded.buildings.first().name)
        assertEquals(1, decoded.contacts.size)
        assertEquals("Alice Liddell", decoded.contacts.first().fullName)
        assertEquals(1, decoded.tasks.size)
        assertEquals("Key Check", decoded.tasks.first().title)
        assertEquals(1, decoded.shifts.size)
        assertTrue(decoded.shifts.first().isFlexibleHours)
        assertEquals(4.5, decoded.shifts.first().allocatedHours, 0.001)
    }
}
