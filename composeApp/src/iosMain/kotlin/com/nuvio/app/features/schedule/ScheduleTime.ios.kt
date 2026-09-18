package com.nuvio.app.features.schedule

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

@OptIn(ExperimentalForeignApi::class)
internal actual object ScheduleTime {
    private val calendar = NSCalendar.currentCalendar
    private val timeFormatter = NSDateFormatter().apply {
        dateFormat = "h:mm a"
        timeZone = NSTimeZone.localTimeZone
    }
    private val dateFormatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterShortStyle
        timeZone = NSTimeZone.localTimeZone
    }
    private val weekdayFormatter = NSDateFormatter().apply {
        dateFormat = "EEEE"
        timeZone = NSTimeZone.localTimeZone
    }

    actual fun startOfDayEpochSec(epochSec: Long): Long {
        val date = NSDate(epochSec.toDouble())
        val components = calendar.components(NSCalendarUnitCalendar or NSCalendarUnitDay, date)
        components.hour = 0
        components.minute = 0
        components.second = 0
        return calendar.dateFromComponents(components).timeIntervalSince1970.toLong()
    }

    actual fun timeLabel(epochSec: Long): String =
        timeFormatter.stringFromDate(NSDate(epochSec.toDouble()))

    actual fun dayLabel(startEpochSec: Long, nowEpochMs: Long): String {
        val dayDate = NSDate(startEpochSec.toDouble())
        val nowDate = NSDate(nowEpochMs / 1000.0)
        val startOfToday = startOfDay(nowDate)
        val dayDelta = daysBetween(startOfToday, dayDate)
        return when (dayDelta) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            in 2..6 -> weekdayFormatter.stringFromDate(dayDate)
            else -> dateFormatter.stringFromDate(dayDate)
        }
    }

    private fun startOfDay(date: NSDate): NSDate {
        val components = calendar.components(NSCalendarUnitCalendar or NSCalendarUnitDay, date)
        components.hour = 0
        components.minute = 0
        components.second = 0
        return calendar.dateFromComponents(components) ?: date
    }

    private fun daysBetween(from: NSDate, to: NSDate): Long =
        (to.timeIntervalSinceDate(from) / 86_400.0).let { Math.round(it) }

    private fun NSDate.timeIntervalSinceDate(other: NSDate): Double =
        this.timeIntervalSince1970 - other.timeIntervalSince1970
}
