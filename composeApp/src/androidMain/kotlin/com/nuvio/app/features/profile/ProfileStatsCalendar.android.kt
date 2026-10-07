package com.nuvio.app.features.profile

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal actual object ProfileStatsCalendar {
    private val monthDay = DateTimeFormatter.ofPattern("MMM d")
    private val monthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy")

    private fun zone(): ZoneId = ZoneId.systemDefault()

    actual fun fieldsOf(epochMs: Long): LocalCalendarFields {
        val dt = Instant.ofEpochMilli(epochMs).atZone(zone())
        return LocalCalendarFields(
            epochDay = dt.toLocalDate().toEpochDay(),
            hour = dt.hour,
            // java.time DayOfWeek: Monday=1..Sunday=7 -> normalize to 0..6 Monday-first.
            weekday = dt.dayOfWeek.value - 1,
            year = dt.year,
        )
    }

    actual fun todayEpochDay(nowEpochMs: Long): Long =
        Instant.ofEpochMilli(nowEpochMs).atZone(zone()).toLocalDate().toEpochDay()

    actual fun dayLabel(epochMs: Long, includeYear: Boolean): String {
        val dt = Instant.ofEpochMilli(epochMs).atZone(zone())
        return dt.format(if (includeYear) monthDayYear else monthDay)
    }
}
