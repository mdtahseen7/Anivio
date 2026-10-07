package com.nuvio.app.features.profile

/**
 * A single watch event decomposed into local-calendar fields. The profile stats engine works purely
 * on these so streak/heatmap/histogram math can be unit tested without touching platform date APIs.
 *
 * @property epochDay days since the Unix epoch in the device timezone (local midnight ordinal). Two
 *   events on the same local day share an [epochDay]; consecutive days differ by exactly 1.
 * @property hour local hour of day, 0..23.
 * @property weekday local day of week, 0 = Monday .. 6 = Sunday.
 * @property year local calendar year.
 */
data class LocalCalendarFields(
    val epochDay: Long,
    val hour: Int,
    val weekday: Int,
    val year: Int,
)

/**
 * Local-calendar access for profile stats. commonMain has no shared datetime library, so Android
 * backs this with java.time and iOS with NSCalendar (mirrors [com.nuvio.app.features.schedule.ScheduleTime]).
 */
internal expect object ProfileStatsCalendar {
    /** Decompose [epochMs] (UTC millis) into device-local calendar fields. */
    fun fieldsOf(epochMs: Long): LocalCalendarFields

    /** The [LocalCalendarFields.epochDay] of "now". */
    fun todayEpochDay(nowEpochMs: Long): Long

    /** A short "MMM d" / "MMM d, yyyy" label for the local day containing [epochMs]. */
    fun dayLabel(epochMs: Long, includeYear: Boolean = false): String
}
