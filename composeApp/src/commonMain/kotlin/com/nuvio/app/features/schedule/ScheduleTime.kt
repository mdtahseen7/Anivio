package com.nuvio.app.features.schedule

/**
 * Local-calendar access for the schedule page. There is no shared datetime library in commonMain,
 * so Android implements this with java.time and iOS with NSDateFormatter/NSTimeZone.
 */
internal expect object ScheduleTime {
    /** Local midnight of the day containing [epochSec], as epoch seconds. */
    fun startOfDayEpochSec(epochSec: Long): Long

    /** Wall-clock label like "7:30 PM" in the device timezone. */
    fun timeLabel(epochSec: Long): String

    /** Day header label: Today / Tomorrow / Yesterday, the weekday within a week, else "MMM d". */
    fun dayLabel(startEpochSec: Long, nowEpochMs: Long): String

    /** Compact weekday label used by the calendar strip, such as "Mon". */
    fun weekdayShortLabel(startEpochSec: Long): String

    /** Local day number used by the calendar strip. */
    fun dayNumber(startEpochSec: Long): String

    /** Month and year label used above the calendar strip. */
    fun monthYearLabel(startEpochSec: Long): String

    /** Local midnight offset by a number of calendar days, preserving daylight-saving changes. */
    fun dayStartOffset(startEpochSec: Long, dayOffset: Int): Long
}
