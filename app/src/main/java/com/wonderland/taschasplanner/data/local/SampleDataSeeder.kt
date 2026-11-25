package com.wonderland.taschasplanner.data.local

import java.time.LocalDate

object SampleDataSeeder {

    suspend fun seedIfEmpty(db: WonderlandDatabase) {
        if (db.buildingDao().count() > 0) return
        seedSampleData(db)
    }

    suspend fun seedSampleData(db: WonderlandDatabase) {
        val today = LocalDate.now().toEpochDay()

        val manorId = db.buildingDao().insert(
            BuildingEntity(
                name = "Main Manor",
                code = "MNR",
                address = "100 Looking-Glass Lane",
                notes = "Primary headquarters, reception & dispatch desk",
                colorHex = 0xFF6A1B9AL
            )
        )
        val teaHouseId = db.buildingDao().insert(
            BuildingEntity(
                name = "Tea House Annex",
                code = "TEA",
                address = "42 Mad Hatter Way",
                notes = "Client hospitality suites & meeting rooms",
                colorHex = 0xFF1A5F6BL
            )
        )
        val conservatoryId = db.buildingDao().insert(
            BuildingEntity(
                name = "Queen's Conservatory",
                code = "QCN",
                address = "7 Rose Garden Court",
                notes = "North wing operations, inventory & maintenance",
                colorHex = 0xFFD81B60L
            )
        )

        val aliceId = db.contactDao().insert(
            ContactEntity(
                type = ContactType.EMPLOYEE,
                fullName = "Alice Liddell",
                roleOrCompany = "Lead Operations Coordinator",
                phone = "555-0101",
                email = "alice@wonderlandops.com",
                buildingId = manorId,
                cardSuit = CardSuit.HEART,
                notes = "Covers morning dispatch & VIP walkthroughs",
                isFavorite = true
            )
        )
        val hatterId = db.contactDao().insert(
            ContactEntity(
                type = ContactType.EMPLOYEE,
                fullName = "Tarrant Hightopp",
                roleOrCompany = "Facilities & Setup Lead",
                phone = "555-0102",
                email = "tarrant@wonderlandops.com",
                buildingId = teaHouseId,
                cardSuit = CardSuit.DIAMOND,
                notes = "Specializes in Tea House room turnovers",
                isFavorite = true
            )
        )
        val rabbitId = db.contactDao().insert(
            ContactEntity(
                type = ContactType.EMPLOYEE,
                fullName = "Warren White",
                roleOrCompany = "Scheduling & Logistics",
                phone = "555-0103",
                email = "warren@wonderlandops.com",
                buildingId = conservatoryId,
                cardSuit = CardSuit.SPADE,
                notes = "Always punctual for delivery windows"
            )
        )

        val customer1Id = db.contactDao().insert(
            ContactEntity(
                type = ContactType.CUSTOMER,
                fullName = "Mirana Marmoreal",
                roleOrCompany = "White Crown Events",
                phone = "555-0201",
                email = "mirana@whitecrown.example",
                buildingId = teaHouseId,
                cardSuit = CardSuit.CLUB,
                notes = "Prefers morning check-ins at Tea House Annex",
                isFavorite = true
            )
        )
        val customer2Id = db.contactDao().insert(
            ContactEntity(
                type = ContactType.CUSTOMER,
                fullName = "Charles Dodgson",
                roleOrCompany = "Oxford Press Partners",
                phone = "555-0202",
                email = "charles@oxfordpress.example",
                buildingId = manorId,
                cardSuit = CardSuit.SPADE,
                notes = "Monthly recurring contract review"
            )
        )

        // Employee Tasks across buildings
        db.taskDao().insert(
            TaskEntity(
                title = "Morning Walkthrough & Key Check",
                description = "Verify reception, conference rooms, and security log.",
                assignedEmployeeId = aliceId,
                buildingId = manorId,
                roomOrZone = "Grand Lobby",
                dueDateEpochDay = today,
                startTimeMinutes = 9 * 60,
                endTimeMinutes = 10 * 60,
                priority = TaskPriority.HIGH,
                status = TaskStatus.IN_PROGRESS,
                recurrence = RecurrenceRule.DAILY
            )
        )
        db.taskDao().insert(
            TaskEntity(
                title = "Prepare Tea House Suite for White Crown Events",
                description = "Confirm AV setup, seating chart, and refreshments with Mirana.",
                assignedEmployeeId = hatterId,
                customerId = customer1Id,
                buildingId = teaHouseId,
                roomOrZone = "Suite B",
                dueDateEpochDay = today,
                startTimeMinutes = 11 * 60,
                endTimeMinutes = 12 * 60 + 30,
                priority = TaskPriority.URGENT,
                status = TaskStatus.TODO
            )
        )
        db.taskDao().insert(
            TaskEntity(
                title = "Conservatory Supply Restock",
                description = "Log incoming shipment and update north wing shelves.",
                assignedEmployeeId = rabbitId,
                buildingId = conservatoryId,
                roomOrZone = "Storage Bay 2",
                dueDateEpochDay = today,
                startTimeMinutes = 14 * 60,
                endTimeMinutes = 15 * 60 + 30,
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.TODO
            )
        )
        db.taskDao().insert(
            TaskEntity(
                title = "Contract Renewal Call — Oxford Press",
                description = "Review Q4 building access schedule with Charles.",
                assignedEmployeeId = aliceId,
                customerId = customer2Id,
                buildingId = manorId,
                roomOrZone = "Office 204",
                dueDateEpochDay = today + 1,
                startTimeMinutes = 10 * 60 + 30,
                endTimeMinutes = 11 * 60 + 30,
                priority = TaskPriority.HIGH,
                status = TaskStatus.TODO,
                recurrence = RecurrenceRule.MONTHLY
            )
        )
        db.taskDao().insert(
            TaskEntity(
                title = "Weekly Cross-Building Inspection",
                description = "Audit checklists across all three buildings.",
                assignedEmployeeId = hatterId,
                buildingId = conservatoryId,
                roomOrZone = "All Zones",
                dueDateEpochDay = today + 3,
                startTimeMinutes = 13 * 60,
                endTimeMinutes = 14 * 60 + 30,
                priority = TaskPriority.LOW,
                status = TaskStatus.TODO,
                recurrence = RecurrenceRule.WEEKLY
            )
        )

        // Multi-Building Employee Shifts
        db.shiftScheduleDao().insert(
            ShiftScheduleEntity(
                employeeId = aliceId,
                buildingId = manorId,
                dateEpochDay = today,
                startMinutes = 8 * 60,
                endMinutes = 16 * 60,
                stationOrRole = "Front Desk & Dispatch",
                status = ShiftStatus.CHECKED_IN,
                notes = "Primary building lead"
            )
        )
        db.shiftScheduleDao().insert(
            ShiftScheduleEntity(
                employeeId = hatterId,
                buildingId = teaHouseId,
                dateEpochDay = today,
                startMinutes = 9 * 60,
                endMinutes = 17 * 60,
                stationOrRole = "Hospitality & Setup",
                status = ShiftStatus.SCHEDULED
            )
        )
        db.shiftScheduleDao().insert(
            ShiftScheduleEntity(
                employeeId = rabbitId,
                buildingId = conservatoryId,
                dateEpochDay = today,
                isFlexibleHours = true,
                allocatedHours = 4.5,
                stationOrRole = "Flexible Inventory & Restock",
                status = ShiftStatus.SCHEDULED,
                notes = "Complete 4.5 hours anytime today"
            )
        )
        db.shiftScheduleDao().insert(
            ShiftScheduleEntity(
                employeeId = aliceId,
                buildingId = teaHouseId,
                dateEpochDay = today + 1,
                startMinutes = 9 * 60,
                endMinutes = 15 * 60,
                stationOrRole = "Client Walkthrough Coverage",
                status = ShiftStatus.SCHEDULED
            )
        )
    }

    suspend fun clearAllData(db: WonderlandDatabase) {
        db.shiftScheduleDao().deleteAll()
        db.taskDao().deleteAll()
        db.contactDao().deleteAll()
        db.buildingDao().deleteAll()
    }
}
