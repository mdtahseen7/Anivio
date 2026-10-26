package com.nuvio.app.features.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal actual object ScheduleTime {
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val weekdayShortFormatter = DateTimeFormatter.ofPattern("EEE")
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    private fun zone(): ZoneId = ZoneId.systemDefault()

    actual fun startOfDayEpochSec(epochSec: Long): Long =
        Instant.ofEpochSecond(epochSec).atZone(zone()).toLocalDate()
            .atStartOfDay(zone()).toEpochSecond()

    actual fun timeLabel(epochSec: Long): String =
        Instant.ofEpochSecond(epochSec).atZone(zone()).format(timeFormatter)

    actual fun dayLabel(startEpochSec: Long, nowEpochMs: Long): String {
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone()).toLocalDate()
        val day = Instant.ofEpochSecond(startEpochSec).atZone(zone()).toLocalDate()
        return when (ChronoUnit.DAYS.between(today, day)) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            in 2..6 -> day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            else -> day.format(dateFormatter)
        }
    }

    actual fun weekdayShortLabel(startEpochSec: Long): String =
        Instant.ofEpochSecond(startEpochSec).atZone(zone()).format(weekdayShortFormatter)

    actual fun dayNumber(startEpochSec: Long): String =
        Instant.ofEpochSecond(startEpochSec).atZone(zone()).dayOfMonth.toString()

    actual fun monthYearLabel(startEpochSec: Long): String =
        Instant.ofEpochSecond(startEpochSec).atZone(zone()).format(monthYearFormatter)

    actual fun dayStartOffset(startEpochSec: Long, dayOffset: Int): Long =
        Instant.ofEpochSecond(startEpochSec).atZone(zone()).toLocalDate()
            .plusDays(dayOffset.toLong())
            .atStartOfDay(zone())
            .toEpochSecond()
}
