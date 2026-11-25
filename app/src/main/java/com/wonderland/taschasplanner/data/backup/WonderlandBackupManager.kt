package com.wonderland.taschasplanner.data.backup

import androidx.room.withTransaction
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
import com.wonderland.taschasplanner.data.local.WonderlandDatabase
import org.json.JSONArray
import org.json.JSONObject

data class WonderlandBackupSnapshot(
    val schemaVersion: Int = 2,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val buildings: List<BuildingEntity>,
    val contacts: List<ContactEntity>,
    val tasks: List<TaskEntity>,
    val shifts: List<ShiftScheduleEntity>
)

object WonderlandBackupCodec {
    fun encode(snapshot: WonderlandBackupSnapshot): String {
        val root = JSONObject().apply {
            put("schemaVersion", snapshot.schemaVersion)
            put("exportedAtMillis", snapshot.exportedAtMillis)
            put("buildings", JSONArray().apply {
                snapshot.buildings.forEach { b ->
                    put(JSONObject().apply {
                        put("id", b.id)
                        put("name", b.name)
                        put("code", b.code)
                        put("address", b.address)
                        put("notes", b.notes)
                        put("colorHex", b.colorHex)
                        put("isActive", b.isActive)
                    })
                }
            })
            put("contacts", JSONArray().apply {
                snapshot.contacts.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("type", c.type.name)
                        put("fullName", c.fullName)
                        put("roleOrCompany", c.roleOrCompany)
                        put("phone", c.phone)
                        put("email", c.email)
                        if (c.buildingId != null) put("buildingId", c.buildingId)
                        put("cardSuit", c.cardSuit.name)
                        put("notes", c.notes)
                        put("isFavorite", c.isFavorite)
                    })
                }
            })
            put("tasks", JSONArray().apply {
                snapshot.tasks.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("description", t.description)
                        if (t.assignedEmployeeId != null) put("assignedEmployeeId", t.assignedEmployeeId)
                        if (t.customerId != null) put("customerId", t.customerId)
                        if (t.buildingId != null) put("buildingId", t.buildingId)
                        put("roomOrZone", t.roomOrZone)
                        put("dueDateEpochDay", t.dueDateEpochDay)
                        if (t.startTimeMinutes != null) put("startTimeMinutes", t.startTimeMinutes)
                        if (t.endTimeMinutes != null) put("endTimeMinutes", t.endTimeMinutes)
                        put("priority", t.priority.name)
                        put("status", t.status.name)
                        put("recurrence", t.recurrence.name)
                        if (t.linkedCalendarEventId != null) put("linkedCalendarEventId", t.linkedCalendarEventId)
                    })
                }
            })
            put("shifts", JSONArray().apply {
                snapshot.shifts.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("employeeId", s.employeeId)
                        put("buildingId", s.buildingId)
                        put("dateEpochDay", s.dateEpochDay)
                        put("startMinutes", s.startMinutes)
                        put("endMinutes", s.endMinutes)
                        put("isFlexibleHours", s.isFlexibleHours)
                        put("allocatedHours", s.allocatedHours)
                        put("stationOrRole", s.stationOrRole)
                        put("status", s.status.name)
                        put("notes", s.notes)
                    })
                }
            })
        }
        return root.toString(2)
    }

    fun decode(jsonString: String): WonderlandBackupSnapshot {
        val root = JSONObject(jsonString)
        val buildingsArr = root.optJSONArray("buildings") ?: JSONArray()
        val buildings = (0 until buildingsArr.length()).map { idx ->
            val o = buildingsArr.getJSONObject(idx)
            BuildingEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                code = o.optString("code", ""),
                address = o.optString("address", ""),
                notes = o.optString("notes", ""),
                colorHex = o.optLong("colorHex", 0xFF6A1B9AL),
                isActive = o.optBoolean("isActive", true)
            )
        }

        val contactsArr = root.optJSONArray("contacts") ?: JSONArray()
        val contacts = (0 until contactsArr.length()).map { idx ->
            val o = contactsArr.getJSONObject(idx)
            ContactEntity(
                id = o.getLong("id"),
                type = ContactType.valueOf(o.getString("type")),
                fullName = o.getString("fullName"),
                roleOrCompany = o.optString("roleOrCompany", ""),
                phone = o.optString("phone", ""),
                email = o.optString("email", ""),
                buildingId = if (o.has("buildingId") && !o.isNull("buildingId")) o.getLong("buildingId") else null,
                cardSuit = runCatching { CardSuit.valueOf(o.optString("cardSuit", "SPADE")) }.getOrDefault(CardSuit.SPADE),
                notes = o.optString("notes", ""),
                isFavorite = o.optBoolean("isFavorite", false)
            )
        }

        val tasksArr = root.optJSONArray("tasks") ?: JSONArray()
        val tasks = (0 until tasksArr.length()).map { idx ->
            val o = tasksArr.getJSONObject(idx)
            TaskEntity(
                id = o.getLong("id"),
                title = o.getString("title"),
                description = o.optString("description", ""),
                assignedEmployeeId = if (o.has("assignedEmployeeId") && !o.isNull("assignedEmployeeId")) o.getLong("assignedEmployeeId") else null,
                customerId = if (o.has("customerId") && !o.isNull("customerId")) o.getLong("customerId") else null,
                buildingId = if (o.has("buildingId") && !o.isNull("buildingId")) o.getLong("buildingId") else null,
                roomOrZone = o.optString("roomOrZone", ""),
                dueDateEpochDay = o.getLong("dueDateEpochDay"),
                startTimeMinutes = if (o.has("startTimeMinutes") && !o.isNull("startTimeMinutes")) o.getInt("startTimeMinutes") else null,
                endTimeMinutes = if (o.has("endTimeMinutes") && !o.isNull("endTimeMinutes")) o.getInt("endTimeMinutes") else null,
                priority = runCatching { TaskPriority.valueOf(o.optString("priority", "MEDIUM")) }.getOrDefault(TaskPriority.MEDIUM),
                status = runCatching { TaskStatus.valueOf(o.optString("status", "TODO")) }.getOrDefault(TaskStatus.TODO),
                recurrence = runCatching { RecurrenceRule.valueOf(o.optString("recurrence", "NONE")) }.getOrDefault(RecurrenceRule.NONE),
                linkedCalendarEventId = if (o.has("linkedCalendarEventId") && !o.isNull("linkedCalendarEventId")) o.getLong("linkedCalendarEventId") else null
            )
        }

        val shiftsArr = root.optJSONArray("shifts") ?: JSONArray()
        val shifts = (0 until shiftsArr.length()).map { idx ->
            val o = shiftsArr.getJSONObject(idx)
            ShiftScheduleEntity(
                id = o.getLong("id"),
                employeeId = o.getLong("employeeId"),
                buildingId = o.getLong("buildingId"),
                dateEpochDay = o.getLong("dateEpochDay"),
                startMinutes = o.optInt("startMinutes", 9 * 60),
                endMinutes = o.optInt("endMinutes", 17 * 60),
                isFlexibleHours = o.optBoolean("isFlexibleHours", false),
                allocatedHours = o.optDouble("allocatedHours", 0.0),
                stationOrRole = o.optString("stationOrRole", ""),
                status = runCatching { ShiftStatus.valueOf(o.optString("status", "SCHEDULED")) }.getOrDefault(ShiftStatus.SCHEDULED),
                notes = o.optString("notes", "")
            )
        }

        return WonderlandBackupSnapshot(
            schemaVersion = root.optInt("schemaVersion", 2),
            exportedAtMillis = root.optLong("exportedAtMillis", System.currentTimeMillis()),
            buildings = buildings,
            contacts = contacts,
            tasks = tasks,
            shifts = shifts
        )
    }
}

class WonderlandBackupManager(private val db: WonderlandDatabase) {
    suspend fun exportJson(): String {
        val snapshot = WonderlandBackupSnapshot(
            buildings = db.buildingDao().getAllBuildingsOnce(),
            contacts = db.contactDao().getAllContactsOnce(),
            tasks = db.taskDao().getAllTasksOnce(),
            shifts = db.shiftScheduleDao().getAllShiftsOnce()
        )
        return WonderlandBackupCodec.encode(snapshot)
    }

    suspend fun restoreFromJson(jsonString: String): WonderlandBackupSnapshot {
        val snapshot = WonderlandBackupCodec.decode(jsonString)
        db.withTransaction {
            db.shiftScheduleDao().deleteAll()
            db.taskDao().deleteAll()
            db.contactDao().deleteAll()
            db.buildingDao().deleteAll()

            snapshot.buildings.forEach { db.buildingDao().insert(it) }
            snapshot.contacts.forEach { db.contactDao().insert(it) }
            snapshot.tasks.forEach { db.taskDao().insert(it) }
            snapshot.shifts.forEach { db.shiftScheduleDao().insert(it) }
        }
        return snapshot
    }
}
