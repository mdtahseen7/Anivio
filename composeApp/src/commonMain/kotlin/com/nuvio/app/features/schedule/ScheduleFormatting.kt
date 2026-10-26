package com.nuvio.app.features.schedule

/** A day bucket on the schedule page. */
internal data class ScheduleDayGroup(
    val startEpochSec: Long,
    val label: String,
    val isToday: Boolean,
    val entries: List<ScheduleEntry>,
)

/**
 * Buckets entries by local calendar day, drops anything before today, and orders the groups today
 * first then upcoming days ascending. Past days are never shown — a schedule that flips to backward
 * dates after the upcoming week reads as a bug.
 */
internal fun buildScheduleGroups(entries: List<ScheduleEntry>, nowEpochMs: Long): List<ScheduleDayGroup> {
    val todayStartSec = ScheduleTime.startOfDayEpochSec(nowEpochMs / 1000)
    return entries
        .groupBy { entry -> ScheduleTime.startOfDayEpochSec(entry.airingAtEpochSec) }
        .filterKeys { startSec -> startSec >= todayStartSec }
        .map { (startSec, groupEntries) ->
            ScheduleDayGroup(
                startEpochSec = startSec,
                label = ScheduleTime.dayLabel(startSec, nowEpochMs),
                isToday = startSec == todayStartSec,
                entries = groupEntries.sortedBy(ScheduleEntry::airingAtEpochSec),
            )
        }
        .sortedBy(ScheduleDayGroup::startEpochSec)
}

/** Builds a complete seven-day calendar, retaining empty days so the layout stays calendar-like. */
internal fun buildScheduleCalendarDays(
    entries: List<ScheduleEntry>,
    nowEpochMs: Long,
): List<ScheduleDayGroup> {
    val todayStartSec = ScheduleTime.startOfDayEpochSec(nowEpochMs / 1000)
    val populatedDays = buildScheduleGroups(entries, nowEpochMs)
        .associateBy(ScheduleDayGroup::startEpochSec)
    return (0 until 7).map { offset ->
        val startSec = ScheduleTime.dayStartOffset(todayStartSec, offset)
        populatedDays[startSec] ?: ScheduleDayGroup(
            startEpochSec = startSec,
            label = ScheduleTime.dayLabel(startSec, nowEpochMs),
            isToday = offset == 0,
            entries = emptyList(),
        )
    }
}

internal fun formatScheduleTime(airingAtEpochSec: Long): String = ScheduleTime.timeLabel(airingAtEpochSec)
