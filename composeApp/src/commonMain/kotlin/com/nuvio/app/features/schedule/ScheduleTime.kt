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
}
