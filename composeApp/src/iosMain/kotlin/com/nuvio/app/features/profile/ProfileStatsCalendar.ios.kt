package com.nuvio.app.features.profile

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

@OptIn(ExperimentalForeignApi::class)
internal actual object ProfileStatsCalendar {
    private val calendar = NSCalendar.currentCalendar
    private val monthDay = NSDateFormatter().apply {
        dateFormat = "MMM d"
        timeZone = NSTimeZone.localTimeZone
    }
    private val monthDayYear = NSDateFormatter().apply {
        dateFormat = "MMM d, yyyy"
        timeZone = NSTimeZone.localTimeZone
    }

    private const val SECONDS_PER_DAY = 86_400.0

    actual fun fieldsOf(epochMs: Long): LocalCalendarFields {
        val date = NSDate(epochMs / 1000.0)
        val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
            NSCalendarUnitHour or NSCalendarUnitWeekday
        val c = calendar.components(units, date)
        // NSDateComponents.weekday: 1 = Sunday .. 7 = Saturday -> normalize to 0 = Monday .. 6 = Sunday.
        val mondayFirst = ((c.weekday.toInt() + 5) % 7)
        return LocalCalendarFields(
            epochDay = epochDayOf(date),
            hour = c.hour.toInt(),
            weekday = mondayFirst,
            year = c.year.toInt(),
        )
    }

    actual fun todayEpochDay(nowEpochMs: Long): Long = epochDayOf(NSDate(nowEpochMs / 1000.0))

    actual fun dayLabel(epochMs: Long, includeYear: Boolean): String {
        val date = NSDate(epochMs / 1000.0)
        return (if (includeYear) monthDayYear else monthDay).stringFromDate(date)
    }

    /** Local-midnight ordinal: floor the start-of-day epoch to whole days. */
    private fun epochDayOf(date: NSDate): Long {
        val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay
        val c = calendar.components(units, date)
        c.hour = 0
        c.minute = 0
        c.second = 0
        val startOfDay = calendar.dateFromComponents(c) ?: date
        return kotlin.math.floor(startOfDay.timeIntervalSince1970 / SECONDS_PER_DAY).toLong()
    }
}
