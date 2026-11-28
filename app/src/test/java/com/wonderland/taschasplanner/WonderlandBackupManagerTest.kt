package com.wonderland.taschasplanner

import com.wonderland.taschasplanner.data.backup.AutoBackupFileHelper
import com.wonderland.taschasplanner.data.backup.WonderlandBackupCodec
import com.wonderland.taschasplanner.data.backup.WonderlandBackupSnapshot
import com.wonderland.taschasplanner.data.local.BuildingEntity
import com.wonderland.taschasplanner.data.local.ContactEntity
import com.wonderland.taschasplanner.data.local.ContactType
import com.wonderland.taschasplanner.data.local.ShiftScheduleEntity
import com.wonderland.taschasplanner.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WonderlandBackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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
            ),
            compTimeEntries = listOf(
                com.wonderland.taschasplanner.data.local.CompTimeEntryEntity(
                    id = 300L,
                    dateEpochDay = 20000L,
                    timeInMinutes = 8 * 60,
                    timeOutMinutes = 17 * 60 + 30,
                    breakMinutes = 30,
                    notes = "Late event setup"
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
        assertEquals(1, decoded.compTimeEntries.size)
        assertEquals("Late event setup", decoded.compTimeEntries.first().notes)
    }

    @Test
    fun autoBackupWritesAtomicallyNeverOverwritesWithEmptyAndRecoversFromMirror() {
        val primary = File(tempFolder.root, "internal/wonderland_auto_backup.json")
        val mirror = File(tempFolder.root, "external/wonderland_auto_backup.json")

        val validSnapshot = WonderlandBackupSnapshot(
            exportedAtMillis = 1_700_000_000_000L,
            buildings = listOf(BuildingEntity(id = 1L, name = "Main Manor", code = "MNR")),
            contacts = listOf(ContactEntity(id = 10L, type = ContactType.EMPLOYEE, fullName = "Alice Liddell", buildingId = 1L)),
            tasks = emptyList(),
            shifts = emptyList()
        )
        assertTrue(AutoBackupFileHelper.writeSnapshotToFiles(validSnapshot, primary, mirror))
        assertTrue(primary.exists())
        assertTrue(mirror.exists())

        // Empty DB snapshot must NEVER overwrite an existing valid backup
        val emptySnapshot = WonderlandBackupSnapshot(
            exportedAtMillis = 1_700_000_005_000L,
            buildings = emptyList(),
            contacts = emptyList(),
            tasks = emptyList(),
            shifts = emptyList()
        )
        assertFalse(AutoBackupFileHelper.writeSnapshotToFiles(emptySnapshot, primary, mirror))

        // Delete primary file and verify mirror recovery still succeeds
        assertTrue(primary.delete())
        val recovered = AutoBackupFileHelper.readLatestValidSnapshot(primary, mirror)
        assertNotNull(recovered)
        assertEquals("Main Manor", recovered!!.second.buildings.first().name)
        assertEquals("Alice Liddell", recovered.second.contacts.first().fullName)
    }
}
