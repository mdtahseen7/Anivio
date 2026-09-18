package com.nuvio.app.features.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal actual object ScheduleTime {
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

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
}
