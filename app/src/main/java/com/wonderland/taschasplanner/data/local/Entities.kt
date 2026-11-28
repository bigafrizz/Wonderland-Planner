package com.wonderland.taschasplanner.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class ContactType { EMPLOYEE, CUSTOMER }

enum class TaskPriority(val displayLabel: String, val wonderlandLabel: String) {
    LOW("Low", "Dormouse Pace"),
    MEDIUM("Medium", "Tea Time"),
    HIGH("High", "White Rabbit"),
    URGENT("Urgent", "Queen's Decree")
}

enum class TaskStatus(val displayLabel: String) {
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    BLOCKED("Blocked"),
    DONE("Done");

    fun next(): TaskStatus = when (this) {
        TODO -> IN_PROGRESS
        IN_PROGRESS -> DONE
        BLOCKED -> IN_PROGRESS
        DONE -> TODO
    }
}

enum class RecurrenceRule(val displayLabel: String) {
    NONE("One-Time"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

enum class ShiftStatus(val displayLabel: String) {
    SCHEDULED("Scheduled"),
    CHECKED_IN("Checked In"),
    COMPLETED("Completed"),
    ABSENT("Absent");

    fun next(): ShiftStatus = when (this) {
        SCHEDULED -> CHECKED_IN
        CHECKED_IN -> COMPLETED
        COMPLETED -> SCHEDULED
        ABSENT -> SCHEDULED
    }
}

enum class CardSuit { SPADE, HEART, DIAMOND, CLUB }

enum class ThemeMode(val displayLabel: String) {
    STORYBOOK_LIGHT("Storybook Parchment (Light)"),
    CHESHIRE_DARK("Cheshire Twilight (Dark)"),
    SYSTEM("Follow System")
}

enum class CompTimeEntryType(val displayLabel: String) {
    WORK_DAY("Daily Time In/Out"),
    COMP_USED("Comp Time Used")
}

@Entity(tableName = "buildings")
data class BuildingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val code: String,
    val address: String = "",
    val notes: String = "",
    val colorHex: Long = 0xFF6A1B9AL,
    val isActive: Boolean = true
)

@Entity(
    tableName = "contacts",
    foreignKeys = [
        ForeignKey(
            entity = BuildingEntity::class,
            parentColumns = ["id"],
            childColumns = ["buildingId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("buildingId")]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: ContactType,
    val fullName: String,
    val roleOrCompany: String = "",
    val phone: String = "",
    val email: String = "",
    val buildingId: Long? = null,
    val cardSuit: CardSuit = CardSuit.SPADE,
    val notes: String = "",
    val isFavorite: Boolean = false,
    val birthdayMonth: Int? = null,
    val birthdayDay: Int? = null,
    val linkedBirthdayEventId: Long? = null
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["assignedEmployeeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = BuildingEntity::class,
            parentColumns = ["id"],
            childColumns = ["buildingId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("assignedEmployeeId"), Index("customerId"), Index("buildingId"), Index("dueDateEpochDay")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val assignedEmployeeId: Long? = null,
    val customerId: Long? = null,
    val buildingId: Long? = null,
    val roomOrZone: String = "",
    val dueDateEpochDay: Long,
    val startTimeMinutes: Int? = null,
    val endTimeMinutes: Int? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val recurrence: RecurrenceRule = RecurrenceRule.NONE,
    val linkedCalendarEventId: Long? = null
)

@Entity(
    tableName = "shift_schedules",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BuildingEntity::class,
            parentColumns = ["id"],
            childColumns = ["buildingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("employeeId"), Index("buildingId"), Index("dateEpochDay")]
)
data class ShiftScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val employeeId: Long,
    val buildingId: Long,
    val dateEpochDay: Long,
    val startMinutes: Int = 9 * 60,
    val endMinutes: Int = 17 * 60,
    val isFlexibleHours: Boolean = false,
    val allocatedHours: Double = 0.0,
    val stationOrRole: String = "",
    val status: ShiftStatus = ShiftStatus.SCHEDULED,
    val notes: String = ""
) {
    fun effectiveHours(): Double = if (isFlexibleHours) {
        allocatedHours.coerceAtLeast(0.0)
    } else {
        ((endMinutes - startMinutes).coerceAtLeast(0)) / 60.0
    }
}

@Entity(tableName = "calendar_prefs")
data class CalendarPrefEntity(
    @PrimaryKey val calendarId: Long,
    val accountName: String,
    val displayName: String,
    val colorInt: Int,
    val isVisible: Boolean = true
)

@Entity(
    tableName = "comp_time_entries",
    indices = [Index("dateEpochDay")]
)
data class CompTimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateEpochDay: Long,
    val entryType: CompTimeEntryType = CompTimeEntryType.WORK_DAY,
    val timeInMinutes: Int? = null,
    val timeOutMinutes: Int? = null,
    val breakMinutes: Int = 30,
    val usedCompHours: Double = 0.0,
    val notes: String = ""
)

@Entity(
    tableName = "note_todos",
    indices = [Index("isCompleted"), Index("updatedAtMillis")]
)
data class NoteTodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val header: String,
    val body: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val isPinned: Boolean = false,
    val accentColorHex: Long = 0xFF6A1B9AL,
    val linkedTaskId: Long? = null
)

class WonderlandTypeConverters {
    @TypeConverter fun fromContactType(v: ContactType): String = v.name
    @TypeConverter fun toContactType(v: String): ContactType = ContactType.valueOf(v)

    @TypeConverter fun fromTaskPriority(v: TaskPriority): String = v.name
    @TypeConverter fun toTaskPriority(v: String): TaskPriority = TaskPriority.valueOf(v)

    @TypeConverter fun fromTaskStatus(v: TaskStatus): String = v.name
    @TypeConverter fun toTaskStatus(v: String): TaskStatus = TaskStatus.valueOf(v)

    @TypeConverter fun fromRecurrence(v: RecurrenceRule): String = v.name
    @TypeConverter fun toRecurrence(v: String): RecurrenceRule = RecurrenceRule.valueOf(v)

    @TypeConverter fun fromShiftStatus(v: ShiftStatus): String = v.name
    @TypeConverter fun toShiftStatus(v: String): ShiftStatus = ShiftStatus.valueOf(v)

    @TypeConverter fun fromCardSuit(v: CardSuit): String = v.name
    @TypeConverter fun toCardSuit(v: String): CardSuit = CardSuit.valueOf(v)

    @TypeConverter fun fromCompTimeEntryType(v: CompTimeEntryType): String = v.name
    @TypeConverter fun toCompTimeEntryType(v: String): CompTimeEntryType = CompTimeEntryType.valueOf(v)
}
