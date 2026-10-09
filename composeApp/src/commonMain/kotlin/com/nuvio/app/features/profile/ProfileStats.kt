package com.nuvio.app.features.profile

/**
 * Fully-computed profile stats, ready to render. Produced by [ProfileStatsEngine] from local watch
 * history (and optionally enriched with AniList statistics). Everything here is derived — never the
 * source of truth — so it can be recomputed cheaply whenever watch history changes.
 */
data class ProfileStats(
    val level: ProfileLevel,
    /** A transparent, deterministic explanation of every point in [level].xp. */
    val xpBreakdown: ProfileXpBreakdown = ProfileXpBreakdown(),
    val streak: ProfileStreak,
    val totals: ProfileTotals,
    val heatmap: List<HeatmapDay>,
    val hourHistogram: List<Int>,      // size 24, episodes marked in each local hour
    val weekdayHistogram: List<Int>,   // size 7, Monday-first
    val habit: WatchHabitType,
    val achievements: List<Achievement>,
    val recentDays: List<HeatmapDay> = heatmap,
) {
    companion object {
        val EMPTY = ProfileStats(
            level = ProfileLevel(level = 1, title = ProfileRank.NEWCOMER.title, xp = 0, xpIntoLevel = 0, xpForNextLevel = ProfileLevelMath.xpForLevel(2)),
            xpBreakdown = ProfileXpBreakdown(),
            streak = ProfileStreak(),
            totals = ProfileTotals(),
            heatmap = emptyList(),
            hourHistogram = List(24) { 0 },
            weekdayHistogram = List(7) { 0 },
            habit = WatchHabitType.UNKNOWN,
            achievements = emptyList(),
        )
    }
}

/**
 * XP always comes from a source that can be re-evaluated.  This makes a history correction lower
 * watch/completion XP correctly, while historical achievement awards remain keyed by achievement
 * id and therefore can never be granted twice by repeated flow emissions.
 */
data class ProfileXpBreakdown(
    val watchMinutes: Long = 0,
    val episodeCompletions: Long = 0,
    val seriesCompletions: Long = 0,
    val streakMilestones: Long = 0,
    val achievementRewards: Long = 0,
) {
    val total: Long
        get() = profileXpSum(
            watchMinutes,
            episodeCompletions,
            seriesCompletions,
            streakMilestones,
            achievementRewards,
        )
}

/** Addition for presentation and progression XP; corrupt/huge imported values never wrap negative. */
internal fun profileXpSum(vararg values: Long): Long {
    var total = 0L
    values.forEach { value ->
        val safeValue = value.coerceAtLeast(0)
        if (Long.MAX_VALUE - total < safeValue) return Long.MAX_VALUE
        total += safeValue
    }
    return total
}

/** XP-driven level plus a named rank derived from level, streak and completion. */
data class ProfileLevel(
    val level: Int,
    val title: String,
    val xp: Long,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    /** Cosmetic rewards are derived from rank; no separate mutable rank state is kept. */
    val rewards: List<ProfileCosmeticReward> = ProfileRank.forLevel(level).rewards,
) {
    /** 0..1 progress toward the next level. */
    val progress: Float
        get() = if (xpForNextLevel <= 0) 1f else (xpIntoLevel.toFloat() / xpForNextLevel.toFloat()).coerceIn(0f, 1f)
}

enum class ProfileCosmeticKind { RANK_EMBLEM, PROFILE_FRAME, TITLE }

data class ProfileCosmeticReward(
    val id: String,
    val kind: ProfileCosmeticKind,
    val label: String,
)

data class ProfileStreak(
    val currentDays: Int = 0,
    val longestDays: Int = 0,
    /** True when the current streak survived a missed day via the grace/"freeze" rule. */
    val freezeActive: Boolean = false,
    /** Local epoch-day of the most recent watch, or null when there's no history. */
    val lastActiveEpochDay: Long? = null,
    /** True when the user has already watched something today. */
    val watchedToday: Boolean = false,
)

data class ProfileTotals(
    val episodesWatched: Int = 0,
    val seriesTouched: Int = 0,
    val seriesCompleted: Int = 0,
    val moviesWatched: Int = 0,
    val minutesWatched: Long = 0,
    val activeDays: Int = 0,
    val completionRate: Float = 0f,
    val longestSeriesEpisodes: Int = 0,
    val longestSeriesName: String? = null,
    /** Average episodes per active day. */
    val episodesPerActiveDay: Float = 0f,
    val firstWatchEpochMs: Long? = null,
)

/** One cell of the activity heatmap. */
data class HeatmapDay(
    val epochDay: Long,
    val count: Int,
    /** 0 = no activity, 1..4 intensity buckets for coloring. */
    val intensity: Int,
)

/** A derived "when do you watch" personality label. */
enum class WatchHabitType(val label: String, val blurb: String) {
    NIGHT_OWL("Night Owl", "You do your best watching after dark."),
    EARLY_BIRD("Early Bird", "Mornings are your prime anime hours."),
    AFTERNOON_DELIGHT("Afternoon Watcher", "Midday is when you press play."),
    PRIME_TIME("Prime-Time Viewer", "Evenings are made for episodes."),
    WEEKEND_WARRIOR("Weekend Warrior", "You save the binges for the weekend."),
    UNKNOWN("Just Getting Started", "Watch a few episodes to reveal your style."),
}
