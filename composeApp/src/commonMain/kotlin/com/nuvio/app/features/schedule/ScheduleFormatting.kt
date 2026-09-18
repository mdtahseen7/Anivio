package com.nuvio.app.features.schedule

/** A day bucket on the schedule page. */
internal data class ScheduleDayGroup(
    val startEpochSec: Long,
    val label: String,
    val isToday: Boolean,
    val entries: List<ScheduleEntry>,
)

/**
 * Buckets entries by local calendar day and orders the groups: today first, then the upcoming days
 * in ascending order, then the past few days (closest first).
 */
internal fun buildScheduleGroups(entries: List<ScheduleEntry>, nowEpochMs: Long): List<ScheduleDayGroup> {
    val todayStartSec = ScheduleTime.startOfDayEpochSec(nowEpochMs / 1000)
    return entries
        .groupBy { entry -> ScheduleTime.startOfDayEpochSec(entry.airingAtEpochSec) }
        .map { (startSec, groupEntries) ->
            ScheduleDayGroup(
                startEpochSec = startSec,
                label = ScheduleTime.dayLabel(startSec, nowEpochMs),
                isToday = startSec == todayStartSec,
                entries = groupEntries.sortedBy(ScheduleEntry::airingAtEpochSec),
            )
        }
        .sortedWith(
            compareByDescending<ScheduleDayGroup> { it.isToday }
                .thenByDescending { it.startEpochSec >= todayStartSec }
                .thenBy { group ->
                    if (group.startEpochSec >= todayStartSec) group.startEpochSec else -group.startEpochSec
                },
        )
}

internal fun formatScheduleTime(airingAtEpochSec: Long): String = ScheduleTime.timeLabel(airingAtEpochSec)
