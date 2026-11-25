package com.wonderland.taschasplanner.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.wonderland.taschasplanner.data.local.RecurrenceRule
import com.wonderland.taschasplanner.data.model.CalendarEvent
import com.wonderland.taschasplanner.data.model.DeviceCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

class GoogleCalendarRepository(private val context: Context) {

    fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Reads all calendars synced on the Android device (prioritizing Google accounts).
     */
    suspend fun fetchDeviceCalendars(googleOnly: Boolean = false): List<DeviceCalendar> =
        withContext(Dispatchers.IO) {
            if (!hasReadPermission()) return@withContext emptyList()

            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
            )
            val selection = if (googleOnly) {
                "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
            } else {
                null
            }
            val selectionArgs = if (googleOnly) arrayOf("com.google") else null

            val results = mutableListOf<DeviceCalendar>()
            try {
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    val accNameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                    val accTypeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                    val colorIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                    val accessIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

                    while (cursor.moveToNext()) {
                        val access = cursor.getInt(accessIdx)
                        results.add(
                            DeviceCalendar(
                                id = cursor.getLong(idIdx),
                                displayName = cursor.getString(nameIdx) ?: "Google Calendar",
                                accountName = cursor.getString(accNameIdx) ?: "",
                                accountType = cursor.getString(accTypeIdx) ?: "",
                                colorInt = cursor.getInt(colorIdx),
                                isWritable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                return@withContext emptyList()
            }
            results
        }

    /**
     * Queries CalendarContract.Instances to expand recurring Google Calendar events across [startDate, endDate].
     */
    suspend fun fetchEventsInRange(
        startDate: LocalDate,
        endDate: LocalDate,
        calendarNamesById: Map<Long, String>
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()

        val zone = ZoneId.systemDefault()
        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, startMillis)
        ContentUris.appendId(uriBuilder, endMillis)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR
        )

        val events = mutableListOf<CalendarEvent>()
        try {
            context.contentResolver.query(
                uriBuilder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val eventIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val calIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val descIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val colorIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)

                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(calIdIdx)
                    val title = cursor.getString(titleIdx)?.takeIf { it.isNotBlank() } ?: "(Untitled Event)"
                    events.add(
                        CalendarEvent(
                            id = cursor.getLong(eventIdIdx),
                            calendarId = calId,
                            calendarName = calendarNamesById[calId] ?: "Google Calendar",
                            title = title,
                            description = cursor.getString(descIdx) ?: "",
                            location = cursor.getString(locIdx) ?: "",
                            startMillis = cursor.getLong(beginIdx),
                            endMillis = cursor.getLong(endIdx),
                            allDay = cursor.getInt(allDayIdx) == 1,
                            colorInt = cursor.getInt(colorIdx)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        }
        events
    }

    /**
     * Pushes a task directly into a specific writable Google Calendar on the device.
     * Handles both timed and all-day (UTC) events as well as recurring RRULEs.
     */
    suspend fun insertEvent(
        calendarId: Long,
        title: String,
        description: String,
        location: String,
        date: LocalDate,
        startMinutes: Int?,
        endMinutes: Int?,
        recurrence: RecurrenceRule = RecurrenceRule.NONE
    ): Long? = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext null

        val values = buildEventContentValues(
            calendarId = calendarId,
            title = title,
            description = description,
            location = location,
            date = date,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            recurrence = recurrence
        )
        try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Updates an existing linked Google Calendar event when a task is edited in the app.
     */
    suspend fun updateEvent(
        eventId: Long,
        title: String,
        description: String,
        location: String,
        date: LocalDate,
        startMinutes: Int?,
        endMinutes: Int?,
        recurrence: RecurrenceRule = RecurrenceRule.NONE
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false

        val values = buildEventContentValues(
            calendarId = null,
            title = title,
            description = description,
            location = location,
            date = date,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            recurrence = recurrence
        )
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Removes a linked Google Calendar event when its task is deleted in the app.
     */
    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun buildEventContentValues(
        calendarId: Long?,
        title: String,
        description: String,
        location: String,
        date: LocalDate,
        startMinutes: Int?,
        endMinutes: Int?,
        recurrence: RecurrenceRule
    ): ContentValues {
        val isAllDay = startMinutes == null
        val startMillis = if (isAllDay) {
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            date.atStartOfDay(ZoneId.systemDefault()).plusMinutes(startMinutes.toLong()).toInstant().toEpochMilli()
        }
        val durationMins = if (isAllDay) {
            24 * 60
        } else {
            ((endMinutes ?: (startMinutes + 60)) - startMinutes).coerceAtLeast(15)
        }
        val endMillis = if (isAllDay) {
            date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            startMillis + durationMins * 60_000L
        }
        val rrule = toRRule(recurrence)

        return ContentValues().apply {
            if (calendarId != null) put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(
                CalendarContract.Events.EVENT_TIMEZONE,
                if (isAllDay) "UTC" else TimeZone.getDefault().id
            )
            if (rrule != null) {
                put(CalendarContract.Events.RRULE, rrule)
                put(CalendarContract.Events.DURATION, if (isAllDay) "P1D" else "PT${durationMins}M")
                putNull(CalendarContract.Events.DTEND)
            } else {
                put(CalendarContract.Events.DTEND, endMillis)
                putNull(CalendarContract.Events.RRULE)
                putNull(CalendarContract.Events.DURATION)
            }
        }
    }

    companion object {
        fun toRRule(rule: RecurrenceRule): String? = when (rule) {
            RecurrenceRule.NONE -> null
            RecurrenceRule.DAILY -> "FREQ=DAILY"
            RecurrenceRule.WEEKLY -> "FREQ=WEEKLY"
            RecurrenceRule.MONTHLY -> "FREQ=MONTHLY"
        }
    }

    /**
     * Optional sample Google Calendar events so users on emulators without a Google account can preview the full planner.
     */
    fun buildSampleCalendarEvents(today: LocalDate = LocalDate.now()): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        fun at(dayOffset: Long, hour: Int, min: Int): Long =
            today.plusDays(dayOffset).atTime(hour, min).atZone(zone).toInstant().toEpochMilli()

        return listOf(
            CalendarEvent(
                id = -101L,
                calendarId = -1L,
                calendarName = "Tascha's Google Cal (Sample)",
                title = "Weekly Leadership Tea & Standup",
                location = "Main Manor • Boardroom",
                startMillis = at(0, 9, 30),
                endMillis = at(0, 10, 15),
                colorInt = 0xFF1A73E8.toInt(),
                isSamplePreview = true
            ),
            CalendarEvent(
                id = -102L,
                calendarId = -1L,
                calendarName = "Tascha's Google Cal (Sample)",
                title = "Vendor Walkthrough — Catering & Floral",
                location = "Tea House Annex",
                startMillis = at(0, 13, 0),
                endMillis = at(0, 14, 0),
                colorInt = 0xFF0B8043.toInt(),
                isSamplePreview = true
            ),
            CalendarEvent(
                id = -103L,
                calendarId = -1L,
                calendarName = "Tascha's Google Cal (Sample)",
                title = "Quarterly Building Safety Review",
                location = "Queen's Conservatory",
                startMillis = at(2, 11, 0),
                endMillis = at(2, 12, 0),
                colorInt = 0xFF8E24AA.toInt(),
                isSamplePreview = true
            )
        )
    }
}
