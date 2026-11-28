package com.wonderland.taschasplanner.data.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.wonderland.taschasplanner.data.local.RecurrenceRule
import com.wonderland.taschasplanner.data.model.CalendarEvent
import com.wonderland.taschasplanner.data.model.DeviceCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
     * Registers a ContentObserver on CalendarContract so external changes or overnight
     * Google Calendar background syncs notify the app immediately.
     */
    fun registerCalendarObserver(onChanged: () -> Unit): ContentObserver? {
        if (!hasReadPermission()) return null
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onChanged()
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onChanged()
            }
        }
        return runCatching {
            context.contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI,
                true,
                observer
            )
            observer
        }.getOrNull()
    }

    fun unregisterCalendarObserver(observer: ContentObserver?) {
        if (observer == null) return
        runCatching {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    /**
     * Nudges Android's sync adapter for connected Google accounts so stale local calendar
     * caches refresh from Google's servers after overnight Doze/idle periods.
     */
    fun requestAccountCalendarSync(calendars: List<DeviceCalendar>) {
        val accounts = calendars
            .filter { it.accountName.isNotBlank() && it.accountType.isNotBlank() }
            .distinctBy { it.accountName to it.accountType }

        accounts.forEach { cal ->
            runCatching {
                val account = Account(cal.accountName, cal.accountType)
                val extras = Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                }
                ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
            }
        }
    }

    /**
     * Reads all active (non-deleted) calendars synced on the Android device.
     * Retries once after a brief backoff if the CalendarProvider binder hiccups after Doze.
     */
    suspend fun fetchDeviceCalendars(googleOnly: Boolean = false): List<DeviceCalendar> =
        withContext(Dispatchers.IO) {
            if (!hasReadPermission()) return@withContext emptyList()
            retryOnTransientProviderError { queryDeviceCalendarsOnce(googleOnly) }
        }

    private fun queryDeviceCalendarsOnce(googleOnly: Boolean): List<DeviceCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val selection = if (googleOnly) {
            "${CalendarContract.Calendars.DELETED} = 0 AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        } else {
            "${CalendarContract.Calendars.DELETED} = 0"
        }
        val selectionArgs = if (googleOnly) arrayOf("com.google") else null

        val results = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accNameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val accTypeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            val colorIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
            val accessIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

            if (idIdx < 0) return results

            while (cursor.moveToNext()) {
                val access = if (accessIdx >= 0) cursor.getInt(accessIdx) else 0
                results.add(
                    DeviceCalendar(
                        id = cursor.getLong(idIdx),
                        displayName = (if (nameIdx >= 0) cursor.getString(nameIdx) else null)
                            ?.takeIf { it.isNotBlank() } ?: "Google Calendar",
                        accountName = if (accNameIdx >= 0) cursor.getString(accNameIdx) ?: "" else "",
                        accountType = if (accTypeIdx >= 0) cursor.getString(accTypeIdx) ?: "" else "",
                        colorInt = if (colorIdx >= 0) cursor.getInt(colorIdx) else 0xFF6A1B9A.toInt(),
                        isWritable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    )
                )
            }
        }
        return results
    }

    /**
     * Queries CalendarContract.Instances to expand recurring Google Calendar events across [startDate, endDate].
     * Retries once after a brief backoff on transient provider/binder errors.
     */
    suspend fun fetchEventsInRange(
        startDate: LocalDate,
        endDate: LocalDate,
        calendarNamesById: Map<Long, String>
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        retryOnTransientProviderError { queryEventsInRangeOnce(startDate, endDate, calendarNamesById) }
    }

    private fun queryEventsInRangeOnce(
        startDate: LocalDate,
        endDate: LocalDate,
        calendarNamesById: Map<Long, String>
    ): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        // Pad by 1 day on each edge so UTC all-day events near month boundaries are never clipped
        val startMillis = startDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()

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
        context.contentResolver.query(
            uriBuilder.build(),
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            val eventIdIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val calIdIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val descIdx = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
            val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
            val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val colorIdx = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)

            if (eventIdIdx < 0 || calIdIdx < 0 || beginIdx < 0 || endIdx < 0) return events

            while (cursor.moveToNext()) {
                val calId = cursor.getLong(calIdIdx)
                val title = (if (titleIdx >= 0) cursor.getString(titleIdx) else null)
                    ?.takeIf { it.isNotBlank() } ?: "(Untitled Event)"
                events.add(
                    CalendarEvent(
                        id = cursor.getLong(eventIdIdx),
                        calendarId = calId,
                        calendarName = calendarNamesById[calId] ?: "Google Calendar",
                        title = title,
                        description = if (descIdx >= 0) cursor.getString(descIdx) ?: "" else "",
                        location = if (locIdx >= 0) cursor.getString(locIdx) ?: "" else "",
                        startMillis = cursor.getLong(beginIdx),
                        endMillis = cursor.getLong(endIdx),
                        allDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false,
                        colorInt = if (colorIdx >= 0) cursor.getInt(colorIdx) else 0xFF6A1B9A.toInt()
                    )
                )
            }
        }
        return events
    }

    private suspend fun <T> retryOnTransientProviderError(block: () -> T): T {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                return block()
            } catch (sec: SecurityException) {
                throw sec
            } catch (e: Exception) {
                lastError = e
                if (attempt == 0) delay(350L)
            }
        }
        throw lastError ?: IllegalStateException("Calendar provider query failed")
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
            RecurrenceRule.YEARLY -> "FREQ=YEARLY"
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
